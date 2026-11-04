package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.MercurySerpentHandler;

import net.minecraft.world.entity.LivingEntity;

/**
 * 1.4.1:受「水银之蛇」保护时,禁止把生命写成 0 或负数(防止"血量 0 但没死"的假死态被利用)。
 */
@Mixin(LivingEntity.class)
public class MercurySerpentHealthGuardMixin {

    @Inject(method = "m_21152_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$clampHealth(float health, CallbackInfo ci) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (health > 0.0F || self.level().isClientSide) {
                return;
            }
            if (MercurySerpentHandler.protectedFromDeath(self)) {
                ci.cancel();   // 直接拒绝这次写入(不递归)
            }
        }
        catch (StackOverflowError ignored) {
            // setHealth 递归保护(极少数情况)
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}