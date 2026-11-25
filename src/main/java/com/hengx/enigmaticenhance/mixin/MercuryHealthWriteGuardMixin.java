package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.MercurySerpentHandler;
import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 4.11.1:挡掉「把玩家血量直接写成 ≤0」的致死路径。
 *
 * <p>涟漪之篇的 {@code CovenantCombat#around} 会直接 {@code setHealth(-407034)} 再 {@code die()},
 * 这种调用不经过 {@code LivingHurtEvent};本守卫在 {@code setHealth} 入口截住它:
 * 只要玩家还有层数,就写回满血并取消这次写入(<b>不扣层数</b> —— 层数由 {@code die()} 那次的守卫消耗,不重复扣)。</p>
 *
 * <h2>ALLOW_WRITE 旁路</h2>
 * 守卫自己写血时必须走 {@link MercurySerpentHandler#setHealthBypass}(内部置 {@code ALLOW_WRITE}),
 * 否则会再次进入本注入被自己拦住 / 递归。
 *
 * <p>{@code m_21153_} = 生产环境 {@code LivingEntity#setHealth(float)} 的 SRG 名;
 * {@code remap = false} + {@code require = 0};{@code setHealth} 是实例方法 ⇒ handler 实例方法。</p>
 */
@Mixin(LivingEntity.class)
public final class MercuryHealthWriteGuardMixin {

    @Inject(method = "m_21153_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$guardNegativeHealth(float newHealth, CallbackInfo ci) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (!(self instanceof Player player) || player.level().isClientSide) {
                return;
            }
            if (MercurySerpentHandler.bypassingWrite()) {
                return;   // 我们自己写的血,放行
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;   // 只在服务端玩家上生效(客户端零参与)
            }
            if (!RingConfig.mercuryGuardDirectDeath() || !RingConfig.mercurySerpentEnabled()) {
                return;
            }
            if (!MercurySerpentHandler.shouldGuardSetHealth(newHealth, MercurySerpentHandler.charges(serverPlayer))) {
                return;
            }
            MercurySerpentHandler.setHealthBypass(serverPlayer, serverPlayer.getMaxHealth());
            ci.cancel();
        }
        catch (RuntimeException | LinkageError ignored) {
            // 守卫失败就交给原版流程
        }
    }
}
