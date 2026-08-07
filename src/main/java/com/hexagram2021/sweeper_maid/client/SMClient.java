package com.hexagram2021.sweeper_maid.client;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.inventory.SMRegistries;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端注册类喵~
 * <p>
 * 负责注册垃圾箱菜单对应的客户端界面喵~
 * </p>
 */
@Mod.EventBusSubscriber(modid = SweeperMaid.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SMClient {
	/**
	 * 客户端初始化：注册菜单界面喵~
	 *
	 * @param event 客户端初始化事件喵~
	 */
	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> MenuScreens.register(SMRegistries.DUSTBIN_MENU.get(), SMDustbinScreen::new));
	}

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMClient() {
	}
}
