package com.hexagram2021.sweeper_maid;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hexagram2021.sweeper_maid.command.SMCommands;
import com.hexagram2021.sweeper_maid.compat.SMCreateCompat;
import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import com.hexagram2021.sweeper_maid.network.SMDustbinStatePayload;
import com.hexagram2021.sweeper_maid.network.SMDustbinSyncRequestPayload;
import com.hexagram2021.sweeper_maid.save.SMSavedData;
import com.hexagram2021.sweeper_maid.util.SMExperienceOrbs;
import com.hexagram2021.sweeper_maid.util.SMText;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 扫帚女仆模组主类，负责定期清理服务器中的掉落物品和多余实体喵~
 * <p>
 * 主要功能：
 * <ul>
 *     <li>定期扫描并清理服务器中的掉落物品</li>
 *     <li>将清理的物品存储到垃圾箱中供玩家查询</li>
 *     <li>支持清理额外的实体类型（如箭矢）</li>
 *     <li>提供倒计时提示和清理结果反馈</li>
 *     <li>支持物品黑名单和白名单配置</li>
 *     <li>检测并警告物品过载的区块</li>
 * </ul>
 * </p>
 *
 * @author liudongyu
 */
@SuppressWarnings({"unused", "java:S1104", "java:S1444", "java:S3010"})
@Mod(SweeperMaid.MODID)
public class SweeperMaid {
	/**
	 * 模组 ID 喵~
	 */
	public static final String MODID = "sweeper_maid";
	/**
	 * 模组名称喵~
	 */
	public static final String MODNAME = "Sweeper Maid";
	/**
	 * 模组版本号喵~
	 */
	public static final String VERSION = ModList.get().getModFileById(MODID).versionString();

	/**
	 * 模组实例喵~
	 */
	@SuppressWarnings("java:S1104")
	public static SweeperMaid instance;
	/**
	 * 立即清理回调函数，用于命令执行时触发清理喵~
	 */
	public static Runnable clean = () -> {};

	/**
	 * 构造方法：注册配置和事件监听器喵~
	 *
	 * @param modContainer 模组容器实例喵~
	 */
	public SweeperMaid(ModContainer modContainer) {
		instance = this;
		modContainer.registerConfig(ModConfig.Type.COMMON, SMCommonConfig.getConfig());
		modContainer.getEventBus().addListener(RegisterPayloadHandlersEvent.class, this::registerPayloadHandlers);
		NeoForge.EVENT_BUS.register(this);

		clean = () -> this.sweepTickRemain = 0;
	}

