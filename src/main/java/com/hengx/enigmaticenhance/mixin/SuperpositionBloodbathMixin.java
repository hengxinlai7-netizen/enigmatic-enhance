package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.DemonBloodChaliceHandler;

import net.minecraft.world.entity.player.Player;

/**
 * 联动一(1.1.28a):佩戴「恶魔血杯」时,<b>每有 1 个正面效果</b>⇒「血战沙场之证」的
 * <b>已损失生命判定 +1%</b>。
 *
 * <h2>为什么注入这个方法</h2>
 * 「血战沙场之证」({@code enigmaticlegacy:berserk_charm},类 {@code BerserkEmblem})的"已损失生命判定"
 * 就是本体自己的 {@code SuperpositionHandler#getMissingHealthPool(Player)}:
 * <pre>
 * (player.getMaxHealth() - min(player.getHealth(), player.getMaxHealth())) / player.getMaxHealth()
 * </pre>
 * 反编译核实(1.1.28a,Enigmaticlegacy-2.30.1-1)它的全部消费点只有 3 处,且都属于这件饰品:
 * <ul>
 * <li>{@code BerserkEmblem#createAttributeMap(Player)} —— 攻击伤害/攻击速度/移动速度/伤害抗性
 *     四项加成全部乘以这个值(佩戴时每 tick 重算);</li>
 * <li>{@code BerserkEmblem#appendHoverText} —— tooltip 里显示的百分比;</li>
 * <li>{@code EnigmaticEventHandler} 的受击结算 —— 额外伤害也乘这个值。</li>
 * </ul>
 * 所以只在这一个方法上加法,四条联动数值(含 tooltip 显示)自然全部一致,不需要碰本体任何代码。
 *
 * <h2>边界</h2>
 * 只对**玩家**、只对**佩戴着恶魔血杯**时加法;没戴 ⇒ 原样返回(与不打补丁逐位一致)。
 * 加完夹到 {@link DemonBloodChaliceHandler#MAX_MISSING_HEALTH}(1.0 = 100% 已损失),
 * 所以永远不会超过原公式的理论上限。
 * <p>
 * 第三方类,{@code required=false} + {@code require=0}:本整合包若没装神秘遗物,这条注入只是不生效。
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler")
public class SuperpositionBloodbathMixin {

    @Inject(method = "getMissingHealthPool(Lnet/minecraft/world/entity/player/Player;)F",
        at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$bloodbathBonus(Player player, CallbackInfoReturnable<Float> cir) {
        try {
            DemonBloodChaliceHandler.BLOODBATH_HOOK_OK = true;   // 探针:这条混入真的跑了
            float bonus = DemonBloodChaliceHandler.extraMissingHealth(player);
            if (bonus <= 0.0F) {
                return;
            }
            float base = cir.getReturnValueF();
            // 1.2.7a:不再夹到 1.0 —— "每个正面效果 +1% 已损失生命"要能真叠出来。
            // 血战沙场之证锁血时已损失本来就在 95%~100%,夹 1.0 会让这项加成完全看不出来。
            float total = base + bonus;
            cir.setReturnValue(total);
        }
        catch (Throwable ignored) {
            // 任何意外 ⇒ 保持本体原值
        }
    }
}
