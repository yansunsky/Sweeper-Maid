package com.hexagram2021.sweeper_maid.save;

import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 大容量垃圾箱容器喵~
 * <p>
 * 与普通 {@link SimpleContainer} 的区别：
 * <ul>
 *     <li>每个槽位可堆叠 {@code DUSTBIN_SLOT_CAPACITY} 个同类物品，一格代表一种物品喵~</li>
 *     <li>使用自定义 NBT 序列化（数量以 int 存储），避免原版 1~99 的存档上限喵~</li>
 *     <li>兼容旧版存档读取喵~</li>
 * </ul>
 * </p>
 */
public class SMDustbinContainer extends SimpleContainer {
	/**
	 * 单页（一个容器）的<b>内容</b>槽位数喵~
	 * <p>
	 * 界面一共 6 行 × 9 列 = 54 格，但<b>最下面一行固定留给翻页按钮</b>，所以真正装物品的只有
	 * 上面 5 行 = 45 格喵~。这个数字必须与界面上的按钮行对齐：未装模组的客户端固定认为箱子有
	 * 54 个格子，若内容也用满 54 格就没有地方放按钮了喵~
	 * </p>
	 */
	public static final int SIZE = 45;
	/**
	 * 翻页按钮占用的格子数（界面最下面一行）喵~
	 */
	public static final int BUTTON_SLOTS = 9;
	/**
	 * 玩家单次取走物品的上限（左键/右键均钳制，防止生成数量超过 99 的物品实体导致存档损坏）喵~
	 */
	public static final int TAKE_LIMIT = 64;

	private static final String TAG_STACK = "stack";
	private static final String TAG_COUNT = "count";
	private static final String TAG_ID = "id";

	/**
	 * 读档时这一页装不下的物品喵~
	 * <p>
	 * 老存档一页 54 格，现在只有 45 格；多出来的由 {@link #drainOverflow()} 交给存档数据重排喵~
	 * </p>
	 */
	private final List<ItemStack> overflow = new ArrayList<>();

	/**
	 * 创建一个大容量垃圾箱容器喵~
	 */
	public SMDustbinContainer() {
		super(SIZE);
	}

	/**
	 * 容器的最大堆叠数量喵~
	 *
	 * @return 配置的槽位容量喵~
	 */
	@Override
	public int getMaxStackSize() {
		return SMCommonConfig.DUSTBIN_SLOT_CAPACITY.get();
	}

	/**
	 * 容器的最大堆叠数量（忽略物品自身上限）喵~
	 *
	 * @param stack 物品堆喵~
	 * @return 配置的槽位容量喵~
	 */
	@Override
	public int getMaxStackSize(ItemStack stack) {
		return this.getMaxStackSize();
	}

	/**
	 * 判断能否添加物品喵~
	 * <p>
	 * 存在同种物品的槽位且未满，或者存在空槽位时返回 true 喵~
	 * </p>
	 *
	 * @param stack 物品堆喵~
	 * @return 能否添加喵~
	 */
	@Override
	public boolean canAddItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		for (int i = 0; i < this.getContainerSize(); ++i) {
			ItemStack item = this.getItem(i);
			if (item.isEmpty() || (ItemStack.isSameItemSameComponents(item, stack) && item.getCount() < this.getMaxStackSize())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 将容器内容序列化为 NBT 喵~
	 * <p>
	 * 每个非空槽位保存为 {@code {stack:{id,components...}, count:<int>}}，count 不受原版 1~99 限制喵~
	 * </p>
	 *
	 * @param provider 注册表提供者喵~
	 * @return 物品列表标签喵~
	 */
	@Override
	public ListTag createTag(HolderLookup.Provider provider) {
		ListTag listTag = new ListTag();
		for (int i = 0; i < this.getContainerSize(); ++i) {
			ItemStack stack = this.getItem(i);
			if (!stack.isEmpty()) {
				CompoundTag slotTag = new CompoundTag();
				slotTag.put(TAG_STACK, ItemStack.SINGLE_ITEM_CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack.copyWithCount(1)).getOrThrow());
				slotTag.putInt(TAG_COUNT, stack.getCount());
				listTag.add(slotTag);
			}
		}
		return listTag;
	}

	/**
	 * 从 NBT 加载容器内容喵~
	 * <p>
	 * 优先读取新格式 {@code {stack, count}}；若为旧版存档格式（含 {@code id} 字段），则走原版解析以兼容迁移喵~<br>
	 * 加载后的数量会钳制到当前配置的容量上限喵~
	 * </p>
	 * <p>
	 * <b>老存档迁移</b>：v2.1.x 及更早的存档里，一页有 54 格物品；现在底部一行要留给翻页按钮，
	 * 内容只剩 {@link #SIZE} = 45 格喵~。多出来的格子<b>绝不能静默丢弃</b>，否则玩家一升级就掉物品；
	 * 这里把它们收进 {@link #overflow}，由 {@link SMSavedData} 读完所有页后重排回桶里喵~。
	 * </p>
	 *
	 * @param listTag  物品列表标签喵~
	 * @param provider 注册表提供者喵~
	 */
	@Override
	public void fromTag(ListTag listTag, HolderLookup.Provider provider) {
		this.clearContent();
		this.overflow.clear();
		for (int i = 0; i < listTag.size(); ++i) {
			CompoundTag slotTag = listTag.getCompound(i);
			ItemStack stack = ItemStack.EMPTY;
			if (slotTag.contains(TAG_STACK, Tag.TAG_COMPOUND)) {
				stack = ItemStack.SINGLE_ITEM_CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), slotTag.get(TAG_STACK)).result().orElse(ItemStack.EMPTY);
				if (!stack.isEmpty()) {
					stack.setCount(slotTag.getInt(TAG_COUNT));
				}
			} else if (slotTag.contains(TAG_ID, Tag.TAG_STRING)) {
				stack = ItemStack.parse(provider, slotTag).orElse(ItemStack.EMPTY);
			}
			if (!stack.isEmpty()) {
				stack.limitSize(this.getMaxStackSize());
				ItemStack leftover = this.addItem(stack);
				if (!leftover.isEmpty()) {
					// 这一页装不下了（老存档 54 格 → 现在 45 格）：先攒着，交给存档数据重排喵~
					this.overflow.add(leftover);
				}
			}
		}
	}

	/**
	 * 取走「读档时这一页装不下」的物品喵~
	 * <p>
	 * 只在 {@link #fromTag} 里产生，正常游戏过程中恒为空喵~。调用方取走后列表会被清空，
	 * 因此同一个实例重复调用不会把同一批物品算两次喵~。
	 * </p>
	 *
	 * @return 溢出物品；没有溢出时返回空列表喵~
	 */
	public List<ItemStack> drainOverflow() {
		if (this.overflow.isEmpty()) {
			return List.of();
		}
		List<ItemStack> drained = new ArrayList<>(this.overflow);
		this.overflow.clear();
		return drained;
	}
}
