package com.hexagram2021.sweeper_maid.command;

import com.hexagram2021.sweeper_maid.SweeperMaid;
import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import com.hexagram2021.sweeper_maid.inventory.DustbinMenu;
import com.hexagram2021.sweeper_maid.save.SMSavedData;
import com.hexagram2021.sweeper_maid.util.SMText;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 扫帚女仆模组命令注册类喵~
 * <p>
 * 提供以下命令：
 * <ul>
 *     <li>{@code /sweepermaid dustbin [index]} - 打开指定页的垃圾箱界面喵~</li>
 *     <li>{@code /sweepermaid clean} - 立即执行清理操作喵~</li>
 *     <li>{@code /sweepermaid welcome on|off} - 开关登录时的分支声明喵~</li>
 *     <li>{@code /sweepermaid trash} - 立即按配置模式处理垃圾桶喵~</li>
 *     <li>{@code /sweepermaid pack} - 立即将垃圾桶打包为压缩文件喵~</li>
 *     <li>{@code /sweepermaid unpack <file>} - 导入打包文件并合并进垃圾桶喵~</li>
 *     <li>{@code /sweepermaid help} - 显示帮助喵~</li>
 * </ul>
 * </p>
 *
 * @author liudongyu
 */
public final class SMCommands {
	/**
	 * 管理类指令的权限等级（2 级 = OP）喵~
	 */
	private static final int OP_PERMISSION = 2;

	/**
	 * 注册扫帚女仆命令喵~
	 *
	 * @return 命令构建器喵~
	 */
	public static LiteralArgumentBuilder<CommandSourceStack> register() {
		return Commands.literal("sweepermaid")
				.then(
						Commands.literal("help")
								.executes(context -> help(context.getSource()))
				)
				.then(
						Commands.literal("dustbin").requires(stack -> stack.hasPermission(SMCommonConfig.PERMISSION_LEVEL_DUSTBIN.get()))
								.executes(context -> dustbin(context.getSource().getPlayer(), 0)) // default the first dustbin
								.then(
										Commands.argument("index", IntegerArgumentType.integer(0))
												.executes(context -> dustbin(context.getSource().getPlayer(), IntegerArgumentType.getInteger(context, "index")))
								)
				)
				.then(
						Commands.literal("clean").requires(stack -> stack.hasPermission(SMCommonConfig.PERMISSION_LEVEL_CLEAN.get()))
								.executes(context -> clean())
				)
				.then(
						Commands.literal("welcome").requires(stack -> stack.hasPermission(OP_PERMISSION))
								.then(Commands.literal("on").executes(context -> setForkWelcome(context.getSource(), true)))
								.then(Commands.literal("off").executes(context -> setForkWelcome(context.getSource(), false)))
				)
				.then(
						Commands.literal("trash").requires(stack -> stack.hasPermission(OP_PERMISSION))
								.executes(context -> trash(context.getSource()))
				)
				.then(
						Commands.literal("pack").requires(stack -> stack.hasPermission(OP_PERMISSION))
								.executes(context -> pack(context.getSource()))
				)
				.then(
						Commands.literal("unpack").requires(stack -> stack.hasPermission(OP_PERMISSION))
								.then(
										Commands.argument("file", StringArgumentType.greedyString())
												.suggests(PACK_FILE_SUGGESTIONS)
												.executes(context -> unpack(context.getSource(), StringArgumentType.getString(context, "file")))
								)
				);
	}

	/**
	 * 打包文件名的 Tab 补全建议喵~
	 */
	private static final SuggestionProvider<CommandSourceStack> PACK_FILE_SUGGESTIONS = (context, builder) -> suggestPackFiles(context, builder);

