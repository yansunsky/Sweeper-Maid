package com.hexagram2021.sweeper_maid.util;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import org.slf4j.Logger;

/**
 * 经验球取值工具喵~
 * <p>
 * 原版经验球实体表示的<b>不是</b>「一颗球」，而是「若干颗同面值的球」：公开字段 {@code value}
 * 是单颗面值，私有字段 {@code count} 是合并进来的球数。玩家每碰撞一次收走一颗
 * （给 {@code value} 点经验、{@code count--}），减到 0 实体才消失。所以一颗实体的真实经验
 * 总值是 <b>{@code value × count}</b>（原版 {@code ExperienceOrb#playerTouch}）喵~。
 * </p>
 * <p>
 * <b>为什么不能直接用 {@link ExperienceOrb#getValue()}</b>：它只返回单颗面值
 * （原版实现就是 {@code return this.value;}），而 {@code count} 既没有 getter、也不是公开字段。
 * 若照 {@code getValue()} 记账，一颗 {@code value=1, count=7} 的球只会记 1 点，剩下 6 点被静默抹掉。
 * 偏偏面值 3 与 1 的小球最容易互相合并（合并要求「面值相同且靠得够近」），
 * 而它们才是刷怪 / 刷矿场景里数量最多的那批 —— 也就是经验球堆积到被清扫的那批喵~。
 * </p>
 * <p>
 * <b>为什么走 NBT 而不是反射</b>：{@code count} 只经由原版自己的序列化暴露，
 * 即 {@link Entity#saveWithoutId} → {@code ExperienceOrb#addAdditionalSaveData}
 * 写出的 {@code Count} 存档键。该键属于原版数据格式，不随映射 / 混淆变化；
 * 反射则要硬编码字段名，上游一改名就是运行时炸，且编译期发现不了喵~。
 * </p>
 */
public final class SMExperienceOrbs {
	private static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * 经验球存档里「合并球数」的 NBT 键名喵~
	 */
	private static final String COUNT_TAG = "Count";

	/**
	 * 私有构造方法，防止实例化喵~
	 */
	private SMExperienceOrbs() {
	}

	/**
	 * 读取一颗经验球实体内部合并了多少颗同面值的球喵~
	 * <p>
	 * 未合并过的球为 1。代价是序列化一次实体（每次清扫每颗球一次），相对清扫本身的
	 * 遍历成本可以忽略喵~。
	 * </p>
	 *
	 * @param orb 目标经验球喵~
	 * @return 内部球数，至少为 1 喵~
	 */
	public static int orbCount(ExperienceOrb orb) {
		try {
			CompoundTag tag = orb.saveWithoutId(new CompoundTag());
			// 键缺失（例如上游改了存档格式）时退回 1：宁可少算，也不能让一次清扫把服务器炸掉喵~
			// 「键一定在」这条约束由 GameTest 钉住，格式真变了会先炸在测试上喵~
			return Math.max(tag.getInt(COUNT_TAG), 1);
		} catch (RuntimeException e) {
			LOGGER.warn("Sweeper Maid: failed to read the orb count of an experience orb, treating it as a single orb", e);
			return 1;
		}
	}

	/**
	 * 读取一颗经验球实体真正代表的经验总值（{@code value × count}）喵~
	 *
	 * @param orb 目标经验球喵~
	 * @return 经验点数的总值，面值非正时返回 0 喵~
	 */
	public static int totalValue(ExperienceOrb orb) {
		int value = orb.getValue();
		if (value <= 0) {
			return 0;
		}
		long total = (long) value * orbCount(orb);
		// 下游是 AtomicInteger 计数，这里对齐 int 上限，避免极端值溢出成负数喵~
		return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
	}
}
