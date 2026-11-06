package com.hexagram2021.sweeper_maid.client;

import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import com.hexagram2021.sweeper_maid.save.SMDustbinSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 垃圾箱界面喵~
 * <p>
 * 装在本模组的客户端上时，把原版 6×9 大箱子界面替换成这个类，负责<b>两件纯展示的事</b>喵~：
 * <ul>
 *     <li>超大堆叠的数量缩写（{@code 1.2K} / {@code 4.6M} / {@code 1.5B}），并在超宽时缩小字号喵~</li>
 *     <li>鼠标悬停时补上原版容器物品 tooltip，大堆叠额外显示确切数量喵~</li>
 * </ul>
 * </p>
 * <p>
 * <b>翻页控件已经不在这里了</b>：翻页按钮是服务端合成的染色玻璃板物品（见
 * {@link DustbinMenu}），未装模组的客户端也能用。客户端不再绘制 {@code <} / {@code >}、
 * 也不再发送翻页请求包，翻页对两端客户端是同一套行为喵~。
 * </p>
 * <p>
 * 本类标了 {@link OnlyIn}：它继承了客户端专有的 {@link AbstractContainerScreen}，
 * 加上该注解后 NeoForge 的 dist cleaner 会在专用服务端把这个类整体剥掉喵~
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public class SMDustbinScreen extends AbstractContainerScreen<DustbinMenu> {
	/**
	 * 界面背景贴图（复用原版 6×9 箱子）喵~
	 */
	private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

	/**
	 * 数量文本在槽位内允许的最大像素宽度喵~
	 * <p>
	 * 槽位宽 16px、间隔 18px，数量文本如果比这个宽就会向左压到相邻槽位的物品上喵~
	 * </p>
	 */
	private static final int COUNT_MAX_WIDTH = 15;
	/**
	 * 数量文本缩放下限喵~（避免缩得太小看不清）
	 */
	private static final float COUNT_MIN_SCALE = 0.35F;

	/**
	 * 创建垃圾箱界面喵~
	 *
	 * @param menu            菜单实例喵~
	 * @param playerInventory 玩家物品栏喵~
	 * @param title           界面标题喵~
	 */
	public SMDustbinScreen(DustbinMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 176;
		this.imageHeight = 222;
		this.titleLabelX = 62;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	/**
	 * 渲染界面背景喵~
	 *
	 * @param graphics    绘制工具喵~
	 * @param partialTick 部分 tick 喵~
	 * @param mouseX      鼠标 X 坐标喵~
	 * @param mouseY      鼠标 Y 坐标喵~
	 */
	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
	}

	/**
	 * 渲染垃圾箱槽位喵~
	 * <p>
	 * 仅对垃圾箱内容槽位（大堆叠）做数量缩写：数量 ≥ 1000 时显示为 {@code 1.2K}、{@code 4.6M}、
	 * {@code 1.5B} 之类；数量文本超出槽位宽度时自动缩小字号，保证不会压到相邻槽位的物品喵~<br>
	 * 翻页按钮槽位与玩家物品栏等其它槽位保持原版渲染喵~；原版「按住拖拽分配物品」时的
	 * 半透明高亮也会保留喵~
	 * </p>
	 */
	@Override
	protected void renderSlot(GuiGraphics graphics, Slot slot) {
		if (!(slot instanceof SMDustbinSlot)) {
			super.renderSlot(graphics, slot);
			return;
		}

		int x = slot.x;
		int y = slot.y;
		ItemStack stack = slot.getItem();
		boolean dragging = !this.menu.getCarried().isEmpty() && this.quickCraftSlots.contains(slot);

		graphics.pose().pushPose();
		graphics.pose().translate(0.0F, 0.0F, 100.0F);
		if (dragging) {
			graphics.fill(x, y, x + 16, y + 16, -2130706433);
		}
		if (!stack.isEmpty()) {
			graphics.renderItem(stack, x, y, x + y * this.imageWidth);
			if (stack.getCount() > 1) {
				renderCount(graphics, abbreviateCount(stack.getCount()), x, y);
			}
		}
		graphics.pose().popPose();
	}

	/**
	 * 在槽位右下角绘制自适应大小的数量文本喵~
	 * <p>
	 * 右对齐且右缘与原版一致；文字宽度超过 {@link #COUNT_MAX_WIDTH} 时缩放字体，
	 * 使其永远不会越出槽位左侧边界、压到相邻格子喵~
	 * </p>
	 *
	 * @param graphics 绘制工具喵~
	 * @param text     数量文本喵~
	 * @param x        槽位 X 坐标喵~
	 * @param y        槽位 Y 坐标喵~
	 */
	private void renderCount(GuiGraphics graphics, String text, int x, int y) {
		int width = this.font.width(text);
		float scale = Math.min(1.0F, Math.max(COUNT_MIN_SCALE, (float) COUNT_MAX_WIDTH / width));
		int scaledWidth = (int) Math.ceil(width * scale);

		graphics.pose().pushPose();
		// 原版 renderItemDecorations 在画数量前会把 z 平移 +200（图标在 z≈150），
		// 这里保持一致，否则文字会被图标盖住。
		graphics.pose().translate(x + 17 - scaledWidth, y + 9, 200.0F);
		graphics.pose().scale(scale, scale, 1.0F);
		graphics.drawString(this.font, text, 0, 0, 0xFFFFFF, true);
		graphics.pose().popPose();
	}

	/**
	 * 渲染物品悬浮信息喵~
	 * <p>
	 * 1.21.1 中 {@link AbstractContainerScreen} 的基类不会自动绘制容器物品 tooltip，
	 * 必须由具体界面在 {@code render} 末尾手动调用（原版 {@code ContainerScreen} 即如此）喵~<br>
	 * 这里在补上自动 tooltip 的同时，为大堆叠物品追加一行「确切数量」，与格子上缩写互补充喵~
	 * </p>
	 */
	@Override
	protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		if (this.menu.getCarried().isEmpty() && this.hoveredSlot instanceof SMDustbinSlot && this.hoveredSlot.hasItem()) {
			ItemStack stack = this.hoveredSlot.getItem();
			List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(stack));
			if (stack.getCount() >= 1000) {
				tooltip.add(Component.translatable("sweeper_maid.dustbin.item_count",
						String.format(Locale.ROOT, "%,d", stack.getCount())).withStyle(ChatFormatting.GRAY));
			}
			graphics.renderTooltip(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
			return;
		}
		super.renderTooltip(graphics, mouseX, mouseY);
	}

	/**
	 * 渲染界面内容喵~
	 *
	 * @param graphics    绘制工具喵~
	 * @param mouseX      鼠标 X 坐标喵~
	 * @param mouseY      鼠标 Y 坐标喵~
	 * @param partialTick 部分 tick 喵~
	 */
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);

		// 1.21.1 容器界面必须在 render 末尾手动绘制物品 tooltip（原版 ContainerScreen 同样如此）
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	/**
	 * 将物品数量缩写为可读的短文本喵~
	 * <p>
	 * 小于 1000 原样返回；达到千 / 百万 / 十亿后保留一位小数，末尾的 {@code .0} 省略
	 * （如 {@code 1200 → 1.2K}、{@code 4600000 → 4.6M}、{@code 1000000 → 1M}）喵~
	 * </p>
	 *
	 * @param count 物品数量喵~
	 * @return 显示用的数量文本喵~
	 */
	private static String abbreviateCount(int count) {
		if (count < 1000) {
			return String.valueOf(count);
		}
		long divisor;
		String suffix;
		if (count >= 1_000_000_000) {
			divisor = 1_000_000_000L;
			suffix = "B";
		} else if (count >= 1_000_000) {
			divisor = 1_000_000L;
			suffix = "M";
		} else {
			divisor = 1_000L;
			suffix = "K";
		}
		long tenths = Math.round(count * 10.0 / divisor);
		if (tenths % 10 == 0) {
			return (tenths / 10) + suffix;
		}
		return (tenths / 10) + "." + (tenths % 10) + suffix;
	}
}