	/**
	 * 列出打包目录下的压缩文件供 Tab 补全喵~
	 *
	 * @param context 命令上下文喵~
	 * @param builder 建议构建器喵~
	 * @return 建议结果喵~
	 */
	private static CompletableFuture<Suggestions> suggestPackFiles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		Path dir = SweeperMaid.getPackDirectory(context.getSource().getServer());
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(path -> path.getFileName().toString().endsWith(".nbt.gz"))
						.forEach(path -> builder.suggest(path.getFileName().toString()));
			} catch (IOException ignored) {
				// Ignored
			}
		}
		return builder.buildFuture();
	}

	/**
	 * 显示帮助喵~
	 *
	 * @param source 命令源喵~
	 * @return 命令执行结果码喵~
	 */
	private static int help(CommandSourceStack source) {
		MutableComponent message = Component.translatable("sweeper_maid.help.title").withStyle(ChatFormatting.GOLD);
		for (String key : new String[]{"dustbin", "clean", "welcome", "trash", "pack", "unpack", "help"}) {
			message.append(Component.literal("\n").append(Component.translatable("sweeper_maid.help." + key).withStyle(ChatFormatting.GRAY)));
		}
		source.sendSuccess(() -> message, false);
		return 1;
	}

	/**
	 * 打开垃圾箱界面喵~
	 * <p>
	 * 根据页索引打开对应的垃圾箱容器界面，如果索引无效则发送错误消息喵~
	 * </p>
	 *
	 * @param player 玩家实例喵~
	 * @param index  页索引喵~
	 * @return 命令执行结果码喵~
	 */
	private static int dustbin(@Nullable ServerPlayer player, int index) {
		if (player == null) {
			return 0;
		}

		if (index >= 0 && index < SMSavedData.getPageCount()) {
			DustbinMenu.open(player, index);
			return 1;
		}
		player.sendSystemMessage(SMText.of(SMCommonConfig.MESSAGE_WRONG_DUSTBIN, "sweeper_maid.message.wrong_dustbin"));
		return 0;
	}

	/**
	 * 立即执行清理操作喵~
	 * <p>
	 * 触发扫帚女仆的清理回调函数，重置清理倒计时喵~
	 * </p>
	 *
	 * @return 命令执行结果码喵~
	 */
	private static int clean() {
		SweeperMaid.clean.run();
		return 1;
	}

	/**
	 * 开关玩家登录时的分支声明喵~
	 *
	 * @param source  命令源喵~
	 * @param enabled 是否发送声明喵~
	 * @return 命令执行结果码喵~
	 */
	private static int setForkWelcome(CommandSourceStack source, boolean enabled) {
		SMCommonConfig.SHOW_FORK_WELCOME.set(enabled);
		SMCommonConfig.SHOW_FORK_WELCOME.save();
		source.sendSuccess(() -> Component.translatable(
				enabled ? "sweeper_maid.message.fork_welcome_enabled" : "sweeper_maid.message.fork_welcome_disabled"
		).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	/**
	 * 立即按当前配置模式处理垃圾桶喵~
	 *
	 * @param source 命令源喵~
	 * @return 命令执行结果码喵~
	 */
	private static int trash(CommandSourceStack source) {
		SweeperMaid.trashDustbins(source.getServer());
		return 1;
	}

	/**
	 * 立即打包垃圾桶喵~
	 *
	 * @param source 命令源喵~
	 * @return 命令执行结果码喵~
	 */
	private static int pack(CommandSourceStack source) {
		SweeperMaid.packDustbinsNow(source.getServer());
		return 1;
	}

	/**
	 * 导入打包文件并合并进垃圾桶喵~
	 *
	 * @param source   命令源喵~
	 * @param fileName 打包文件名喵~
	 * @return 命令执行结果码喵~
	 */
	private static int unpack(CommandSourceStack source, String fileName) {
		if (source.getPlayer() instanceof ServerPlayer player) {
			return SweeperMaid.unpackDustbinFile(source.getServer(), fileName, player) ? 1 : 0;
		}
		source.sendFailure(Component.translatable("sweeper_maid.message.unpack_console_only"));
		return 0;
	}

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMCommands() {
	}
}
