package com.hexagram2021.sweeper_maid;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hexagram2021.sweeper_maid.command.SMCommands;
import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import com.hexagram2021.sweeper_maid.inventory.SMRegistries;
import com.hexagram2021.sweeper_maid.network.SMPageChangePayload;
import com.hexagram2021.sweeper_maid.save.SMSavedData;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
	 * 构造方法：注册配置、网络通道和事件监听器喵~
	 *
	 */
	public SweeperMaid() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		instance = this;
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SMCommonConfig.getConfig());
		SMRegistries.MENUS.register(modBus);
		CHANNEL.registerMessage(0, SMPageChangePayload.class, SMPageChangePayload::encode, SMPageChangePayload::decode, SMPageChangePayload::handle);
		MinecraftForge.EVENT_BUS.register(this);

		clean = () -> this.sweepTickRemain = 0;
	}

	/**
	 * 网络通道喵~
	 * <p>
	 * 翻页按钮的请求通过该通道发送到服务端喵~
	 * </p>
	 */
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(SweeperMaid.MODID, "main"),
			() -> SweeperMaid.VERSION,
			SweeperMaid.VERSION::equals,
			SweeperMaid.VERSION::equals
	);

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
	 * 当前所有维度中掉落物实体（堆）的总数，随实体进出世界实时增减喵~
	 */
	private int itemEntityCount = 0;
	/**
	 * 上次因过载触发额外清扫的服务器 tick 数（负数表示从未触发）喵~
	 */
	private long lastOverloadSweepTick = Long.MIN_VALUE;
	/**
	 * 下一次清扫是否因掉落物过载而触发喵~
	 */
	private boolean lastSweepWasOverload = false;

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
	/**
	 * 服务器刻 tick 处理喵~
	 * <p>
	 * START 阶段：过载检测与倒计时消息；END 阶段：首次初始化存档并执行清扫喵~
	 * </p>
	 *
	 * @param event 服务器刻 tick 事件喵~
	 */
	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		MinecraftServer server = event.getServer();
		if(SMCommonConfig.ITEM_SWEEP_INTERVAL.get() == 0) {
			return;
		}
		if (event.phase == TickEvent.Phase.START) {
			if (server.getTickCount() % RECOUNT_INTERVAL == 0) {
				this.recountItemEntities(server);
			}
			this.checkOverloadSweep(server);
			this.prepareAndSendCountdownMessage(server);
		} else if (event.phase == TickEvent.Phase.END) {
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
	}

	/**
	 * 掉落物实体计数校正周期（tick，100 tick = 5 秒）喵~
	 */
	private static final int RECOUNT_INTERVAL = 100;

	/**
	 * 重新统计所有维度的掉落物实体总数喵~
	 * <p>
	 * 事件计数偶尔可能因实体追踪状态变化失真（如区块卸载），定时全量校正保证计数准确喵~
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 */
	private void recountItemEntities(MinecraftServer server) {
		int count = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getEntities().getAll()) {
				if (entity instanceof ItemEntity) {
					++count;
				}
			}
		}
		this.itemEntityCount = count;
	}

	/**
	 * 掉落物实体进入世界时计数喵~
	 *
	 * @param event 实体进入世界事件喵~
	 */
	@SubscribeEvent
	public void onEntityJoin(EntityJoinLevelEvent event) {
		if (event.getEntity() instanceof ItemEntity) {
			++this.itemEntityCount;
		}
	}

	/**
	 * 掉落物实体离开世界时计数喵~
	 *
	 * @param event 实体离开世界事件喵~
	 */
	@SubscribeEvent
	public void onEntityLeave(EntityLeaveLevelEvent event) {
		if (event.getEntity() instanceof ItemEntity) {
			--this.itemEntityCount;
		}
	}

	/**
	 * 检查全局掉落物是否过载喵~
	 * <p>
	 * 当所有维度的掉落物实体（堆）总数超过配置阈值，且距上次过载清扫已超过冷却时间时，
	 * 立即标记一次清扫，并将定时倒计时重置喵~
	 * </p>
	 *
	 * @param server 服务器实例喵~
	 */
	private void checkOverloadSweep(MinecraftServer server) {
		int threshold = SMCommonConfig.GLOBAL_ITEM_COUNT_THRESHOLD.get();
		int cooldownTicks = SMCommonConfig.OVERLOAD_SWEEP_COOLDOWN.get() * SharedConstants.TICKS_PER_SECOND;
		long tick = server.getTickCount();
		boolean cooldownPassed = this.lastOverloadSweepTick < 0 || tick - this.lastOverloadSweepTick >= cooldownTicks;
		if (this.itemEntityCount > threshold && !this.toSweep && cooldownPassed) {
			this.toSweep = true;
			this.lastSweepWasOverload = true;
			this.lastOverloadSweepTick = tick;
			this.sweepTickRemain = SMCommonConfig.ITEM_SWEEP_INTERVAL.get() * SharedConstants.TICKS_PER_SECOND;
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
			SMSavedData worldData = world.getDataStorage().computeIfAbsent(SMSavedData::new, SMSavedData::new, SMSavedData.SAVED_DATA_NAME);
			SMSavedData.setInstance(worldData);
		}
	}

	/**
	 * 玩家登录事件处理：发送分支欢迎提示喵~
	 * <p>
	 * 玩家进入服务器后提醒这是 Yansunsky 分支版本，问题反馈请勿联系原作者喵~
	 * </p>
	 *
	 * @param event 玩家登录事件喵~
	 */
	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			player.sendSystemMessage(createForkWelcomeMessage());
		}
	}

	/**
	 * 构建分支欢迎提示消息喵~
	 * <p>
	 * 关键词（分支名、QQ、邮箱）使用金色高亮，其余文字灰色喵~
	 * </p>
	 *
	 * @return 欢迎消息组件喵~
	 */
	private static MutableComponent createForkWelcomeMessage() {
		ChatFormatting normal = ChatFormatting.GRAY;
		ChatFormatting highlight = ChatFormatting.GOLD;
		return Component.literal("欢迎使用").withStyle(normal)
				.append(Component.literal("Yansunsky分支").withStyle(highlight))
				.append(Component.literal("的清扫女仆，本版本所有问题请不要反馈到原作者那边，你可以反馈到QQ").withStyle(normal))
				.append(Component.literal("1321833118").withStyle(highlight))
				.append(Component.literal("，最好的发邮件到").withStyle(normal))
				.append(Component.literal("Yansunsky@outlook.com").withStyle(highlight));
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
	 *
	 * @param server 服务器实例喵~
	 * @param message 要发送的消息组件喵~
	 */
	private static void broadcastToAdmins(MinecraftServer server, Component message) {
		if (server.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
			for(ServerPlayer serverplayer : server.getPlayerList().getPlayers()) {
				if (server.getPlayerList().isOp(serverplayer.getGameProfile())) {
					serverplayer.sendSystemMessage(message);
				}
			}
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
							SMText.of(SMCommonConfig.MESSAGE_BEFORE_SWEEP_15_30_60, "sweeper_maid.message.before_sweep_15_30_60", this.sweepTickRemain / SharedConstants.TICKS_PER_SECOND).withStyle(ChatFormatting.GRAY),
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
							SMText.of(SMCommonConfig.MESSAGE_BEFORE_SWEEP_1_10, "sweeper_maid.message.before_sweep_1_10", this.sweepTickRemain / SharedConstants.TICKS_PER_SECOND).withStyle(ChatFormatting.GOLD),
							player, 0
					)));
				} catch (CommandSyntaxException ignored) {
					// Ignored
				}
			});
		}
	}

	/**
	 * 执行清理操作喵~
	 * <p>
	 * 扫描所有维度的实体，清理掉落物品和额外实体类型，并将物品存入垃圾箱喵~
	 * 清理完成后向玩家发送统计信息和垃圾箱访问提示喵~
	 * 检测并警告物品过载的区块喵~
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

		// 将收集到的经验折算为附魔之瓶存入垃圾箱（向上取整）
		int xp = collectedXp.get();
		int bottles = 0;
		if (xp > 0) {
			int xpPerBottle = SMCommonConfig.XP_PER_BOTTLE.get();
			bottles = (xp + xpPerBottle - 1) / xpPerBottle;
			ItemStack bottleStack = new ItemStack(Items.EXPERIENCE_BOTTLE, bottles);
			instance.addItemToDustbin(bottleStack);
		}

		server.getPlayerList().getPlayers().forEach(player -> {
			try {
				player.connection.send(new ClientboundSetActionBarTextPacket(ComponentUtils.updateForEntity(
						createCommandSourceStack(player, player.level(), player.blockPosition()),
						SMText.of(SMCommonConfig.MESSAGE_AFTER_SWEEP, "sweeper_maid.message.after_sweep", droppedItems, extraEntities, blacklistedItems)  // 增加黑名单物品统计
								.withStyle(ChatFormatting.AQUA),
						player, 0
				)));
			} catch (CommandSyntaxException ignored) {
				// Ignored
			}
		});


		// Generate all dustbin messages and links
		int xpConverted = xp;
		int bottleCount = bottles;
		server.getPlayerList().getPlayers().forEach(player -> {
			MutableComponent message = SMText.of(SMCommonConfig.CHAT_MESSAGE_AFTER_SWEEP, "sweeper_maid.message.chat_after_sweep");

			if (xpConverted > 0) {
				message.append(Component.literal(" "))
						.append(SMText.of(SMCommonConfig.MESSAGE_XP_CONVERTED, "sweeper_maid.message.xp_converted", xpConverted, bottleCount)
								.withStyle(ChatFormatting.YELLOW));
			}

			instance.accessDustbins(dustbins -> {
				if(dustbins.isEmpty()) {
					return;
				}
				boolean first = true;
				for (int i = 0; i < dustbins.size(); ++i) {
					if(SMSavedData.getDustbinContainer(i).isEmpty()) {
						continue;
					}
					if(first) {
						first = false;
					} else {
						message.append(Component.literal(", "));
					}
					final int dustbinIndex = i;
					message.append(Component.literal("[")
							.append(SMText.of(SMCommonConfig.DUSTBIN_NAME, "sweeper_maid.dustbin.name", dustbinIndex))
							.append("]")
							.withStyle(style -> style.withColor(ChatFormatting.GREEN)
									.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sweepermaid dustbin " + dustbinIndex))));
				}
			});

			player.sendSystemMessage(message);
		});

		// Send overload message to players
		int itemOverloadThreshold = SMCommonConfig.ITEM_OVERLOAD_THRESHOLD.get();
		chunkItemCounts.forEach((chunk, itemCounts) -> itemCounts.forEach((itemKey, count) -> {
			if (count > itemOverloadThreshold) {
				BlockPos chunkPos = chunk.getPos().getWorldPosition();
				MutableComponent overloadMessage = SMText.of(SMCommonConfig.OVERLOAD_MESSAGE, "sweeper_maid.message.overload",
								chunkPos.getX(), chunkPos.getZ(), count, itemKey)
						.withStyle(ChatFormatting.BLUE);

				broadcastToAdmins(server, overloadMessage);
			}
		}));

		// 每次清扫结束后按物品数量从大到小全局重排垃圾箱
		instance.sortDustbins();

		instance.setDirty();

		// 过载触发的额外清扫：向所有在线玩家发送提示消息
		if (overloadSweep) {
			server.getPlayerList().getPlayers().forEach(player ->
					player.sendSystemMessage(SMText.of(SMCommonConfig.MESSAGE_OVERLOAD_SWEEP, "sweeper_maid.message.overload_sweep", sweptItemPiles)));
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
				collectedXp.addAndGet(experienceOrb.getValue());
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
