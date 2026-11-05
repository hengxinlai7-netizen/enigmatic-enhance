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

    /** 本次调用之前玩家是否已经能飞(用于区分"这枚术石刚给的飞行"和"别的来源给的飞行")。 */
    private static final ThreadLocal<Boolean> HAD_FLIGHT_BEFORE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "handleFlight", at = @At("HEAD"), remap = false, require = 0)
    private void enigmaticenhance$recordFlight(Player player, CallbackInfo ci) {
        try {
            HAD_FLIGHT_BEFORE.set(player != null && player.getAbilities().mayfly);
        }
        catch (RuntimeException | LinkageError ignored) {
            HAD_FLIGHT_BEFORE.set(Boolean.FALSE);
        }
    }

    /**
     * 4.2.2:只在「这枚术石刚刚授予了飞行」时撤销它,别的来源(创造模式、龙首、其它模组)
     * 给的飞行一律不动 —— 之前是无条件撤销,把它们一起禁掉了。
     */
    @Inject(method = "handleFlight", at = @At("RETURN"), remap = false, require = 0)
    private void enigmaticenhance$revokeFlight(Player player, CallbackInfo ci) {
        try {
            boolean hadBefore = Boolean.TRUE.equals(HAD_FLIGHT_BEFORE.get());
            HAD_FLIGHT_BEFORE.remove();
            if (player == null || !RevivalLeafStoneHandler.flightDisabled(player)) {
                return;
            }
            if (hadBefore) {
                return;   // 原本就能飞 ⇒ 不是它给的,别动
            }
            if (player.isCreative() || player.isSpectator()) {
                return;
            }
            if (player.getAbilities().mayfly) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }}
