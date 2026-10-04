package com.hexagram2021.sweeper_maid.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 文本工具类喵~
 * <p>
 * 提供「配置自定义优先、翻译键兜底」的消息构建：
 * <ul>
 *     <li>配置值非空 → 按 {@code $1..$n} 占位符替换后作为字面文本返回喵~</li>
 *     <li>配置值为空 → 返回可翻译组件，随客户端语言自动本地化喵~</li>
 * </ul>
 * </p>
 * <p>
 * <b>为什么每条发给玩家的消息都要带兜底</b>：本模组允许「服务端装了、客户端没装」。
 * 那种客户端没有本模组的语言文件，若组件里只有翻译键，聊天栏就会把
 * {@code sweeper_maid.message.fork_welcome}、{@code sweeper_maid.dustbin.name}
 * 这样的<b>键名</b>直接显示给玩家（实测确实如此）喵~。
 * </p>
 * <p>
 * 兜底文本按<b>该玩家客户端的语言</b>查模组自带的语言文件：服务端在握手时就知道每个客户端的
 * {@code ClientInformation#language()}，所以中文客户端即使没装模组，也照样看到中文，
 * 而不是裸键名或生硬的英文喵~。装了模组的客户端仍按自己的语言显示，不受影响喵~。
 * </p>
 */
public final class SMText {
	private static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * 模组自带语言文件在 jar 内的目录前缀喵~
	 */
	private static final String LANG_DIR = "/assets/sweeper_maid/lang/";

	/**
	 * 拿不到客户端语言时使用的兜底语言喵~
	 */
	private static final String FALLBACK_LANGUAGE = "en_us";

	/**
	 * 各语言的语言表缓存（懒加载）喵~
	 */
	private static final Map<String, Map<String, String>> LANGUAGE_TABLES = new ConcurrentHashMap<>();

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMText() {
	}

	/**
	 * 构建消息组件喵~
	 * <p>
	 * 拿不到目标玩家时使用（例如控制台广播），兜底文本取英文喵~。
	 * 发给玩家的消息请改用 {@link #forPlayer} 以获得正确语言喵~
	 * </p>
	 *
	 * @param custom         自定义消息配置项，可为空喵~
	 * @param translationKey 翻译键喵~
	 * @param args           占位符参数（配置用 {@code $1..$n}，翻译键用 {@code %s}）喵~
	 * @return 消息组件喵~
	 */
	public static MutableComponent of(Supplier<String> custom, String translationKey, Object... args) {
		return withFallback(custom, translationKey, null, FALLBACK_LANGUAGE, args);
	}

	/**
	 * 按<b>指定玩家客户端的语言</b>构建消息组件喵~
	 * <p>
	 * 所有会发给玩家的消息都应走这里：未装模组的客户端也能拿到与它语言一致的文本喵~。
	 * </p>
	 *
	 * @param player         目标玩家，可为 {@code null}（退回英文）喵~
	 * @param custom         自定义消息配置项，可为空喵~
	 * @param translationKey 翻译键喵~
	 * @param args           占位符参数（配置用 {@code $1..$n}，翻译键用 {@code %s}）喵~
	 * @return 消息组件喵~
	 */
	public static MutableComponent forPlayer(@Nullable ServerPlayer player, Supplier<String> custom, String translationKey, Object... args) {
		return withFallback(custom, translationKey, null, languageOf(player), args);
	}

	/**
	 * 按<b>指定玩家客户端的语言</b>构建消息组件（兜底文本由调用方指定）喵~
	 * <p>
	 * 用于语言文件里没有对应条目的场景：兜底文本不会再被语言文件覆盖，
	 * 但仍会按玩家语言优先查一次语言文件喵~。
	 * </p>
	 *
	 * @param player         目标玩家，可为 {@code null} 喵~
	 * @param custom         自定义消息配置项，可为空喵~
	 * @param translationKey 翻译键喵~
	 * @param fallback       语言文件里查不到时使用的字面文本喵~
	 * @param args           占位符参数喵~
	 * @return 消息组件喵~
	 */
	public static MutableComponent forPlayerWithFallback(@Nullable ServerPlayer player, Supplier<String> custom, String translationKey, String fallback, Object... args) {
		return withFallback(custom, translationKey, fallback, languageOf(player), args);
	}

	/**
	 * 构建带兜底文本的消息组件喵~
	 * <p>
	 * 与 {@link #of(Supplier, String, Object...)} 的区别：兜底文本由调用方指定，
	 * 而不是查模组自带的语言文件喵~。
	 * </p>
	 *
	 * @param custom         自定义消息配置项，可为空喵~
	 * @param translationKey 翻译键喵~
	 * @param fallback       客户端缺少该翻译键时显示的字面文本喵~
	 * @param args           占位符参数喵~
	 * @return 消息组件喵~
	 */
	public static MutableComponent ofWithFallback(Supplier<String> custom, String translationKey, String fallback, Object... args) {
		return withFallback(custom, translationKey, fallback, FALLBACK_LANGUAGE, args);
	}

	/**
	 * 按<b>指定玩家客户端的语言</b>构建容器标题等文本喵~
	 * <p>
	 * 与 {@link #forPlayer} 的区别：多一个「语言文件查不到时」的字面兜底，
	 * 但<b>仍优先使用玩家语言的翻译</b>——这样中文客户端看到中文，
	 * 而模组尚未翻译的语言则退回给调用方的字面文本，不会显示成翻译键喵~。
	 * </p>
	 *
	 * @param player         目标玩家，可为 {@code null} 喵~
	 * @param custom         自定义消息配置项，可为空喵~
	 * @param translationKey 翻译键喵~
	 * @param fallback       语言文件里查不到时使用的字面文本喵~
	 * @param args           占位符参数喵~
	 * @return 消息组件喵~
	 */
	public static MutableComponent forPlayerOrLiteral(@Nullable ServerPlayer player, Supplier<String> custom, String translationKey, String fallback, Object... args) {
		return withFallback(custom, translationKey, fallback, languageOf(player), args);
	}

	/**
	 * 按玩家客户端语言构建消息组件（<b>没有</b>对应配置项时使用）喵~
	 * <p>
	 * 适用于只有语言文件、没有配置项的文本（例如 {@code /sweepermaid help} 的每一行）喵~。
	 * </p>
	 *
	 * @param player         目标玩家，可为 {@code null} 喵~
	 * @param translationKey 翻译键喵~
	 * @param args           占位符参数喵~
	 * @return 消息组件喵~
	 */
	public static MutableComponent forPlayer(@Nullable ServerPlayer player, String translationKey, Object... args) {
		return rawForPlayer(player, translationKey, args);
	}

	/**
	 * 按玩家客户端语言构建<b>可翻译 + 兜底</b>组件喵~
	 * <p>
	 * 与 {@link #forPlayer} 的区别：参数是 {@link Component} 而不是普通值，
	 * 用于需要给占位符加样式（如金色高亮）的消息喵~。
	 * </p>
	 *
	 * @param player         目标玩家，可为 {@code null} 喵~
	 * @param translationKey 翻译键喵~
	 * @param args           占位符参数（{@link Component}）喵~
	 * @return 消息组件喵~
	 */
	public static MutableComponent rawForPlayer(@Nullable ServerPlayer player, String translationKey, Object... args) {
		String fallback = textFor(languageOf(player), translationKey);
		return fallback == null
				? Component.translatable(translationKey, args)
				: Component.translatableWithFallback(translationKey, fallback, args);
	}

	/**
	 * 查询翻译键在指定语言下的文本喵~
	 *
	 * @param language       客户端语言代码（如 {@code zh_cn}）喵~
	 * @param translationKey 翻译键喵~
	 * @return 对应文本；该语言查不到时回退到英文；仍查不到返回 {@code null} 喵~
	 */
	@Nullable
	public static String textFor(String language, String translationKey) {
		String text = tableFor(language).get(translationKey);
		if (text == null && !FALLBACK_LANGUAGE.equals(language)) {
			text = tableFor(FALLBACK_LANGUAGE).get(translationKey);
		}
		return text;
	}

	/**
	 * 读取玩家客户端的语言代码喵~
	 *
	 * @param player 目标玩家，可为 {@code null} 喵~
	 * @return 语言代码；拿不到时返回英文喵~
	 */
	private static String languageOf(@Nullable ServerPlayer player) {
		if (player == null) {
			return FALLBACK_LANGUAGE;
		}
		try {
			String language = player.clientInformation().language();
			return language == null || language.isEmpty() ? FALLBACK_LANGUAGE : language;
		} catch (RuntimeException e) {
			// 玩家尚未完成握手时可能取不到；退回英文即可，绝不能因此发不出消息喵~
			return FALLBACK_LANGUAGE;
		}
	}

	/**
	 * 构建组件的统一实现喵~
	 *
	 * @param custom           自定义消息配置项喵~
	 * @param translationKey   翻译键喵~
	 * @param explicitFallback 调用方指定的兜底文本，可为 {@code null} 喵~
	 * @param language         用于查语言文件的语言代码喵~
	 * @param args             占位符参数喵~
	 * @return 消息组件喵~
	 */
	private static MutableComponent withFallback(Supplier<String> custom, String translationKey, @Nullable String explicitFallback, String language, Object... args) {
		String text = custom.get();
		if (text != null && !text.isEmpty()) {
			for (int i = 0; i < args.length; ++i) {
				text = text.replace("$" + (i + 1), String.valueOf(args[i]));
			}
			return Component.literal(text);
		}
		String fallback = textFor(language, translationKey);
		if (fallback == null) {
			// 该玩家语言查不到（例如模组还没翻译这种语言）：退回调用方给的字面文本喵~
			fallback = explicitFallback;
		}
		if (fallback == null) {
			// 两者都没有：只能退化成纯翻译键（旧行为，未装模组的客户端会看到键名）喵~
			return Component.translatable(translationKey, args);
		}
		return Component.translatableWithFallback(translationKey, fallback, args);
	}

	/**
	 * 懒加载指定语言的语言文件喵~
	 *
	 * @param language 语言代码喵~
	 * @return 翻译键到文本的映射（读取失败时为空表）喵~
	 */
	private static Map<String, String> tableFor(String language) {
		return LANGUAGE_TABLES.computeIfAbsent(language, SMText::loadLanguageTable);
	}

	/**
	 * 从 jar 内读取指定语言的语言文件喵~
	 *
	 * @param language 语言代码喵~
	 * @return 翻译键到文本的映射（读取失败时为空表）喵~
	 */
	private static Map<String, String> loadLanguageTable(String language) {
		// 语言代码来自客户端，必须先校验字符集，避免路径穿越喵~
		if (!language.matches("[a-z0-9_]{2,16}")) {
			return Collections.emptyMap();
		}
		String path = LANG_DIR + language + ".json";
		try (InputStream in = SMText.class.getResourceAsStream(path)) {
			if (in == null) {
				return Collections.emptyMap();
			}
			JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return Collections.emptyMap();
			}
			JsonObject object = root.getAsJsonObject();
			Map<String, String> result = new HashMap<>(object.size() * 2);
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					result.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
			return Collections.unmodifiableMap(result);
		} catch (Exception e) {
			LOGGER.warn("Sweeper Maid: failed to read {}, messages may show raw translation keys on unmodded clients", path, e);
			return Collections.emptyMap();
		}
	}
}
