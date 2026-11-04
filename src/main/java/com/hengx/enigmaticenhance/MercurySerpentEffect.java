package com.hengx.enigmaticenhance;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 1.3.3:水银之蛇(有益效果)。
 *
 * <p>机制见 {@link MercurySerpentHandler}:生命降到 50% 时回满并获得次数盾;
 * 每层盾抵挡一次任意伤害并给 10 tick 无敌;层数耗尽则效果消失。</p>
 */
public class MercurySerpentEffect extends MobEffect {

    public MercurySerpentEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE8EEF4);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;   // 逻辑全在事件里
    }
}