package com.hexagram2021.sweeper_maid.inventory;

import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import com.hexagram2021.sweeper_maid.save.SMDustbinContainer;
import com.hexagram2021.sweeper_maid.save.SMSavedData;
import com.hexagram2021.sweeper_maid.util.SMText;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/**
 * 垃圾箱菜单喵~
 * <p>
 * 6×9 大箱子布局，支持翻页：
 * <ul>
 *     <li>菜单自身实现 {@link Container}，槽位绑定到菜单，翻页时仅切换当前页容器引用，
 *     界面无需关闭重开（避免鼠标被锁定居中）喵~</li>
 *     <li>持有当前页索引与总页数两个数据槽，自动同步到客户端喵~</li>
 *     <li>容器槽位使用 {@link com.hexagram2021.sweeper_maid.save.SMDustbinSlot}，支持大堆叠与取走限制喵~</li>
 *     <li>通过 {@link #open(ServerPlayer, int)} 打开指定页喵~</li>
 * </ul>
 * </p>
 */
public class DustbinMenu extends AbstractContainerMenu implements Container {
	/**
	 * 当前页数据槽索引喵~
	 */
	public static final int DATA_PAGE = 0;
	/**
	 * 总页数数据槽索引喵~
	 */
	public static final int DATA_PAGE_COUNT = 1;

	private final DataSlot pageData;
	private final DataSlot pageCountData;
	/**
	 * 当前页容器（委托目标，翻页时切换引用）喵~
	 */
	private SMDustbinContainer pageContainer;
	private int lastSyncedPageCount = -1;

