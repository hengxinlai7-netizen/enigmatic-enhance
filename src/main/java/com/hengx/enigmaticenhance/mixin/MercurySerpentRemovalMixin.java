package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.MercurySerpentHandler;
import com.hengx.enigmaticenhance.ModEffects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.3.3:「水银之蛇」**只能靠触发次数耗尽**,牛奶/净化/命令/死亡都无法清除。
 *
 * <p>拦 {@code LivingEntity#removeEffect(MobEffect)}(SRG {@code m_21195_});
 * 只有 {@link MercurySerpentHandler#removalAllowed()} 为 true(层数耗尽)时放行。</p>
 */
@Mixin(LivingEntity.class)
public class MercurySerpentRemovalMixin {

    @Inject(method = "m_21195_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockRemoval(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        try {
            MobEffect ours = ModEffects.mercurySerpentOrNull();
            if (ours == null || effect != ours) {
                return;
            }
            if (MercurySerpentHandler.removalAllowed()) {
                return;
            }
            cir.setReturnValue(Boolean.FALSE);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就允许移除
        }
    }
}