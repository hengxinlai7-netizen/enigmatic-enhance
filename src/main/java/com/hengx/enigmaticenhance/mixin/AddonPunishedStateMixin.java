package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.CompatPatchRules;
import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 补丁 A:「允许两戒同戴」时,让扩展的「惩罚态」判定恒为 false。
 *
 * <h2>为什么修判据而不是修消费方</h2>
 * 扩展的 {@code SuperAddonHandler#isPunishedOne(LivingEntity)} 就是「两戒同戴 = 处于惩罚态」的唯一判据:
 * <pre>
 * public static boolean isPunishedOne(LivingEntity entity) {
 *     if (entity instanceof Player player)
 *         return hasBlessRing(player) &amp;&amp; SuperpositionHandler.isTheCursedOne(player);
 *     return false;
 * }
 * </pre>
 * 它被 {@code BlessRing.curioTick}(惩罚执行)、{@code MixinLightTexture}(客户端每帧覆盖 lightmap)等多处读取。
 * 本模组的设定是"允许同戴且不惩罚",那么在这个前提下惩罚态就应当恒为 false ——
 * 从判据源头修,所有消费方(渲染、惩罚、其它)就都一致了。
 *
 * <p>beta5 曾试图 {@code @Redirect} {@code MixinLightTexture} 里的 {@code CallbackInfo.cancel()},
 * 但那个类<b>本身是 mixin</b>,Mixin 报 {@code Cannot add target ...},注入失败 —— 已删除该做法。</p>
 *
 * <p>{@code isPunishedOne} 是 <b>static</b> 方法(javap 确认:{@code (LivingEntity)Z}),所以 handler 也是 static;
 * {@code remap = false}、{@code require = 0}、整体 {@code try/catch}。</p>
 */
@Mixin(targets = "auviotre.enigmatic.addon.handlers.SuperAddonHandler", remap = false)
public final class AddonPunishedStateMixin {

    private AddonPunishedStateMixin() {}

    @Inject(method = "isPunishedOne", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void enh$notPunishedWhenBothAllowed(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!RingConfig.compatSuppressAddonPunishedState()) {
                return;   // 补丁关掉:保持扩展原判定
            }
            if (!(entity instanceof Player player)) {
                return;
            }
            boolean hasBless = hasItem(player, RingConfig.ringItem());
            boolean hasCursed = hasItem(player, RingConfig.cursedRingItem());
            if (CompatPatchRules.shouldSuppressPunished(true, RingConfig.allowRingWithCursedRing(), hasBless, hasCursed)) {
                cir.setReturnValue(Boolean.FALSE);
            }
        }
        catch (Throwable ignored) {
            // 补丁自身绝不打断判定
        }
    }

    /** Curios 槽 + 背包都算「在身上」。 */
    private static boolean hasItem(Player player, net.minecraft.world.item.Item item) {
        if (item == null) {
            return false;
        }
        try {
            var inv = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            if (inv.isPresent()) {
                boolean[] found = { false };
                inv.get().getCurios().forEach((slot, handler) -> {
                    for (int i = 0; i < handler.getSlots() && !found[0]; i++) {
                        ItemStack stack = handler.getStacks().getStackInSlot(i);
                        if (!stack.isEmpty() && stack.is(item)) {
                            found[0] = true;
                        }
                    }
                });
                if (found[0]) {
                    return true;
                }
            }
        }
        catch (Throwable ignored) {
            // Curios 不在:继续扫背包
        }
        try {
            for (ItemStack stack : player.getInventory().items) {
                if (!stack.isEmpty() && stack.is(item)) {
                    return true;
                }
            }
            for (ItemStack stack : player.getInventory().offhand) {
                if (!stack.isEmpty() && stack.is(item)) {
                    return true;
                }
            }
        }
        catch (Throwable ignored) {
            // ignore
        }
        return false;
    }
}
