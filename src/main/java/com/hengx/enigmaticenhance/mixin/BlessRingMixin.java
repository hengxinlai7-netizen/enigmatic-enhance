package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.DebugLog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 允许**七咒之戒与救赎之戒一起佩戴**。
 * <p>
 * 反编译 {@code BlessRing#canEquip}:
 * <pre>
 * if (super.canEquip(...)) {
 *     if (entity instanceof Player player) {
 *         return !SuperpositionHandler.isTheCursedOne(player)          // 戴七咒之戒 → 为真 → 禁止
 *             &amp;&amp; !SuperpositionHandler.hasCurio(player, BROKEN_RING);
 *     }
 *     return false;
 * }
 * return false;
 * </pre>
 * 本 mixin 在入口处判断:配置允许 + 玩家确实带着对应的"对立饰品"时,直接放行。
 * 用 {@code @Inject(cancellable)} 而不是改写逻辑,配置关掉即恢复原版行为。
 * <p>
 * {@code required=false}(见 mixins.json):没装神秘遗物扩展时只是一条警告。
 */
@Mixin(targets = "auviotre.enigmatic.addon.contents.items.BlessRing")
public class BlessRingMixin {

    /** 玩家(curio 栏)是否带着某个物品;模组缺失时返回 false。 */
    private static boolean enigmaticenhance$hasCurio(Player player, Item item) {
        if (item == null) {
            return false;
        }
        try {
            Class<?> handler = Class.forName("com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler");
            Object result = handler.getMethod("hasCurio", LivingEntity.class, Item.class).invoke(null, player, item);
            return result instanceof Boolean b && b;
        }
        catch (Throwable t) {
            return false;
        }
    }

    @Inject(method = "canEquip", at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$allowWithCursedRing(
        top.theillusivec4.curios.api.SlotContext context, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {

        if (!(context.entity() instanceof Player player)) {
            return;
        }
        if (RingConfig.allowRingWithCursedRing() && enigmaticenhance$hasCurio(player, RingConfig.cursedRingItem())) {
            if (RingConfig.debugLog()) {
                DebugLog.info(
                    "[戒指放行] {} 同时带着七咒之戒,允许佩戴救赎之戒", player.getGameProfile().getName());
            }
            cir.setReturnValue(true);
            return;
        }
        if (RingConfig.allowRingWithBrokenRing() && enigmaticenhance$hasCurio(player, RingConfig.brokenRingItem())) {
            if (RingConfig.debugLog()) {
                DebugLog.info(
                    "[戒指放行] {} 同时带着破损之戒,允许佩戴救赎之戒", player.getGameProfile().getName());
            }
            cir.setReturnValue(true);
        }
    }
}
