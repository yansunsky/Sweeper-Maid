package com.hexagram2021.sweeper_maid.inventory;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 扫帚女仆模组注册类喵~
 * <p>
 * 负责注册自定义菜单类型喵~
 * </p>
 */
public final class SMRegistries {
	/**
	 * 菜单类型注册表喵~
	 */
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SweeperMaid.MODID);

	/**
	 * 垃圾箱菜单类型喵~
	 */
	public static final DeferredHolder<MenuType<?>, MenuType<DustbinMenu>> DUSTBIN_MENU = MENUS.register("dustbin", () -> new MenuType<>((IContainerFactory<DustbinMenu>) DustbinMenu::new, FeatureFlags.VANILLA_SET));

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMRegistries() {
	}
}
