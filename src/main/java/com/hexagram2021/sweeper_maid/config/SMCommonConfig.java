package com.hexagram2021.sweeper_maid.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 扫帚女仆模组通用配置类喵~
 * <p>
 * 定义所有可配置选项，包括：
 * <ul>
 *     <li>清理间隔和时间设置喵~</li>
 *     <li>物品白名单和黑名单喵~</li>
 *     <li>额外实体类型喵~</li>
 *     <li>消息模板和提示语喵~</li>
 *     <li>垃圾箱每格容量和权限等级喵~</li>
 *     <li>物品过载阈值和警告消息喵~</li>
 * </ul>
 * </p>
 *
 * @author liudongyu
 */
@SuppressWarnings("java:S4968")
public final class SMCommonConfig {
	private static final String REGISTRY_NAME_MATCHER = "([a-z0-9_.-]+:[a-z0-9_/.-]+)";

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMCommonConfig() {
	}

	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
	private static final ModConfigSpec SPEC;

	/**
	 * 物品清理间隔（秒），0 表示禁用自动清理喵~
	 */
	public static final ModConfigSpec.IntValue ITEM_SWEEP_INTERVAL;
	/**
	 * 物品过载阈值，超过此数量的区块将发送警告喵~
	 */
	public static final ModConfigSpec.IntValue ITEM_OVERLOAD_THRESHOLD;
	/**
	 * 物品过载警告消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> OVERLOAD_MESSAGE;
	/**
	 * 需要清理的额外实体类型列表喵~
	 */
	public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_ENTITY_TYPES;
	/**
	 * 物品白名单，列表中的物品不会被清理喵~
	 */
	public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_WHITELIST;
	/**
	 * 物品黑名单，列表中的物品会被清理但不存入垃圾箱喵~
	 */
	public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;
	/**
	 * 清理前 15/30/60 秒的提示消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> MESSAGE_BEFORE_SWEEP_15_30_60;
	/**
	 * 清理前 1-10 秒的提示消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> MESSAGE_BEFORE_SWEEP_1_10;
	/**
	 * 清理完成后的 ActionBar 消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> MESSAGE_AFTER_SWEEP;
	/**
	 * 打开错误垃圾箱时的提示消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> MESSAGE_WRONG_DUSTBIN;
	/**
	 * 垃圾箱名称前缀喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> DUSTBIN_NAME;
	/**
	 * 单个垃圾箱槽位的最大堆叠数量喵~
	 */
	public static final ModConfigSpec.IntValue DUSTBIN_SLOT_CAPACITY;
	/**
	 * 清理完成后的聊天消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> CHAT_MESSAGE_AFTER_SWEEP;
	/**
	 * 打开垃圾箱所需的权限等级喵~
	 */
	public static final ModConfigSpec.IntValue PERMISSION_LEVEL_DUSTBIN;
	/**
	 * 立即清理所需的权限等级喵~
	 */
	public static final ModConfigSpec.IntValue PERMISSION_LEVEL_CLEAN;
	/**
	 * 全局掉落物实体（堆）数量阈值，超过则提前额外清扫一次喵~
	 */
	public static final ModConfigSpec.IntValue GLOBAL_ITEM_COUNT_THRESHOLD;
	/**
	 * 额外清扫的冷却时间（秒）喵~
	 */
	public static final ModConfigSpec.IntValue OVERLOAD_SWEEP_COOLDOWN;
	/**
	 * 额外清扫完成后的提示消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> MESSAGE_OVERLOAD_SWEEP;
	/**
	 * 多少点经验折算为 1 个附魔之瓶喵~
	 */
	public static final ModConfigSpec.IntValue XP_PER_BOTTLE;
	/**
	 * 经验转换提示消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> MESSAGE_XP_CONVERTED;
	/**
	 * 玩家登录时是否发送 Yansunsky 分支声明喵~
	 */
	public static final ModConfigSpec.BooleanValue SHOW_FORK_WELCOME;
	/**
	 * 垃圾桶自动处理模式（clear=清空 / pack=打包）喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> DUSTBIN_TRASH_MODE;
	/**
	 * 垃圾桶自动处理间隔（秒），-1 表示禁用自动处理喵~
	 */
	public static final ModConfigSpec.IntValue DUSTBIN_TRASH_INTERVAL;
	/**
	 * 垃圾桶清空完成后的提示消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> MESSAGE_DUSTBIN_CLEARED;
	/**
	 * 垃圾桶打包完成后的提示消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> MESSAGE_DUSTBIN_PACKED;
	/**
	 * 垃圾桶打包文件导入完成后的提示消息模板喵~
	 */
	public static final ModConfigSpec.ConfigValue<String> MESSAGE_DUSTBIN_UNPACKED;

