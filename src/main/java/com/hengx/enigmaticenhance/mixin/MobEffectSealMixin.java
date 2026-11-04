package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.DemonBloodChaliceHandler;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.Entity;

/**
 * 「恶魔血杯」的核心:佩戴者身上的药水效果**保留、但不起任何作用**(1.1.28a)。
 *
 * <h2>为什么是这 4 个方法</h2>
 * <ul>
 * <li>{@code applyEffectTick}(SRG {@code m_6742_}):<b>每 tick 型</b>效果的唯一执行入口
 *     ({@code LivingEntity#tickEffects} → {@code MobEffectInstance#tick} → {@code applyEffect} → 这里)。
 *     拦掉它 = 再生不回血、中毒/凋零不掉血、饥饿不加重……而效果的时长仍按原版自然递减;</li>
 * <li>{@code applyInstantenousEffect}(SRG {@code m_19461_}):<b>瞬时型</b>(瞬间治疗/瞬间伤害/饱和)
 *     不进效果列表,喝下去那一刻直接走这里,所以只能在执行点拦(否则"佩戴时喝治疗药水"会回血);</li>
 * <li>{@code addAttributeModifiers}(SRG {@code m_6385_}):<b>属性型</b>效果(速度/力量/抗性/生命提升/挖掘疲劳……)
 *     的属性修饰符是在<b>效果被加上/更新那一刻</b>由 {@code LivingEntity#onEffectAdded/onEffectUpdated}
 *     一次性挂上的,不是每 tick 重算 —— 拦掉它,效果就永远不会挂上修饰符;</li>
 * <li>{@code removeAttributeModifiers}(SRG {@code m_6386_}):对称地也短路,避免"摘除动作"和我们的
 *     {@link DemonBloodChaliceHandler#purify} 打架(摘除由我们自己按效果的 UUID 精确做);
 *     真正需要移除时(卸下饰品恢复)走 {@code restore()} 的绕过路径。</li>
 * </ul>
 *
 * <h2>为什么不会引入"无效状态"</h2>
 * 这 4 个注入只<b>跳过执行</b>,不写任何数据:不碰 {@code MobEffect} 实例的字段、不碰效果列表、
 * 不新增 {@code MobEffect}/{@code MobEffectCategory}、不给玩家挂属性、不写 NBT。
 * 所以 {@code getActiveEffects()} / {@code hasEffect} / HUD / {@code isBeneficial()} 全都与没戴时完全一样,
 * 别的模组"检测到 buff"的逻辑照常命中,只是这个 buff 产生不了实际结果。
 *
 * <h2>兼容性</h2>
 * 目标类 {@code MobEffect} 是原版类,方法名按 <b>SRG + {@code remap = false}</b> 写(与
 * {@code BlockGetDropsMixin} / {@code EnchantmentHelperSweepMixin} 同一套项目约定:本模组的 jar 只跑在
 * Forge 生产运行期,那里方法名就是 SRG)。注入全部 {@code require = 0}、且整体登记在
 * {@code enigmaticenhance.optional.mixins.json}({@code required:false} + {@code injectors.defaultRequire:0}):
 * 万一将来目标方法改名,这里只是"不再封印",绝不会让游戏崩。
 * <p>
 * 已知边界:如果某个模组自己写了 {@code MobEffect} 子类并<b>覆写</b>了 {@code applyEffectTick}
 * 且不调 {@code super},那次执行不经过这里(原版与整合包里现有模组都没有这种写法,
 * 见 1.1.28a 报告)。
 */
@Mixin(MobEffect.class)
public class MobEffectSealMixin {

    /** 每 tick 型:直接跳过(效果还在,只是不产生作用)。 */
    @Inject(method = "m_6742_(Lnet/minecraft/world/entity/LivingEntity;I)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$sealEffectTick(LivingEntity entity, int amplifier, CallbackInfo ci) {
        try {
            if (DemonBloodChaliceHandler.shouldSeal(entity, (MobEffect) (Object) this, amplifier)) {
                ci.cancel();
            }
        }
        catch (Throwable ignored) {
            // 判定失败 ⇒ 保持原版行为
        }
    }

    /** 瞬时型(瞬间治疗/伤害/饱和):喝下去那一刻直接跳过。 */
    @Inject(method = "m_19461_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;"
        + "Lnet/minecraft/world/entity/LivingEntity;ID)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$sealInstantEffect(Entity source, Entity indirectSource, LivingEntity entity,
        int amplifier, double health, CallbackInfo ci) {
        try {
            MobEffect self = (MobEffect) (Object) this;
            if (DemonBloodChaliceHandler.shouldSeal(entity, self, amplifier)) {
                ci.cancel();
                return;
            }
            // 1.2.13c:三件套"正面效果只按 I 级生效" —— 瞬时型(瞬治/瞬伤/饱和)在这里用 0 级重放一次
            if (DemonBloodChaliceHandler.shouldLimitBeneficial(entity, self, amplifier)) {
                ci.cancel();
                boolean previous = DemonBloodChaliceHandler.beginBypass();
                try {
                    self.applyInstantenousEffect(source, indirectSource, entity, 0, health);
                }
                finally {
                    DemonBloodChaliceHandler.endBypass(previous);
                }
            }
        }
        catch (Throwable ignored) {
            // 判定失败 ⇒ 保持原版行为
        }
    }

    /** 属性型:不挂任何修饰符(否则速度/力量/生命提升会真的生效)。 */
    @Inject(method = "m_6385_(Lnet/minecraft/world/entity/LivingEntity;"
        + "Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$sealAddAttributeModifiers(LivingEntity entity, AttributeMap attributes,
        int amplifier, CallbackInfo ci) {
        try {
            if (DemonBloodChaliceHandler.shouldSeal(entity, (MobEffect) (Object) this, amplifier)) {
                ci.cancel();
            }
        }
        catch (Throwable ignored) {
            // 同上
        }
    }

    /** 属性型(对称):移除也短路,摘除动作统一由 {@code DemonBloodChaliceHandler#purify} 精确处理。 */
    @Inject(method = "m_6386_(Lnet/minecraft/world/entity/LivingEntity;"
        + "Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$sealRemoveAttributeModifiers(LivingEntity entity, AttributeMap attributes,
        int amplifier, CallbackInfo ci) {
        try {
            if (DemonBloodChaliceHandler.shouldSeal(entity, (MobEffect) (Object) this, amplifier)) {
                ci.cancel();
            }
        }
        catch (Throwable ignored) {
            // 同上
        }
    }
}
