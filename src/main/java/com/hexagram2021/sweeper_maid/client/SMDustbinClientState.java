package com.hexagram2021.sweeper_maid.client;

import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import com.hexagram2021.sweeper_maid.network.SMDustbinStatePayload;
import com.hexagram2021.sweeper_maid.network.SMDustbinSyncRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端垃圾箱状态喵~
 * <p>
 * 服务端打开垃圾箱时发的是<b>原版</b> 6 行大箱子菜单，所以装了模组的客户端在本类里做一次「接管」：
 * <ol>
 *     <li>把原版 {@link ChestMenu} 换成 {@link DustbinMenu}——原版菜单的容器是 {@code SimpleContainer}，
 *     它会把数量钳到 64，超大堆叠会显示成 64 喵~</li>
 *     <li>把原版 {@code ContainerScreen} 换成 {@link SMDustbinScreen}，多出翻页控件与数量缩写喵~</li>
 *     <li>向服务端请求一次全量重发，把接管前已经被钳掉的数量纠正回来喵~</li>
 * </ol>
 * 没装模组的客户端收不到 {@link SMDustbinStatePayload}，于是完全不会走到这里，继续用原版界面喵~
 * </p>
 * <p>
 * 本类标了 {@link OnlyIn}：它引用了 {@code Minecraft} 等客户端专有类型，加上该注解后 NeoForge 的
 * dist cleaner 会在专用服务端把这个类整体剥掉，不必再依赖「服务端永远不会调用它」这种口头约定喵~
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public final class SMDustbinClientState {
	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMDustbinClientState() {
	}

	/**
	 * 收到服务端下发的翻页状态喵~
	 * <p>
	 * 首次收到时接管界面；之后只更新页码。所有分支都先校验 {@code containerId}，
	 * 避免迟到的负载劫持玩家后开的其它界面喵~
	 * </p>
	 *
	 * @param payload 状态负载喵~
	 */
	public static void onStateReceived(SMDustbinStatePayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		AbstractContainerMenu open = player.containerMenu;
		if (open == null) {
			return;
		}

		// 已经被接管过：只刷新页码喵~
		if (open instanceof DustbinMenu dustbin) {
			if (dustbin.containerId == payload.containerId()) {
				dustbin.applyClientState(payload.page(), payload.pageCount());
			}
			return;
		}

		// 只接管服务端刚刚打开的那只原版大箱子喵~
		if (!(open instanceof ChestMenu chestMenu) || chestMenu.containerId != payload.containerId()) {
			return;
		}

		Screen current = minecraft.screen;
		if (!(current instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}

		DustbinMenu menu = new DustbinMenu(chestMenu.containerId, player.getInventory());
		menu.applyClientState(payload.page(), payload.pageCount());
		// 沿用服务端下发的标题（服务端已按配置 / 语言组装好）喵~
		SMDustbinScreen screen = new SMDustbinScreen(menu, player.getInventory(), containerScreen.getTitle());
		player.containerMenu = menu;
		// setScreen 只会调用旧界面的 removed()（纯客户端清理），不会发关闭包，所以 menuId 保持不变喵~
		minecraft.setScreen(screen);
		// 接管前收到的槽位数量已被原版容器钳到 64，主动要一次全量重发喵~
		PacketDistributor.sendToServer(new SMDustbinSyncRequestPayload());
	}
}
