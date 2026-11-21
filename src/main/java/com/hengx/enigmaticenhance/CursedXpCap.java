package com.hengx.enigmaticenhance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 4.8.4:无知诅咒卷轴({@code enigmaticaddons:cursed_xp_scroll})「到上限就停手 + 不把人吸到 0 级」的判定。
 *
 * <h2>为什么要有这个类(排查结论)</h2>
 * 反编译 {@code auviotre.enigmatic.addon.contents.items.CursedXPScroll}:
 * <ul>
 *   <li>{@code XPLevelUpperLimit} 默认 <b>1000</b>,注释是
 *       "The Max Level of <b>stored experience</b> … which provides attribute amplification";</li>
 *   <li>它<b>只在</b> {@code getXPAttributeMultiplier(ItemStack)} 里被用到:
 *       {@code Mth.clamp((double) getLevel(stack) / XPLevelUpperLimit, 0, 1)}
 *       —— 上限只压<b>属性加成倍率</b>,**不限制继续吸收**;</li>
 *   <li>{@code curioTick(SlotContext, ItemStack)} 里**没有任何上限判断**;
 *       并且吸收分支的 else-if 分支在"玩家剩余经验不足一份"时把剩下的**全部**吸干
 *       ⇒ 玩家等级直接掉回 0 级。</li>
 * </ul>
 * 所以本类 + {@code CursedXpScrollCapMixin} 做两件事:到上限停手、不足一份不吸。
 *
 * <h2>计量口径</h2>
 * 上限是**存储经验折算的等级**:{@code levelForExperience(XPStored) >= XPLevelUpperLimit},
 * 与玩家当前等级无关。默认 1000 级 ⇔ {@code xpForLevel(1000) = 4,339,720} 点存储经验。
 * 判定完全无状态(每 tick 重算),取回把 {@code XPStored} 降下去后立刻恢复吸收。
 *
 * <p>纯数学部分在 {@link CursedXpMath}(不引用任何 MC 类型,可离线自检)。</p>
 */
public final class CursedXpCap {

    /** 卷轴的 NBT 键(反编译确认,与 CursedXPScroll 一致)。 */
    public static final String NBT_XP_STORED = "XPStored";

    /** NBT 键:吸收模式 true / 取回模式 false(反编译确认)。 */
    public static final String NBT_ABSORPTION_MODE = "AbsorptionMode";

    /** 扩展类名(只用字符串 + 反射)。 */
    private static final String SCROLL_CLASS = "auviotre.enigmatic.addon.contents.items.CursedXPScroll";

    /** 读不到配置时的兜底(与扩展默认值一致)。 */
    public static final int DEFAULT_LIMIT = 1000;

    private CursedXpCap() {}

    // ------------------------------------------------------------------ 物品侧判定

    /** 卷轴里已存的原始经验值。 */
    public static int storedXp(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_XP_STORED)) {
            return 0;
        }
        return tag.getInt(NBT_XP_STORED);
    }

    /**
     * 是否处于「吸收模式」。
     *
     * <p><b>缺省值必须是 true</b>:扩展自己的判定是
     * {@code ItemNBTHelper.getBoolean(stack, "AbsorptionMode", true)},
     * 也就是没有这个 NBT 时按"吸收"处理。若这里用 false 当缺省,全新卷轴(还没有任何 NBT)
     * 就会被当成取回模式 → 上限完全不生效。所以必须与扩展的缺省值一致。</p>
     */
    public static boolean isAbsorptionMode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(NBT_ABSORPTION_MODE)) {
                return true; // 与 CursedXPScroll 的 getBoolean(..., true) 对齐
            }
            return tag.getBoolean(NBT_ABSORPTION_MODE);
        }
        catch (Throwable t) {
            return true; // fail-open
        }
    }

    /** 吸收模式下是否应当整个 tick 早退。 */
    public static boolean shouldSkip(ItemStack stack, int playerXp, int playerLevel) {
        return CursedXpMath.shouldSkip(playerXp, playerLevel, storedXp(stack), isAbsorptionMode(stack), limit());
    }

    /** {@code ExperienceHelper.getPlayerXP(Player)} 的等价真实值(见 {@link CursedXpMath#realPlayerXp})。 */
    public static int realPlayerXp(Player player) {
        if (player == null) {
            return 0;
        }
        return CursedXpMath.realPlayerXp(player.experienceLevel, player.experienceProgress);
    }

    /**
     * 诊断日志(仅 {@code debugLog=true} 时输出,5 秒最多一条)。
     * <p>缩略写法沿用项目里的节流约定(见 {@code PearlHandler} 的 warnThrottled)。</p>
     */
    public static void logSkipDecision(ItemStack stack, int playerXp, int playerLevel, boolean skip) {
        try {
            if (!RingConfig.debugLog()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastLogAt < 5000L) {
                return;
            }
            lastLogAt = now;
            DebugLog.info("[无知诅咒] stored={} cap={} limit={} portion={} playerXp={} level={} skip={}",
                storedXp(stack), xpForLevel(limit()), limit(), portionForLevel(playerLevel), playerXp,
                playerLevel, skip);
        }
        catch (Throwable ignored) {
            // 诊断日志绝不影响主流程
        }
    }

    private static long lastLogAt;

    // ------------------------------------------------------------------ 转发到纯数学(供 mixin 使用)

    public static int portionForLevel(int level) {
        return CursedXpMath.portionForLevel(level);
    }

    public static int clampPortion(int rawPortion, int storedXp) {
        return CursedXpMath.clampPortion(rawPortion, storedXp, limit());
    }

    public static long xpForLevel(int level) {
        return CursedXpMath.xpForLevel(level);
    }

    // ------------------------------------------------------------------ 上限配置(反射,CursedXPScroll.XPLevelUpperLimit)

    private static Object limitParameter;
    private static boolean limitResolved;
    /** {@code limitParameter.getClass()#getValue} 的缓存(热路径每 tick 都会问一次上限)。 */
    private static Method limitGetValue;
    /** {@link #limitGetValue} 是按哪个类解析的;类型变了就重解析。 */
    private static Class<?> limitGetValueClass;

    /**
     * {@code CursedXPScroll.XPLevelUpperLimit#getValue()}(取不到用 {@link #DEFAULT_LIMIT})。
     *
     * <p>4.9.1(CPU/内存):{@code getValue()} 的 {@link Method} 只解析一次。原来每 tick 每名佩戴者
     * 都要走两遍 {@code limit()}(mixin 里 {@code clampPortion} + {@code shouldSkip}),也就是每次
     * {@code getMethod} 一次 —— 而 {@code getMethod} 每次都会复制一个新的 {@link Method} 对象。</p>
     */
    public static int limit() {
        if (!limitResolved) {
            limitResolved = true;
            try {
                Field field = Class.forName(SCROLL_CLASS).getField("XPLevelUpperLimit");
                limitParameter = field.get(null);
            }
            catch (Throwable ignored) {
                limitParameter = null;
            }
        }
        if (limitParameter == null) {
            return DEFAULT_LIMIT;
        }
        try {
            Class<?> type = limitParameter.getClass();
            if (limitGetValue == null || limitGetValueClass != type) {
                limitGetValueClass = type;
                limitGetValue = type.getMethod("getValue");
            }
            Object value = limitGetValue.invoke(limitParameter);
            return value instanceof Number n ? n.intValue() : DEFAULT_LIMIT;
        }
        catch (Throwable ignored) {
            return DEFAULT_LIMIT; // 配置还没加载
        }
    }
}
