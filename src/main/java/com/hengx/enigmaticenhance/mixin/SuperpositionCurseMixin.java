package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.DemonBloodChaliceHandler;

import net.minecraft.world.entity.player.Player;

/**
 * 联动二(1.1.28a):佩戴「恶魔血杯」时,<b>每有 1 个负面效果</b>⇒ 视为<b>1 个诅咒附魔</b>。
 *
 * <h2>为什么注入这个方法</h2>
 * 「千咒卷轴」({@code enigmaticlegacy:cursed_scroll},类 {@code CursedScroll})自己没有任何计数逻辑,
 * 它的三条加成全部读本体这一个入口:{@code SuperpositionHandler#getCurseAmount(Player)}
 * (反编译核实 1.1.28a,Enigmaticlegacy-2.30.1-1):
 * <ul>
 * <li>{@code EnigmaticEventHandler#onBreakSpeed}:挖掘速度 = 加成 × 诅咒数;</li>
 * <li>{@code EnigmaticEventHandler#onLivingHurt}:攻击伤害 = 加成 × 诅咒数;</li>
 * <li>{@code EnigmaticEventHandler#onLivingHeal}:再生量 = 加成 × 诅咒数;</li>
 * <li>{@code CursedScroll#appendHoverText}:tooltip 里显示的诅咒数也是它。</li>
 * </ul>
 * 原实现是遍历全身装备(主手/副手/盔甲/全部饰品)累计每个物品的诅咒附魔数,七咒之戒一次算 7 个。
 * 我们只在**返回值上加负数效果的数量**:不新增附魔、不改任何物品、不改那三条公式,
 * 所以千咒卷轴的加成会自然按"多了 N 个诅咒"结算。
 * <p>
 * 只对**玩家**、只对**佩戴着恶魔血杯**时加法;没戴 ⇒ 原样返回。
 * 第三方类,{@code required=false} + {@code require=0}:没装神秘遗物时这条注入只是不生效。
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler")
public class SuperpositionCurseMixin {

    @Inject(method = "getCurseAmount(Lnet/minecraft/world/entity/player/Player;)I",
        at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$curseBonus(Player player, CallbackInfoReturnable<Integer> cir) {
        try {
            DemonBloodChaliceHandler.CURSE_HOOK_OK = true;   // 探针
            int bonus = DemonBloodChaliceHandler.extraCurses(player);
            if (bonus <= 0) {
                return;
            }
            cir.setReturnValue(cir.getReturnValueI() + bonus);
        }
        catch (Throwable ignored) {
            // 任何意外 ⇒ 保持本体原值
        }
    }
}
