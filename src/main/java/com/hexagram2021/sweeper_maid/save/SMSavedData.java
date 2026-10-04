package com.hexagram2021.sweeper_maid.save;

import com.google.common.collect.Lists;
import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
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
	 * @param nbt      NBT 标签喵~
	 * @param provider 注册表提供者喵~
	 */
	public SMSavedData(CompoundTag nbt, HolderLookup.Provider provider) {
		this();
		if (nbt.contains(TAG_DUSTBINS, Tag.TAG_LIST)) {
			ListTag dustbinList = nbt.getList(TAG_DUSTBINS, Tag.TAG_COMPOUND);
			synchronized (this.dustbins) {
				this.dustbins.clear();
				for (int i = 0; i < dustbinList.size(); ++i) {
					CompoundTag dustbinTag = dustbinList.getCompound(i);
					SMDustbinContainer dustbin = createNewDustbin();
					dustbin.fromTag(dustbinTag.getList(TAG_ITEMS, Tag.TAG_COMPOUND), provider);
					this.dustbins.add(dustbin);
				}
			}
		}
	}

	/**
	 * 将存档数据保存到 NBT 标签喵~
	 *
	 * @param nbt      NBT 标签喵~
	 * @param provider 注册表提供者喵~
	 * @return 保存后的 NBT 标签喵~
	 */
	@Override
	public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
		synchronized (this.dustbins) {
			ListTag dustbinList = new ListTag();
			for (SMDustbinContainer dustbin : this.dustbins) {
				CompoundTag dustbinTag = new CompoundTag();
				dustbinTag.put(TAG_ITEMS, dustbin.createTag(provider));
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
	 * 一次原子地取回「总页数 + 钳制后的页码 + 该页容器」喵~
	 * <p>
	 * 清扫结束后的 {@code sortDustbins()} 以及导入打包都会 {@code clear()} 再 {@code addAll()}，
	 * 把 {@code dustbins} 列表整体换掉。如果分两次调用 {@link #getPageCount()} 与
	 * {@link #getDustbinContainer(int)}，中间就可能被这些重建打断，出现「读到 3 页、再取第 2 页时
	 * 列表只剩 1 页」的越界崩溃喵~。菜单每 tick 都要读这份状态，所以必须一次拿全喵~。
	 * </p>
	 *
	 * @param preferredPage 期望的页索引，越界会被钳制喵~
	 * @return 快照；垃圾箱列表为空时返回 {@code null} 喵~
	 */
	@Nullable
	public static PageSnapshot snapshotPage(int preferredPage) {
		synchronized (getInstance().dustbins) {
			List<SMDustbinContainer> dustbins = getInstance().dustbins;
			if (dustbins.isEmpty()) {
				return null;
			}
			int page = Mth.clamp(preferredPage, 0, dustbins.size() - 1);
			return new PageSnapshot(dustbins.size(), page, dustbins.get(page));
		}
	}

	/**
	 * 某一页状态的原子快照喵~
	 *
	 * @param pageCount 总页数（至少为 1）喵~
	 * @param page      钳制后的页索引喵~
	 * @param container 该页的垃圾箱容器喵~
	 */
	public record PageSnapshot(int pageCount, int page, SMDustbinContainer container) {
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
						ItemStack key = stack.copyWithCount(1);
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
					page.setItem(slot, entry.getKey().copyWithCount(put));
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

	/**
	 * 清空所有垃圾箱喵~
	 *
	 * @return 清除的物品总数喵~
	 */
	public int clearDustbins() {
		synchronized (this.dustbins) {
			int cleared = 0;
			for (SMDustbinContainer dustbin : this.dustbins) {
				for (int i = 0; i < dustbin.getContainerSize(); ++i) {
					ItemStack stack = dustbin.getItem(i);
					if (!stack.isEmpty()) {
						cleared += stack.getCount();
					}
				}
				dustbin.clearContent();
			}
			this.setDirty();
			return cleared;
		}
	}

	/**
	 * 将所有垃圾箱内容打包到指定文件（gzip NBT），随后清空垃圾箱喵~
	 * <p>
	 * 文件格式为 {@code {Items:[{stack, count}, ...]}}，与垃圾箱槽位格式一致喵~
	 * </p>
	 *
	 * @param provider 注册表提供者喵~
	 * @param file     输出文件喵~
	 * @return 打包的物品总数；垃圾箱为空时返回 0 且不写文件喵~
	 * @throws IOException 文件写入失败喵~
	 */
	public int packDustbins(HolderLookup.Provider provider, Path file) throws IOException {
		synchronized (this.dustbins) {
			ListTag all = new ListTag();
			for (SMDustbinContainer dustbin : this.dustbins) {
				ListTag pageTag = dustbin.createTag(provider);
				for (Tag tag : pageTag) {
					all.add(tag);
				}
			}
			if (all.isEmpty()) {
				return 0;
			}
			CompoundTag root = new CompoundTag();
			root.put("Items", all);
			NbtIo.writeCompressed(root, file);
			for (SMDustbinContainer dustbin : this.dustbins) {
				dustbin.clearContent();
			}
			this.setDirty();
			int total = 0;
			for (Tag tag : all) {
				total += ((CompoundTag) tag).getInt("count");
			}
			return total;
		}
	}

	/**
	 * 从打包文件导入物品并合并进垃圾箱喵~
	 *
	 * @param provider 注册表提供者喵~
	 * @param file     打包文件喵~
	 * @return 导入的物品总数喵~
	 * @throws IOException 文件读取失败喵~
	 */
	public int importPack(HolderLookup.Provider provider, Path file) throws IOException {
		CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		ListTag all = root.getList("Items", Tag.TAG_COMPOUND);
		int total = 0;
		for (Tag tag : all) {
			CompoundTag slot = (CompoundTag) tag;
			ItemStack stack = ItemStack.SINGLE_ITEM_CODEC
					.parse(provider.createSerializationContext(NbtOps.INSTANCE), slot.get("stack"))
					.result().orElse(ItemStack.EMPTY);
			if (!stack.isEmpty()) {
				stack.setCount(slot.getInt("count"));
				this.addItemToDustbin(stack);
				total += stack.getCount();
			}
		}
		this.sortDustbins();
		return total;
	}
}