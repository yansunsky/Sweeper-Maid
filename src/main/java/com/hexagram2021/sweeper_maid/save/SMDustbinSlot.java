package com.hexagram2021.sweeper_maid.save;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 垃圾箱槽位喵~
 * <p>
 * 容量上限跟随容器（大容量），且玩家单次取走时最多取出 {@link SMDustbinContainer#TAKE_LIMIT} 个，
 * 防止生成数量超过 99 的物品实体导致存档损坏喵~
 * </p>
 */
public class SMDustbinSlot extends Slot {
	/**
	 * 创建垃圾箱槽位喵~
	 *
	 * @param container 容器实例喵~
	 * @param slot      槽位索引喵~
	 * @param x         X 坐标喵~
	 * @param y         Y 坐标喵~
	 */
	public SMDustbinSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	/**
	 * 槽位可接受的最大数量（忽略物品自身上限）喵~
	 *
	 * @param stack 物品堆喵~
	 * @return 容器容量喵~
	 */
	@Override
	public int getMaxStackSize(ItemStack stack) {
		return this.container.getMaxStackSize();
	}

	/**
	 * 取走物品喵~
	 * <p>
	 * 将取走数量钳制到 {@link SMDustbinContainer#TAKE_LIMIT}，避免超大堆被一次取出喵~
	 * </p>
	 * <p>
	 * 注意：「数字键交换」（{@code ClickType.SWAP}）不会走到这里，它直接把整堆搬进快捷栏，
	 * 所以那条路径由 {@code DustbinMenu#clicked} 单独拦下喵~
	 * </p>
	 *
	 * @param amount    请求取走的数量喵~
	 * @param decrement 允许的最大减少量喵~
	 * @param player    玩家实例喵~
	 * @return 取出的物品堆喵~
	 */
	@Override
	public Optional<ItemStack> tryRemove(int amount, int decrement, Player player) {
		return super.tryRemove(Math.min(amount, SMDustbinContainer.TAKE_LIMIT), decrement, player);
	}
}
