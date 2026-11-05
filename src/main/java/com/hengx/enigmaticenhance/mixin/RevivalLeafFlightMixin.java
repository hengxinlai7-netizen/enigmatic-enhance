package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.RevivalLeafStoneHandler;

import net.minecraft.world.entity.player.Player;

/**
 * 3.7.1:「复苏之叶」飞行开关的真正落点。
 *
 * <h2>反编译结论(这是关键,之前理解错了)</h2>
 * <p>神秘遗物扩展的 {@code auviotre.enigmatic.addon.contents.items.RevivalLeaf#handleFlight(Player)}
 * 逻辑是:</p>
 *
 * <pre>
 *   if (hasPlantBy(player)) {                    // 附近有植物
 *       if (!player.getAbilities().mayfly) {
 *           player.getAbilities().mayfly = true; // 直接授予"飞行能力"
 *           player.onUpdateAbilities();
 *       }
 *       flyMap.put(player, 5);                   // 5 tick 宽限
 *   } else {
 *       // 递减,归零时撤销 mayfly
 *   }
 * </pre>
 *
 * <p>所以这枚术石的飞行是<b>它自己授予 {@code mayfly}</b>,和「龙首」无关。
 * 之前把开关做成了"禁龙首飞行",结果关掉开关后它自己的飞行照样生效 ——
 * 这就是"关了还能飞"的原因。</p>
 *
 * <h2>本 Mixin</h2>
 * <p>玩家把这枚术石的开关关掉时,直接取消 {@code handleFlight},
 * 并且(非创造/旁观时)撤销它给过的 {@code mayfly},让飞行立刻停下。</p>
 */
@Mixin(targets = "auviotre.enigmatic.addon.contents.items.RevivalLeaf", remap = false)
public class RevivalLeafFlightMixin {

    @Inject(method = "handleFlight", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$gateFlight(Player player, CallbackInfo ci) {
        try {
            if (player == null) {
                return;
            }
            if (!RevivalLeafStoneHandler.flightDisabled(player)) {
                return;   // 开关是开的 ⇒ 交给扩展自己处理
            }
            // 开关关闭:撤销这枚术石给过的飞行能力(不动创造/旁观的原生飞行)
            if (!player.isCreative() && !player.isSpectator()
                && player.getAbilities().mayfly) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            ci.cancel();
        }
        catch (RuntimeException | LinkageError ignored) {
            // 判定失败 ⇒ 放行原逻辑
        }
    }
}
