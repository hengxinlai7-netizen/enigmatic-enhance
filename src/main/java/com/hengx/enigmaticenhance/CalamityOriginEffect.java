package com.hengx.enigmaticenhance;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** 1.5.1:根源之祸 —— 下次受到伤害时,额外承受自身最大生命 40% 的绝对真伤,然后清除。 */
public class CalamityOriginEffect extends MobEffect {

    /** 真伤比例(写死)。 */
    public static final float TRUE_DAMAGE_RATIO = 0.40F;

    public CalamityOriginEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF6B2B);
    }
}