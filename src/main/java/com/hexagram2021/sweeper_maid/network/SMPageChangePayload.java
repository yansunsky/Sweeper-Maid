package com.hexagram2021.sweeper_maid.network;

import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 翻页请求消息喵~
 * <p>
 * 客户端点击上一页/下一页时发送到服务端，服务端仅切换当前打开菜单的页容器并同步数据喵~
 * </p>
 *
 * @param delta 翻页增量（-1 上一页，+1 下一页）喵~
 */
public class SMPageChangePayload {
	private final int delta;

	/**
	 * 创建翻页请求喵~
	 *
	 * @param delta 翻页增量喵~
	 */
	public SMPageChangePayload(int delta) {
		this.delta = delta;
	}

	/**
	 * 编码喵~
	 *
	 * @param msg   消息喵~
	 * @param buffer 缓冲区喵~
	 */
	public static void encode(SMPageChangePayload msg, FriendlyByteBuf buffer) {
		buffer.writeVarInt(msg.delta);
	}

	/**
	 * 解码喵~
	 *
	 * @param buffer 缓冲区喵~
	 * @return 消息喵~
	 */
	public static SMPageChangePayload decode(FriendlyByteBuf buffer) {
		return new SMPageChangePayload(buffer.readVarInt());
	}

	/**
	 * 服务端处理翻页请求喵~
	 * <p>
	 * 在服务端主线程执行，仅切换页容器并同步数据，不关闭重开界面喵~
	 * </p>
	 *
	 * @param msg     消息喵~
	 * @param ctxSupplier 网络上下文喵~
	 */
	public static void handle(SMPageChangePayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player != null && player.containerMenu instanceof DustbinMenu menu) {
				menu.setPageIndex(menu.getCurrentPage() + msg.delta);
			}
		});
		ctx.setPacketHandled(true);
	}
}
