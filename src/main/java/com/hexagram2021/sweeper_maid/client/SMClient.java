package com.hexagram2021.sweeper_maid.client;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.inventory.SMRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端注册类喵~
 * <p>
 * 负责注册垃圾箱菜单对应的客户端界面喵~
 * </p>
 */
@EventBusSubscriber(modid = SweeperMaid.MODID, value = Dist.CLIENT)
public final class SMClient {
	/**
	 * 注册菜单界面喵~
	 *
	 * @param event 菜单界面注册事件喵~
	 */
	@SubscribeEvent
	public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
		event.register(SMRegistries.DUSTBIN_MENU.get(), SMDustbinScreen::new);
	}

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMClient() {
	}
}