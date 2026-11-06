package com.hexagram2021.sweeper_maid.inventory;

import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import com.hexagram2021.sweeper_maid.network.SMDustbinStatePayload;
import com.hexagram2021.sweeper_maid.save.SMDustbinContainer;
import com.hexagram2021.sweeper_maid.save.SMDustbinSlot;
import com.hexagram2021.sweeper_maid.save.SMSavedData;
import com.hexagram2021.sweeper_maid.util.SMText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 垃圾箱菜单喵~
 * <p>
 * 6×9 大箱子布局，支持翻页。为了「服务端装模组、客户端可以不装」，本类刻意<b>不</b>注册自定义
 * {@link MenuType}，而是复用原版 {@link MenuType#GENERIC_9x6} 喵~：
 * <ul>
 *     <li><b>菜单类型必须存在于原版注册表</b>：自定义 {@code MenuType} 会被 NeoForge 视为「同步注册表」，
 * 未装本模组的客户端（无论是纯原版还是只装了 NeoForge）在连接阶段就会因为注册表键无法解析而断开喵~</li>
 *     <li>槽位数量与坐标与原版 6 行大箱子完全一致，所以未装模组的客户端照样能查看 / 取出 / 放入当前页喵~</li>
 *     <li>当前页与总页数<b>不再</b>走数据槽（{@code DataSlot}）：原版大箱子菜单的数据槽数量为 0，
 * 未装模组的客户端收到数据槽同步包会下标越界崩溃喵~。改由
 *     {@link SMDustbinStatePayload} 这条可选网络通道承载，翻页状态始终以服务端为准喵~</li>
 *     <li>容器槽位使用 {@link SMDustbinSlot}，支持大堆叠并限制单次取走数量喵~</li>
 * </ul>
 * </p>
 * <p>
 * <b>翻页按钮是服务端合成的假物品</b>（见 {@link #buttonStackFor}）：界面最下面一行，
 * 也就是第 {@link SMDustbinContainer#SIZE} ~ 53 号槽位，固定放按钮而不是实物喵~。
 * 这样未装模组的玩家也能翻页——他们收到的就是普通箱子界面上几个改过名的染色玻璃板，
 * 点一下是一次普通点击，服务端在 {@link #clicked} 里认出来并换页，客户端什么都不用做喵~。
 * </p>
 * <p>
 * <b>为什么按钮必须占满整行、内容只能 45 格</b>：原版客户端固定认为 6 行箱子有 54 个格子，
 * 服务端全量同步时按索引逐个塞、不做长度校验。若服务端少发 9 个槽位，未装模组的客户端会把
 * 玩家背包的物品错位塞进箱子的空格里喵~。
 * </p>
 */
public class DustbinMenu extends AbstractContainerMenu implements Container {
	/**
	 * 上一页按钮所在的槽位（最下面一行的第 1 格）喵~
	 */
	private static final int SLOT_PREVIOUS = SMDustbinContainer.SIZE;
	/**
	 * 页码按钮所在的槽位（最下面一行的正中间）喵~
	 */
	private static final int SLOT_PAGE_INFO = SMDustbinContainer.SIZE + 4;
	/**
	 * 下一页按钮所在的槽位（最下面一行的最后 1 格）喵~
	 */
	private static final int SLOT_NEXT = SMDustbinContainer.SIZE + 8;
	/**
	 * 箱子区域的槽位总数（45 个内容 + 9 个按钮）喵~
	 */
	private static final int CHEST_SLOTS = SMDustbinContainer.SIZE + SMDustbinContainer.BUTTON_SLOTS;

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
	 * 客户端菜单只用于渲染，绝不访问 {@link SMSavedData}（客户端没有存档数据）喵~
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
	 * 服务端合成按钮物品的缓存（每 tick 的同步会读 9 次，没必要反复新建对象）喵~
	 */
	private ItemStack[] cachedButtons;
	/**
	 * 缓存对应的页码 / 页数，任一变化即失效喵~
	 */
	private int cachedPage = -1;
	private int cachedPageCount = -1;

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

		// 前 45 格：真正的垃圾箱内容；后 9 格：翻页按钮（虚拟槽位）喵~
		for (int row = 0; row < 6; ++row) {
			for (int column = 0; column < 9; ++column) {
				int index = column + row * 9;
				int x = 8 + column * 18;
				int y = 18 + row * 18;
				if (index < SMDustbinContainer.SIZE) {
					this.addSlot(new SMDustbinSlot(this, index, x, y));
				} else {
					this.addSlot(new SMDustbinButtonSlot(this, index, x, y));
				}
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
	 * 判断某个槽位是不是翻页按钮（而不是真正的垃圾箱内容）喵~
	 *
	 * @param index 槽位索引喵~
	 * @return 属于按钮行时返回 true 喵~
	 */
	public static boolean isButtonSlot(int index) {
		return index >= SMDustbinContainer.SIZE && index < CHEST_SLOTS;
	}

	/**
	 * 生成某个按钮槽位要显示的物品喵~
	 * <p>
	 * 只用原版数据组件（{@code CUSTOM_NAME} / {@code LORE}），所以<b>未装模组的客户端也能
	 * 正常显示</b>按钮名称与提示——它们看到的只是几个改过名的染色玻璃板喵~。
	 * </p>
	 *
	 * @param index 槽位索引喵~
	 * @return 该槽位的按钮物品；非按钮槽位返回空喵~
	 */
	private ItemStack buttonStackFor(int index) {
		boolean hasPrevious = this.currentPage > 0;
		boolean hasNext = this.currentPage < this.pageCount - 1;
		return switch (index) {
			case SLOT_PREVIOUS -> buttonStack(
					hasPrevious ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE,
					"sweeper_maid.dustbin.previous_page",
					"Previous page",
					hasPrevious);
			case SLOT_NEXT -> buttonStack(
					hasNext ? Items.LIGHT_BLUE_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE,
					"sweeper_maid.dustbin.next_page",
					"Next page",
					hasNext);
			case SLOT_PAGE_INFO -> pageInfoStack();
			default -> ItemStack.EMPTY;
		};
	}

	/**
	 * 构造一个翻页按钮物品喵~
	 *
	 * @param item     玻璃板物品喵~
	 * @param langKey  名称翻译键喵~
	 * @param fallback 未装模组客户端显示的字面名称喵~
	 * @param enabled  当前页码下该按钮是否可用喵~
	 * @return 按钮物品喵~
	 */
	private static ItemStack buttonStack(Item item, String langKey, String fallback, boolean enabled) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatableWithFallback(langKey, fallback)
				.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatableWithFallback(
								enabled ? "sweeper_maid.dustbin.page_hint" : "sweeper_maid.dustbin.page_blocked",
								enabled ? "Click to turn the page" : "There are no more pages")
						.withStyle(ChatFormatting.DARK_PURPLE))));
		return stack;
	}

	/**
	 * 构造页码物品喵~
	 *
	 * @return 显示「当前页 / 总页数」的纸喵~
	 */
	private ItemStack pageInfoStack() {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.translatableWithFallback("sweeper_maid.dustbin.page_info", "%s / %s",
						this.currentPage + 1, this.pageCount).withStyle(ChatFormatting.WHITE));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatableWithFallback("sweeper_maid.dustbin.page_info_hint", "Current page")
						.withStyle(ChatFormatting.DARK_PURPLE))));
		return stack;
	}

	/**
	 * 取按钮物品（带缓存）喵~
	 * <p>
	 * 服务端每 tick 的 {@code broadcastChanges} 会把 9 个按钮各读一次；页码没变时直接复用上次
	 * 合成的结果，避免无意义地反复新建组件对象喵~。
	 * </p>
	 *
	 * @param index 槽位索引喵~
	 * @return 该槽位的按钮物品喵~
	 */
	private ItemStack cachedButton(int index) {
		if (this.cachedButtons == null || this.cachedPage != this.currentPage || this.cachedPageCount != this.pageCount) {
			ItemStack[] buttons = new ItemStack[SMDustbinContainer.BUTTON_SLOTS];
			for (int i = 0; i < buttons.length; ++i) {
				buttons[i] = this.buttonStackFor(SMDustbinContainer.SIZE + i);
			}
			this.cachedButtons = buttons;
			this.cachedPage = this.currentPage;
			this.cachedPageCount = this.pageCount;
		}
		return this.cachedButtons[index - SMDustbinContainer.SIZE];
	}

	/**
	 * 切换当前页喵~
	 * <p>
	 * 仅切换内部容器引用并同步，不关闭重开界面（避免鼠标被锁定居中）喵~。
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
		this.pageCount = pageCount;
		// 与 rebindPageContainerIfRotated 保持一致：换页等同于换容器，必须全量重发，
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
	 * 对未装模组的客户端这条消息不会发送（{@code hasChannel} 为 false），它们靠着服务端下发的
	 * 按钮物品本身就能翻页喵~。
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
	 * 从垃圾箱移出时按物品栏槽位上限（64）分块搬运，多余部分留在垃圾箱中喵~。
	 * 按钮槽位不参与搬运（搬运范围只到 {@link SMDustbinContainer#SIZE}）喵~。
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
		if (slot != null && slot.hasItem() && !isButtonSlot(index)) {
			ItemStack current = slot.getItem();
			result = current.copy();
			if (index < SMDustbinContainer.SIZE) {
				if (!this.moveItemStackTo(current, CHEST_SLOTS, this.slots.size(), true)) {
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
	 * 两类点击被本类接管：
	 * </p>
	 * <ol>
	 *     <li><b>翻页按钮</b>（{@link #isButtonSlot}）：不论左键、右键、Shift、数字键、Q 还是拖拽，
	 *     一律拦下并吃掉，绝不让那个「假物品」流入任何真实容器喵~。这正是翻页能纯服务端实现的原因——
	 *     未装模组的客户端点一下就是发一次普通点击包，服务端在这里认出来就行喵~。</li>
	 *     <li><b>「数字键 / F 键与快捷栏交换」</b>（{@code ClickType.SWAP}）：原版那条分支是
	 *     {@code inventory.setItem(hotbar, slot.getItem())}，而 {@code Inventory#setItem} 内部只是
	 *     {@code nonnulllist.set(...)}，<b>既不调用 {@code limitSize}，也完全不过</b>
	 *     {@link SMDustbinSlot#tryRemove} 喵~。于是玩家按一下数字键，就能把整个超大堆
	 *     （默认上限 100 万）原样搬进一个快捷栏格子，随后掉落 / 死亡写档时会生成超大堆，
	 *     正是本模组要防的存档损坏喵~。</li>
	 * </ol>
	 *
	 * @param slotId    槽位索引喵~
	 * @param button    按钮 / 快捷栏索引喵~
	 * @param clickType 点击类型喵~
	 * @param player    玩家实例喵~
	 */
	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		if (this.handleButtonClick(slotId, button, clickType)) {
			return;
		}
		if (clickType == ClickType.SWAP && this.handleSwapClick(slotId, button, player)) {
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	/**
	 * 处理对翻页按钮的点击喵~
	 * <p>
	 * 返回 {@code true} 表示这次点击已被完全消化，{@code super.clicked} 不会再执行——按钮物品
	 * 因此<b>永远不可能</b>被拿起来、被 Shift 搬进背包、被 Q 丢出去喵~。
	 * </p>
	 * <p>
	 * 只有左键（含 Shift + 左键）会真正翻页；其它点击类型只是被静默吞掉，既不翻页也不报错，
	 * 与「点了但没反应」的原版按钮手感一致喵~。
	 * </p>
	 *
	 * @param slotId    槽位索引喵~
	 * @param button    鼠标键位喵~
	 * @param clickType 点击类型喵~
	 * @return 是否已处理喵~
	 */
	private boolean handleButtonClick(int slotId, int button, ClickType clickType) {
		if (!isButtonSlot(slotId)) {
			return false;
		}
		if (this.clientSide) {
			// 客户端不做本地预测：翻页完全由服务端裁决，界面内容随服务端全量重发刷新喵~
			return true;
		}
		boolean leftClick = button == 0 && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE);
		if (leftClick) {
			int delta = switch (slotId) {
				case SLOT_PREVIOUS -> -1;
				case SLOT_NEXT -> 1;
				default -> 0;
			};
			if (delta != 0) {
				this.setPageIndex(this.currentPage + delta);
			}
		}
		return true;
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

	/**
	 * 容器的槽位数喵~
	 * <p>
	 * 返回的是<b>箱子内容</b>格数（{@link SMDustbinContainer#SIZE}），不含按钮行喵~。
	 * 按钮槽位是 {@link SMDustbinButtonSlot} 自己的虚拟物品，不经过这里喵~。
	 * </p>
	 *
	 * @return 内容槽位数喵~
	 */
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
		if (isButtonSlot(index)) {
			return this.cachedButton(index);
		}
		return this.pageContainer.getItem(index);
	}

	@Override
	public ItemStack removeItem(int index, int count) {
		if (isButtonSlot(index)) {
			return ItemStack.EMPTY;
		}
		return this.pageContainer.removeItem(index, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int index) {
		if (isButtonSlot(index)) {
			return ItemStack.EMPTY;
		}
		return this.pageContainer.removeItemNoUpdate(index);
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		if (isButtonSlot(index)) {
			return;
		}
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
		if (isButtonSlot(index)) {
			// 按钮格绝不接受任何物品：漏斗、拖拽分配、Shift 搬运都不能把东西塞进来喵~
			return false;
		}
		return this.pageContainer.canPlaceItem(index, stack);
	}

	/**
	 * 翻页按钮的虚拟槽位喵~
	 * <p>
	 * 背后<b>没有</b>真实容器：服务端由 {@link DustbinMenu#buttonStackFor} 现场合成，
	 * 因此天然不可能被复制、也不可能被取出喵~。<br>
	 * {@code mayPickup} / {@code mayPlace} 都返回 {@code false}，从「槽位」这一层就把
	 * 原版所有搬运路径（左键、右键、拖拽、Shift、数字键、Q）堵死喵~。
	 * </p>
	 * <p>
	 * 客户端不走合成分支，而是接住服务端同步下来的那一份：按钮的文案、颜色、可用状态全部由
	 * 服务端决定，客户端只负责画出来喵~。
	 * </p>
	 */
	private static final class SMDustbinButtonSlot extends Slot {
		private final DustbinMenu menu;
		/**
		 * 客户端侧收到并保留的按钮物品喵~
		 */
		private ItemStack synced = ItemStack.EMPTY;

		private SMDustbinButtonSlot(DustbinMenu menu, int index, int x, int y) {
			super(menu, index, x, y);
			this.menu = menu;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public ItemStack getItem() {
			return this.menu.clientSide ? this.synced : this.menu.cachedButton(this.getContainerSlot());
		}

		@Override
		public void set(ItemStack stack) {
			// 服务端不接受写入（保持现场合成的状态）；客户端接住同步下来的那一份喵~
			if (this.menu.clientSide) {
				this.synced = stack;
			}
		}

		@Override
		public void setChanged() {
			// 按钮不是真实内容，改不动任何东西喵~
		}
	}
}
