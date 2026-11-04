package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.TrueDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 2.10.5:通用绕过所有"事件层限伤"。
 *
 * <p>{@code ForgeHooks.onLivingHurt / onLivingDamage} 是<b>所有模组</b>
 * LivingHurtEvent / LivingDamageEvent 限伤的唯一派发口。</p>
 *
 * <p>当伤害源是 {@code enigmaticenhance:true_damage} 时,直接返回原值、不派发事件 ——
 * 于是任何模组(传奇怪物、诡厄巫法、其它)在事件里夹伤害的逻辑全部不会执行。
 * 不针对任何具体模组类名,对面改名/更新也不会失效。</p>
 */
@Mixin(value = ForgeHooks.class, remap = false)
public class TrueDamageBypassMixin {

    @Inject(method = "onLivingHurt", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$skipHurt(LivingEntity entity, DamageSource src, float amount,
        CallbackInfoReturnable<Float> cir) {
        if (src != null && TrueDamage.isTrueDamage(src)) {
            cir.setReturnValue(amount);
        }
    }

    @Inject(method = "onLivingDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$skipDamage(LivingEntity entity, DamageSource src, float amount,
        CallbackInfoReturnable<Float> cir) {
        if (src != null && TrueDamage.isTrueDamage(src)) {
            cir.setReturnValue(amount);
        }
    }

    @Inject(method = "onLivingAttack", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$skipAttack(LivingEntity entity, DamageSource src, float amount,
        CallbackInfoReturnable<Boolean> cir) {
        if (src != null && TrueDamage.isTrueDamage(src)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}