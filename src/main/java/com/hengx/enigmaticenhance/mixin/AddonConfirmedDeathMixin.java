package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.DebugLog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.RingConfig;
import com.hengx.enigmaticenhance.Superposition;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * Stops the addon death handler for players wearing the cursed ring plus the redemption ring.
 *
 * AddonEventHandler.onConfirmedDeath does, among other things:
 *   - lightning bolt at the player (no HarmlessThunder tag -> sets fire);
 *   - BLESS_STONE stacks setCount(0);
 *   - POSTMORTAL_POSSESSIONS.put(BLESS_STONE) - the flag that later makes onCurioDrops
 *     wipe every curio slot (and add DESTROY rules for the cursed ring).
 * Cancelling at HEAD removes the death lightning and keeps armor/curios intact.
 */
@Mixin(targets = "auviotre.enigmatic.addon.handlers.AddonEventHandler")
public class AddonConfirmedDeathMixin {

    @Inject(method = "onConfirmedDeath(Lnet/minecraftforge/event/entity/living/LivingDeathEvent;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$keepGearOnDeath(LivingDeathEvent event, CallbackInfo ci) {
        if (!RingConfig.allowRingWithCursedRing()) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (Superposition.isPunishedOne(player) || Superposition.hasCurio(player, RingConfig.ringItem())
            && Superposition.hasCurio(player, RingConfig.cursedRingItem())) {
            ci.cancel();
            // 默认静默:debugLog 关闭时不写日志(取消死亡的业务逻辑照常)。
            if (RingConfig.debugLog()) {
                DebugLog.info("[惩罚拦截] 已跳过扩展的死亡处理(七咒+救赎同戴),护甲/饰品不会被清");
            }
        }
    }
}