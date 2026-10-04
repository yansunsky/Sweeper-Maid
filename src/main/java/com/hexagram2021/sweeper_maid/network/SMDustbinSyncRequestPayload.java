package com.hexagram2021.sweeper_maid.network;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端的「请重发全部槽位」请求喵~
 * <p>
 * 客户端接管界面时，<b>原版</b>大箱子界面的容器是 {@code SimpleContainer}，它会把数量钳到
 * 64（{@code SimpleContainer#setItem} 里调用 {@code limitSize}），所以在这之前收到的槽位内容
 * 已经被截断了。替换成模组自己的菜单后请求一次全量重发，超大堆叠的数量才能正确显示喵~
 * </p>
 * <p>
 * 用一次显式请求而不是依赖「状态包之后紧跟全量包」的先后顺序：两者走的是不同的线程派发路径，
 * 显式请求与顺序无关，结果确定喵~
 * </p>
 */
public record SMDustbinSyncRequestPayload() implements CustomPacketPayload {
	/**
	 * 负载类型喵~
	 */
	public static final Type<SMDustbinSyncRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SweeperMaid.MODID, "dustbin_sync"));
	/**
	 * 负载流编解码器喵~
	 */
	public static final StreamCodec<RegistryFriendlyByteBuf, SMDustbinSyncRequestPayload> STREAM_CODEC =
			StreamCodec.unit(new SMDustbinSyncRequestPayload());

	/**
	 * 返回负载类型喵~
	 *
	 * @return 负载类型喵~
	 */
	@Override
	public Type<SMDustbinSyncRequestPayload> type() {
		return TYPE;
	}

	/**
	 * 服务端处理重发请求喵~
	 * <p>
	 * 只有装了本模组的客户端会发这个包，服务端仍然重新校验一次当前菜单喵~
	 * </p>
	 *
	 * @param payload 负载实例喵~
	 * @param context 连接上下文喵~
	 */
	public static void handle(SMDustbinSyncRequestPayload payload, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof DustbinMenu menu) {
			menu.broadcastFullState();
		}
	}
}
