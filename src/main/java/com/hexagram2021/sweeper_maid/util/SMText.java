package com.hexagram2021.sweeper_maid.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
 */
public final class SMText {
	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMText() {
	}

	/**
	 * 构建消息组件喵~
	 *
	 * @param custom        自定义消息配置项，可为空喵~
	 * @param translationKey 翻译键喵~
	 * @param args          占位符参数（配置用 {@code $1..$n}，翻译键用 {@code %s}）喵~
	 * @return 消息组件喵~
	 */
	public static MutableComponent of(Supplier<String> custom, String translationKey, Object... args) {
		String text = custom.get();
		if (text != null && !text.isEmpty()) {
			for (int i = 0; i < args.length; ++i) {
				text = text.replace("$" + (i + 1), String.valueOf(args[i]));
			}
			return Component.literal(text);
		}
		return Component.translatable(translationKey, args);
	}
}
