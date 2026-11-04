package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.UncertainFogHandler;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 1.2.11b:把「瓶装不定之雾」的禁传送做到真正生效的地方。
 *
 * <p>原版末影人（以及潜影贝、部分模组 BOSS）的瞬移走的是
 * {@code LivingEntity#randomTeleport(double,double,double,boolean)}（SRG {@code m_20984_}），
 * <b>不发任何 Forge 事件</b> —— 所以 1.2.11a 只拦 {@code EntityTeleportEvent} 时末影人照样瞬移。
 * 这里在方法入口判断"附近有没有带着不定之雾的玩家"，有就直接返回 false（这次瞬移失败）。</p>
 *
 * <p>只对**非玩家**的活体生效；玩家自己的传送不受影响。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRandomTeleportMixin {

    @Inject(method = "m_20984_", at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$blockRandomTeleport(double x, double y, double z, boolean respectBlocks,
        CallbackInfoReturnable<Boolean> cir) {
        UncertainFogHandler.noteAttempt();
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player) {
            return;
        }
        if (UncertainFogHandler.isSuppressedNearby(self)) {
            UncertainFogHandler.noteBlocked();
            cir.setReturnValue(false);
        }
    }
}
