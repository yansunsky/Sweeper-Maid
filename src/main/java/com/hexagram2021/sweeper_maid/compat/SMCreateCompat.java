package com.hexagram2021.sweeper_maid.compat;

import com.hexagram2021.sweeper_maid.config.SMCommonConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/**
 * 机械动力（Create）经验颗粒联动喵~
 * <p>
 * 本模组清扫时会收集经验球。默认把它们折算成附魔之瓶；装了机械动力后改为折算成
 * <b>经验颗粒</b>（{@code create:experience_nugget}）喵~。
 * </p>
 * <p>
 * <b>换算率来自 Create 源码</b>（{@code com.simibubi.create.content.materials.ExperienceNuggetItem#use}）：
 * <pre>
 * int total = Mth.ceil(3f * amountUsed);
 * </pre>
 * 即 1 颗经验颗粒恰好换回 <b>3 点</b>经验（整数颗时 {@code ceil} 无误差）。
 * 反向折算时取 {@code xp / 3} 向下取整，因此绝不会凭空刷出经验喵~。
 * </p>
 * <p>
 * <b>为什么用注册名而不是编译期依赖</b>：本模组把 Create 当作<b>可选</b>依赖，不引入任何
 * Create 的编译期/运行期依赖，因此没有 {@code NoClassDefFoundError} 风险，
 * 未装 Create 时也不会加载到任何 Create 的类喵~。
 * </p>
 */
public final class SMCreateCompat {
	/**
	 * 机械动力的模组 ID 喵~
	 */
	public static final String CREATE_MODID = "create";

	/**
	 * 经验颗粒的注册名喵~
	 */
	public static final ResourceLocation EXPERIENCE_NUGGET_ID =
			ResourceLocation.fromNamespaceAndPath(CREATE_MODID, "experience_nugget");

	/**
	 * 1 颗经验颗粒换回的经验值，取自 Create 的 {@code ExperienceNuggetItem#use} 里的 {@code 3f} 喵~
	 */
	public static final int XP_PER_NUGGET = 3;

	/**
	 * 经验颗粒物品的缓存（只缓存命中结果，理由见 {@link #getExperienceNugget()}）喵~
	 */
	@Nullable
	private static Item cachedNugget;

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMCreateCompat() {
	}

	/**
	 * 机械动力是否已加载喵~
	 *
	 * @return 已加载返回 true 喵~
	 */
	public static boolean isCreateLoaded() {
		return ModList.get().isLoaded(CREATE_MODID);
	}

	/**
	 * 取经验颗粒物品喵~
	 * <p>
	 * <b>注意</b>：{@code BuiltInRegistries.ITEM} 是 {@code DefaultedRegistry}，
	 * 查不到键时 {@code get()} 会返回 {@code Items.AIR} 而<b>不是</b> {@code null}，
	 * 所以这里必须用 {@code getOptional()} 并且额外排除 AIR，
	 * 否则「Create 没装」会被误判成「拿到了经验颗粒」，最终往垃圾箱里塞一堆空气喵~。
	 * </p>
	 *
	 * @return 经验颗粒物品；拿不到时返回 {@code null} 喵~
	 */
	@Nullable
	public static Item getExperienceNugget() {
		// 只缓存**命中**结果，不缓存「未命中」：
		// 原版注册表在模组加载完成后就固定了，命中一次即可永久复用；
		// 而万一首次查询发生在 Create 注册表尚未建好之前（例如极早的调用时机），
		// 缓存未命中会让整个联动**永久失效**且没有任何报错，属于最难查的静默故障。
		// 因此未命中时不置 nuggetQueried，下次调用会重新查一遍喵~
		if (cachedNugget != null) {
			return cachedNugget;
		}
		Item item = BuiltInRegistries.ITEM.getOptional(EXPERIENCE_NUGGET_ID).orElse(null);
		// 注册表里找不到键时 getOptional 返回空；但若该键被显式映射到 air 也要当作没拿到喵~
		if (item == null || item == Items.AIR) {
			return null;
		}
		cachedNugget = item;
		return cachedNugget;
	}

	/**
	 * 经验颗粒当前是否可用（Create 已加载<b>且</b>物品确实存在）喵~
	 * <p>
	 * 判据取「物品真的在注册表里」而不是「模组 ID 已加载」：万一将来 Create 改名或移除该物品，
	 * 也能正确退回附魔之瓶，而不是产出空气喵~。
	 * </p>
	 *
	 * @return 可用返回 true 喵~
	 */
	public static boolean isNuggetAvailable() {
		return isCreateLoaded() && getExperienceNugget() != null;
	}

	/**
	 * 解析当前的折算模式喵~
	 * <p>
	 * 配置值非法或拼错时按 {@code auto} 处理，保证服主写错配置也不会让经验凭空消失喵~。
	 * </p>
	 *
	 * @return 实际生效的折算模式喵~
	 */
	public static ConversionMode resolveMode() {
		String raw = SMCommonConfig.XP_CONVERSION_MODE.get();
		ConversionMode configured = ConversionMode.byName(raw);
		if (configured == ConversionMode.NUGGET && !isNuggetAvailable()) {
			// 服主强制要经验颗粒但当前没有 Create：如实退回附魔之瓶，绝不让经验凭空消失喵~
			return ConversionMode.BOTTLE;
		}
		if (configured == ConversionMode.AUTO) {
			return isNuggetAvailable() ? ConversionMode.NUGGET : ConversionMode.BOTTLE;
		}
		return configured;
	}

