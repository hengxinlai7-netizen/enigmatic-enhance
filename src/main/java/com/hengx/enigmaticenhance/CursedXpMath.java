package com.hengx.enigmaticenhance;

/**
 * 无知诅咒卷轴的**纯数学**部分(没有任何 Minecraft / 扩展类的引用),
 * 因此可以脱离游戏、脱离 classpath 直接做离线自检(见 tools 里的 CursedXpCapProbe)。
 *
 * <p>所有 {@code limit} 都由调用方传入,方便自检构造边界场景。</p>
 */
public final class CursedXpMath {

    private CursedXpMath() {}

    // ------------------------------------------------------------------ 经验 ↔ 等级(原版经验表)

    /** 到达 {@code level} 级所需的总经验(原版公式)。 */
    public static long xpForLevel(int level) {
        if (level <= 0) {
            return 0L;
        }
        if (level <= 16) {
            return (long) level * level + 6L * level;
        }
        if (level <= 31) {
            return (long) Math.floor(2.5D * level * level - 40.5D * level + 360.0D);
        }
        return (long) Math.floor(4.5D * level * level - 162.5D * level + 2220.0D);
    }

    /** 等价于 {@code ExperienceHelper.getLevelForExperience}:总经验 ≤ xp 的最大等级。 */
    public static int levelForExperience(int xp) {
        if (xp <= 0) {
            return 0;
        }
        int level;
        if (xp < 394) {                 // 0..16 级:L² + 6L = xp
            level = (int) Math.floor((-6.0D + Math.sqrt(36.0D + 4.0D * xp)) / 2.0D);
            if (level > 16) {
                level = 16;
            }
        }
        else if (xp < 1628) {           // 17..31 级:2.5L² - 40.5L + 360 = xp
            level = (int) Math.floor((40.5D + Math.sqrt(1640.25D - 10.0D * (360.0D - xp))) / 5.0D);
            if (level < 17) {
                level = 17;
            }
            if (level > 31) {
                level = 31;
            }
        }
        else {                          // 32 级起:4.5L² - 162.5L + 2220 = xp
            level = (int) Math.floor((162.5D + Math.sqrt(26406.25D - 18.0D * (2220.0D - xp))) / 9.0D);
            if (level < 32) {
                level = 32;
            }
        }
        // 1e9 量级上闭式解会有 ±1 的浮点误差,按定义校正
        while (level > 0 && xpForLevel(level) > xp) {
            level--;
        }
        while (xpForLevel(level + 1) <= xp) {
            level++;
        }
        return level;
    }

    // ------------------------------------------------------------------ 份额 / 额度

    /**
     * 扩展 {@code CursedXPScroll#getXPPortion(Player)} 的等价实现(每 tick 吸多少)。
     *
     * <pre>
     * levelXP = xpForLevel(level+1) - xpForLevel(level);
     * portion = levelXP / 5;
     * if (level &gt; 100) portion *= 1 + level / 100;
     * return portion &gt; 0 ? portion : 5;
     * </pre>
     */
    public static int portionForLevel(int level) {
        if (level < 0) {
            level = 0;
        }
        long perLevel = xpForLevel(level + 1) - xpForLevel(level);
        int portion = (int) (perLevel / 5L);
        if (level > 100) {
            portion *= 1 + level / 100;
        }
        return portion > 0 ? portion : 5;
    }

    /** 升到下一级还需要多少经验(原版 {@code Player#getXpNeededForNextLevel})。 */
    public static int xpNeededForNextLevel(int level) {
        if (level < 0) {
            level = 0;
        }
        return (int) (xpForLevel(level + 1) - xpForLevel(level));
    }

    /**
     * {@code ExperienceHelper.getPlayerXP(Player)} 的等价实现(反编译确认):
     *
     * <pre>
     * return (int) (getExperienceForLevel(player.experienceLevel)
     *              + player.experienceProgress * player.getXpNeededForNextLevel());
     * </pre>
     * 注意它<b>不是</b> {@code player.totalExperience},而是"当前等级已累计的经验 + 本级进度"。
     */
    public static int realPlayerXp(int level, float progress) {
        if (level < 0) {
            level = 0;
        }
        double base = xpForLevel(level);
        double fraction = (double) progress * (double) xpNeededForNextLevel(level);
        return (int) (base + fraction);
    }

    /** 距离上限还差多少**原始经验**(已达/超过 ⇒ 0)。 */
    public static long remainingToCap(int storedXp, int limitLevel) {
        long cap = xpForLevel(limitLevel);
        return Math.max(cap - Math.max(storedXp, 0), 0L);
    }

    /**
     * 把本 tick 的份额夹到剩余额度以内。
     * 未到上限时返回恒 ≥ 1(绝不给扩展 0/负数,否则它会走 {@code portion<=0 → 5} 的兜底)。
     */
    public static int clampPortion(int rawPortion, int storedXp, int limitLevel) {
        long remaining = remainingToCap(storedXp, limitLevel);
        if (remaining <= 0L) {
            return 0;
        }
        int effective = Math.max(rawPortion, 1);
        return (int) Math.min((long) effective, remaining);
    }

    /** 吸收模式下是否应当整个 tick 早退(纯数值版)。 */
    public static boolean shouldSkip(int playerXp, int playerLevel, int storedXp, boolean absorptionMode,
                                     int limitLevel) {
        if (!absorptionMode) {
            return false;                       // 取回模式一律放行
        }
        if (remainingToCap(storedXp, limitLevel) <= 0L) {
            return true;                        // 已到上限
        }
        int portion = clampPortion(portionForLevel(playerLevel), storedXp, limitLevel);
        return playerXp < portion;              // 不足一份 ⇒ 不吸(否则会被"全吸干")
    }
}
