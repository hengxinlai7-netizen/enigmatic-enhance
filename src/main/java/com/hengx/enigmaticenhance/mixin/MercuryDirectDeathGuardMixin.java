package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.MercurySerpentHandler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * 4.11.1:拦下「直调死亡」。
 *
 * <p>有些模组(例如涟漪之篇的 {@code CovenantCombat})会在 {@code ServerPlayer#hurt} 内部
 * 直接 {@code setHealth(-N)} 再调 {@code die()},完全绕过 {@code LivingHurtEvent} ——
 * 事件层的次数盾、防洪之闸、灵魂方舟全都看不到,表现为"死一次→被复活→又死"的每秒循环。</p>
 *
 * <p>只要玩家还有层数:消耗 1 层、拉回满血、取消这次 {@code die()};层数为 0 或开关关闭时什么都不做。</p>
 *
 * <p>{@code m_6667_} = 生产环境 {@code ServerPlayer#die(DamageSource)} 的 SRG 名;
 * {@code remap = false} + {@code require = 0};{@code die} 是实例方法 ⇒ handler 实例方法。</p>
 */
@Mixin(ServerPlayer.class)
public final class MercuryDirectDeathGuardMixin {

    @Inject(method = "m_6667_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$guardDirectDie(DamageSource source, CallbackInfo ci) {
        try {
            ServerPlayer self = (ServerPlayer) (Object) this;
            if (MercurySerpentHandler.guardDirectDeath(self)) {
                ci.cancel();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 守卫失败就交给原版流程
        }
    }
}
