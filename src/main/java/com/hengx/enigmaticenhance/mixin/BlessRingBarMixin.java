package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.world.item.ItemStack;

/**
 * 隐藏救赎之戒的「假耐久条」。
 * <p>
 * {@code BlessRing} 覆写了原版 {@code Item#isBarVisible}(生产环境 SRG 名 <b>{@code m_142522_}</b>,
 * 描述符 {@code (Lnet/minecraft/world/item/ItemStack;)Z}),用
 * {@code Helper.getBlessAttribute(stack, "BlessBetrayal") > 0} 决定是否显示那条金色耐久条。
 * 这里在 HEAD 直接返回 false,玩家就再也看不到这条"耐久"在掉。
 * <p>
 * 注意:{@code m_142522_} 是<b>原版</b>方法,目标类里的名字是 SRG 名,所以必须
 * {@code remap = false} + 字面 SRG 名 + 完整描述符(与 research/ea-report.md 的结论一致)。
 * {@code getBarWidth}/{@code getBarColor}({@code m_142158_}/{@code m_142159_})不必拦,条子不显示就不起作用。
 */
@Mixin(targets = "auviotre.enigmatic.addon.contents.items.BlessRing")
public class BlessRingBarMixin {

    @Inject(method = "m_142522_(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$hideBetrayalBar(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (RingConfig.hideBetrayalBar()) {
            cir.setReturnValue(false);
        }
    }
}