	/**
	 * 注册自定义网络负载喵~
	 * <p>
	 * 这两条负载都只服务于「装了本模组的客户端」喵~：翻页本身已经是纯服务端行为
	 * （点击服务端合成的按钮物品即可），所以原先的翻页请求负载已经删除喵~。
	 * {@link RegisterPayloadHandlersEvent} 是模组总线事件（IModBusEvent），
	 * 必须注册到模组事件总线而不能挂在 {@link NeoForge#EVENT_BUS} 上喵~
	 * </p>
	 *
	 * @param event 负载注册事件喵~
	 */
	public void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
		// 必须用 optional()：不带该标记的负载属于「连接必需」，未装本模组的客户端会在握手阶段
		// 被直接踢下线（"multiplayer.disconnect.incompatible"），连服务器都进不去喵~
		// 服务端每次发送前还会再用 hasChannel 兜一次底（见各负载的 send 方法）喵~
		event.registrar(SweeperMaid.VERSION)
				.optional()
				.playToServer(SMDustbinSyncRequestPayload.TYPE, SMDustbinSyncRequestPayload.STREAM_CODEC, SMDustbinSyncRequestPayload::handle)
				.playToClient(SMDustbinStatePayload.TYPE, SMDustbinStatePayload.STREAM_CODEC, SMDustbinStatePayload::handle);
	}

	/**
	 * 距离下次清理的剩余刻数喵~
	 */
	private int sweepTickRemain = 0;
	/**
	 * 是否需要在下一刻执行清理喵~
	 */
	private boolean toSweep = false;
	/**
	 * 是否为首次刻 tick 喵~
	 */
	private boolean firstTick = true;
	/**
	 * 当前所有维度中「可清扫堆」的总数，随实体进出世界实时增减喵~
	 * <p>
	 * 统计对象由 {@link #isSweepablePile} 定义：掉落物堆 + 经验球。
	 * 字段名不再叫 {@code itemEntityCount} —— 旧名字只提「物品」，而经验球漏统计的缺陷
	 * 恰恰就是从「以为只有物品才算一堆」这个误解里长出来的喵~。
	 * </p>
	 */
	private int sweepablePileCount = 0;
	/**
	 * 上次因过载触发额外清扫的服务器 tick 数（负数表示从未触发）喵~
	 */
	private long lastOverloadSweepTick = Long.MIN_VALUE;
	/**
	 * 下一次清扫是否因掉落物过载而触发喵~
	 */
	private boolean lastSweepWasOverload = false;
	/**
	 * 上次自动处理垃圾桶的服务器 tick 数喵~
	 */
	private long lastDustbinTrashTick = 0;

	/**
	 * 注册模组命令事件处理喵~
	 * <p>
	 * 将扫帚女仆的命令注册到命令分发器中喵~
	 * </p>
	 *
	 * @param event 命令注册事件喵~
	 */
	@SubscribeEvent
	public void registerCommands(RegisterCommandsEvent event) {
		final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		dispatcher.register(SMCommands.register());
	}

	/**
	 * 服务器刻 tick 前处理：发送倒计时消息喵~
	 * <p>
	 * 在每次刻 tick 前检查是否需要清理，并向所有玩家发送 ActionBar 倒计时消息喵~
	 * 倒计时节点：60 秒、30 秒、15 秒以及 10 秒以内每秒喵~
	 * </p>
	 *
	 * @param event 服务器刻 tick 前事件喵~
	 */
	@SubscribeEvent
	public void onTickPre(ServerTickEvent.Pre event) {
		MinecraftServer server = event.getServer();
		this.checkDustbinTrash(server);
		if(SMCommonConfig.ITEM_SWEEP_INTERVAL.get() == 0) {
			return;
		}
		if (server.getTickCount() % RECOUNT_INTERVAL == 0) {
			this.recountSweepablePiles(server);
		}
		this.checkOverloadSweep(server);
		this.prepareAndSendCountdownMessage(server);
	}

	/**
	 * 检查并执行垃圾桶自动处理喵~
	 * <p>
	 * 间隔配置 {@code DUSTBIN_TRASH_INTERVAL} 为 -1 时禁用；到时按当前配置模式处理喵~
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 */
	private void checkDustbinTrash(MinecraftServer server) {
		if (!SMSavedData.hasInstance()) {
			return;
		}
		int interval = SMCommonConfig.DUSTBIN_TRASH_INTERVAL.get();
		if (interval < 0) {
			return;
		}
		long tick = server.getTickCount();
		long intervalTicks = (long) interval * SharedConstants.TICKS_PER_SECOND;
		if (tick - this.lastDustbinTrashTick >= intervalTicks) {
			this.lastDustbinTrashTick = tick;
			trashDustbins(server);
		}
	}

	/**
	 * 掉落物实体计数校正周期（tick，100 tick = 5 秒）喵~
	 */
	private static final int RECOUNT_INTERVAL = 100;

	/**
	 * 判断实体是否算作「一堆可清扫的东西」喵~
	 * <p>
	 * 掉落物堆（{@link ItemEntity}）与经验球（{@link ExperienceOrb}）都算：两者都会被
	 * {@link #doSweeping} 直接清掉，也都是「一堆东西赖在地上」的卡顿来源喵~。
	 * </p>
	 * <p>
	 * 抽成单一定义、而不是在每个调用点各写一遍 {@code instanceof}：本模组正是这样漏掉经验球的
	 * ——全量校正、进世界、离开世界三处都只判 {@code ItemEntity}，于是经验球对
	 * {@code GLOBAL_ITEM_COUNT_THRESHOLD} 完全不可见，一地几千颗球也不会触发过载清扫，
	 * 只能干等下一次定时清扫喵~。
	 * </p>
	 * <p>
	 * 可见性为 {@code public} 而非包私有：GameTest 位于 {@code sweeper_maid.gametest} 子包，
	 * 包私有对它不可见喵~。
	 * </p>
	 *
	 * @param entity 待判定的实体，可为 null 喵~
	 * @return 计作一堆时返回 true 喵~
	 */
	public static boolean isSweepablePile(@Nullable Entity entity) {
		return entity instanceof ItemEntity || entity instanceof ExperienceOrb;
	}

	/**
	 * 统计一批实体里「可清扫堆」的数量喵~
	 * <p>
	 * {@link #recountSweepablePiles} 走的就是这个函数，因此 GameTest 断言的是
	 * 「真正被用到的那个统计口径」，而不是另写一份等价逻辑自证喵~。
	 * </p>
	 *
	 * @param entities 候选实体喵~
	 * @return 其中可清扫堆的数量喵~
	 */
	public static int countSweepablePiles(Iterable<? extends Entity> entities) {
		int count = 0;
		for (Entity entity : entities) {
			if (isSweepablePile(entity)) {
				++count;
			}
		}
		return count;
	}

	/**
	 * 重新统计所有维度的「可清扫堆」总数喵~
	 * <p>
	 * 事件计数偶尔可能因实体追踪状态变化失真（如区块卸载），定时全量校正保证计数准确喵~
	 * </p>
	 * <p>
	 * 这里不自己再写一遍 {@code instanceof}，而是调用 {@link #countSweepablePiles}：
	 * 「什么算一堆」必须只有一处定义，否则「校正」与「增减」随时可能各判一套标准喵~。
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 */
	private void recountSweepablePiles(MinecraftServer server) {
		int count = 0;
		for (ServerLevel level : server.getAllLevels()) {
			count += countSweepablePiles(level.getEntities().getAll());
		}
		this.sweepablePileCount = count;
	}

	/**
	 * 可清扫堆（掉落物堆 / 经验球）进入世界时计数喵~
	 *
	 * @param event 实体进入世界事件喵~
	 */
	@SubscribeEvent
	public void onEntityJoin(EntityJoinLevelEvent event) {
		if (isSweepablePile(event.getEntity())) {
			++this.sweepablePileCount;
		}
	}

	/**
	 * 可清扫堆（掉落物堆 / 经验球）离开世界时计数喵~
	 *
	 * @param event 实体离开世界事件喵~
	 */
	@SubscribeEvent
	public void onEntityLeave(EntityLeaveLevelEvent event) {
		if (isSweepablePile(event.getEntity())) {
			--this.sweepablePileCount;
		}
	}

	/**
	 * 检查全局掉落物是否过载喵~
	 * <p>
	 * 当所有维度的「可清扫堆」（掉落物堆 + 经验球）总数超过配置阈值，且距上次过载清扫
	 * 已超过冷却时间时，立即标记一次清扫，并将定时倒计时重置喵~
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 */
	private void checkOverloadSweep(MinecraftServer server) {
		int threshold = SMCommonConfig.GLOBAL_ITEM_COUNT_THRESHOLD.get();
		int cooldownTicks = SMCommonConfig.OVERLOAD_SWEEP_COOLDOWN.get() * SharedConstants.TICKS_PER_SECOND;
		long tick = server.getTickCount();
		boolean cooldownPassed = this.lastOverloadSweepTick < 0 || tick - this.lastOverloadSweepTick >= cooldownTicks;
		if (this.sweepablePileCount > threshold && !this.toSweep && cooldownPassed) {
			this.toSweep = true;
			this.lastSweepWasOverload = true;
			this.lastOverloadSweepTick = tick;
			this.sweepTickRemain = SMCommonConfig.ITEM_SWEEP_INTERVAL.get() * SharedConstants.TICKS_PER_SECOND;
		}
	}

	/**
	 * 服务器刻 tick 后处理：执行清理逻辑喵~
	 * <p>
	 * 在首次 tick 时初始化存档数据，之后在需要清理时执行清扫操作喵~
	 * </p>
	 *
	 * @param event 服务器刻 tick 后事件喵~
	 */
	@SubscribeEvent
	public void onTickPost(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		if(SMCommonConfig.ITEM_SWEEP_INTERVAL.get() == 0) {
			return;
		}
		if(this.firstTick) {
			this.firstTick = false;
			this.toSweep = false;
			SMSavedData.initialize();
		} else if(this.toSweep) {
			this.toSweep = false;
			boolean overloaded = this.lastSweepWasOverload;
			this.lastSweepWasOverload = false;
			doSweeping(server, overloaded);
		}
	}

	/**
	 * 服务器启动后事件处理：初始化存档数据喵~
	 * <p>
	 * 在服务器启动后，从世界数据存储中加载或创建扫帚女仆的存档数据喵~
	 * </p>
	 *
	 * @param event 服务器启动事件喵~
	 */
	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		ServerLevel world = event.getServer().getLevel(Level.OVERWORLD);
		assert world != null;
		if (!world.isClientSide) {
			SMSavedData worldData = world.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SMSavedData::new, SMSavedData::new), SMSavedData.SAVED_DATA_NAME);
			SMSavedData.setInstance(worldData);
		}
	}

	/**
	 * 玩家登录事件处理：发送分支欢迎提示喵~
	 * <p>
	 * 玩家进入服务器后提醒这是 Yansunsky 分支版本，问题反馈请勿联系原作者；可通过配置或 OP 指令关闭喵~
	 * </p>
	 *
	 * @param event 玩家登录事件喵~
	 */
	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && SMCommonConfig.SHOW_FORK_WELCOME.get()) {
			player.sendSystemMessage(createForkWelcomeMessage(player));
		}
	}

	/**
	 * 构建分支欢迎提示消息喵~
	 * <p>
	 * 关键词（分支名、QQ、邮箱、关闭指令、配置项）使用金色高亮，其余文字灰色喵~
	 * </p>
	 * <p>
	 * 兜底文本按<b>该玩家客户端的语言</b>取：未装模组的客户端没有本模组的语言文件，
	 * 若不带兜底就会把 {@code sweeper_maid.message.fork_welcome} 这个键名直接显示给玩家喵~
	 * </p>
	 *
	 * @param player 目标玩家喵~
	 * @return 欢迎消息组件喵~
	 */
	private static MutableComponent createForkWelcomeMessage(ServerPlayer player) {
		ChatFormatting normal = ChatFormatting.GRAY;
		ChatFormatting highlight = ChatFormatting.GOLD;
		return SMText.rawForPlayer(
				player,
				"sweeper_maid.message.fork_welcome",
				Component.literal("Yansunsky").withStyle(highlight),
				Component.literal("1321833118").withStyle(highlight),
				Component.literal("Yansunsky@outlook.com").withStyle(highlight),
				Component.literal("/sweepermaid welcome off").withStyle(highlight),
				Component.literal("SHOW_FORK_WELCOME").withStyle(highlight))
				.withStyle(normal);
	}

	/**
	 * 创建命令源堆栈喵~
	 * <p>
	 * 用于在发送消息时模拟命令执行上下文喵~
	 * </p>
	 *
	 * @param player 玩家实例喵~
	 * @param level 世界实例喵~
	 * @param blockPos 方块位置喵~
	 * @return 命令源堆栈喵~
	 */
	private static CommandSourceStack createCommandSourceStack(Player player, Level level, BlockPos blockPos) {
		return new CommandSourceStack(CommandSource.NULL, Vec3.atCenterOf(blockPos), Vec2.ZERO, (ServerLevel) level, 2, player.getName().getString(), player.getDisplayName(), level.getServer(), player);
	}

	/**
	 * 向管理员广播消息喵~
	 * <p>
	 * 仅当服务器启用命令反馈时，向所有在线管理员发送系统消息喵~
	 * </p>
	 * <p>
	 * 参数是「按玩家生成组件」的函数而不是现成组件：每个管理员的客户端语言可能不同，
	 * 各自生成才能都拿到正确语言的兜底文本（未装模组的客户端尤其需要）喵~。
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 * @param messageFactory 按玩家生成消息组件的函数喵~
	 */
	private static void broadcastToAdmins(MinecraftServer server, Function<ServerPlayer, Component> messageFactory) {
		if (server.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
			for(ServerPlayer serverplayer : server.getPlayerList().getPlayers()) {
				if (server.getPlayerList().isOp(serverplayer.getGameProfile())) {
					serverplayer.sendSystemMessage(messageFactory.apply(serverplayer));
				}
			}
		}
	}

	/**
	 * 获取打包文件目录喵~
	 *
	 * @param server 服务器实例喵~
	 * @return 存档目录下的 {@code sweeper_maid_packs} 目录喵~
	 */
	public static Path getPackDirectory(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("sweeper_maid_packs");
	}

	/**
	 * 按当前配置模式立即处理垃圾桶喵~
	 * <p>
	 * 供定时自动处理与 {@code /sweepermaid trash} 指令使用；空桶时静默喵~
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 */
	public static void trashDustbins(MinecraftServer server) {
		if (!SMSavedData.hasInstance()) {
			return;
		}
		String mode = SMCommonConfig.DUSTBIN_TRASH_MODE.get();
		if ("pack".equalsIgnoreCase(mode)) {
			packDustbinsInternal(server, false);
		} else {
			int cleared = SMSavedData.getInstance().clearDustbins();
			if (cleared > 0) {
				broadcastToAdmins(server, player -> SMText.forPlayer(player, SMCommonConfig.MESSAGE_DUSTBIN_CLEARED, "sweeper_maid.message.dustbin_trash_cleared", cleared));
			}
		}
	}

	/**
	 * 立即打包垃圾桶喵~
	 * <p>
	 * 供 {@code /sweepermaid pack} 指令使用；空桶时提示管理员喵~
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 */
	public static void packDustbinsNow(MinecraftServer server) {
		if (!SMSavedData.hasInstance()) {
			return;
		}
		packDustbinsInternal(server, true);
	}

	/**
	 * 打包垃圾桶到压缩文件并清空喵~
	 *
	 * @param server      服务器实例喵~
	 * @param notifyEmpty 空桶时是否提示管理员喵~
	 */
	private static void packDustbinsInternal(MinecraftServer server, boolean notifyEmpty) {
		try {
			Path dir = getPackDirectory(server);
			Files.createDirectories(dir);
			String name = "pack-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".nbt.gz";
			Path file = dir.resolve(name);
			int packed = SMSavedData.getInstance().packDustbins(server.registryAccess(), file);
			if (packed > 0) {
				broadcastToAdmins(server, player -> SMText.forPlayer(player, SMCommonConfig.MESSAGE_DUSTBIN_PACKED, "sweeper_maid.message.dustbin_trash_packed", packed, file.toString()));
			} else if (notifyEmpty) {
				broadcastToAdmins(server, player -> SMText.forPlayer(player, "sweeper_maid.message.dustbin_pack_empty").withStyle(ChatFormatting.YELLOW));
			}
		} catch (IOException e) {
			broadcastToAdmins(server, player -> Component.literal("[Sweeper Maid] Failed to pack dustbins: " + e.getMessage()).withStyle(ChatFormatting.RED));
		}
	}

	/**
	 * 导入打包文件并合并进垃圾桶喵~
	 * <p>
	 * 成功后文件移入 {@code imported/} 子目录喵~
	 * </p>
	 *
	 * @param server   服务器实例喵~
	 * @param fileName 打包文件名喵~
	 * @param player   执行玩家喵~
	 * @return 是否导入成功喵~
	 */
	public static boolean unpackDustbinFile(MinecraftServer server, String fileName, ServerPlayer player) {
		try {
			Path dir = getPackDirectory(server);
			Path file = dir.resolve(fileName).normalize();
			if (!file.startsWith(dir.normalize()) || !Files.isRegularFile(file)) {
				player.sendSystemMessage(Component.translatable("sweeper_maid.message.unpack_file_not_found", fileName).withStyle(ChatFormatting.RED));
				return false;
			}
			int imported = SMSavedData.getInstance().importPack(server.registryAccess(), file);
			Path importedDir = dir.resolve("imported");
			Files.createDirectories(importedDir);
			Files.move(file, importedDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
			player.sendSystemMessage(SMText.forPlayer(player, SMCommonConfig.MESSAGE_DUSTBIN_UNPACKED, "sweeper_maid.message.dustbin_trash_unpacked", fileName, imported));
			return true;
		} catch (IOException e) {
			player.sendSystemMessage(Component.literal("[Sweeper Maid] Failed to import pack: " + e.getMessage()).withStyle(ChatFormatting.RED));
			return false;
		}
	}

	/**
	 * 准备并发送倒计时消息给所有玩家喵~
	 * <p>
	 * 更新清理倒计时，并在特定时间点（60/30/15 秒和 10 秒内）向玩家发送 ActionBar 消息喵~
	 * 当倒计时归零时标记需要执行清理喵~
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 */
	private void prepareAndSendCountdownMessage(MinecraftServer server) {
		this.sweepTickRemain -= 1;
		if (this.sweepTickRemain <= 0) {
			this.toSweep = true;
			this.sweepTickRemain = SMCommonConfig.ITEM_SWEEP_INTERVAL.get() * SharedConstants.TICKS_PER_SECOND;
		} else if(this.sweepTickRemain == 15 * SharedConstants.TICKS_PER_SECOND || this.sweepTickRemain == 30 * SharedConstants.TICKS_PER_SECOND || this.sweepTickRemain == 60 * SharedConstants.TICKS_PER_SECOND) {
			server.getPlayerList().getPlayers().forEach(player -> {
				try {
					player.connection.send(new ClientboundSetActionBarTextPacket(ComponentUtils.updateForEntity(
							createCommandSourceStack(player, player.level(), player.blockPosition()),
							SMText.forPlayer(player, SMCommonConfig.MESSAGE_BEFORE_SWEEP_15_30_60, "sweeper_maid.message.before_sweep_15_30_60", this.sweepTickRemain / SharedConstants.TICKS_PER_SECOND).withStyle(ChatFormatting.GRAY),
							player, 0
					)));
				} catch (CommandSyntaxException ignored) {
					// Ignored
				}
			});
		} else if(this.sweepTickRemain % SharedConstants.TICKS_PER_SECOND == 0 && this.sweepTickRemain / SharedConstants.TICKS_PER_SECOND <= 10) {
			server.getPlayerList().getPlayers().forEach(player -> {
				try {
					player.connection.send(new ClientboundSetActionBarTextPacket(ComponentUtils.updateForEntity(
							createCommandSourceStack(player, player.level(), player.blockPosition()),
							SMText.forPlayer(player, SMCommonConfig.MESSAGE_BEFORE_SWEEP_1_10, "sweeper_maid.message.before_sweep_1_10", this.sweepTickRemain / SharedConstants.TICKS_PER_SECOND).withStyle(ChatFormatting.GOLD),
							player, 0
					)));
				} catch (CommandSyntaxException ignored) {
					// Ignored
				}
			});
		}
	}

	/**
	 * 判断某玩家是否应收到清扫后的垃圾箱聊天提示喵~
	 * <p>
	 * 清扫后的聊天消息内容就是「有什么东西丢了吗？去垃圾箱里看看吧：」加上若干
	 * {@code [垃圾箱 N]} 可点击链接。而打开垃圾箱的命令本身受
	 * {@code PERMISSION_LEVEL_DUSTBIN} 限制，所以把这条消息发给权限不足的玩家，
	 * 等于发一个点不动的死链接——权限与提示必须同源喵~。
	 * </p>
	 * <p>
	 * 判定所需权限由调用方传入（而不是在这里读配置），是为了让 GameTest 能在
	 * <b>不改动全局配置</b>的前提下覆盖「有权限 / 无权限」两条分支喵~。
	 * </p>
	 * <p>
	 * 可见性为 {@code public} 而非包私有：GameTest 位于 {@code sweeper_maid.gametest} 子包，
	 * 包私有对它不可见喵~。
	 * </p>
	 *
	 * @param player          目标玩家喵~
	 * @param requiredLevel   打开垃圾箱所需的权限等级喵~
	 * @return 应发送提示时返回 true 喵~
	 */
	public static boolean shouldReceiveDustbinPrompt(ServerPlayer player, int requiredLevel) {
		// 需求等级为 0 时人人可开箱，直接放行，省掉一次权限查询喵~
		return requiredLevel <= 0 || player.hasPermissions(requiredLevel);
	}

	/**
	 * 从玩家列表中筛出应收到垃圾箱提示的玩家喵~
	 * <p>
	 * 单有 {@link #shouldReceiveDustbinPrompt} 只能证明「判定函数是对的」，
	 * 证明不了「它真的被接到了发送路径上」——而用户报的缺陷恰恰出在后者
	 * （命令端有校验、提示端漏了校验）。所以把筛选本身也抽成可测方法，
	 * 让测试能对着<b>整个玩家列表</b>断言过滤结果喵~。
	 * </p>
	 *
	 * @param players       候选玩家喵~
	 * @param requiredLevel 打开垃圾箱所需的权限等级喵~
	 * @return 应收到提示的玩家列表（保持原顺序）喵~
	 */
	public static List<ServerPlayer> dustbinPromptRecipients(List<ServerPlayer> players, int requiredLevel) {
		return players.stream()
				.filter(player -> shouldReceiveDustbinPrompt(player, requiredLevel))
				.toList();
	}

	/**
	 * 执行清理操作喵~
	 * <p>
	 * 扫描所有维度的实体，清理掉落物品和额外实体类型，并将物品存入垃圾箱喵~
	 * 清理完成后向玩家发送统计信息和垃圾箱访问提示喵~
	 * 检测并警告物品过载的区块喵~
	 * </p>
	 * <p>
	 * <b>为什么不暴露给 GameTest 调用</b>：这是<b>全服</b>清扫，会连同其它模组测试用的实体一起
	 * 吞掉——实测在装了机械动力的 GameTest 环境里会让它自己的存储测试因「内容缺失」而失败。
	 * 因此经验折算链路由 {@code SMCreateCompat} 的纯函数负责单测，
	 * 整条链路的实机验证交给真实服务端（详见 tools/README.md）喵~。
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 * @param overloadSweep 本次清扫是否由掉落物过载触发喵~
	 */
	private static void doSweeping(MinecraftServer server, boolean overloadSweep) {
		SMSavedData instance = SMSavedData.getInstance();

		AtomicInteger droppedItems = new AtomicInteger();
		AtomicInteger extraEntities = new AtomicInteger();
		AtomicInteger blacklistedItems = new AtomicInteger();
		AtomicInteger sweptItemPiles = new AtomicInteger();
		AtomicInteger collectedXp = new AtomicInteger();
		Map<LevelChunk, Map<String, Integer>> chunkItemCounts = Maps.newIdentityHashMap();

		server.getAllLevels().forEach(serverLevel -> {
			Iterable<Entity> entities = serverLevel.getAllEntities();
			List<Entity> killedEntities = Lists.newArrayList();

			for (Entity entity : entities) {
				checkAndCollectEntity(server, serverLevel, entity, killedEntities, blacklistedItems, instance, droppedItems, chunkItemCounts, extraEntities, sweptItemPiles, collectedXp);
			}

			killedEntities.forEach(Entity::discard);
		});

		// 将收集到的经验按配置模式折算后存入垃圾箱喵~
		// auto：装了机械动力就折算成经验颗粒（1 颗 = 3 点，向下取整），否则折算成附魔之瓶（向上取整）喵~
		// collectedXp 已按经验球的真实价值累加（合并球按 value × count 计，见 SMExperienceOrbs）喵~
		// 折算逻辑统一走 SMCreateCompat.conversionResultFor，以保证「被测试断言的那段逻辑」
		// 与「清扫真正执行的逻辑」是同一段，不会出现测试通过但线上走另一条分支的情况喵~
		int xp = collectedXp.get();
		SMCreateCompat.ConversionMode xpMode = SMCreateCompat.resolveMode();
		ItemStack xpProduce = SMCreateCompat.conversionResultFor(xp, xpMode, SMCommonConfig.XP_PER_BOTTLE.get());
		int xpItems = xpProduce.getCount();
		if (!xpProduce.isEmpty()) {
			instance.addItemToDustbin(xpProduce);
		}

		server.getPlayerList().getPlayers().forEach(player -> {
			try {
				player.connection.send(new ClientboundSetActionBarTextPacket(ComponentUtils.updateForEntity(
						createCommandSourceStack(player, player.level(), player.blockPosition()),
						SMText.forPlayer(player, SMCommonConfig.MESSAGE_AFTER_SWEEP, "sweeper_maid.message.after_sweep", droppedItems, extraEntities, blacklistedItems)  // 增加黑名单物品统计
								.withStyle(ChatFormatting.AQUA),
						player, 0
				)));
			} catch (CommandSyntaxException ignored) {
				// Ignored
			}
		});


		// Generate all dustbin messages and links
		// 只发给有权打开垃圾箱的玩家：这条消息本身就是「去垃圾箱看看」+ [垃圾箱 N] 链接，
		// 发给没权限的玩家只会得到一个点不动的死链接（命令端有 hasPermission 校验，
		// 而提示端原先没有，属于权限与提示脱节）喵~。
		// 权限等级取 PERMISSION_LEVEL_DUSTBIN，与 /sweepermaid dustbin 命令用的是同一项配置，
		// 因此调到 4 就等于「不再向任何人提示」喵~。
		int chatPermissionLevel = SMCommonConfig.PERMISSION_LEVEL_DUSTBIN.get();
		int xpConverted = xp;
		int xpItemCount = xpItems;
		SMCreateCompat.ConversionMode xpModeForMessage = xpMode;
		// 走 dustbinPromptRecipients（而不是就地写 filter），让「被测试断言的筛选逻辑」
		// 与「清扫真正执行的筛选逻辑」是同一段代码喵~
		dustbinPromptRecipients(server.getPlayerList().getPlayers(), chatPermissionLevel)
				.forEach(player -> {
					MutableComponent message = SMText.forPlayer(player, SMCommonConfig.CHAT_MESSAGE_AFTER_SWEEP, "sweeper_maid.message.chat_after_sweep");

					if (xpConverted > 0) {
						message.append(Component.literal(" "))
								.append(switch (xpModeForMessage) {
									case DISCARD -> SMText.forPlayer(player, SMCommonConfig.MESSAGE_XP_DISCARDED, "sweeper_maid.message.xp_discarded", xpConverted)
											.withStyle(ChatFormatting.YELLOW);
									case NUGGET -> SMText.forPlayer(player, SMCommonConfig.MESSAGE_XP_CONVERTED_NUGGET, "sweeper_maid.message.xp_converted_nugget", xpConverted, xpItemCount)
											.withStyle(ChatFormatting.YELLOW);
									default -> SMText.forPlayer(player, SMCommonConfig.MESSAGE_XP_CONVERTED, "sweeper_maid.message.xp_converted", xpConverted, xpItemCount)
											.withStyle(ChatFormatting.YELLOW);
								});
					}

					instance.accessDustbins(dustbins -> {
						if(dustbins.isEmpty()) {
							return;
						}
						// 只发一条链接，默认打开第 1 页喵~
						// 以前这里按页逐个 append [垃圾箱 N]：垃圾桶一多，聊天栏就会被刷屏
						// （用户反馈的正是这条）喵~。现在靠界面里的翻页按钮就够了，
						// 玩家点一个链接进去，再用底部的上一页 / 下一页翻喵~。
						message.append(Component.literal("[")
								.append(SMText.forPlayer(player, SMCommonConfig.DUSTBIN_NAME, "sweeper_maid.dustbin.name", 0))
								.append("]")
								.withStyle(style -> style.withColor(ChatFormatting.GREEN)
										.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sweepermaid dustbin 0"))));
					});

					player.sendSystemMessage(message);
				});

		// Send overload message to players
		int itemOverloadThreshold = SMCommonConfig.ITEM_OVERLOAD_THRESHOLD.get();
		chunkItemCounts.forEach((chunk, itemCounts) -> itemCounts.forEach((itemKey, count) -> {
			if (count > itemOverloadThreshold) {
				BlockPos chunkPos = chunk.getPos().getWorldPosition();
				broadcastToAdmins(server, player -> SMText.forPlayer(player, SMCommonConfig.OVERLOAD_MESSAGE, "sweeper_maid.message.overload",
						chunkPos.getX(), chunkPos.getZ(), count, itemKey).withStyle(ChatFormatting.BLUE));
			}
		}));

		// 每次清扫结束后按物品数量从大到小全局重排垃圾箱
		instance.sortDustbins();

		instance.setDirty();

		// 过载触发的额外清扫：向所有在线玩家发送提示消息
		if (overloadSweep) {
			server.getPlayerList().getPlayers().forEach(player ->
					player.sendSystemMessage(SMText.forPlayer(player, SMCommonConfig.MESSAGE_OVERLOAD_SWEEP, "sweeper_maid.message.overload_sweep", sweptItemPiles)));
		}
	}

	private static void checkAndCollectEntity(MinecraftServer server, ServerLevel serverLevel, @Nullable Entity entity, List<Entity> killedEntities, AtomicInteger blacklistedItems, SMSavedData instance, AtomicInteger droppedItems, Map<LevelChunk, Map<String, Integer>> chunkItemCounts, AtomicInteger extraEntities, AtomicInteger sweptItemPiles, AtomicInteger collectedXp) {
		if (entity instanceof ItemEntity itemEntity) {
			ItemStack itemStack = itemEntity.getItem();
			ResourceLocation itemKey = server.registryAccess().registryOrThrow(Registries.ITEM).getKey(itemStack.getItem());

			if (itemKey != null) {
				String item = itemKey.toString();

				// Make sure each entity will be processed only once.
				if (!killedEntities.contains(entity)) {
					if (SMCommonConfig.ITEM_BLACKLIST.get().contains(item)) {
						blacklistedItems.addAndGet(itemStack.getCount());
						sweptItemPiles.incrementAndGet();
						killedEntities.add(itemEntity);
					} else if(!SMCommonConfig.ITEM_WHITELIST.get().contains(item)) {
						instance.addItemToDustbin(itemStack);
						droppedItems.addAndGet(itemStack.getCount());
						sweptItemPiles.incrementAndGet();
						killedEntities.add(itemEntity);

						LevelChunk chunk = serverLevel.getChunkAt(entity.blockPosition());
						chunkItemCounts.computeIfAbsent(chunk, k -> Maps.newHashMap());
						Map<String, Integer> itemCounts = chunkItemCounts.get(chunk);
						itemCounts.put(item, itemCounts.getOrDefault(item, 0) + itemStack.getCount());
					}
				}
			}
		} else if (entity instanceof ExperienceOrb experienceOrb) {
			// 经验球全部收集（不受物品白/黑名单影响），计入过载堆数
			if (!killedEntities.contains(entity)) {
				// 用 totalValue 而不是 getValue：一颗经验球实体可能是多颗同面值球合并来的
				// （真实经验 = value × count，见 SMExperienceOrbs），只读 value 会把合并进去的
				// 那部分经验静默抹掉——小球（面值 1、3）最易合并，恰好也是堆积场景里的主力喵~
				collectedXp.addAndGet(SMExperienceOrbs.totalValue(experienceOrb));
				sweptItemPiles.incrementAndGet();
				killedEntities.add(experienceOrb);
			}
		} else if(entity != null) {
			ResourceLocation typeKey = server.registryAccess().registryOrThrow(Registries.ENTITY_TYPE).getKey(entity.getType());
			if (typeKey != null) {
				String type = typeKey.toString();
				if (SMCommonConfig.EXTRA_ENTITY_TYPES.get().contains(type)) {
					extraEntities.incrementAndGet();
					killedEntities.add(entity);
				}
			}
		}
	}
}
