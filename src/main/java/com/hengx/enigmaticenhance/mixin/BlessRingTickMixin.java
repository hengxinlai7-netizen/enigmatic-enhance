package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.DebugLog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.RingConfig;
import com.hengx.enigmaticenhance.Superposition;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * Stops the whole "Ring of Seven Curses + Ring of Redemption" punishment.
 *
 * BlessRing.curioTick runs the punishment every tick while the player is PUNISHED
 * (isPunishedOne = hasBlessRing && isTheCursedOne) or wears both rings:
 *   - counter "Punishment" +1 each tick;
 *   - every 20 ticks: lightning bolt (setDamage 1, cause = player) + Level.explode(2.0F, fire=true)
 *     -> that is the fire damage (in_fire / on_fire, 0.8);
 *   - every 20 ticks: setHealth(maxHealth), SoulCrystal stolen, Inventory.clearContent()
 *     (items + armor + offhand) and every curio slot emptied except the two rings;
 *   - after Punishment > 100 (~5s): the redemption ring is wiped, clearContent again,
 *     XP reset, 1000x maxHealth hurt, explode(32, fire=true) and kill().
 *
 * Cancelling at HEAD removes all of it (the legitimate regeneration while
 * SavedSevenCurseLevel > 1 also stops, which is the intended trade-off here).
 */
@Mixin(targets = "auviotre.enigmatic.addon.contents.items.BlessRing")
public class BlessRingTickMixin {

    @Inject(method = "curioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$stopPunishment(SlotContext context, ItemStack stack, CallbackInfo ci) {
        if (!RingConfig.allowRingWithCursedRing()) {
            return;
        }
        if (!(context.entity() instanceof Player)) {
            return;
        }
        Player player = (Player) context.entity();
        if (Superposition.isPunishedOne(player) || Superposition.hasCurio(player, RingConfig.ringItem())
            && Superposition.hasCurio(player, RingConfig.cursedRingItem())) {
            ci.cancel();
            // curioTick 每 tick 都会走进这里,所以默认静默:debugLog 关闭时一行都不写。
            if (RingConfig.debugLog()) {
                DebugLog.info("[惩罚拦截] 已跳过救赎之戒的惩罚 tick(七咒+救赎同戴)");
            }
        }
    }
}