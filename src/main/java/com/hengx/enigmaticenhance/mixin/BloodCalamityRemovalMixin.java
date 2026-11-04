package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.BloodCalamityHandler;
import com.hengx.enigmaticenhance.ModEffects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.2.23:「血色之灾」**不能被牛奶 / 净化 / 任何外部手段清除**。
 *
 * <p>拦 {@code LivingEntity#removeEffect(MobEffect)}(SRG {@code m_21195_}):
 * 目标是血色之灾且不在内部放行状态时,直接取消。
 * 内部放行由 {@link BloodCalamityHandler#removalAllowed()} 控制(击杀生物后自动清除用)。</p>
 */
@Mixin(LivingEntity.class)
public class BloodCalamityRemovalMixin {

    @Inject(method = "m_21195_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockRemoval(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        try {
            MobEffect ours = ModEffects.bloodCalamityOrNull();
            if (ours == null || effect != ours) {
                return;
            }
            if (BloodCalamityHandler.removalAllowed()) {
                return;   // 内部允许(击杀清除)
            }
            cir.setReturnValue(Boolean.FALSE);   // removeEffect 返回 false = 没移除
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就允许移除
        }
    }
}