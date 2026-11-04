package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.5.16:**本模组的所有状态效果都无法被外部清除**。
 *
 * <p>牛奶、净化药水、/effect clear、其它模组的净化统一走
 * {@code LivingEntity#removeEffect(MobEffect)}({@code m_21195_}),这里按
 * "效果注册名是否属于 enigmaticenhance 命名空间"统一拦截。</p>
 *
 * <p>内部需要移除时(例如根源之祸触发后)通过
 * {@link com.hengx.enigmaticenhance.ModEffectGuard#ALLOW_REMOVE} 旁路放行。</p>
 */
@Mixin(LivingEntity.class)
public class ModEffectRemovalGuardMixin {

    @Inject(method = "m_21195_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockAllOurEffects(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (effect == null || com.hengx.enigmaticenhance.ModEffectGuard.ALLOW_REMOVE.get().booleanValue()) {
                return;
            }
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (id != null && "enigmaticenhance".equals(id.getNamespace())) {
                cir.setReturnValue(Boolean.FALSE);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}