package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.ModEffects;

/**
 * 1.2.23:「血色之灾」期间**无法回复生命**。
 *
 * <p>拦 {@code LivingEntity#heal(float)}(SRG {@code m_5634_}):身上带该效果时直接取消,
 * 覆盖自然回血、药水回血、饱和回血、模组治疗全部来源。</p>
 */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public class BloodCalamityMixin {

    @Inject(method = "m_5634_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockHeal(float amount, CallbackInfo ci) {
        try {
            net.minecraft.world.effect.MobEffect effect = ModEffects.bloodCalamityOrNull();
            if (effect == null) {
                return;
            }
            net.minecraft.world.entity.LivingEntity self =
                (net.minecraft.world.entity.LivingEntity) (Object) this;
            if (!self.hasEffect(effect)) {
                return;
            }
            if (self instanceof net.minecraft.world.entity.player.Player player
                && com.hengx.enigmaticenhance.BloodCalamityHandler.wearingBerserkCharm(player)) {
                return;   // 佩戴血战沙场之证 → 允许回血
            }
            ci.cancel();
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就允许治疗
        }
    }
}