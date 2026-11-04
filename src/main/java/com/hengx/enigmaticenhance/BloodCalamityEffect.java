package com.hengx.enigmaticenhance;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 1.2.23:血色之灾(负面效果)。
 *
 * <ul>
 *   <li>护甲值清零 —— 运行时按"是否佩戴血战沙场之证"施加属性修饰符:
 *       未佩戴 → {@code MULTIPLY_TOTAL -1.0}(护甲归零);
 *       佩戴 → {@code MULTIPLY_TOTAL -0.6}(保留 40% 护甲)。</li>
 *   <li>无法回复生命 —— {@code BloodCalamityMixin} 拦 {@code LivingEntity#heal};
 *       佩戴沙场之证时不拦。</li>
 *   <li>身上每有 1 个负面效果:受到的伤害 +10%(恒定);
 *       若佩戴沙场之证,**造成的伤害**也 +10%/层。</li>
 * </ul>
 */
public class BloodCalamityEffect extends MobEffect {

    public BloodCalamityEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B1A1A);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
}