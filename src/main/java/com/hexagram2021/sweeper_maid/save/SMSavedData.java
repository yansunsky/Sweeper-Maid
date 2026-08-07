package com.hexagram2021.sweeper_maid.save;

import com.google.common.collect.Lists;
import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 扫帚女仆模组存档数据类喵~
 * <p>
 * 负责管理垃圾箱数据，包括：
 * <ul>
 *     <li>存储清理后的物品到多个动态页面的垃圾箱容器中喵~</li>
 *     <li>NBT 数据序列化和反序列化（含旧档迁移）喵~</li>
 *     <li>垃圾箱页面动态生成与全局重排喵~</li>
 *     <li>线程安全的垃圾箱访问喵~</li>
 * </ul>
 * </p>
 *
 * @author liudongyu
 */
public final class SMSavedData extends SavedData {
	@SuppressWarnings("java:S3008")
	@Nullable
	private static SMSavedData INSTANCE;
	/**
	 * 存档数据名称喵~
	 */
	public static final String SAVED_DATA_NAME = "SweeperMaid-SavedData";

	/**
	 * 垃圾箱容器列表（每个容器代表一页）喵~
	 */
	public final List<SMDustbinContainer> dustbins = Lists.newArrayList();

	/**
	 * 默认构造方法喵~
	 */
	public SMSavedData() {
		super();
	}

	/**
	 * NBT 数据标签 - 物品列表喵~
	 */
	private static final String TAG_ITEMS = "Items";
	/**
	 * NBT 数据标签 - 垃圾箱列表喵~
	 */
	private static final String TAG_DUSTBINS = "Dustbins";

	/**
	 * 从 NBT 数据加载存档数据喵~
	 *
	 * @param nbt NBT 标签喵~
	 */
	public SMSavedData(CompoundTag nbt) {
		this();
		if (nbt.contains(TAG_DUSTBINS, Tag.TAG_LIST)) {
			ListTag dustbinList = nbt.getList(TAG_DUSTBINS, Tag.TAG_COMPOUND);
			synchronized (this.dustbins) {
				this.dustbins.clear();
				for (int i = 0; i < dustbinList.size(); ++i) {
					CompoundTag dustbinTag = dustbinList.getCompound(i);
					SMDustbinContainer dustbin = createNewDustbin();
					dustbin.fromTag(dustbinTag.getList(TAG_ITEMS, Tag.TAG_COMPOUND));
					this.dustbins.add(dustbin);
				}
			}
		}
	}

	/**
	 * 将存档数据保存到 NBT 标签喵~
	 *
	 * @param nbt NBT 标签喵~
	 * @return 保存后的 NBT 标签喵~
	 */
	@Override
	public CompoundTag save(CompoundTag nbt) {
		synchronized (this.dustbins) {
			ListTag dustbinList = new ListTag();
			for (SMDustbinContainer dustbin : this.dustbins) {
				CompoundTag dustbinTag = new CompoundTag();
				dustbinTag.put(TAG_ITEMS, dustbin.createTag());
				dustbinList.add(dustbinTag);
			}
			nbt.put(TAG_DUSTBINS, dustbinList);
		}
		return nbt;
	}

	/**
	 * 获取存档数据实例喵~
	 *
	 * @return 存档数据实例喵~
	 * @throws NullPointerException 如果实例尚未初始化喵~
	 */
	public static SMSavedData getInstance() {
		return Objects.requireNonNull(INSTANCE, "SMSavedData has not been initialized yet!");
	}

	/**
	 * 检查存档数据实例是否已初始化喵~
	 *
	 * @return 是否已初始化喵~
	 */
	public static boolean hasInstance() {
		return INSTANCE != null;
	}

	/**
	 * 设置存档数据实例喵~
	 *
	 * @param in 存档数据实例喵~
	 */
	public static void setInstance(SMSavedData in) {
		INSTANCE = in;
	}

	/**
	 * 添加物品到垃圾箱喵~
	 * <p>
	 * 优先并入已存在的同种物品格；否则放入第一个有空格的页；没有空格则新建一页喵~
	 * </p>
	 *
	 * @param stack 要添加的物品堆喵~
	 */
	public void addItemToDustbin(ItemStack stack) {
		synchronized (this.dustbins) {
			for (SMDustbinContainer dustbin : this.dustbins) {
				if (dustbin.canAddItem(stack)) {
					dustbin.addItem(stack);
					this.setDirty();
					return;
				}
			}
			SMDustbinContainer dustbin = createNewDustbin();
			dustbin.addItem(stack);
			this.dustbins.add(dustbin);
			this.setDirty();
		}
	}