	static {
		BUILDER.push("sweeper_maid-common-config");
		ITEM_SWEEP_INTERVAL = BUILDER.comment("If 0, disable item sweeping. If > 0, cool down (in seconds) between two item sweeping.\n若为 0 则禁用自动清扫；若大于 0，则为两次清扫之间的冷却时间（秒）。")
				.defineInRange("ITEM_SWEEP_INTERVAL", 600, 0, 360000);

		ITEM_OVERLOAD_THRESHOLD = BUILDER.comment("Item overload in a chunk. If exceeded, a warning message will be sent.\n区块内的掉落物数量超过此值时，将发送一条警告消息。")
				.defineInRange("ITEM_OVERLOAD_THRESHOLD", 640, 1, 6400000);

		OVERLOAD_MESSAGE = BUILDER.comment("Message to be sent when item overload threshold is exceeded. \"$1\" stands for chunk X position, \"$2\" stands for chunk Z position, \"$3\" stands for item count, and \"$4\" stands for item id. Leave empty to use the built-in localized message (follows the client language).\n区块掉落物过载时发送的消息。\"$1\" 为区块 X 坐标，\"$2\" 为区块 Z 坐标，\"$3\" 为物品数量，\"$4\" 为物品 ID。留空则使用内置本地化消息（跟随游戏语言）。")
				.define("OVERLOAD_MESSAGE", "");

		EXTRA_ENTITY_TYPES = BUILDER.comment("Other entities of types will be killed when cleaning, e.g. arrows. You can also kill mobs or even players by setting this.\n清扫时额外清除的实体类型，例如箭矢。也可以借此清除生物甚至玩家。")
				.defineListAllowEmpty("EXTRA_ENTITY_TYPES", List.of(
						ResourceLocation.withDefaultNamespace("arrow").toString(),
						ResourceLocation.withDefaultNamespace("spectral_arrow").toString(),
						ResourceLocation.fromNamespaceAndPath("oceanworld", "drip_ice").toString()
				), () -> "minecraft:zombie", o -> o instanceof String str && str.matches(REGISTRY_NAME_MATCHER));
		ITEM_WHITELIST = BUILDER.comment("Items in this list will never be cleaned and will be kept as item entity until it disappear.\n此列表中的物品永远不会被清扫，会以掉落物形式保留直到自然消失。")
				.defineListAllowEmpty("ITEM_WHITELIST", List.of(
						ResourceLocation.withDefaultNamespace("nether_star").toString(),
						ResourceLocation.withDefaultNamespace("heavy_core").toString()
				), () -> "minecraft:dirt", o -> o instanceof String str && str.matches(REGISTRY_NAME_MATCHER));
		ITEM_BLACKLIST = BUILDER.comment("Items in this list will be cleaned but not added to the dustbin.\n此列表中的物品会被清扫，但不会存入垃圾箱。")
				.defineListAllowEmpty("ITEM_BLACKLIST", List.of(
						ResourceLocation.withDefaultNamespace("cobblestone").toString(),
						ResourceLocation.withDefaultNamespace("sand").toString()
				), () -> "minecraft:dirt", o -> o instanceof String str && str.matches(REGISTRY_NAME_MATCHER));
		MESSAGE_BEFORE_SWEEP_15_30_60 = BUILDER.comment("What message will be sent to players when there's 15s, 30s and 60s left to sweep. \"$1\" stands for the remaining time (in seconds). Leave empty to use the built-in localized message (follows the client language).\n距离清扫还有 15 秒、30 秒、60 秒时发送给玩家的提示消息。\"$1\" 为剩余时间（秒）。留空则使用内置本地化消息。")
				.define("MESSAGE_BEFORE_SWEEP_15_30_60", "");
		MESSAGE_BEFORE_SWEEP_1_10 = BUILDER.comment("What message will be sent to players when there's 1s~10s left to sweep. \"$1\" stands for the remaining time (in seconds). Leave empty to use the built-in localized message (follows the client language).\n距离清扫还有 1~10 秒时发送给玩家的提示消息。\"$1\" 为剩余时间（秒）。留空则使用内置本地化消息。")
				.define("MESSAGE_BEFORE_SWEEP_1_10", "");
		MESSAGE_AFTER_SWEEP = BUILDER.comment("What message will be sent to players after a sweep. \"$1\" stands for the number of killed dropped items, \"$2\" stands for the number of killed entities, and \"$3\" stands for the number of blacklist items. Leave empty to use the built-in localized message (follows the client language).\n清扫完成后发送给玩家的消息。\"$1\" 为清除的掉落物品数，\"$2\" 为清除的实体数，\"$3\" 为黑名单物品数。留空则使用内置本地化消息。")
				.define("MESSAGE_AFTER_SWEEP", "");
		MESSAGE_WRONG_DUSTBIN = BUILDER.comment("What message will be sent to players when open a wrong dustbin. Leave empty to use the built-in localized message (follows the client language).\n打开无效垃圾箱时发送给玩家的消息。留空则使用内置本地化消息。")
				.define("MESSAGE_WRONG_DUSTBIN", "");
		DUSTBIN_NAME = BUILDER.comment("Name of dustbins. Leave empty to use the built-in localized name (follows the client language).\n垃圾箱名称前缀。留空则使用内置本地化名称（跟随游戏语言）。").define("DUSTBIN_NAME", "");
		DUSTBIN_SLOT_CAPACITY = BUILDER.comment("Max item count that can be stacked in a single dustbin slot. Bigger values reduce the chance that the dustbin gets full.\n单个垃圾箱槽位最多可堆叠的物品数量。更大的值可降低垃圾箱被装满的概率。")
				.defineInRange("DUSTBIN_SLOT_CAPACITY", 1000000, 1, Integer.MAX_VALUE);
		CHAT_MESSAGE_AFTER_SWEEP = BUILDER.comment("What chat message will be sent to players after a sweep. Command will be appended to the end of the chat message. Leave empty to use the built-in localized message (follows the client language).\n清扫完成后发送给玩家的聊天消息，末尾会附加垃圾箱链接。留空则使用内置本地化消息。")
				.define("CHAT_MESSAGE_AFTER_SWEEP", "");
		PERMISSION_LEVEL_DUSTBIN = BUILDER.comment("Permission level of a player to open the dustbin.\n打开垃圾箱所需的权限等级。").defineInRange("PERMISSION_LEVEL_DUSTBIN", 0, 0, 4);
		PERMISSION_LEVEL_CLEAN = BUILDER.comment("Permission level of a player to clean immediately.\n立即执行清扫所需的权限等级。").defineInRange("PERMISSION_LEVEL_CLEAN", 2, 0, 4);
		GLOBAL_ITEM_COUNT_THRESHOLD = BUILDER.comment("Global number of dropped item entities (piles) in all dimensions. If exceeded, an extra sweeping will be triggered before the scheduled interval (subject to the cooldown below). Set a huge value to disable.\n所有维度中掉落物实体（堆）的总数阈值。超过此值将触发一次额外清扫（受下方冷却限制）。设为极大值可禁用。")
				.defineInRange("GLOBAL_ITEM_COUNT_THRESHOLD", 500, 1, Integer.MAX_VALUE);
		OVERLOAD_SWEEP_COOLDOWN = BUILDER.comment("Cooldown (in seconds) between two overload-triggered sweepings.\n两次过载触发清扫之间的冷却时间（秒）。")
				.defineInRange("OVERLOAD_SWEEP_COOLDOWN", 60, 0, 86400);
		MESSAGE_OVERLOAD_SWEEP = BUILDER.comment("Message to be sent to all players after an overload-triggered sweeping. \"$1\" stands for the number of swept item piles. Leave empty to use the built-in localized message (follows the client language).\n过载触发清扫后发送给所有玩家的提示消息。\"$1\" 为本次清扫的掉落物堆数。留空则使用内置本地化消息。")
				.define("MESSAGE_OVERLOAD_SWEEP", "");
		XP_PER_BOTTLE = BUILDER.comment("Experience points needed to convert into 1 experience bottle (rounded up). A bottle gives 3~11 xp, so a loss is expected to avoid xp duplication.\n每多少点经验折算为 1 个附魔之瓶（向上取整）。附魔之瓶开启后只给 3~11 点经验，适度折损可避免刷经验。")
				.defineInRange("XP_PER_BOTTLE", 10, 1, Integer.MAX_VALUE);
		MESSAGE_XP_CONVERTED = BUILDER.comment("Message appended to the sweep chat message when experience orbs were collected. \"$1\" stands for the total experience, \"$2\" stands for the number of converted bottles. Leave empty to use the built-in localized message (follows the client language).\n清扫收集到经验时追加到聊天消息的提示。\"$1\" 为收集的经验总值，\"$2\" 为转换的附魔之瓶数量。留空则使用内置本地化消息。")
				.define("MESSAGE_XP_CONVERTED", "");
		SHOW_FORK_WELCOME = BUILDER.comment("Whether to send the Yansunsky fork welcome message when a player joins. It can also be toggled with /sweepermaid welcome on|off.\n玩家登录时是否发送 Yansunsky 分支声明。也可使用 /sweepermaid welcome on|off 开关。")
				.define("SHOW_FORK_WELCOME", true);
		DUSTBIN_TRASH_MODE = BUILDER.comment("Dustbin auto trash mode. \"clear\" empties all dustbins, \"pack\" compresses the dustbin contents into a file under the world save and then empties them. Only used by the scheduled auto trash and the /sweepermaid trash command.\n垃圾桶自动处理模式。\"clear\" 清空所有垃圾桶；\"pack\" 将垃圾桶内容压缩存盘后清空。仅用于定时自动处理与 /sweepermaid trash 指令。")
				.define("DUSTBIN_TRASH_MODE", "clear");
		DUSTBIN_TRASH_INTERVAL = BUILDER.comment("Interval (in seconds) of the scheduled dustbin auto trash. -1 disables the auto trash.\n垃圾桶自动处理间隔（秒）。-1 表示禁用自动处理。")
				.defineInRange("DUSTBIN_TRASH_INTERVAL", -1, -1, 86400);
		MESSAGE_DUSTBIN_CLEARED = BUILDER.comment("Message sent to online operators after the dustbins are cleared. \"$1\" stands for the number of removed items. Leave empty to use the built-in localized message (follows the client language).\n垃圾桶被清空后发送给在线管理员的提示消息。\"$1\" 为清除的物品总数。留空则使用内置本地化消息。")
				.define("MESSAGE_DUSTBIN_CLEARED", "");
		MESSAGE_DUSTBIN_PACKED = BUILDER.comment("Message sent to online operators after the dustbins are packed. \"$1\" stands for the number of packed items, \"$2\" stands for the output file path. Leave empty to use the built-in localized message (follows the client language).\n垃圾桶被打包后发送给在线管理员的提示消息。\"$1\" 为打包的物品总数，\"$2\" 为输出文件路径。留空则使用内置本地化消息。")
				.define("MESSAGE_DUSTBIN_PACKED", "");
		MESSAGE_DUSTBIN_UNPACKED = BUILDER.comment("Message sent to the player after a pack file is imported. \"$1\" stands for the file name, \"$2\" stands for the number of imported items. Leave empty to use the built-in localized message (follows the client language).\n导入打包文件后发送给玩家的提示消息。\"$1\" 为文件名，\"$2\" 为导入的物品总数。留空则使用内置本地化消息。")
				.define("MESSAGE_DUSTBIN_UNPACKED", "");
		BUILDER.pop();
		SPEC = BUILDER.build();
	}

	/**
	 * 获取配置规范实例喵~
	 *
	 * @return 配置规范对象喵~
	 */
	public static ModConfigSpec getConfig() {
		return SPEC;
	}
}
