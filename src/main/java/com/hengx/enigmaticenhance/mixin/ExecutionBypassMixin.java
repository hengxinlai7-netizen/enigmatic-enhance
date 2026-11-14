package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.ExecutionDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 4.8.3:「执行之手」的<b>兜底补发</b>通道(替代 2.10.5 的 TrueDamageBypassMixin)。
 *
 * <p>{@code ForgeHooks.onLivingAttack / onLivingHurt / onLivingDamage} 是所有模组的
 * LivingAttackEvent / LivingHurtEvent / LivingDamageEvent 的<b>唯一派发口</b>。
 * 在这里直接返回原值 = 这一次伤害不经过任何事件处理器。</p>
 *
 * <h2>与 valettein(断曜流光)的区别</h2>
 * <p>它把 {@code DamageSource#is(TagKey)}、{@code Event#setCanceled}、{@code setAmount}
 * 三处<b>全局</b>改掉,于是整个游戏里所有伤害、所有事件的语义都变了。这里:</p>
 * <ul>
 *   <li>只认伤害类型 {@code enigmaticenhance:execution}(别的伤害源一律原样放行);</li>
 *   <li>而且只在这个类型<b>处于补发阶段</b>({@link ExecutionDamage#forced()} 线程内标记)时生效 ——
 *       正常那一脚的事件照常派发,双保险、受击登记、别的模组的逻辑都还在;</li>
 *   <li>补发阶段只在 {@link ExecutionDamage#apply} 里"事件层确实吃掉了伤害"时置位,
 *       调用一结束立即复位。</li>
 * </ul>
 */
@Mixin(value = ForgeHooks.class, remap = false)
public class ExecutionBypassMixin {

    @Inject(method = "onLivingAttack", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$skipAttack(LivingEntity entity, DamageSource src, float amount,
        CallbackInfoReturnable<Boolean> cir) {
        if (ExecutionDamage.forced() && ExecutionDamage.isExecution(src)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    @Inject(method = "onLivingHurt", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$skipHurt(LivingEntity entity, DamageSource src, float amount,
        CallbackInfoReturnable<Float> cir) {
        if (ExecutionDamage.forced() && ExecutionDamage.isExecution(src)) {
            cir.setReturnValue(amount);
        }
    }

    @Inject(method = "onLivingDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$skipDamage(LivingEntity entity, DamageSource src, float amount,
        CallbackInfoReturnable<Float> cir) {
        if (ExecutionDamage.forced() && ExecutionDamage.isExecution(src)) {
            cir.setReturnValue(amount);
        }
    }
}