	/**
	 * 获取指定页的垃圾箱容器喵~
	 *
	 * @param index 页面索引喵~
	 * @return 垃圾箱容器喵~
	 */
	public static SMDustbinContainer getDustbinContainer(int index) {
		return getInstance().dustbins.get(index);
	}

	/**
	 * 获取当前总页数喵~
	 *
	 * @return 总页数喵~
	 */
	public static int getPageCount() {
		return getInstance().dustbins.size();
	}

	/**
	 * 访问垃圾箱列表喵~
	 * <p>
	 * 以线程安全的方式访问垃圾箱列表，并标记数据为已修改喵~
	 * </p>
	 *
	 * @param consumer 访问垃圾箱列表的消费者函数喵~
	 */
	public void accessDustbins(Consumer<List<SMDustbinContainer>> consumer) {
		synchronized (this.dustbins) {
			consumer.accept(this.dustbins);
		}
		this.setDirty();
	}

	/**
	 * 全局重排喵~
	 * <p>
	 * 收集所有页所有物品，同种合并后按数量从大到小排序（并列时按物品 ID 排序），
	 * 再按每页 54 格重新打包；空页自动移除；至少保留一页喵~。<br>
	 * 使用 Java 内置 Timsort（近乎有序时接近 O(n)），每次清扫完成后调用一次喵~
	 * </p>
	 */
	public void sortDustbins() {
		synchronized (this.dustbins) {
			if (this.dustbins.isEmpty()) {
				return;
			}
			Map<ItemStack, Integer> counts = new HashMap<>();
			for (SMDustbinContainer dustbin : this.dustbins) {
				for (int i = 0; i < dustbin.getContainerSize(); ++i) {
					ItemStack stack = dustbin.getItem(i);
					if (!stack.isEmpty()) {
						ItemStack key = stack.copy();
				key.setCount(1);
						counts.merge(key, stack.getCount(), (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
					}
				}
			}

			List<Map.Entry<ItemStack, Integer>> entries = new ArrayList<>(counts.entrySet());
			entries.sort((a, b) -> {
				int countCmp = Integer.compare(b.getValue(), a.getValue());
				if (countCmp != 0) {
					return countCmp;
				}
				return BuiltInRegistries.ITEM.getKey(a.getKey().getItem()).compareTo(BuiltInRegistries.ITEM.getKey(b.getKey().getItem()));
			});

			int capacity = SMCommonConfig.DUSTBIN_SLOT_CAPACITY.get();
			List<SMDustbinContainer> packed = new ArrayList<>();
			SMDustbinContainer page = null;
			int slot = 0;
			for (Map.Entry<ItemStack, Integer> entry : entries) {
				int remaining = entry.getValue();
				while (remaining > 0) {
					if (page == null || slot >= SMDustbinContainer.SIZE) {
						if (page != null) {
							packed.add(page);
						}
						page = createNewDustbin();
						slot = 0;
					}
					int put = Math.min(remaining, capacity);
					ItemStack packedStack = entry.getKey().copy();
					packedStack.setCount(put);
					page.setItem(slot, packedStack);
					remaining -= put;
					++slot;
				}
			}
			if (page != null) {
				packed.add(page);
			}
			if (packed.isEmpty()) {
				packed.add(createNewDustbin());
			}

			this.dustbins.clear();
			this.dustbins.addAll(packed);
		}
		this.setDirty();
	}

	/**
	 * 初始化垃圾箱列表喵~
	 * <p>
	 * 确保至少存在一页垃圾箱喵~
	 * </p>
	 */
	public static void initialize() {
		synchronized (getInstance().dustbins) {
			if (getInstance().dustbins.isEmpty()) {
				getInstance().dustbins.add(createNewDustbin());
			}
		}
	}

	/**
	 * 创建新的垃圾箱容器喵~
	 * <p>
	 * 创建一个大容量容器，并在内容变化时标记存档数据为已修改喵~
	 * </p>
	 *
	 * @return 新的垃圾箱容器喵~
	 */
	private static SMDustbinContainer createNewDustbin() {
		return new SMDustbinContainer() {
			@Override
			public void setChanged() {
				super.setChanged();
				if (INSTANCE != null) {
					INSTANCE.setDirty();
				}
			}
		};
	}
}