package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.MercurySerpentHandler;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.4.6:「水银之蛇」把死亡保护写死到 <b>die()</b> 与 <b>kill()</b> 入口。
 *
 * <p>很多模组的"秒杀/处决"(例如冰与火之歌的蛇发女妖石化、各类 execute 指令)并不走
 * {@code hurt},而是直接调 {@code kill()} 或 {@code die()},甚至先 setHealth(0) 再 die。
 * 这里在最底层把它们拦下来:</p>
 * <ul>
 *   <li>{@code die(DamageSource)}(SRG {@code m_6667_}) —— 受保护时回满血并取消;</li>
 *   <li>{@code kill()}(SRG {@code m_6074_}) —— 受保护时直接取消。</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class MercurySerpentDieGuardMixin {

    @Inject(method = "m_6667_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockDie(DamageSource source, CallbackInfo ci) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (self.level().isClientSide) {
                return;
            }
            if (MercurySerpentHandler.protectedFromDeath(self)) {
                MercurySerpentHandler.consumeShieldOnBlock(self);
                MercurySerpentHandler.resetDyingState(self);
                ci.cancel();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就允许死亡
        }
    }

    @Inject(method = "m_6074_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$allowKill(CallbackInfo ci) {
        // 1.6.31:/kill 走 kill(),这里**不再拦截**,否则玩家无法自杀
    }

    private void enigmaticenhance$unusedKill(CallbackInfo ci) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (self.level().isClientSide) {
                return;
            }
            if (MercurySerpentHandler.protectedFromDeath(self)) {
                MercurySerpentHandler.resetDyingState(self);
                ci.cancel();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就允许
        }
    }
}