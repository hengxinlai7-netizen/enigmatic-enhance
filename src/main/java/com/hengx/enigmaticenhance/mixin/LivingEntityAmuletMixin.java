package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.EarthAmuletData;
import com.hengx.enigmaticenhance.EarthAmuletHandler;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 「大地护符」的两处 **vanilla 行为** 注入(1.1.24)。
 * <p>
 * 1) {@code m_6336_}({@code getMobType},1.20.1 SRG,已用 {@code javap}/{@code joined.tsrg} 核实):
 * 戴僵尸头的护符时把**玩家**的 MobType 报成 {@code UNDEAD}。
 * 这一个点就同时覆盖了:
 * <ul>
 * <li>{@code LivingEntity#isInvertedHealAndHarm()}(它的实现就是 {@code getMobType() == UNDEAD})
 *     ⇒ 治疗/伤害药水效果反转;</li>
 * <li>亡灵相关判定(例如附魔"亡灵杀手"对玩家生效)⇒ "玩家被视为亡灵生物"。</li>
 * </ul>
 * 2) {@code m_7301_}({@code canBeAffected}):戴凋灵骷髅头的护符时直接拒绝凋零效果的施加
 * (凋零不是"减少伤害",只能在施加/清除处处理)。
 * <p>
 * 两个注入都是 **HEAD + cancellable**,只对玩家、只在佩戴时改写;没装 Curios / 没戴护符 /
 * 没解锁对应头颅 ⇒ 原样返回,行为与 vanilla 完全一致。异常一律吞掉(绝不因饰品判定崩游戏)。
 * {@code remap = false} 与本项目其它 mixin 一致(运行期是 SRG 名)。
 */
@Mixin(LivingEntity.class)
public class LivingEntityAmuletMixin {

    @Inject(method = "m_6336_", at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$undeadPlayerMobType(CallbackInfoReturnable<MobType> cir) {
        if (!((Object) this instanceof Player player)) {
            return;
        }
        try {
            ItemStack amulet = EarthAmuletData.equippedAmulet(player);
            if (!amulet.isEmpty() && EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_ZOMBIE)) {
                cir.setReturnValue(MobType.UNDEAD);
            }
        }
        catch (Throwable ignored) {
            // 饰品判定失败 ⇒ 保持 vanilla 行为
        }
    }

    @Inject(method = "m_7301_", at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$witherImmune(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        if (instance == null || instance.getEffect() != MobEffects.WITHER) {
            return;
        }
        if (!((Object) this instanceof Player player)) {
            return;
        }
        try {
            ItemStack amulet = EarthAmuletData.equippedAmulet(player);
            if (!amulet.isEmpty() && EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_WITHER_SKELETON)) {
                cir.setReturnValue(false);
                EarthAmuletHandler.onWitherBlocked(player);
            }
        }
        catch (Throwable ignored) {
            // 同上:保持 vanilla 行为
        }
    }
}
