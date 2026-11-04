package com.hengx.enigmaticenhance;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** 1.5.2:永暗之寂 —— 禁用一切「可右键使用」的物品。 */
public class EternalSilenceEffect extends MobEffect {

    public EternalSilenceEffect() {
        super(MobEffectCategory.HARMFUL, 0x101014);
    }
}