package com.hexagram2021.sweeper_maid.network;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.client.SMDustbinClientState;
import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端的垃圾箱状态通知喵~
 * <p>
 * 界面打开时发送一次，翻页或页数变化时再发。装了本模组的客户端会把原版大箱子界面替换成
 * 带翻页控件的 {@link com.hexagram2021.sweeper_maid.client.SMDustbinScreen}；没装本模组的
 * 客户端根本收不到这个负载（通道注册为 {@code optional()}），于是继续用原版界面——只是没有翻页喵~
 * </p>
 *
 * @param containerId 当前打开的菜单 ID（防止过期负载劫持其它界面）喵~
 * @param page        当前页索引（0 起）喵~
 * @param pageCount   总页数喵~
 */
public record SMDustbinStatePayload(int containerId, int page, int pageCount) implements CustomPacketPayload {
	/**
	 * 负载类型喵~
	 */
	public static final Type<SMDustbinStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SweeperMaid.MODID, "dustbin_state"));
	/**
	 * 负载流编解码器喵~
	 */
	public static final StreamCodec<ByteBuf, SMDustbinStatePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SMDustbinStatePayload::containerId,
			ByteBufCodecs.VAR_INT, SMDustbinStatePayload::page,
			ByteBufCodecs.VAR_INT, SMDustbinStatePayload::pageCount,
			SMDustbinStatePayload::new
	);

	/**
	 * 返回负载类型喵~
	 *
	 * @return 负载类型喵~
	 */
	@Override
	public Type<SMDustbinStatePayload> type() {
		return TYPE;
	}

	/**
	 * 向玩家同步当前页状态喵~
	 * <p>
	 * 必须先判断 {@code hasChannel}：没协商过该通道的客户端（未安装本模组）收到本负载会被
	 * NeoForge 直接抛异常，而不是静默丢包喵~
	 * </p>
	 *
	 * @param player 目标玩家喵~
	 * @param menu   垃圾箱菜单喵~
	 */
	public static void send(ServerPlayer player, DustbinMenu menu) {
		if (!player.connection.hasChannel(TYPE)) {
			return;
		}
		PacketDistributor.sendToPlayer(player,
				new SMDustbinStatePayload(menu.containerId, menu.getCurrentPage(), menu.getPageCount()));
	}

	/**
	 * 客户端处理状态通知喵~
	 * <p>
	 * 负载注册时未指定线程，默认走 {@code HandlerThread.MAIN}，NeoForge 会用
	 * {@code MainThreadPayloadHandler} 包一层，所以这里已经在客户端主线程上，可以直接碰界面喵~
	 * </p>
	 *
	 * @param payload 负载实例喵~
	 * @param context 连接上下文喵~
	 */
	public static void handle(SMDustbinStatePayload payload, IPayloadContext context) {
		SMDustbinClientState.onStateReceived(payload);
	}
}
