package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.MercurySerpentHandler;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.3.3:「水银之蛇」的破盾无敌帧**写死在 hurt 入口**。
 *
 * <p>不依赖 {@code invulnerableTime}(会被 genericKill / setHealth / 其它模组绕过),
 * 而是查实体的 PersistentData 里记录的到期时间:未到期则直接让 {@code hurt} 返回 false,
 * 任何伤害来源(含 BYPASSES_INVULNERABILITY)都进不来。</p>
 */
@Mixin(LivingEntity.class)
public class MercurySerpentInvulnMixin {

    @Inject(method = "m_6469_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockDuringInvuln(DamageSource source, float amount,
        CallbackInfoReturnable<Boolean> cir) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (self.level().isClientSide) {
                return;
            }
            if (MercurySerpentHandler.invulnerable(self)) {
                cir.setReturnValue(Boolean.FALSE);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就不拦
        }
    }
}