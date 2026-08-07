package com.hexagram2021.sweeper_maid.inventory;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SweeperMaid.MODID);

	/**
	 * 垃圾箱菜单类型喵~
	 */
	public static final RegistryObject<MenuType<DustbinMenu>> DUSTBIN_MENU = MENUS.register("dustbin",
			() -> IForgeMenuType.create((int containerId, net.minecraft.world.entity.player.Inventory playerInventory, FriendlyByteBuf extraData) -> new DustbinMenu(containerId, playerInventory, extraData)));

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMRegistries() {
	}
}