	/**
	 * 服务端/通用构造方法喵~
	 *
	 * @param containerId 菜单 ID 喵~
	 * @param playerInventory 玩家物品栏喵~
	 * @param dustbin     垃圾箱容器喵~
	 * @param page        当前页索引（0 起）喵~
	 * @param pageCount   当前总页数喵~
	 */
	public DustbinMenu(int containerId, Inventory playerInventory, SMDustbinContainer dustbin, int page, int pageCount) {
		super(SMRegistries.DUSTBIN_MENU.get(), containerId);
		checkContainerSize(dustbin, SMDustbinContainer.SIZE);
		this.pageContainer = dustbin;
		this.pageData = this.addDataSlot(DataSlot.standalone());
		this.pageData.set(Math.max(page, 0));
		this.pageCountData = this.addDataSlot(DataSlot.standalone());
		this.pageCountData.set(Math.max(pageCount, 1));
		this.lastSyncedPageCount = Math.max(pageCount, 1);
		this.pageContainer.startOpen(playerInventory.player);

		for (int j = 0; j < 6; ++j) {
			for (int k = 0; k < 9; ++k) {
				this.addSlot(new com.hexagram2021.sweeper_maid.save.SMDustbinSlot(this, k + j * 9, 8 + k * 18, 18 + j * 18));
			}
		}

		for (int l = 0; l < 3; ++l) {
			for (int j1 = 0; j1 < 9; ++j1) {
				this.addSlot(new Slot(playerInventory, j1 + l * 9 + 9, 8 + j1 * 18, 103 + l * 18 + 36));
			}
		}

		for (int i1 = 0; i1 < 9; ++i1) {
			this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 161 + 36));
		}
	}

	/**
	 * 切换当前页喵~
	 * <p>
	 * 仅切换内部容器引用并同步数据，不关闭重开界面喵~
	 * </p>
	 *
	 * @param newPage 目标页索引（越界自动钳制）喵~
	 */
	public void setPageIndex(int newPage) {
		int pageCount = SMSavedData.hasInstance() ? SMSavedData.getPageCount() : 1;
		int clamped = Mth.clamp(newPage, 0, Math.max(pageCount - 1, 0));
		this.pageContainer = SMSavedData.getDustbinContainer(clamped);
		this.pageData.set(clamped);
		this.pageCountData.set(pageCount);
		this.broadcastChanges();
	}

	/**
	 * 客户端构造方法喵~
	 * <p>
	 * 从服务端随打开界面发送的数据中读取当前页与总页数喵~
	 * </p>
	 *
	 * @param containerId 菜单 ID 喵~
	 * @param playerInventory 玩家物品栏喵~
	 * @param extraData   附加数据喵~
	 */
	public DustbinMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
		this(containerId, playerInventory, new SMDustbinContainer(), extraData.readVarInt(), extraData.readVarInt());
	}

	/**
	 * 打开垃圾箱指定页喵~
	 *
	 * @param player 玩家实例喵~
	 * @param page   页索引（0 起），越界时自动钳制喵~
	 */
	public static void open(ServerPlayer player, int page) {
		int pageCount = SMSavedData.getPageCount();
		if (pageCount <= 0) {
			return;
		}
		int clamped = Mth.clamp(page, 0, pageCount - 1);
		NetworkHooks.openScreen(player, new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return SMText.of(SMCommonConfig.DUSTBIN_NAME, "sweeper_maid.dustbin.name", clamped);
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player1) {
				return new DustbinMenu(id, inventory, SMSavedData.getDustbinContainer(clamped), clamped, pageCount);
			}
		}, buffer -> {
			buffer.writeVarInt(clamped);
			buffer.writeVarInt(pageCount);
		});
	}

	/**
	 * 获取当前页索引喵~
	 *
	 * @return 当前页索引喵~
	 */
	public int getCurrentPage() {
		return this.pageData.get();
	}

	/**
	 * 获取总页数喵~
	 *
	 * @return 总页数喵~
	 */
	public int getPageCount() {
		return this.pageCountData.get();
	}

	/**
	 * 每 tick 同步总页数到客户端喵~
	 */
	@Override
	public void broadcastChanges() {
		if (SMSavedData.hasInstance()) {
			int pageCount = SMSavedData.getPageCount();
			if (pageCount != this.lastSyncedPageCount) {
				this.lastSyncedPageCount = pageCount;
				this.pageCountData.set(pageCount);
			}
		}
		super.broadcastChanges();
	}

	/**
	 * 判断玩家是否仍可访问该菜单喵~
	 *
	 * @param player 玩家实例喵~
	 * @return 是否有效喵~
	 */
	@Override
	public boolean stillValid(Player player) {
		return this.pageContainer.stillValid(player);
	}

	/**
	 * Shift 快速移动物品喵~
	 * <p>
	 * 从垃圾箱移出时按物品栏槽位上限（64）分块搬运，多余部分留在垃圾箱中喵~
	 * </p>
	 *
	 * @param player 玩家实例喵~
	 * @param index  槽位索引喵~
	 * @return 移动结果喵~
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack current = slot.getItem();
			result = current.copy();
			if (index < SMDustbinContainer.SIZE) {
				if (!this.moveItemStackTo(current, SMDustbinContainer.SIZE, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(current, 0, SMDustbinContainer.SIZE, false)) {
				return ItemStack.EMPTY;
			}

			if (current.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return result;
	}

	/**
	 * 关闭菜单喵~
	 *
	 * @param player 玩家实例喵~
	 */
	@Override
	public void removed(Player player) {
		super.removed(player);
		this.pageContainer.stopOpen(player);
	}

	// ---- Container 委托实现（槽位绑定到菜单自身，翻页仅切换 pageContainer 引用）喵~ ----

	@Override
	public int getContainerSize() {
		return SMDustbinContainer.SIZE;
	}

	@Override
	public boolean isEmpty() {
		return this.pageContainer.isEmpty();
	}

	@Override
	public ItemStack getItem(int index) {
		return this.pageContainer.getItem(index);
	}

	@Override
	public ItemStack removeItem(int index, int count) {
		return this.pageContainer.removeItem(index, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int index) {
		return this.pageContainer.removeItemNoUpdate(index);
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		this.pageContainer.setItem(index, stack);
	}

	@Override
	public void setChanged() {
		this.pageContainer.setChanged();
	}

	@Override
	public void clearContent() {
		this.pageContainer.clearContent();
	}

	@Override
	public int getMaxStackSize() {
		return this.pageContainer.getMaxStackSize();
	}

	@Override
	public void startOpen(Player player) {
		this.pageContainer.startOpen(player);
	}

	@Override
	public void stopOpen(Player player) {
		this.pageContainer.stopOpen(player);
	}

	@Override
	public boolean canPlaceItem(int index, ItemStack stack) {
		return this.pageContainer.canPlaceItem(index, stack);
	}
}
