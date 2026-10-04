package com.hexagram2021.sweeper_maid.gametest;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import com.hexagram2021.sweeper_maid.network.SMDustbinStatePayload;
import com.hexagram2021.sweeper_maid.network.SMDustbinSyncRequestPayload;
import com.hexagram2021.sweeper_maid.network.SMPageChangePayload;
import com.hexagram2021.sweeper_maid.save.SMDustbinContainer;
import com.hexagram2021.sweeper_maid.save.SMDustbinSlot;
import com.hexagram2021.sweeper_maid.save.SMSavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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

/**
 * 扫帚女仆模组游戏测试类喵~
 * <p>
 * 覆盖垃圾箱改造的核心逻辑：
 * <ul>
 *     <li>大容量堆叠的自定义序列化（>99 存档回归测试）喵~</li>
 *     <li>同种物品合并与满页自动新建喵~</li>
 *     <li>清扫后全局重排与动态页数喵~</li>
 *     <li>玩家单次取走数量钳制（最多 64）喵~</li>
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
	 * 读取模组实例的掉落物实体计数喵~
	 *
	 * @return 当前掉落物实体（堆）总数喵~
	 */
	private static int getItemEntityCount() {
		try {
			Field field = SweeperMaid.instance.getClass().getDeclaredField("itemEntityCount");
			field.setAccessible(true);
			return field.getInt(SweeperMaid.instance);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 掉落物实体计数测试喵~
	 * <p>
	 * 生成掉落物实体后计数应增加，移除后计数应回落喵~
	 * </p>
	 */
	@GameTest(template = "empty", timeoutTicks = 300)
	public static void testItemEntityCounter(GameTestHelper helper) {
		Vec3 pos = helper.absoluteVec(new Vec3(0.5, 2.0, 0.5));
		ItemEntity first = helper.spawnItem(Items.DIAMOND, pos);
		ItemEntity second = helper.spawnItem(Items.EMERALD, pos);

		helper.runAfterDelay(2, () -> {
			if (getItemEntityCount() < 2) {
				helper.fail("Item entity counter did not increase after spawning, got " + getItemEntityCount());
				return;
			}
			first.discard();
			second.discard();
		});

		helper.runAfterDelay(130, () -> {
			int count = getItemEntityCount();
			if (count > 0) {
				helper.fail("Item entity counter did not return to zero after discard, got " + count);
				return;
			}
			helper.succeed();
		});
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
	 * 这是最贴近真实场景的回归测试：把本模组注册的<b>全部</b>负载喂给 NeoForge 真正的协商器，
	 * 客户端一侧传<b>空列表</b>——正是纯原版客户端在配置阶段能提供的通道集合
	 * （见 {@code NetworkRegistry#initializeOtherConnection}）喵~
	 * </p>
	 * <p>
	 * 只要本模组漏掉一个 {@code .optional()}，协商就会失败，真实服务器会立刻把玩家踢下线。
	 * 这里断言协商必须成功，从而把「未装模组的客户端能不能进服」这条验收标准钉死在自动化测试里喵~
	 * </p>
	 */
	@GameTest(template = "empty")
	public static void testVanillaClientHandshakeNegotiates(GameTestHelper helper) {
		List<NegotiableNetworkComponent> serverComponents = new ArrayList<>();
		for (ConnectionProtocol protocol : ConnectionProtocol.values()) {
			for (PayloadRegistration<?> registration : allRegistrations(protocol)) {
				serverComponents.add(new NegotiableNetworkComponent(registration));
			}
		}
		if (serverComponents.isEmpty()) {
			helper.fail("No payloads were registered at all; the reflection lookup is broken");
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
	 * 私有构造方法，防止实例化喵~
	 */
	private SMGameTests() {
	}
}
