package com.hexagram2021.sweeper_maid.gametest;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.save.SMDustbinContainer;
import com.hexagram2021.sweeper_maid.save.SMDustbinSlot;
import com.hexagram2021.sweeper_maid.save.SMSavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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
	 * 私有构造方法，防止实例化喵~
	 */
	private SMGameTests() {
	}
}
