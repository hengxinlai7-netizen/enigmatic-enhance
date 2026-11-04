package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.DemonBloodChaliceHandler;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/**
 * 「恶魔血杯」1.1.28d 补的三处**通用**封印点(原来的 4 处在 {@code MobEffectSealMixin})。
 *
 * <h2>1) {@code MobEffectInstance#applyEffect} —— 让"每 tick 型"对所有效果都成立</h2>
 * 原来拦的是 {@code MobEffect#applyEffectTick}(基类)。**只要模组写了自定义效果,就一定会覆写
 * {@code applyEffectTick}**(原版基类里那串 {@code this == MobEffects.X} 的身份判断永远匹配不到自定义
 * 效果实例),覆写之后那次调用<b>不经过基类</b> ⇒ 自定义效果的每 tick 逻辑照样生效 ——
 * 这正是玩家看到的"有几个药水效果正常生效了"。
 * 现在改成拦<b>调用方</b>:{@code MobEffectInstance.tick} 里唯一的分发点
 * {@code this.applyEffect(entity)}(SRG {@code m_19550_}),它在调用任何效果的
 * {@code applyEffectTick} 之前就被取消,所以原版/模组/覆写全都封住,效果的时长仍按原版自然递减。
 *
 * <h2>2) {@code LivingEntity#onEffectAdded} / {@code #onEffectUpdated} 里的属性修饰符调用</h2>
 * 属性修饰符是这两个方法里调 {@code MobEffect#addAttributeModifiers}/{@code removeAttributeModifiers}
 * 挂上去的;模组覆写这两个方法后同样绕过基类。所以改用 {@code @Redirect} 只替换那两个调用:
 * 佩戴者身上直接不调用(不挂修饰符),其它一切照旧 —— 特别注意**不能整体取消这两个方法**,
 * 因为它们还负责 {@code effectsDirty}(效果列表同步)与乘客同步,整体取消会让状态栏不同步。
 *
 * <h2>1.1.28f:不再改 hasEffect/getEffect</h2>
 * 之前在这里还有一组"读取型白名单"注入(把 16 个靠 hasEffect 生效的原版效果对佩戴者报成"没有")。
 * 用户拍板保留 {@code hasEffect = true}(保证别的模组能正常检测到 buff),所以那组注入已删除:
 * 这 16 个效果现在**会正常生效**,这是明确的取舍(其余每 tick 型/属性型效果仍然被封印)。
 *
 * <p>全部 {@code remap = false} + {@code require = 0}、类登记在
 * {@code enigmaticenhance.optional.mixins.json}({@code required:false} + {@code defaultRequire:0})。</p>
 */
@Mixin(LivingEntity.class)
public class LivingEntityEffectSealMixin {

    // ------------------------------------------------------------------ 1) 每 tick 型:拦调用方
    //      (见 MobEffectInstanceSealMixin:applyEffect 属于 MobEffectInstance,不在这个类里)

    // ------------------------------------------------------------------ 2) 属性型:拦 onEffectAdded/Updated 里的两个调用

    @Redirect(method = "m_142540_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffect;m_6385_"
            + "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V"),
        remap = false, require = 0)
    private void enigmaticenhance$sealAddedAttributes(MobEffect effect, LivingEntity entity, AttributeMap attributes,
        int amplifier) {
        if (!DemonBloodChaliceHandler.shouldSeal(entity, effect, amplifier)) {
            effect.addAttributeModifiers(entity, attributes, amplifier);
        }
    }

    @Redirect(method = "m_141973_(Lnet/minecraft/world/effect/MobEffectInstance;ZLnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffect;m_6386_"
            + "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V"),
        remap = false, require = 0)
    private void enigmaticenhance$sealUpdatedRemoval(MobEffect effect, LivingEntity entity, AttributeMap attributes,
        int amplifier) {
        if (!DemonBloodChaliceHandler.shouldSeal(entity, effect, amplifier)) {
            effect.removeAttributeModifiers(entity, attributes, amplifier);
        }
    }

    @Redirect(method = "m_141973_(Lnet/minecraft/world/effect/MobEffectInstance;ZLnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffect;m_6385_"
            + "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V"),
        remap = false, require = 0)
    private void enigmaticenhance$sealUpdatedAddition(MobEffect effect, LivingEntity entity, AttributeMap attributes,
        int amplifier) {
        if (!DemonBloodChaliceHandler.shouldSeal(entity, effect, amplifier)) {
            effect.addAttributeModifiers(entity, attributes, amplifier);
        }
    }

}
