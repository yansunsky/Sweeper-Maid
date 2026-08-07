package com.hexagram2021.sweeper_maid.network;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 翻页请求负载喵~
 * <p>
 * 客户端点击上一页/下一页时发送到服务端，服务端计算目标页并重新打开界面喵~
 * </p>
 *
 * @param delta 翻页增量（-1 上一页，+1 下一页）喵~
 */
public record SMPageChangePayload(int delta) implements CustomPacketPayload {
	/**
	 * 负载类型喵~
	 */
	public static final Type<SMPageChangePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SweeperMaid.MODID, "page_change"));
	/**
	 * 负载流编解码器喵~
	 */
	public static final StreamCodec<ByteBuf, SMPageChangePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SMPageChangePayload::delta,
			SMPageChangePayload::new
	);

	/**
	 * 返回负载类型喵~
	 *
	 * @return 负载类型喵~
	 */
	@Override
	public Type<SMPageChangePayload> type() {
		return TYPE;
	}

	/**
	 * 服务端处理翻页请求喵~
	 * <p>
	 * 仅切换当前打开菜单的页容器并同步数据，不关闭重开界面（避免鼠标被锁定居中）喵~
	 * </p>
	 *
	 * @param payload 负载实例喵~
	 * @param context 连接上下文喵~
	 */
	public static void handle(SMPageChangePayload payload, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof DustbinMenu menu) {
			menu.setPageIndex(menu.getCurrentPage() + payload.delta());
		}
	}
}