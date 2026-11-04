package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.MercurySerpentHandler;

import net.minecraft.world.entity.LivingEntity;

/**
 * 1.6.15:水银之蛇 —— **血量回滚**。
 *
 * <p>很多"秒杀"不伤害、不死亡,而是直接
 * {@code setHealth(0)} 或写同步血量字段。这里在所有 {@code setHealth} 入口做回滚:</p>
 * <ul>
 *   <li>受保护时,任何"降低血量"的写入都会被取消(保留原血量);</li>
 *   <li>内部逻辑(回满、复位)通过 {@link MercurySerpentHandler#ALLOW_WRITE} 放行。</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class MercurySerpentHealthRollbackMixin {

    @Inject(method = "m_21152_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$rollback(float health, CallbackInfo ci) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (self.level().isClientSide) {
                return;
            }
            if (MercurySerpentHandler.ALLOW_WRITE.get().booleanValue()) {
                return;
            }
            if (!MercurySerpentHandler.protectedFromDeath(self)) {
                return;
            }
            if (health < self.getHealth() - 0.001F) {
                self.invulnerableTime = Math.max(self.invulnerableTime, 10);   // 顺带锁无敌帧
                ci.cancel();   // 拒绝任何"降低血量"的写入
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}