	/**
	 * 把经验值折算成经验颗粒的<b>纯函数</b>喵~
	 * <p>
	 * 单独抽出来是为了能在 GameTest 里脱离世界直接断言数学，也避免把「取物品」和「算数量」
	 * 混在一起（取物品失败时不该影响数量计算）喵~。
	 * </p>
	 *
	 * @param xp 收集到的经验总值喵~
	 * @return 应折算出的经验颗粒数量（向下取整，可能为 0）喵~
	 */
	public static int nuggetsFor(int xp) {
		return xp <= 0 ? 0 : xp / XP_PER_NUGGET;
	}

	/**
	 * 把经验值折算成附魔之瓶的<b>纯函数</b>喵~
	 * <p>
	 * 与原实现保持一致：向上取整（附魔之瓶开启只给 3~11 点经验，适度折损可避免刷经验）喵~
	 * </p>
	 *
	 * @param xp          收集到的经验总值喵~
	 * @param xpPerBottle 每个附魔之瓶折算所需经验喵~
	 * @return 应折算出的附魔之瓶数量喵~
	 */
	public static int bottlesFor(int xp, int xpPerBottle) {
		if (xp <= 0 || xpPerBottle <= 0) {
			return 0;
		}
		return (xp + xpPerBottle - 1) / xpPerBottle;
	}

	/**
	 * 构建一组可入桶的物品堆喵~
	 *
	 * @param item  物品喵~
	 * @param count 数量喵~
	 * @return 物品堆；数量非正时返回 {@link ItemStack#EMPTY} 喵~
	 */
	public static ItemStack stackOf(@Nullable Item item, int count) {
		return item == null || count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
	}

	/**
	 * 按折算模式把经验值折算成<b>最终要入桶的那一堆物品</b>喵~
	 * <p>
	 * 这是清扫流程真正调用的那段逻辑，单独抽成纯函数是为了让它可被 GameTest 直接断言：
	 * 「装了机械动力时 auto 模式必须产出经验颗粒」这条核心承诺，
	 * 只有断言这个函数真的返回了经验颗粒才算被钉住——只测换算数学是不够的喵~。
	 * </p>
	 * <p>
	 * 各模式行为：
	 * <ul>
	 *     <li>{@code DISCARD}：不产出任何物品（返回空堆）喵~</li>
	 *     <li>{@code AUTO}：装了 Create 则产经验颗粒，否则产附魔之瓶喵~</li>
	 *     <li>{@code NUGGET}：产经验颗粒；万一物品取不到则退化为附魔之瓶，不让经验凭空消失喵~</li>
	 *     <li>{@code BOTTLE}：产附魔之瓶喵~</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <b>为什么 {@code AUTO} 要在这里自己解析</b>：调用方若直接传 {@code AUTO} 进来
	 * （而不是先调 {@link #resolveMode()}），旧实现会一路落到 {@code default} 分支产出附魔之瓶——
	 * 装了机械动力也拿不到经验颗粒，属于静默走错分支。这条路径已被 GameTest 抓到过一次，
	 * 所以现在由本函数自己兜住，不依赖调用顺序喵~。
	 * </p>
	 *
	 * @param xp          收集到的经验总值喵~
	 * @param mode        折算模式喵~
	 * @param xpPerBottle 每个附魔之瓶折算所需经验喵~
	 * @return 应存入垃圾箱的物品堆；无产出时返回 {@link ItemStack#EMPTY} 喵~
	 */
	public static ItemStack conversionResultFor(int xp, ConversionMode mode, int xpPerBottle) {
		if (xp <= 0) {
			return ItemStack.EMPTY;
		}
		// AUTO 与「NUGGET 但没装 Create」都要退化为附魔之瓶，这里统一解析一次喵~
		ConversionMode effective = switch (mode) {
			case AUTO -> isNuggetAvailable() ? ConversionMode.NUGGET : ConversionMode.BOTTLE;
			case NUGGET -> isNuggetAvailable() ? ConversionMode.NUGGET : ConversionMode.BOTTLE;
			default -> mode;
		};
		return switch (effective) {
			case DISCARD -> ItemStack.EMPTY;
			case NUGGET -> stackOf(getExperienceNugget(), nuggetsFor(xp));
			default -> stackOf(Items.EXPERIENCE_BOTTLE, bottlesFor(xp, xpPerBottle));
		};
	}

	/**
	 * 经验折算模式喵~
	 */
	public enum ConversionMode {
		/**
		 * 自动：装了机械动力用经验颗粒，否则用附魔之瓶喵~
		 */
		AUTO("auto"),
		/**
		 * 始终折算成附魔之瓶喵~
		 */
		BOTTLE("bottle"),
		/**
		 * 始终折算成经验颗粒（未装机械动力时退回附魔之瓶）喵~
		 */
		NUGGET("nugget"),
		/**
		 * 直接清除，不产出任何物品喵~
		 */
		DISCARD("discard");

		private final String configName;

		ConversionMode(String configName) {
			this.configName = configName;
		}

		/**
		 * 配置文件中使用的名称喵~
		 *
		 * @return 配置名喵~
		 */
		public String configName() {
			return this.configName;
		}

		/**
		 * 按配置字符串解析模式（忽略大小写与首尾空白）喵~
		 *
		 * @param name 配置值，可为 {@code null} 喵~
		 * @return 解析出的模式；无法识别时返回 {@link #AUTO} 喵~
		 */
		public static ConversionMode byName(@Nullable String name) {
			if (name == null) {
				return AUTO;
			}
			String trimmed = name.trim();
			for (ConversionMode mode : values()) {
				if (mode.configName.equalsIgnoreCase(trimmed)) {
					return mode;
				}
			}
			return AUTO;
		}
	}
}
