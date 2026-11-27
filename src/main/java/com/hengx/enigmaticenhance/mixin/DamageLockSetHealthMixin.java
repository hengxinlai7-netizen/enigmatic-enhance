package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.DamageLock;

import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 4.18.0：伤害锁的写入层守卫。
 *
 * <p>执行之手打完那一脚后，伤害线程之外（延迟任务 / 异步回血 / 血量托管还原）
 * 仍可能调用 {@code setHealth} 把血加回来。这里在方法入口直接拒绝"上调写入"，
 * 比"下一 tick 再写回去"更早、更彻底。</p>
 *
 * <p>我们自己写血时通过 {@link DamageLock#authorize(Runnable)} 放行。</p>
 */
@Mixin(LivingEntity.class)
public abstract class DamageLockSetHealthMixin {

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void enigmaticenhance$guardDev(float health, CallbackInfo ci) {
        if (DamageLock.guardSetHealth((LivingEntity) (Object) this, health)) {
            ci.cancel();
        }
    }

    @Inject(method = "m_21153_", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void enigmaticenhance$guardSrg(float health, CallbackInfo ci) {
        if (DamageLock.guardSetHealth((LivingEntity) (Object) this, health)) {
            ci.cancel();
        }
    }
}
