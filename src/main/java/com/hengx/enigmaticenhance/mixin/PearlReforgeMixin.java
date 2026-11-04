package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.PearlHandler;
import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.world.entity.player.Player;

/**
 * 附魔师珍珠:神化重铸台不消耗素材/符石。
 *
 * <p>神化(Apotheosis)的重铸消耗分三处:</p>
 * <ul>
 *   <li>{@code getMatCost(int)} —— 珍宝材料;</li>
 *   <li>{@code getSigilCost(int)} —— 新生符石;</li>
 * </ul>
 * 珍珠在场时把这两处都返回 0 即可(经验消耗保持原样)。
 */
@Mixin(targets = "dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu")
public abstract class PearlReforgeMixin {

    @Shadow(remap = false)
    @Final
    protected Player player;

    /** 1.6.43:材料数量(无参 getMatCount)同样归零。 */
    @Inject(method = "getMatCount()I", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$freeMatCount(CallbackInfoReturnable<Integer> cir) {
        try {
            if (!RingConfig.pearlFreeReforge()) {
                return;
            }
            if (this.player == null || !PearlHandler.pearlPresent(this.player)) {
                return;
            }
            cir.setReturnValue(0);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @Inject(method = "getMatCost(I)I", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$freeMatReforge(int slot, CallbackInfoReturnable<Integer> cir) {
        try {
            if (!RingConfig.pearlFreeReforge()) {
                return;
            }
            if (this.player == null || !PearlHandler.pearlPresent(this.player)) {
                return;
            }
            cir.setReturnValue(0);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @Inject(method = "getSigilCost(I)I", at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$freeSigilReforge(int slot, CallbackInfoReturnable<Integer> cir) {
        if (!RingConfig.pearlFreeReforge()) {
            return;
        }
        if (this.player == null || !PearlHandler.pearlPresent(this.player)) {
            return;
        }
        cir.setReturnValue(0);
    }
}
