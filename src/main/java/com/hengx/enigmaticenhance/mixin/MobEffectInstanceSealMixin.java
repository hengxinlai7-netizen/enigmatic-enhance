package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.DemonBloodChaliceHandler;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「恶魔血杯」1.1.28d:**每 tick 型**效果的通用封印点。
 *
 * <h2>为什么拦这里,而不是 {@code MobEffect#applyEffectTick}</h2>
 * {@code MobEffectInstance#tick(LivingEntity, Runnable)}(SRG {@code m_19552_})按原版节奏递减时长,
 * 到点就调用本类的 {@code applyEffect(entity)}(SRG {@code m_19550_}),后者再转发给
 * {@code effect.applyEffectTick(entity, amplifier)}。
 * <ul>
 * <li>原版效果没有覆写 {@code applyEffectTick}(基类里是一串 {@code this == MobEffects.X} 判断),
 *     所以在基类上拦就行;</li>
 * <li><b>任何自定义效果都必须覆写 {@code applyEffectTick}</b> 才有效果,覆写之后调用不经过基类 ——
 *     这就是 1.1.28a 漏掉的那批"正常生效的药水效果";</li>
 * <li>拦<b>调用方</b>{@code applyEffect} 是在分发之前,原版/模组/覆写一次全封;时长仍由
 *     {@code tick} 正常递减(不取消 {@code tick},只取消它内部这一次 {@code applyEffect})。</li>
 * </ul>
 * {@code remap = false} + {@code require = 0};类登记在
 * {@code enigmaticenhance.optional.mixins.json}({@code required:false} + {@code defaultRequire:0})。
 */
@Mixin(MobEffectInstance.class)
public class MobEffectInstanceSealMixin {

    @Inject(method = "m_19550_(Lnet/minecraft/world/entity/LivingEntity;)V",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$sealApplyEffect(LivingEntity entity, CallbackInfo ci) {
        try {
            MobEffectInstance self = (MobEffectInstance) (Object) this;
            if (DemonBloodChaliceHandler.shouldSeal(entity, self.getEffect(), self.getAmplifier())) {
                ci.cancel();
            }
        }
        catch (Throwable ignored) {
            // 判定失败 ⇒ 保持原版行为
        }
    }
}
