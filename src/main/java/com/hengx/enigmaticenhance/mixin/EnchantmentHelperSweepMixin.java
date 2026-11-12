package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.DebugLog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.RingConfig;
import com.hengx.enigmaticenhance.Superposition;

import net.minecraft.world.entity.LivingEntity;

/**
 * 撤掉「万钧之护卷轴」原本的"+100% 横扫伤害"。
 * <p>
 * 反编译依据:扩展的 {@code auviotre.enigmatic.addon.mixin.MixinEnchantmentHelper#getSweeping} 注入
 * {@code EnchantmentHelper#getSweepingDamageRatio}(SRG <b>{@code m_44821_(Lnet/minecraft/world/entity/LivingEntity;)F}</b>,
 * 原版实现 = {@code level > 0 ? level/(level+1) : 0},最高 0.75)的 RETURN,对佩戴者做
 * {@code max(1.0F, 原值)} —— 这就是"横扫攻击造成 100% 伤害"。
 * <p>
 * <b>关键点 1:必须抢在它前面执行。</b>把 prodserver 的 {@code -Dmixin.debug.export=true} 导出产物反编译可见,
 * 转换后的 {@code m_44821_} 里两个回调各自是一个私有 handler 方法,按 mixin priority 升序依次调用:
 * <pre>
 *  handler$zzk000$getSweeping(entity, cir)                     // 扩展(priority 1000)
 *  if (cir.isCancelled()) return cir.getReturnValueF();        // setReturnValue 会置为取消,直接 freturn
 *  handler$zdg000$enigmaticenhance$...(entity, cir)            // priority 比它大的回调根本轮不到
 * </pre>
 * 所以本 mixin 的 priority 设成 <b>500</b>(小于 1000,先应用、先执行):在 RETURN 处"提前定值并取消",
 * 扩展那次 {@code max(1.0, ...)} 便不再执行,方法直接返回此刻的原版公式值。
 * <p>
 * <b>关键点 2:不能反过来去改扩展的 mixin 类。</b>Mixin 0.8.5 明确拒绝这件事:
 * <pre>java.lang.IllegalArgumentException: Cannot add target .../MixinEnchantmentHelper ... because the target is a mixin</pre>
 * (实测直接抛 InvalidMixinException,所以只能从目标方法这一侧下手。)
 * <ul>
 * <li>总开关 {@code scaleOfProtectionEnabled=false} 时完全不动作,扩展原版横扫 100% 照旧;</li>
 * <li>没戴卷轴时完全不动作;</li>
 * <li>代价:priority 比 500 更晚的其它模组对**这一个方法**的 RETURN 回调,在戴卷轴时会被跳过(已在报告说明)。</li>
 * </ul>
 * 卷轴的伤害来源于是换成 {@code generic.attack_damage} 的百分比修饰符
 * (见 {@code CurioCompat} / {@code ScrollHandler#attackDamageModifier()})。
 * <p>
 * <b>1.1.14 补充</b>:横扫倍率归 0 之后原版横扫伤害恒为 1.0,而 {@code LivingEntity#hurt} 在无敌帧里对
 * {@code 伤害 <= 上次受伤} 直接 {@code return false} —— 横扫不再对附近目标产生伤害事件,扩展的落雷电荷
 * 也就再也扩散不出去(详见 {@code LightningChargeSpread} 类注释与 1.1.14 报告)。
 * 电荷扩散默认由 {@code LightningChargeSpread} 补上;想让横扫完全回到 1.1.12 之前的手感,把
 * {@code scaleOfProtectionRestoreSweepBoost} 设 true 即可(本 mixin 直接放行,恢复扩展的 {@code max(1.0, …)})。
 */
@Mixin(targets = "net.minecraft.world.item.enchantment.EnchantmentHelper", priority = 500)
public class EnchantmentHelperSweepMixin {

    @Inject(method = "m_44821_(Lnet/minecraft/world/entity/LivingEntity;)F",
        at = @At("RETURN"), cancellable = true, remap = false)
    private static void enigmaticenhance$keepVanillaSweeping(LivingEntity entity, CallbackInfoReturnable<Float> cir) {
        if (!RingConfig.scaleEnabled()) {
            return; // 总开关关 = 保留扩展原版(横扫 100%)
        }
        if (RingConfig.scaleRestoreSweepBoost()) {
            return; // 用户选择"恢复扩展原本的横扫 100% 伤害":这里不动作,交给扩展自己的 max(1.0, ...)
        }
        if (Superposition.getCurioStack(entity, RingConfig.thunderScrollItem()).isEmpty()) {
            return; // 没戴卷轴,与本功能无关
        }
        float vanilla = cir.getReturnValueF();
        cir.setReturnValue(vanilla); // 提前定值 = 取消回调,扩展的 max(1.0, ...) 不会再跑
        if (RingConfig.debugLog()) {
            DebugLog.info("[卷轴横扫] 横扫伤害比例保持原版 {} (已阻止扩展抬到 1.0)", vanilla);
        }
    }
}