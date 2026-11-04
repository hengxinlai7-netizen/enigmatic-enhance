package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.ConsciousnessSeaHandler;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** 1.6.18:持有「瓶装意识之海」时,生物无法把它设为目标。 */
@Mixin(Mob.class)
public class SeaTargetGuardMixin {

    @Inject(method = "m_6710_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockSetTarget(LivingEntity target, CallbackInfo ci) {
        try {
            if (target != null && ConsciousnessSeaHandler.holding(target)) {
                ci.cancel();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}