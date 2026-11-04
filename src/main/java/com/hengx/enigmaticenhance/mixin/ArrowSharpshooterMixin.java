package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.SharpshooterHandler;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * 1.2.17:「神射手护符」对箭矢的三条硬效果。
 *
 * <p>原版 {@code AbstractArrow#shoot(x, y, z, velocity, inaccuracy)} 负责:</p>
 * <ul>
 *   <li>按拉弓力度决定初速(velocity);</li>
 *   <li>按 {@code inaccuracy} 加随机偏移;</li>
 *   <li>是否暴击({@code critArrow})其实在更早的 {@code performShoot} 里定好。</li>
 * </ul>
 *
 * <p>这里在 HEAD 拦截:佩戴护符时**自己重放一次** {@code shoot}(inaccuracy = 0),
 * 然后把速度归一化到 {@link SharpshooterHandler#FIXED_SPEED} 并强制暴击,再取消原调用。
 * 用 ThreadLocal 防止自己调用自己导致无限递归。</p>
 */
@Mixin(AbstractArrow.class)
public class ArrowSharpshooterMixin {

    private static final ThreadLocal<Boolean> IN_SHARPSHOOTER = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "m_6686_(DDDFF)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$sharpshooterShoot(double x, double y, double z, float velocity, float inaccuracy,
        CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (Boolean.TRUE.equals(IN_SHARPSHOOTER.get())) {
            return;
        }
        if (!SharpshooterHandler.fromSharpshooter(self)) {
            return;
        }
        IN_SHARPSHOOTER.set(Boolean.TRUE);
        try {
            // 重放:偏移强制 0
            self.shoot(x, y, z, velocity, 0.0F);
            // 速度归一化到固定值
            Vec3 motion = self.getDeltaMovement();
            double length = motion.length();
            if (length > 1.0E-6D) {
                if (length < SharpshooterHandler.MIN_SPEED) {
                self.setDeltaMovement(motion.scale(SharpshooterHandler.MIN_SPEED / length));
            }
            }
            // 必暴击
            self.setCritArrow(true);
            ci.cancel();
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就交给原版
        }
        finally {
            IN_SHARPSHOOTER.set(Boolean.FALSE);
        }
    }
}