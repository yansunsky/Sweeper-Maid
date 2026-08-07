package com.hexagram2021.sweeper_maid.client;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import com.hexagram2021.sweeper_maid.network.SMPageChangePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 垃圾箱界面喵~
 * <p>
 * 在原版大箱子界面基础上，于顶部左侧绘制「上一页 / 页码 / 下一页」翻页控件喵~
 * </p>
 */
public class SMDustbinScreen extends AbstractContainerScreen<DustbinMenu> {
	/**
	 * 界面背景贴图（复用原版 6×9 箱子）喵~
	 */
	private static final ResourceLocation BACKGROUND = new ResourceLocation("textures/gui/container/generic_54.png");

	private static final int BUTTON_WIDTH = 16;
	private static final int BUTTON_HEIGHT = 12;
	/**
	 * 上一页按钮相对界面左上角的 X 坐标喵~
	 */
	private static final int PREV_BUTTON_REL_X = 4;
	/**
	 * 下一页按钮相对界面左上角的 X 坐标喵~
	 */
	private static final int NEXT_BUTTON_REL_X = 42;
	/**
	 * 翻页按钮相对界面左上角的 Y 坐标喵~
	 */
	private static final int BUTTON_REL_Y = 4;

	/**
	 * 创建垃圾箱界面喵~
	 *
	 * @param menu       菜单实例喵~
	 * @param playerInventory 玩家物品栏喵~
	 * @param title      界面标题喵~
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

		int currentPage = this.menu.getCurrentPage();
		int pageCount = this.menu.getPageCount();

		boolean hasPrev = currentPage > 0;
		boolean hasNext = currentPage < pageCount - 1;

		int arrowColor = 0xFFE0E0E0;
		int disabledColor = 0xFF606060;
		int hoverColor = 0xFFFFA500;

		boolean hoveringPrev = isHovering(PREV_BUTTON_REL_X, BUTTON_REL_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
		boolean hoveringNext = isHovering(NEXT_BUTTON_REL_X, BUTTON_REL_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);

		graphics.drawString(this.font, "<", this.leftPos + PREV_BUTTON_REL_X + 3, this.topPos + BUTTON_REL_Y + 3, hasPrev ? (hoveringPrev ? hoverColor : arrowColor) : disabledColor);
		graphics.drawString(this.font, ">", this.leftPos + NEXT_BUTTON_REL_X + 3, this.topPos + BUTTON_REL_Y + 3, hasNext ? (hoveringNext ? hoverColor : arrowColor) : disabledColor);

		if (hoveringPrev && hasPrev) {
			graphics.renderTooltip(this.font, Component.translatable("sweeper_maid.dustbin.previous_page"), mouseX, mouseY);
		} else if (hoveringNext && hasNext) {
			graphics.renderTooltip(this.font, Component.translatable("sweeper_maid.dustbin.next_page"), mouseX, mouseY);
		}

		int textColor = 0xFFE0E0E0;
		String pageText = (currentPage + 1) + "/" + pageCount;
		graphics.drawString(this.font, pageText, this.leftPos + 22, this.topPos + 7, textColor);
	}

	/**
	 * 鼠标点击处理喵~
	 *
	 * @param mouseX X 坐标喵~
	 * @param mouseY Y 坐标喵~
	 * @param button 按钮编号喵~
	 * @return 是否已处理喵~
	 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int currentPage = this.menu.getCurrentPage();
			int pageCount = this.menu.getPageCount();

			if (currentPage > 0 && isHovering(PREV_BUTTON_REL_X, BUTTON_REL_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
				SweeperMaid.CHANNEL.sendToServer(new SMPageChangePayload(-1));
				return true;
			}
			if (currentPage < pageCount - 1 && isHovering(NEXT_BUTTON_REL_X, BUTTON_REL_Y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
				SweeperMaid.CHANNEL.sendToServer(new SMPageChangePayload(1));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
}