package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.ModEffects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** 1.5.1:根源之祸无法被任何手段清除(牛奶/净化/命令都不行),只能靠"受伤触发"消耗掉。 */
@Mixin(LivingEntity.class)
public class CalamityOriginRemovalMixin {

    @Inject(method = "m_21195_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockRemoval(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (com.hengx.enigmaticenhance.CalamityOriginHandler.ALLOW_REMOVE.get().booleanValue()) {
                return;
            }
            if (effect != null && ModEffects.CALAMITY_ORIGIN.isPresent()
                && effect == ModEffects.CALAMITY_ORIGIN.get()) {
                cir.setReturnValue(Boolean.FALSE);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}