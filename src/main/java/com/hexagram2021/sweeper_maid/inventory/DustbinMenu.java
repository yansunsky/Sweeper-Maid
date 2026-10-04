package com.hexagram2021.sweeper_maid.inventory;

import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import com.hexagram2021.sweeper_maid.network.SMDustbinStatePayload;
import com.hexagram2021.sweeper_maid.save.SMDustbinContainer;
import com.hexagram2021.sweeper_maid.save.SMDustbinSlot;
import com.hexagram2021.sweeper_maid.save.SMSavedData;
import com.hexagram2021.sweeper_maid.util.SMText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 垃圾箱菜单喵~
 * <p>
 * 6×9 大箱子布局，支持翻页。为了「服务端装模组、客户端可以不装」，本类刻意<b>不</b>注册自定义
 * {@link MenuType}，而是复用原版 {@link MenuType#GENERIC_9x6} 喵~：
 * <ul>
 *     <li><b>菜单类型必须存在于原版注册表</b>：自定义 {@code MenuType} 会被 NeoForge 视为「同步注册表」，
 *     未装本模组的客户端（无论是纯原版还是只装了 NeoForge）在连接阶段就会因为注册表键无法解析而断开，
 *     或者打开界面时抛「No value with id」异常喵~</li>
 *     <li>槽位数量与坐标与原版 6 行大箱子完全一致，所以未装模组的客户端照样能查看 / 取出 / 放入当前页，
 *     只是没有翻页按钮喵~</li>
 *     <li>当前页与总页数<b>不再</b>走数据槽（{@code DataSlot}）：原版大箱子菜单的数据槽数量为 0，
 *     未装模组的客户端收到数据槽同步包会下标越界崩溃喵~。改由
 *     {@link SMDustbinStatePayload} 这条可选网络通道承载，翻页状态始终以服务端为准喵~</li>
 *     <li>容器槽位使用 {@link SMDustbinSlot}，支持大堆叠并限制单次取走数量喵~</li>
 * </ul>
 * </p>
 */
public class DustbinMenu extends AbstractContainerMenu implements Container {
	/**
	 * 当前页容器（委托目标，翻页时切换引用）喵~
	 */
	private SMDustbinContainer pageContainer;
	/**
	 * 当前页索引（0 起）喵~
	 */
	private int currentPage;
	/**
	 * 总页数（显示用，至少为 1）喵~
	 */
	private int pageCount;
	/**
	 * 是否为客户端侧菜单喵~
	 * <p>
	 * 客户端菜单只用于渲染与本地点击预测，绝不访问 {@link SMSavedData}（客户端没有存档数据）喵~
	 * </p>
	 */
	private final boolean clientSide;
	/**
	 * 打开本菜单的服务端玩家，用于主动推送翻页状态；客户端菜单为 {@code null} 喵~
	 */
	@Nullable
	private final ServerPlayer owner;

	private int lastSyncedPage = -1;
	private int lastSyncedPageCount = -1;

	/**
	 * 服务端构造方法喵~
	 *
	 * @param containerId     菜单 ID 喵~
	 * @param playerInventory 玩家物品栏喵~
	 * @param dustbin         垃圾箱容器（当前页）喵~
	 * @param page            当前页索引（0 起）喵~
	 * @param pageCount       当前总页数喵~
	 */
	public DustbinMenu(int containerId, Inventory playerInventory, SMDustbinContainer dustbin, int page, int pageCount) {
		this(containerId, playerInventory, dustbin, page, pageCount, false);
	}

	/**
	 * 客户端构造方法喵~
	 * <p>
	 * 客户端接管界面时使用：使用一个本地的大容量容器承接服务端同步过来的槽位内容。必须是
	 * {@link SMDustbinContainer} 而不是原版 {@code SimpleContainer}，否则数量会被钳到 64，
	 * 超大堆叠显示不正确喵~
	 * </p>
	 *
	 * @param containerId     菜单 ID（必须与服务端一致）喵~
	 * @param playerInventory 玩家物品栏喵~
	 */
	public DustbinMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new SMDustbinContainer(), 0, 1, true);
	}

	private DustbinMenu(int containerId, Inventory playerInventory, SMDustbinContainer dustbin, int page, int pageCount, boolean clientSide) {
		// 复用原版大箱子类型：未装模组的客户端也能创建出槽位布局一致的菜单喵~
		super(MenuType.GENERIC_9x6, containerId);
		checkContainerSize(dustbin, SMDustbinContainer.SIZE);
		this.clientSide = clientSide;
		this.pageContainer = dustbin;
		this.currentPage = Math.max(page, 0);
		this.pageCount = Math.max(pageCount, 1);
		this.owner = !clientSide && playerInventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
		this.lastSyncedPage = this.currentPage;
		this.lastSyncedPageCount = this.pageCount;
		dustbin.startOpen(playerInventory.player);

		for (int j = 0; j < 6; ++j) {
			for (int k = 0; k < 9; ++k) {
				this.addSlot(new SMDustbinSlot(this, k + j * 9, 8 + k * 18, 18 + j * 18));
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
		player.openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				// 标题会直接发往客户端：装了模组的中文客户端应当看到「垃圾箱 N」，
				// 而未装模组的客户端没有语言文件——优先按它自己的语言查模组语言文件，
				// 查不到再退回字面英文，绝不把 sweeper_maid.dustbin.name 这样的键名画到标题上喵~
				return SMText.forPlayerOrLiteral(player, SMCommonConfig.DUSTBIN_NAME, "sweeper_maid.dustbin.name", "Dustbin %s", clamped);
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player1) {
				return new DustbinMenu(id, inventory, SMSavedData.getDustbinContainer(clamped), clamped, pageCount);
			}
			// 注意：这里不能覆写 writeClientSideData 喵~
			// 一旦写入非空附加数据，ServerPlayer#openMenu 会改发 NeoForge 的 AdvancedOpenScreenPayload，
			// 而未装模组的客户端没有这条通道，服务端会在发送时直接抛异常喵~
		});
		if (player.containerMenu instanceof DustbinMenu menu) {
			SMDustbinStatePayload.send(player, menu);
		}
	}

	/**
	 * 切换当前页喵~
	 * <p>
	 * 仅切换内部容器引用并同步，不关闭重开界面（避免鼠标被锁定居中）喵~。翻页状态随后由
	 * {@link #syncPageStateToClient()} 推送给装了模组的客户端喵~
	 * </p>
	 *
	 * @param newPage 目标页索引（越界自动钳制）喵~
	 */
	public void setPageIndex(int newPage) {
		if (this.clientSide || !SMSavedData.hasInstance()) {
			return;
		}
		int pageCount = SMSavedData.getPageCount();
		if (pageCount <= 0) {
			return;
		}
		this.currentPage = Mth.clamp(newPage, 0, pageCount - 1);
		this.pageContainer = SMSavedData.getDustbinContainer(this.currentPage);
		// 与 rebindPageContainerIfDetached 保持一致：换页等同于换容器，必须全量重发，
		// 否则增量同步会拿新页去比对旧页的 lastSlots，判定成「没变」而一格格都不发喵~
		this.broadcastFullState();
	}

	/**
	 * 应用服务端下发的翻页状态（仅客户端）喵~
	 *
	 * @param page      当前页索引喵~
	 * @param pageCount 总页数喵~
	 */
	public void applyClientState(int page, int pageCount) {
		this.currentPage = Math.max(page, 0);
		this.pageCount = Math.max(pageCount, 1);
	}

	/**
	 * 获取当前页索引喵~
	 *
	 * @return 当前页索引喵~
	 */
	public int getCurrentPage() {
		return this.currentPage;
	}

	/**
	 * 获取总页数喵~
	 *
	 * @return 总页数喵~
	 */
	public int getPageCount() {
		return this.pageCount;
	}

	/**
	 * 每 tick 同步翻页状态到客户端喵~
	 * <p>
	 * 页数可能因为清扫新增页、或管理员清空 / 打包垃圾箱而减少，重开界面是来不及的，
	 * 所以在这里检测变化并补发状态喵~
	 * </p>
	 */
	@Override
	public void broadcastChanges() {
		this.rebindPageContainerIfRotated();
		this.syncPageStateToClient();
		super.broadcastChanges();
	}

	/**
	 * 清扫重排后重新挂上存档里<b>当前</b>的页容器喵~
	 * <p>
	 * {@code sortDustbins()} 会 {@code clear()} 再 {@code addAll()}，把每一页都换成<b>新的</b>容器实例，
	 * 但页码与页数往往都没变喵~。如果不重新指向，槽位会一直读到那个孤立的旧容器：界面「冻」在清扫前的内容，
	 * 玩家的点击也会落在一个没人再看的对象上，等于白点喵~。
	 * </p>
	 * <p>
	 * 这段逻辑<b>不能</b>挂在 {@link #syncPageStateToClient()} 里：后者在客户端菜单或
	 * 「没有目标玩家」时会提前返回，而重新挂容器是所有服务端菜单都必须做的事，
	 * 与「要不要给这个客户端发状态包」无关喵~。
	 * </p>
	 */
	private void rebindPageContainerIfRotated() {
		if (this.clientSide || !SMSavedData.hasInstance()) {
			return;
		}
		SMSavedData.PageSnapshot snapshot = SMSavedData.snapshotPage(this.currentPage);
		if (snapshot == null) {
			return;
		}
		if (snapshot.container() != this.pageContainer) {
			this.pageContainer = snapshot.container();
			this.currentPage = snapshot.page();
			// 换了容器就必须全量重发：增量同步只比对 lastSlots 里旧的槽位内容，会把新页当成「没变」喵~
			this.broadcastFullState();
		}
	}

	/**
	 * 把当前页与总页数推送给装了模组的客户端喵~
	 * <p>
	 * 页码 / 页数一旦变化就补发一次状态；缓存未命中判断依赖 {@link #rebindPageContainerIfRotated()}
	 * 已经跑过，所以这里读到的 {@link #pageContainer} 一定是最新的喵~。
	 * </p>
	 */
	private void syncPageStateToClient() {
		if (this.clientSide || this.owner == null || !SMSavedData.hasInstance()) {
			return;
		}
		SMSavedData.PageSnapshot snapshot = SMSavedData.snapshotPage(this.currentPage);
		int shownCount = snapshot == null ? 1 : snapshot.pageCount();
		int page = snapshot == null ? 0 : snapshot.page();
		if (page == this.lastSyncedPage && shownCount == this.lastSyncedPageCount) {
			return;
		}
		this.currentPage = page;
		this.pageCount = shownCount;
		this.lastSyncedPage = page;
		this.lastSyncedPageCount = shownCount;
		SMDustbinStatePayload.send(this.owner, this);
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
	 * 处理玩家点击喵~
	 * <p>
	 * 唯一被接管的是「数字键 / F 键与快捷栏交换」（{@code ClickType.SWAP}）：原版那条分支是
	 * {@code inventory.setItem(hotbar, slot.getItem())}，而 {@code Inventory#setItem} 内部只是
	 * {@code nonnulllist.set(...)}，<b>既不调用 {@code limitSize}，也完全不过</b>
	 * {@link SMDustbinSlot#tryRemove} 喵~。于是玩家按一下数字键，就能把整个超大堆
	 * （默认上限 100 万）原样搬进一个快捷栏格子，随后掉落 / 死亡写档时会生成超大堆，
	 * 正是本模组要防的存档损坏喵~。
	 * </p>
	 * <p>
	 * 因此这里在进入原版逻辑前先做一次「拆分搬运」：快捷栏目标格为空时，只搬走
	 * {@link SMDustbinContainer#TAKE_LIMIT} 个，其余留在原位；目标格非空时无法同时安置
	 * 「换进来的物品」与「留下的余量」，就直接不响应（与原版各种被 {@code mayPickup} 挡下的
	 * 情况一样是静默无效），绝不做会让数量凭空增减的操作喵~。
	 * </p>
	 *
	 * @param slotId    槽位索引喵~
	 * @param button    按钮 / 快捷栏索引喵~
	 * @param clickType 点击类型喵~
	 * @param player    玩家实例喵~
	 */
	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		if (clickType == ClickType.SWAP && this.handleSwapClick(slotId, button, player)) {
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	/**
	 * 拦截「超大堆 + 数字键交换」喵~
	 *
	 * @param slotId 槽位索引喵~
	 * @param button 快捷栏索引（0..8）或副手（40）喵~
	 * @param player 玩家实例喵~
	 * @return 是否已完全处理（{@code true} 时不再走原版逻辑）喵~
	 */
	private boolean handleSwapClick(int slotId, int button, Player player) {
		if (this.clientSide || slotId < 0 || slotId >= SMDustbinContainer.SIZE) {
			return false;
		}
		if (!((button >= 0 && button < 9) || button == 40)) {
			return false;
		}
		Slot slot = this.getSlot(slotId);
		ItemStack inSlot = slot.getItem();
		if (inSlot.getCount() <= SMDustbinContainer.TAKE_LIMIT) {
			// 正常堆叠交给原版处理，行为与其它容器一致喵~
			return false;
		}
		Inventory inventory = player.getInventory();
		if (!inventory.getItem(button).isEmpty()) {
			// 目标格非空：余量与换进来的物品抢同一个槽位，无法安全安置，直接不响应喵~
			return true;
		}
		ItemStack taken = inSlot.copyWithCount(SMDustbinContainer.TAKE_LIMIT);
		ItemStack remainder = inSlot.copyWithCount(inSlot.getCount() - SMDustbinContainer.TAKE_LIMIT);
		inventory.setItem(button, taken);
		slot.setByPlayer(remainder);
		slot.onTake(player, taken);
		// 两边数量都变了，推一次全量状态，避免客户端按原版预测留下错误的本地视图喵~
		this.broadcastFullState();
		return true;
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
