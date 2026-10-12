package com.hexagram2021.sweeper_maid.gametest;

import com.mojang.authlib.GameProfile;
import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.compat.SMCreateCompat;
import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import com.hexagram2021.sweeper_maid.network.SMDustbinStatePayload;
import com.hexagram2021.sweeper_maid.network.SMDustbinSyncRequestPayload;
import com.hexagram2021.sweeper_maid.network.SMPageChangePayload;
import com.hexagram2021.sweeper_maid.save.SMDustbinContainer;
import com.hexagram2021.sweeper_maid.save.SMDustbinSlot;
import com.hexagram2021.sweeper_maid.save.SMSavedData;
import com.hexagram2021.sweeper_maid.util.SMExperienceOrbs;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import net.neoforged.neoforge.network.negotiation.NegotiationResult;
import net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistration;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 扫帚女仆模组游戏测试类喵~
 * <p>
 * 覆盖垃圾箱改造的核心逻辑：
 * <ul>
 *     <li>大容量堆叠的自定义序列化（>99 存档回归测试）喵~</li>
 *     <li>同种物品合并与满页自动新建喵~</li>
 *     <li>清扫后全局重排与动态页数喵~</li>
 *     <li>玩家单次取走数量钳制（最多 64）喵~</li>
 *     <li>过载堆数统计口径（掉落物堆 + 经验球）与经验球真实经验值喵~</li>
 * </ul>
 * </p>
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(SweeperMaid.MODID)
public final class SMGameTests {
	/**
	 * 取前 n 个非空气物品喵~
	 *
	 * @param n 数量喵~
	 * @return 物品列表喵~
	 */
	private static List<Item> firstItems(int n) {
		List<Item> items = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			items.add(item);
			if (items.size() >= n) {
				break;
			}
		}
		return items;
	}

	/**
	 * 大容量堆叠的序列化回归测试喵~
	 * <p>
	 * 旧版存档格式会把数量钳制到 99；本测试验证 123456 个物品经存档往返后数量不丢失喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testLargeStackSerialization(GameTestHelper helper) {
		HolderLookup.Provider provider = helper.getLevel().registryAccess();
		SMSavedData data = new SMSavedData();
		data.dustbins.add(new SMDustbinContainer());

		ItemStack diamond = new ItemStack(Items.DIAMOND);
		diamond.setCount(123456);
		data.dustbins.get(0).setItem(0, diamond);

		CompoundTag tag = data.save(new CompoundTag(), provider);
		SMSavedData loaded = new SMSavedData(tag, provider);

		ItemStack result = loaded.dustbins.get(0).getItem(0);
		if (result.isEmpty() || !result.is(Items.DIAMOND) || result.getCount() != 123456) {
			helper.fail("Large stack lost after save/load: " + result);
			return;
		}
		helper.succeed();
	}

	/**
	 * 同种物品合并与满页自动新建测试喵~
	 * <p>
	 * 已存在的物品种类应并入原格（不占新槽）；填满 54 格后再收新物品应自动新建页面喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testMergeAndNewPage(GameTestHelper helper) {
		SMSavedData data = new SMSavedData();
		data.dustbins.add(new SMDustbinContainer());

		ItemStack diamond = new ItemStack(Items.DIAMOND);
		diamond.setCount(100);
		data.dustbins.get(0).setItem(0, diamond);

		int added = 0;
		for (Item item : firstItems(120)) {
			if (item == Items.DIAMOND) {
				continue;
			}
			if (added >= 53) {
				break;
			}
			data.addItemToDustbin(new ItemStack(item));
			++added;
		}

		ItemStack moreDiamond = new ItemStack(Items.DIAMOND);
		moreDiamond.setCount(50);
		data.addItemToDustbin(moreDiamond);

		if (data.dustbins.get(0).getItem(0).getCount() != 150) {
			helper.fail("Same-item merge failed: slot 0 has " + data.dustbins.get(0).getItem(0).getCount() + " diamonds, expected 150");
			return;
		}

		Item newItem = null;
		for (Item item : firstItems(120)) {
			if (item == Items.DIAMOND) {
				continue;
			}
			newItem = item;
		}
		data.addItemToDustbin(new ItemStack(newItem));

		if (data.dustbins.size() != 2) {
			helper.fail("Expected a new page after the first page is full, got " + data.dustbins.size() + " pages");
			return;
		}
		helper.succeed();
	}

	/**
	 * 全局重排与动态页数测试喵~
	 * <p>
	 * 120 种物品应重排为 3 页（54+54+12），按数量降序，第 1 页第 1 格是数量最多的物品喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testSortAndDynamicPages(GameTestHelper helper) {
		SMSavedData data = new SMSavedData();
		data.dustbins.add(new SMDustbinContainer());

		List<Item> items = firstItems(120);
		int index = 0;
		for (Item item : items) {
			ItemStack stack = new ItemStack(item);
			stack.setCount((index + 1) * 10);
			data.addItemToDustbin(stack);
			++index;
		}

		data.sortDustbins();

		if (data.dustbins.size() != 3) {
			helper.fail("Expected 3 pages after sorting 120 items, got " + data.dustbins.size());
			return;
		}

		ItemStack first = data.dustbins.get(0).getItem(0);
		if (!first.is(items.get(119)) || first.getCount() != 1200) {
			helper.fail("Page 1 slot 1 should be the largest stack, got " + first);
			return;
		}

		int lastCount = Integer.MAX_VALUE;
		for (SMDustbinContainer page : data.dustbins) {
			for (int i = 0; i < page.getContainerSize(); ++i) {
				ItemStack stack = page.getItem(i);
				if (stack.isEmpty()) {
					continue;
				}
				if (stack.getCount() > lastCount) {
					helper.fail("Not sorted in descending order: " + stack.getCount() + " after " + lastCount);
					return;
				}
				lastCount = stack.getCount();
			}
		}
		helper.succeed();
	}

	/**
	 * 玩家单次取走数量钳制测试喵~
	 * <p>
	 * 无论请求取走多少，每次最多取出 64 个，防止生成超大堆导致存档损坏喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testTakeLimit(GameTestHelper helper) {
		SMDustbinContainer container = new SMDustbinContainer();
		ItemStack diamond = new ItemStack(Items.DIAMOND);
		diamond.setCount(5000);
		container.setItem(0, diamond);

		SMDustbinSlot slot = new SMDustbinSlot(container, 0, 0, 0);
		ItemStack taken = slot.tryRemove(500, 500, null).orElse(ItemStack.EMPTY);

		if (taken.getCount() != SMDustbinContainer.TAKE_LIMIT) {
			helper.fail("Single take should be clamped to " + SMDustbinContainer.TAKE_LIMIT + ", got " + taken.getCount());
			return;
		}
		if (container.getItem(0).getCount() != 5000 - SMDustbinContainer.TAKE_LIMIT) {
			helper.fail("Container count after take is wrong: " + container.getItem(0).getCount());
			return;
		}
		helper.succeed();
	}

	/**
	 * 读取模组的「可清扫堆」计数喵~
	 *
	 * @return 当前掉落物堆 + 经验球总数喵~
	 */
	private static int getSweepablePileCount() {
		try {
			Field field = SweeperMaid.instance.getClass().getDeclaredField("sweepablePileCount");
			field.setAccessible(true);
			return field.getInt(SweeperMaid.instance);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 用「不进入世界」的探针实体精确校验进出世界事件对堆数计数的 ±1 影响喵~
	 * <p>
	 * 之所以敢要求<b>恰好</b> ±1：GameTest 回调与事件处理都跑在<b>服务端主线程</b>上，
	 * 两行读改写之间不可能被其它并行测试插入；而依赖世界里的真实生成/销毁则做不到这一点喵~。
	 * </p>
	 *
	 * @param probe 探针实体（只构造、不加入世界）喵~
	 * @param level 探针实体所属维度喵~
	 * @return 通过时返回 null，否则返回失败原因喵~
	 */
	@Nullable
	private static String probePileCounterJoinLeave(Entity probe, ServerLevel level) {
		int before = getSweepablePileCount();
		SweeperMaid.instance.onEntityJoin(new EntityJoinLevelEvent(probe, level));
		int afterJoin = getSweepablePileCount();
		if (afterJoin != before + 1) {
			return "EntityJoinLevelEvent should increase the pile counter by exactly 1, but " + before + " -> " + afterJoin;
		}
		SweeperMaid.instance.onEntityLeave(new EntityLeaveLevelEvent(probe, level));
		int afterLeave = getSweepablePileCount();
		if (afterLeave != before) {
			return "EntityLeaveLevelEvent should decrease the pile counter by exactly 1, but " + afterJoin + " -> " + afterLeave;
		}
		return null;
	}

	/**
	 * 掉落物堆计数测试喵~
	 * <p>
	 * 分两段，分别覆盖「集成」与「精确」两个口径喵~：
	 * </p>
	 * <p>
	 * <b>第一段（集成）</b>：真的往世界里生成 2 个掉落物，计数必须至少 +2。
	 * 只断言「不低于 +2」而<b>不</b>断言归零：这个计数器统计的是<b>全服</b>堆数，
	 * 而 GameTest 会并行跑其它模组的测试。装了机械动力时它自带 64 个测试会同时生成/销毁掉落物，
	 * 绝对计数永远不可能稳定归零（旧断言 `== 0` 实测读到 11 而误报失败）喵~。
	 * </p>
	 * <p>
	 * <b>第二段（精确）</b>：走 {@link #probePileCounterJoinLeave}，断言计数<b>恰好</b> ±1。
	 * 这一段之所以能要求精确值，是因为 GameTest 回调与事件处理都跑在<b>服务端主线程</b>上，
	 * 两行读改写之间不可能被其它测试并行插入；而依赖世界里的真实生成/销毁则做不到这一点喵~。
	 * </p>
	 */
	@GameTest(template = "empty", timeoutTicks = 300)
	public static void testItemEntityCounter(GameTestHelper helper) {
		final int baseline = getSweepablePileCount();
		Vec3 pos = helper.absoluteVec(new Vec3(0.5, 2.0, 0.5));
		ItemEntity first = helper.spawnItem(Items.DIAMOND, pos);
		ItemEntity second = helper.spawnItem(Items.EMERALD, pos);

		helper.runAfterDelay(2, () -> {
			// 第一段：集成口径——只要求「至少 +2」，容忍并发测试带来的其它增减喵~
			int afterSpawn = getSweepablePileCount();
			if (afterSpawn < baseline + 2) {
				helper.fail("Pile counter did not increase by at least 2 after spawning items (baseline="
						+ baseline + ", now=" + afterSpawn + ")");
				return;
			}
			first.discard();
			second.discard();

			// 第二段：精确口径——用不进入世界的探针实体，手动触发模组的事件处理器喵~
			ServerLevel level = helper.getLevel();
			ItemEntity probe = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(Items.DIAMOND));
			String error = probePileCounterJoinLeave(probe, level);
			if (error != null) {
				helper.fail(error);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * 「可清扫堆」判定口径测试喵~
	 * <p>
	 * 这是本次缺陷（经验球不计入过载阈值）的正面钉子：判定必须同时认掉落物堆与经验球，
	 * 又不能把无关实体算进来。断言的是<b>生产代码真正调用的那两个函数</b>，
	 * 而不是在测试里另写一份 {@code instanceof} 自证喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testSweepablePilePredicate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemEntity item = new ItemEntity(level, 0.5D, 2.0D, 0.5D, new ItemStack(Items.DIAMOND));
		ExperienceOrb orb = new ExperienceOrb(level, 0.5D, 2.0D, 0.5D, 3);
		ArmorStand bystander = new ArmorStand(level, 0.5D, 2.0D, 0.5D);

		if (!SweeperMaid.isSweepablePile(item)) {
			helper.fail("A dropped-item pile must count as a sweepable pile");
			return;
		}
		if (!SweeperMaid.isSweepablePile(orb)) {
			helper.fail("An experience orb must count as a sweepable pile - this is the overload-threshold bug");
			return;
		}
		if (SweeperMaid.isSweepablePile(bystander)) {
			helper.fail("An armor stand must not count as a sweepable pile");
			return;
		}
		int counted = SweeperMaid.countSweepablePiles(List.of(item, orb, bystander));
		if (counted != 2) {
			helper.fail("countSweepablePiles should return 2 for (item, orb, armor stand), got " + counted);
			return;
		}
		helper.succeed();
	}

	/**
	 * 经验球计数测试喵~
	 * <p>
	 * 与 {@link #testItemEntityCounter} 对称：经验球同样必须计入全局堆数，否则一地经验球
	 * 永远不会触发过载清扫、只能干等到下一次定时清扫——这正是用户报上来的缺陷喵~。
	 * </p>
	 * <p>
	 * 第一段用<b>真实入世界</b>的球，并在同一次调用里完成「读—加—读」：
	 * {@code addFreshEntity} 同步发出进世界事件，所以不必依赖 2 tick 的容忍窗口，
	 * 可以直接要求恰好 +1（并发测试没有插空的余地）喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testExperienceOrbPileCounter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(0.5, 2.0, 0.5));

		ExperienceOrb orb = new ExperienceOrb(level, pos.x, pos.y, pos.z, 3);
		int before = getSweepablePileCount();
		level.addFreshEntity(orb);
		int afterSpawn = getSweepablePileCount();
		if (afterSpawn != before + 1) {
			helper.fail("Spawning a real experience orb should raise the pile counter by exactly 1, but "
					+ before + " -> " + afterSpawn);
			return;
		}

		// 第二段：不进入世界的探针实体，补上「离开世界」方向——真实移除由区块管理器延迟处理，
		// 在这里同步断言会误报喵~
		ExperienceOrb probe = new ExperienceOrb(level, pos.x, pos.y + 2.0D, pos.z, 3);
		String error = probePileCounterJoinLeave(probe, level);
		if (error != null) {
			helper.fail(error);
			return;
		}

		orb.discard();
		helper.succeed();
	}

	/**
	 * 经验球真实经验值测试喵~
	 * <p>
	 * 覆盖本次缺陷的第二处：原版经验球实体可以是「多颗同面值球合并」的产物（私有字段
	 * {@code count}），拾取时每碰撞一次给 {@code value} 点、{@code count--}，减到 0 才消失，
	 * 因此真实经验是 {@code value × count}；只读 {@code getValue()} 会把合并进去的那部分抹掉喵~。
	 * </p>
	 * <p>
	 * 这里用原版自己的存档通道（{@code saveWithoutId} / {@code load}）构造「已合并」状态：
	 * 既不必为了碰私有字段而引入 AT / Mixin，又顺带钉住「{@code Count} 这个存档键确实存在」——
	 * 万一上游改了存档格式，会先炸在这个测试上，而不是在服里悄悄少算经验喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testExperienceOrbTotalValue(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ExperienceOrb orb = new ExperienceOrb(level, 0.5D, 2.0D, 0.5D, 3);

		if (SMExperienceOrbs.orbCount(orb) != 1) {
			helper.fail("A freshly created experience orb should hold exactly 1 orb, got " + SMExperienceOrbs.orbCount(orb));
			return;
		}
		if (SMExperienceOrbs.totalValue(orb) != 3) {
			helper.fail("A fresh value=3 orb should be worth 3 xp, got " + SMExperienceOrbs.totalValue(orb));
			return;
		}

		CompoundTag tag = orb.saveWithoutId(new CompoundTag());
		if (!tag.contains("Count")) {
			helper.fail("Vanilla ExperienceOrb no longer serializes a Count tag - the merged orb count cannot be read");
			return;
		}
		tag.putInt("Count", 7);
		orb.load(tag);

		if (SMExperienceOrbs.orbCount(orb) != 7) {
			helper.fail("orbCount should read back 7 after loading Count=7, got " + SMExperienceOrbs.orbCount(orb));
			return;
		}
		int total = SMExperienceOrbs.totalValue(orb);
		if (total != 21) {
			helper.fail("A merged orb (value=3, count=7) must be worth 21 xp, got " + total);
			return;
		}
		helper.succeed();
	}

	/**
	 * 垃圾桶打包→清空→导入往返测试喵~
	 * <p>
	 * 打包后数量不丢失、桶被清空；导入后物品合并回桶，数量一致喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testPackImportRoundtrip(GameTestHelper helper) {
		HolderLookup.Provider provider = helper.getLevel().registryAccess();
		SMSavedData data = new SMSavedData();
		data.dustbins.add(new SMDustbinContainer());

		ItemStack diamond = new ItemStack(Items.DIAMOND);
		diamond.setCount(123456);
		data.dustbins.get(0).setItem(0, diamond);
		ItemStack emerald = new ItemStack(Items.EMERALD);
		emerald.setCount(99);
		data.dustbins.get(0).setItem(1, emerald);

		Path file;
		try {
			file = Files.createTempFile("sweeper_maid_test", ".nbt.gz");
		} catch (IOException e) {
			helper.fail("Failed to create temp file: " + e.getMessage());
			return;
		}

		try {
			int packed = data.packDustbins(provider, file);
			if (packed != 123555) {
				helper.fail("Packed count mismatch: " + packed + ", expected 123555");
				return;
			}
			if (!data.dustbins.get(0).isEmpty()) {
				helper.fail("Dustbin should be empty after packing");
				return;
			}

			int imported = data.importPack(provider, file);
			if (imported != 123555) {
				helper.fail("Imported count mismatch: " + imported + ", expected 123555");
				return;
			}
			if (data.dustbins.get(0).getItem(0).getCount() != 123456 || !data.dustbins.get(0).getItem(0).is(Items.DIAMOND)) {
				helper.fail("Diamond count after import is wrong: " + data.dustbins.get(0).getItem(0));
				return;
			}
			if (data.dustbins.get(0).getItem(1).getCount() != 99 || !data.dustbins.get(0).getItem(1).is(Items.EMERALD)) {
				helper.fail("Emerald count after import is wrong: " + data.dustbins.get(0).getItem(1));
				return;
			}
			helper.succeed();
		} catch (IOException e) {
			helper.fail("Pack/import failed: " + e.getMessage());
		} finally {
			try {
				Files.deleteIfExists(file);
			} catch (IOException ignored) {
				// Ignored
			}
		}
	}

	/**
	 * 垃圾箱菜单「原版兼容」回归测试喵~
	 * <p>
	 * 本模组必须允许「服务端装了、客户端没装」的玩家正常游戏，因此菜单有三条硬约束，
	 * 任何一条被破坏都会让未装模组的客户端崩在打开界面或连接阶段：
	 * <ul>
	 *     <li>菜单类型必须是原版 {@link MenuType#GENERIC_9x6}——自定义 {@code MenuType} 会被 NeoForge
	 *     当作同步注册表条目，未装模组的客户端解析不了该键，连接阶段即断开喵~</li>
	 *     <li>数据槽（{@code DataSlot}）数量必须为 0——原版大箱子菜单没有数据槽，未装模组的客户端
	 *     收到数据槽同步包会下标越界崩溃，所以翻页状态改走可选网络通道喵~</li>
	 *     <li>槽位布局必须是 54 个垃圾箱槽位 + 36 个玩家槽位，且与原版大箱子坐标一致，
	 *     未装模组的客户端才能正常查看与操作喵~</li>
	 * </ul>
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testMenuStaysVanillaCompatible(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		SMDustbinContainer dustbin = new SMDustbinContainer();
		DustbinMenu menu = new DustbinMenu(0, player.getInventory(), dustbin, 0, 1);

		// 1) 菜单类型必须是原版大箱子，否则未装模组的客户端无法解析喵~
		if (menu.getType() != MenuType.GENERIC_9x6) {
			helper.fail("Menu type must be MenuType.GENERIC_9x6 for vanilla clients, got " + menu.getType());
			return;
		}

		// 2) 不能有数据槽，否则未装模组的客户端会下标越界崩溃喵~
		// dataSlots 是私有的，所以直接测「客户端收到数据槽同步包」时会踩到的那个行为：
		// ClientPacketListener#handleContainerSetData -> AbstractContainerMenu#setData(0, x)。
		// 一条数据槽都没有时，这里必须抛 IndexOutOfBoundsException，而不是静默通过喵~
		try {
			menu.setData(0, 1);
			helper.fail("Menu exposed a data slot; an unmodded client would crash on container data sync");
			return;
		} catch (IndexOutOfBoundsException expected) {
			// 正是未装模组客户端会踩的崩溃路径——本模组必须在服务端就避免走到它喵~
		}

		// 3) 槽位数量与布局必须与原版 6 行大箱子一致喵~
		if (menu.slots.size() != SMDustbinContainer.SIZE + 36) {
			helper.fail("Unexpected slot count " + menu.slots.size() + ", expected " + (SMDustbinContainer.SIZE + 36));
			return;
		}
		for (int i = 0; i < SMDustbinContainer.SIZE; ++i) {
			if (!(menu.getSlot(i) instanceof SMDustbinSlot)) {
				helper.fail("Dustbin slot " + i + " is not an SMDustbinSlot: " + menu.getSlot(i).getClass());
				return;
			}
		}
		// 第 0 个垃圾箱槽位 / 第 0 个玩家主槽位 / 第 0 个快捷栏槽位的坐标须与原版 ChestMenu 一致喵~
		if (menu.getSlot(0).x != 8 || menu.getSlot(0).y != 18
				|| menu.getSlot(SMDustbinContainer.SIZE).x != 8 || menu.getSlot(SMDustbinContainer.SIZE).y != 139
				|| menu.getSlot(SMDustbinContainer.SIZE + 27).x != 8 || menu.getSlot(SMDustbinContainer.SIZE + 27).y != 197) {
			helper.fail("Slot layout diverges from vanilla ChestMenu coordinates");
			return;
		}

		// 服务端菜单不应被误判为客户端菜单：翻页状态仍可正常设置喵~
		if (menu.getCurrentPage() != 0 || menu.getPageCount() != 1) {
			helper.fail("Initial page state wrong: page=" + menu.getCurrentPage() + " pageCount=" + menu.getPageCount());
			return;
		}
		helper.succeed();
	}

	/**
	 * 网络负载「可选通道」回归测试喵~
	 * <p>
	 * 这是「服务端装模组、客户端可以不装」的<b>第一道</b>硬约束：NeoForge 在握手阶段会比对双方的通道列表，
	 * 只要存在一个<b>非可选</b>负载而对面没有，就会直接把连接踢掉
	 * （纯原版会收到「请安装 NeoForge」的提示，装了 NeoForge 但没装本模组的会收到「不兼容」）喵~
	 * </p>
	 * <p>
	 * 所以本模组注册的每一条负载都必须带 {@code .optional()}。本测试直接反射读 NeoForge 的注册表，
	 * 比「能否启动」这种间接信号更能锁死回归喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testAllPayloadsAreOptional(GameTestHelper helper) {
		List<CustomPacketPayload.Type<?>> ours = List.of(
				SMPageChangePayload.TYPE, SMDustbinStatePayload.TYPE, SMDustbinSyncRequestPayload.TYPE);

		for (CustomPacketPayload.Type<?> type : ours) {
			boolean found = false;
			for (ConnectionProtocol protocol : ConnectionProtocol.values()) {
				PayloadRegistration<?> registration = findRegistration(protocol, type.id());
				if (registration == null) {
					continue;
				}
				found = true;
				if (!registration.optional()) {
					helper.fail("Payload " + type.id() + " in " + protocol
							+ " is not optional; unmodded clients would be disconnected during negotiation");
					return;
				}
			}
			if (!found) {
				helper.fail("Payload " + type.id() + " is not registered at all");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * 反射查询 NeoForge 的负载注册表喵~
	 * <p>
	 * {@code NetworkRegistry.PAYLOAD_REGISTRATIONS} 是私有的，且没有公开的查询入口，
	 * 这里用反射只读访问，测试结束不修改任何状态喵~
	 * </p>
	 *
	 * @param protocol 连接协议喵~
	 * @param id       负载 ID 喵~
	 * @return 注册信息，未注册时返回 {@code null} 喵~
	 */
	@Nullable
	private static PayloadRegistration<?> findRegistration(ConnectionProtocol protocol, ResourceLocation id) {
		return registrationMap().get(protocol) instanceof Map<?, ?> byId
				&& byId.get(id) instanceof PayloadRegistration<?> registration
				? registration
				: null;
	}

	/**
	 * 「纯原版客户端」握手模拟测试喵~
	 * <p>
	 * 这是最贴近真实场景的回归测试：把本模组注册的负载喂给 NeoForge 真正的协商器，
	 * 客户端一侧传<b>空列表</b>——正是纯原版客户端在配置阶段能提供的通道集合
	 * （见 {@code NetworkRegistry#initializeOtherConnection}）喵~
	 * </p>
	 * <p>
	 * 只要本模组漏掉一个 {@code .optional()}，协商就会失败，真实服务器会立刻把玩家踢下线。
	 * 这里断言协商必须成功，从而把「未装模组的客户端能不能进服」这条验收标准钉死在自动化测试里喵~
	 * </p>
	 * <p>
	 * <b>为什么只喂「本模组自己的」负载</b>：NeoForge 的负载注册表是<b>全服共享</b>的。
	 * 若把别的模组（如机械动力，它注册了大量<b>非可选</b>通道）的负载也一起喂进来，
	 * 失败原因会来自别的模组，而与本模组的 optional 纪律无关——
	 * 实测装了机械动力后本测试就会因此误报失败喵~。
	 * 本测试要钉的是<b>本模组</b>的契约，所以必须按命名空间过滤，只保留 {@code sweeper_maid:} 的负载喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testVanillaClientHandshakeNegotiates(GameTestHelper helper) {
		List<NegotiableNetworkComponent> serverComponents = new ArrayList<>();
		for (ConnectionProtocol protocol : ConnectionProtocol.values()) {
			for (PayloadRegistration<?> registration : allRegistrations(protocol)) {
				// 只测本模组的通道：别的模组的 optional 纪律不归本模组负责喵~
				if (isOurPayload(registration)) {
					serverComponents.add(new NegotiableNetworkComponent(registration));
				}
			}
		}
		if (serverComponents.isEmpty()) {
			helper.fail("No sweeper_maid payloads were found; the reflection lookup or the namespace filter is broken");
			return;
		}

		// 客户端什么都不提供 == 纯原版 / 只装 NeoForge 没装本模组喵~
		NegotiationResult result = NetworkComponentNegotiator.negotiate(serverComponents, List.of());
		if (!result.success()) {
			helper.fail("A vanilla client would be disconnected during negotiation: " + result.failureReasons());
			return;
		}
		helper.succeed();
	}

	/**
	 * 判断某个负载是否属于本模组喵~
	 *
	 * @param registration 负载注册信息喵~
	 * @return 命名空间为 {@code sweeper_maid} 时返回 true 喵~
	 */
	private static boolean isOurPayload(PayloadRegistration<?> registration) {
		return SweeperMaid.MODID.equals(registration.id().getNamespace());
	}

	/**
	 * 反射取出某个协议下注册的全部负载喵~
	 *
	 * @param protocol 连接协议喵~
	 * @return 该协议下的全部注册信息喵~
	 */
	private static List<PayloadRegistration<?>> allRegistrations(ConnectionProtocol protocol) {
		if (!(registrationMap().get(protocol) instanceof Map<?, ?> byId)) {
			return List.of();
		}
		List<PayloadRegistration<?>> result = new ArrayList<>();
		for (Object value : byId.values()) {
			if (value instanceof PayloadRegistration<?> registration) {
				result.add(registration);
			}
		}
		return result;
	}

	/**
	 * 反射读取 NeoForge 的负载注册表喵~
	 *
	 * @return 协议到「负载 ID → 注册信息」的映射喵~
	 */
	private static Map<?, ?> registrationMap() {
		try {
			Field field = NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
			field.setAccessible(true);
			Object value = field.get(null);
			if (value instanceof Map<?, ?> map) {
				return map;
			}
			throw new IllegalStateException("PAYLOAD_REGISTRATIONS is not a map");
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to inspect NeoForge payload registrations", e);
		}
	}

	/**
	 * 数字键交换（{@code ClickType.SWAP}）不得绕过单次取走上限喵~
	 * <p>
	 * 原版 SWAP 分支的写法是 {@code inventory.setItem(hotbar, slot.getItem())}，而
	 * {@code Inventory#setItem} 内部只是 {@code nonnulllist.set(...)}，<b>既不调用
	 * {@code limitSize}，也完全不经过</b> {@link SMDustbinSlot#tryRemove} 喵~。
	 * 所以只守 {@code tryRemove} 是不够的：玩家按一下数字键就能把 5000 个钻石原样塞进一个快捷栏格子，
	 * 之后就可能在掉落 / 死亡写档时生成超大堆，正是本模组要防的存档损坏喵~。
	 * </p>
	 * <p>
	 * 这里走真实的 <b>菜单点击入口</b>（而不是直接调助手方法），确保拦的是玩家实际会触发的那条路径喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testSwapCannotBypassTakeLimit(GameTestHelper helper) {
		SMDustbinContainer container = new SMDustbinContainer();
		ItemStack diamond = new ItemStack(Items.DIAMOND);
		diamond.setCount(5000);
		container.setItem(0, diamond);

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		DustbinMenu menu = new DustbinMenu(0, player.getInventory(), container, 0, 1);

		// 空手 + 快捷栏 0 格为空：按数字键 1 试图把整堆换出来喵~
		menu.clicked(0, 0, ClickType.SWAP, player);

		int moved = player.getInventory().getItem(0).getCount();
		if (moved != SMDustbinContainer.TAKE_LIMIT) {
			helper.fail("SWAP moved " + moved + " items into the hotbar, expected exactly "
					+ SMDustbinContainer.TAKE_LIMIT);
			return;
		}
		int remaining = container.getItem(0).getCount();
		if (remaining != 5000 - SMDustbinContainer.TAKE_LIMIT) {
			helper.fail("SWAP must leave the remainder in the dustbin, but " + remaining
					+ " items are left (expected " + (5000 - SMDustbinContainer.TAKE_LIMIT) + ")");
			return;
		}

		// 正常堆叠必须保持原版行为：整堆换出喵~
		ItemStack small = new ItemStack(Items.EMERALD);
		small.setCount(32);
		container.setItem(1, small);
		menu.clicked(1, 1, ClickType.SWAP, player);
		if (player.getInventory().getItem(1).getCount() != 32 || !container.getItem(1).isEmpty()) {
			helper.fail("A stack within the take limit must swap out entirely, hotbar="
					+ player.getInventory().getItem(1) + " dustbin=" + container.getItem(1));
			return;
		}
		helper.succeed();
	}

	/**
	 * 清扫重排后菜单必须重新挂上新的页容器喵~
	 * <p>
	 * {@code sortDustbins()} 会 {@code clear()} 再 {@code addAll()}，把每一页都换成<b>新的</b>容器实例喵~。
	 * 若菜单仍持有旧实例，且页码与页数都没变，旧代码的 {@code lastSynced*} 提前返回会让菜单永远不再重新指向，
	 * 于是客户端界面冻结在清扫前的内容、玩家点击落在孤立的旧容器上喵~。
	 * </p>
	 * <p>
	 * 这里直接构造「页数不变但容器实例被换掉」的场景，断言菜单能重新指向新容器喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testMenuRebindsAfterContainerRotation(GameTestHelper helper) {
		SMSavedData data = new SMSavedData();
		data.dustbins.add(new SMDustbinContainer());
		SMSavedData.setInstance(data);

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		SMDustbinContainer original = data.dustbins.get(0);
		DustbinMenu menu = new DustbinMenu(0, player.getInventory(), original, 0, 1);

		if (menu.getItem(0) != original.getItem(0) && !menu.getItem(0).isEmpty()) {
			helper.fail("Menu is not bound to the container it was opened with");
			return;
		}

		// 模拟清扫重排：页数保持 1，但换成另一个全新的容器实例喵~
		SMDustbinContainer replacement = new SMDustbinContainer();
		ItemStack diamond = new ItemStack(Items.DIAMOND);
		diamond.setCount(1234);
		replacement.setItem(0, diamond);
		data.dustbins.clear();
		data.dustbins.addAll(List.of(replacement));

		// 菜单每 tick 会走 broadcastChanges，这里直接触发同一段逻辑喵~
		menu.broadcastChanges();

		if (menu.getItem(0).getCount() != 1234) {
			helper.fail("Menu did not rebind to the rotated container: read " + menu.getItem(0)
					+ ", expected 1234 diamonds");
			return;
		}
		helper.succeed();
	}

	/**
	 * 经验颗粒换算率的回归测试喵~
	 * <p>
	 * 换算率不是拍脑袋定的，而是取自 Create 源码
	 * {@code ExperienceNuggetItem#use} 里的 {@code Mth.ceil(3f * amountUsed)}：
	 * <b>1 颗经验颗粒 = 3 点经验</b>喵~。
	 * </p>
	 * <p>
	 * 这里把数学钉死在测试里，防止将来有人「顺手」改成 1 或 5：
	 * 向下取整保证绝不刷经验，且 3 的整数倍必须无损耗喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testExperienceNuggetMath(GameTestHelper helper) {
		if (SMCreateCompat.XP_PER_NUGGET != 3) {
			helper.fail("XP_PER_NUGGET must stay 3 to match Create's ExperienceNuggetItem, got " + SMCreateCompat.XP_PER_NUGGET);
			return;
		}
		// 必须是「1 颗换 3 点」：少于 3 点换不出整颗，3 的倍数必须无损耗喵~
		int[][] cases = {{0, 0}, {1, 0}, {2, 0}, {3, 1}, {4, 1}, {5, 1}, {6, 2}, {9, 3}, {100, 33}, {1234, 411}};
		for (int[] c : cases) {
			int actual = SMCreateCompat.nuggetsFor(c[0]);
			if (actual != c[1]) {
				helper.fail(c[0] + " xp should convert into " + c[1] + " nugget(s), got " + actual);
				return;
			}
		}
		// 负经验不该产出物品（经验球数值理论上非负，防御性断言）喵~
		if (SMCreateCompat.nuggetsFor(-5) != 0) {
			helper.fail("Negative xp must not produce nuggets");
			return;
		}
		helper.succeed();
	}

	/**
	 * 附魔之瓶换算率的回归测试喵~
	 * <p>
	 * 附魔之瓶仍按原作口径「向上取整」（开瓶只给 3~11 点经验，适度折损可避免刷经验），
	 * 这条路径在未装机械动力时必须保持不变喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testExperienceBottleMath(GameTestHelper helper) {
		int[][] cases = {{0, 0}, {1, 1}, {9, 1}, {10, 1}, {11, 2}, {100, 10}, {101, 11}};
		for (int[] c : cases) {
			int actual = SMCreateCompat.bottlesFor(c[0], 10);
			if (actual != c[1]) {
				helper.fail(c[0] + " xp with 10 xp/bottle should be " + c[1] + " bottle(s), got " + actual);
				return;
			}
		}
		// 配置为 0 或负数时必须安全返回 0，而不是除零崩溃喵~
		if (SMCreateCompat.bottlesFor(100, 0) != 0 || SMCreateCompat.bottlesFor(100, -1) != 0) {
			helper.fail("A non-positive XP_PER_BOTTLE must yield 0 bottles instead of crashing");
			return;
		}
		helper.succeed();
	}

	/**
	 * 折算模式解析测试喵~
	 * <p>
	 * 配置是服主手写的，必须容忍大小写、首尾空白与拼写错误：
	 * 无法识别的值退回 {@code auto}，绝不能因为写错就让经验凭空消失喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testConversionModeParsing(GameTestHelper helper) {
		Object[][] cases = {
				{"auto", SMCreateCompat.ConversionMode.AUTO},
				{"bottle", SMCreateCompat.ConversionMode.BOTTLE},
				{"nugget", SMCreateCompat.ConversionMode.NUGGET},
				{"discard", SMCreateCompat.ConversionMode.DISCARD},
				{"  DISCARD  ", SMCreateCompat.ConversionMode.DISCARD},
				{"Bottle", SMCreateCompat.ConversionMode.BOTTLE},
				{"NUUGET", SMCreateCompat.ConversionMode.AUTO},
				{"", SMCreateCompat.ConversionMode.AUTO},
				{null, SMCreateCompat.ConversionMode.AUTO},
		};
		for (Object[] c : cases) {
			SMCreateCompat.ConversionMode actual = SMCreateCompat.ConversionMode.byName((String) c[0]);
			if (actual != c[1]) {
				helper.fail("Mode '" + c[0] + "' should parse as " + c[1] + ", got " + actual);
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * 未装机械动力时不得产出经验颗粒的回归测试喵~
	 * <p>
	 * 这是本联动最关键的一条安全约束：{@code BuiltInRegistries.ITEM} 是
	 * {@code DefaultedRegistry}，查不到键时 {@code get()} 返回的是 {@code Items.AIR}
	 * 而<b>不是</b> {@code null}。若照抄 {@code get()} 的写法，未装 Create 时就会
	 * 「成功」拿到空气并往垃圾箱里塞一堆空气（或数量虚高），属于静默故障喵~。
	 * </p>
	 * <p>
	 * 本测试在<b>没有</b> Create 的环境下运行，断言：
	 * <ul>
	 *     <li>取物品返回 {@code null}，不会返回 air 或别的物品；</li>
	 *     <li>{@code resolveMode()} 把 {@code auto} 解析成附魔之瓶（而不是颗粒）；</li>
	 *     <li>服主强制 {@code nugget} 时也退回附魔之瓶，不让经验消失；</li>
	 *     <li>用它构造的物品堆必为空，入桶路径不会被触发喵~</li>
	 * </ul>
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testNuggetUnavailableWithoutCreate(GameTestHelper helper) {
		if (SMCreateCompat.isCreateLoaded()) {
			// 本环境装了 Create 时，这条断言不适用（改由 testNuggetAvailableWithCreate 覆盖）喵~
			helper.succeed();
			return;
		}
		if (!SMCreateCompat.EXPERIENCE_NUGGET_ID.toString().equals("create:experience_nugget")) {
			helper.fail("The nugget id must be create:experience_nugget, got " + SMCreateCompat.EXPERIENCE_NUGGET_ID);
			return;
		}
		if (SMCreateCompat.getExperienceNugget() != null) {
			helper.fail("Without Create the nugget item must be null, got " + SMCreateCompat.getExperienceNugget());
			return;
		}
		if (SMCreateCompat.isNuggetAvailable()) {
			helper.fail("isNuggetAvailable() must be false without Create");
			return;
		}
		if (!SMCreateCompat.stackOf(null, 5).isEmpty()) {
			helper.fail("A null item must produce an empty stack, never a stack of air");
			return;
		}
		helper.succeed();
	}

	/**
	 * 装了机械动力时必须能取到经验颗粒的回归测试喵~
	 * <p>
	 * 与上一条互补：本环境装了 Create 时，断言物品确实存在于注册表里、
	 * 且 {@code auto} 会解析成颗粒模式；同时物品 ID 必须是
	 * {@code create:experience_nugget}（官方中文译名「经验颗粒」）喵~。
	 * </p>
	 * <p>
	 * 未装 Create 的环境下该测试直接通过（由上面那条覆盖），因此两种环境下都能全绿喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testNuggetAvailableWithCreate(GameTestHelper helper) {
		if (!SMCreateCompat.isCreateLoaded()) {
			helper.succeed();
			return;
		}
		Item nugget = SMCreateCompat.getExperienceNugget();
		if (nugget == null) {
			helper.fail("Create is loaded but create:experience_nugget was not found in the item registry");
			return;
		}
		if (!SMCreateCompat.isNuggetAvailable()) {
			helper.fail("isNuggetAvailable() must be true when Create is loaded");
			return;
		}
		// 取到的必须是真正的经验颗粒，而不是被 DefaultedRegistry 兜底成 air 之类的占位喵~
		ResourceLocation key = BuiltInRegistries.ITEM.getKey(nugget);
		if (!SMCreateCompat.EXPERIENCE_NUGGET_ID.equals(key)) {
			helper.fail("Resolved item is not create:experience_nugget but " + key);
			return;
		}
		ItemStack stack = SMCreateCompat.stackOf(nugget, 4);
		if (stack.isEmpty() || !stack.is(nugget) || stack.getCount() != 4) {
			helper.fail("Unexpected nugget stack: " + stack);
			return;
		}
		helper.succeed();
	}

	/**
	 * 折算产物（清扫真正调用的那段逻辑）的回归测试喵~
	 * <p>
	 * 前面的测试只钉住了换算数学与模式解析；这里直接断言<b>产出物本身</b>，
	 * 才能保证「装了机械动力时 auto 模式真的产出经验颗粒」这条核心承诺不会在重构中丢失喵~。
	 * </p>
	 * <p>
	 * 本测试两种环境下都要通过，但断言不同：
	 * <ul>
	 *     <li>未装 Create：{@code auto}/{@code nugget} 都必须退化成附魔之瓶，且绝不产出空气喵~</li>
	 *     <li>装了 Create：{@code auto}/{@code nugget} 都必须产出经验颗粒，且数量 = 经验/3 向下取整喵~</li>
	 * </ul>
	 * {@code discard} 在两种环境下都不得产出任何物品；{@code bottle} 永远产出附魔之瓶喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testConversionProducesExpectedItem(GameTestHelper helper) {
		int xp = 100;
		boolean createLoaded = SMCreateCompat.isNuggetAvailable();
		Item nugget = SMCreateCompat.getExperienceNugget();

		// 1) discard：任何环境下都不产出物品喵~
		if (!SMCreateCompat.conversionResultFor(xp, SMCreateCompat.ConversionMode.DISCARD, 10).isEmpty()) {
			helper.fail("discard mode must produce no item at all");
			return;
		}

		// 2) 经验为 0 或负数：不产出物品，也不应抛异常喵~
		if (!SMCreateCompat.conversionResultFor(0, SMCreateCompat.ConversionMode.NUGGET, 10).isEmpty()
				|| !SMCreateCompat.conversionResultFor(-3, SMCreateCompat.ConversionMode.BOTTLE, 10).isEmpty()) {
			helper.fail("A non-positive xp amount must produce no item");
			return;
		}

		// 3) bottle：永远产出附魔之瓶，数量向上取整喵~
		ItemStack bottle = SMCreateCompat.conversionResultFor(xp, SMCreateCompat.ConversionMode.BOTTLE, 10);
		if (!bottle.is(Items.EXPERIENCE_BOTTLE) || bottle.getCount() != 10) {
			helper.fail("bottle mode should yield 10 experience bottles for 100 xp, got " + bottle);
			return;
		}

		// 4) nugget / auto：按环境断言到底产出什么喵~
		for (SMCreateCompat.ConversionMode mode : List.of(
				SMCreateCompat.ConversionMode.NUGGET, SMCreateCompat.ConversionMode.AUTO)) {
			ItemStack produced = SMCreateCompat.conversionResultFor(xp, mode, 10);
			if (createLoaded) {
				if (nugget == null || !produced.is(nugget)) {
					helper.fail("With Create, mode " + mode + " must produce experience nuggets, got " + produced);
					return;
				}
				if (produced.getCount() != 33) {
					helper.fail("With Create, 100 xp should yield 33 nuggets (100/3), got " + produced.getCount());
					return;
				}
			} else {
				if (!produced.is(Items.EXPERIENCE_BOTTLE)) {
					helper.fail("Without Create, mode " + mode + " must fall back to experience bottles, got " + produced);
					return;
				}
			}
			// 无论哪条路径，都绝不能产出空气这种静默故障喵~
			if (produced.isEmpty() || produced.is(Items.AIR)) {
				helper.fail("Conversion produced air for mode " + mode);
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * 清扫后垃圾箱里应出现经验颗粒的测试（仅装了机械动力时断言）喵~
	 * <p>
	 * 这里<b>不</b>直接驱动 {@code doSweeping()}：那是<b>全服</b>清扫，会把同一测试服务器上
	 * 其它模组的测试实体一起吞掉——实测装了机械动力时会让它自己的 {@code TestItems.storages}
	 * 因「存储内容缺失」而失败，属于测试之间的相互破坏喵~。
	 * </p>
	 * <p>
	 * 改为直接断言「清扫真正调用的那段折算逻辑」：{@link SMCreateCompat#resolveMode()} 在装了
	 * Create 时解析出颗粒模式，且 {@link SMCreateCompat#conversionResultFor} 产出经验颗粒。
	 * 这两者合起来就是清扫里经验入桶前的全部决策；剩下的 {@code addItemToDustbin} 已由
	 * 垃圾箱相关测试覆盖。整条链路的实机验证改由真实服务端 + RCON 完成
	 * （见 tools/README.md 的联调流程），不再污染 GameTest 环境喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testSweepingStoresExperienceNuggets(GameTestHelper helper) {
		Item nugget = SMCreateCompat.getExperienceNugget();
		if (nugget == null) {
			// 未装 Create：产物是附魔之瓶，属于既有行为，本测试不适用喵~
			helper.succeed();
			return;
		}

		// 1) 清扫入口所用的模式解析：auto 必须解析成颗粒喵~
		SMCreateCompat.ConversionMode resolved = SMCreateCompat.resolveMode();
		if (resolved != SMCreateCompat.ConversionMode.NUGGET) {
			helper.fail("With Create, resolveMode() must resolve to NUGGET, got " + resolved);
			return;
		}

		// 2) 清扫真正调用的折算函数必须产出经验颗粒，且数量 = 经验/3 向下取整喵~
		int xp = 300;
		ItemStack produced = SMCreateCompat.conversionResultFor(xp, resolved, 10);
		if (!produced.is(nugget)) {
			helper.fail("With Create, the sweep conversion must produce experience nuggets, got " + produced);
			return;
		}
		if (produced.getCount() != xp / SMCreateCompat.XP_PER_NUGGET) {
			helper.fail("Expected " + (xp / SMCreateCompat.XP_PER_NUGGET) + " nuggets for " + xp
					+ " xp, got " + produced.getCount());
			return;
		}

		// 3) 产物必须真的能入桶（合并/新建页都不异常）喵~
		SMSavedData data = new SMSavedData();
		data.dustbins.add(new SMDustbinContainer());
		data.addItemToDustbin(produced);
		ItemStack stored = data.dustbins.get(0).getItem(0);
		if (!stored.is(nugget) || stored.getCount() != produced.getCount()) {
			helper.fail("Nuggets were not stored into the dustbin correctly, got " + stored);
			return;
		}
		helper.succeed();
	}

	/**
	 * 经验颗粒查询「不得把未命中永久缓存」的回归测试喵~
	 * <p>
	 * 原版注册表在模组加载完成后固定，因此命中结果可以永久缓存；但「未命中」一旦被缓存，
	 * 万一首次查询发生在 Create 的注册表建好之前，整个联动就会<b>永久失效且不报任何错</b>——
	 * 属于最难排查的静默故障喵~。
	 * </p>
	 * <p>
	 * 本测试连续多次调用，断言结果<b>稳定一致</b>（不会第一次拿到、第二次变成 null，
	 * 也不会反过来）；未装 Create 时还要求每次都稳定返回 null 而不抛异常喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testNuggetLookupIsStable(GameTestHelper helper) {
		Item first = SMCreateCompat.getExperienceNugget();
		for (int i = 0; i < 5; i++) {
			Item again = SMCreateCompat.getExperienceNugget();
			if (again != first) {
				helper.fail("getExperienceNugget() changed between calls: " + first + " vs " + again);
				return;
			}
			// 布尔判据也必须与物品查询保持一致，不能一个说有一说没有喵~
			if (SMCreateCompat.isNuggetAvailable() != (first != null)) {
				helper.fail("isNuggetAvailable() disagrees with getExperienceNugget()");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * 清扫后「垃圾箱提示」必须跟随权限的回归测试喵~
	 * <p>
	 * 这是用户实际报上来的缺陷：把 {@code PERMISSION_LEVEL_DUSTBIN} 调高后，
	 * 玩家明明打不开垃圾箱，却仍会收到「去垃圾箱里看看吧：[垃圾箱 0]」——
	 * 因为命令端有 {@code hasPermission} 校验，而<b>提示端原先没有</b>，
	 * 于是那条链接点下去毫无反应（死链接）喵~。
	 * </p>
	 * <p>
	 * 本测试用真实 {@link ServerPlayer} 覆盖两条分支：普通玩家（权限 0）不得收到提示，
	 * 而 OP（权限 2）在要求 2 级时必须收到；另断言要求 0 级时人人放行，
	 * 因此默认配置下的行为与从前完全一致喵~。
	 * </p>
	 * <p>
	 * <b>为什么不直接调用 {@code helper.makeMockServerPlayerInLevel()}</b>：那个辅助方法会走
	 * {@code PlayerList#placeNewPlayer} 真正把玩家放进服务器，于是其它模组（如机械动力）会在
	 * 上线流程里向这个假连接推送自己的负载，直接抛
	 * {@code Payload create:sync_edge_group may not be sent to the client!} 而失败——实测如此喵~。
	 * 本测试只关心权限判定，因此直接构造 {@link ServerPlayer} 实例、<b>不</b>让其上线，
	 * 既避开第三方模组的上线副作用，也不污染玩家列表喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testDustbinPromptFollowsPermission(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		// 直接构造，不调用 placeNewPlayer，避免第三方模组的登录流程副作用喵~
		ServerPlayer player = new ServerPlayer(server, level,
				new GameProfile(UUID.randomUUID(), "test-dustbin-perm"), ClientInformation.createDefault());
		GameProfile profile = player.getGameProfile();
		PlayerList playerList = server.getPlayerList();

		// 默认配置（要求 0 级）：人人可开箱，提示照发，不改变既有行为喵~
		if (!SweeperMaid.shouldReceiveDustbinPrompt(player, 0)) {
			helper.fail("With required level 0 every player must receive the dustbin prompt");
			return;
		}

		// 权限不足：非 OP 玩家在要求 1 级及以上时不得收到提示喵~
		if (!playerList.isOp(profile)) {
			if (SweeperMaid.shouldReceiveDustbinPrompt(player, 1)) {
				helper.fail("A non-op player must NOT receive the dustbin prompt when level 1 is required");
				return;
			}
			if (SweeperMaid.shouldReceiveDustbinPrompt(player, 4)) {
				helper.fail("A non-op player must NOT receive the dustbin prompt when level 4 is required");
				return;
			}
		}

		// 提权为 OP 后必须收到提示；测完立刻撤销，避免污染同批次的其它测试喵~
		boolean wasOp = playerList.isOp(profile);
		playerList.op(profile);
		int opLevel = server.getOperatorUserPermissionLevel();
		boolean receivesAsOp = SweeperMaid.shouldReceiveDustbinPrompt(player, 2);
		if (!wasOp) {
			playerList.deop(profile);
		}
		if (opLevel >= 2 && !receivesAsOp) {
			helper.fail("An operator (level " + opLevel + ") must receive the dustbin prompt when level 2 is required");
			return;
		}

		// 关键断言：清扫真正调用的是 dustbinPromptRecipients，
		// 必须能把「没权限的玩家」整条剔出去——用户报的正是这里漏了校验喵~。
		List<ServerPlayer> all = List.of(player);
		if (SweeperMaid.dustbinPromptRecipients(all, 0).size() != 1) {
			helper.fail("With required level 0 the recipient list must keep every player");
			return;
		}
		if (!playerList.isOp(profile) && !SweeperMaid.dustbinPromptRecipients(all, 1).isEmpty()) {
			helper.fail("A non-op player must be filtered out of the dustbin prompt recipients at level 1");
			return;
		}
		// 设为 4 级（最高）时必须谁都不提示，等价于「彻底关掉垃圾箱提示」喵~
		if (!SweeperMaid.dustbinPromptRecipients(all, 4).isEmpty() && opLevel < 4) {
			helper.fail("Required level 4 must prompt nobody below level 4");
			return;
		}
		helper.succeed();
	}

	/**
	 * 直接改配置项的回归测试：模拟用户「关掉开垃圾桶权限」的真实操作喵~
	 * <p>
	 * 前面那条测试用的是显式传入的等级，证明不了「清扫真的会去读这条配置」。
	 * 而用户报的现象是「我关掉了开垃圾桶的权限但还是会提示」——要钉死这个场景，
	 * 必须真的把 {@code PERMISSION_LEVEL_DUSTBIN} 改掉，再看接收者列表是否为空喵~。
	 * </p>
	 * <p>
	 * 配置项是<b>全局单例</b>，所以用 try/finally 保证无论断言是否失败都还原原值，
	 * 绝不把改过的配置留给同批次的其它测试喵~。
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testDustbinPromptRespectsConfigValue(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer player = new ServerPlayer(server, level,
				new GameProfile(UUID.randomUUID(), "test-dustbin-config"), ClientInformation.createDefault());
		List<ServerPlayer> all = List.of(player);

		int original = SMCommonConfig.PERMISSION_LEVEL_DUSTBIN.get();
		try {
			// 模拟用户操作：把开箱权限调到最高，等于「不向任何人提示」喵~
			SMCommonConfig.PERMISSION_LEVEL_DUSTBIN.set(4);
			if (!SweeperMaid.dustbinPromptRecipients(all, SMCommonConfig.PERMISSION_LEVEL_DUSTBIN.get()).isEmpty()) {
				helper.fail("Setting PERMISSION_LEVEL_DUSTBIN to 4 must stop the dustbin prompt for everyone");
				return;
			}
			// 调回 0：提示应恢复，确认不是「一旦关掉就再也开不回来」喵~
			SMCommonConfig.PERMISSION_LEVEL_DUSTBIN.set(0);
			if (SweeperMaid.dustbinPromptRecipients(all, SMCommonConfig.PERMISSION_LEVEL_DUSTBIN.get()).size() != 1) {
				helper.fail("Setting PERMISSION_LEVEL_DUSTBIN back to 0 must restore the prompt");
				return;
			}
		} finally {
			SMCommonConfig.PERMISSION_LEVEL_DUSTBIN.set(original);
		}
		helper.succeed();
	}

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMGameTests() {
	}
}
