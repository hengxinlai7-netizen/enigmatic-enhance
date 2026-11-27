package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.HealthDataGuard;

import net.minecraft.network.syncher.SynchedEntityData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 4.18.1：同步数据层的血量守卫（对应 {@code DATA_HEALTH_ID}）。
 *
 * <p>覆盖"绕过 {@code setHealth}、直接写同步数据"的回血路径。
 * 判定逻辑在 {@link HealthDataGuard}，拿不到实体/访问器时一律放行。</p>
 */
@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataHealthMixin {

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void enigmaticenhance$guard3Dev(net.minecraft.network.syncher.EntityDataAccessor<?> accessor,
                                            Object value, boolean force, CallbackInfo ci) {
        if (HealthDataGuard.block(this, accessor, value)) {
            ci.cancel();
        }
    }

    @Inject(method = "m_276349_", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void enigmaticenhance$guard3Srg(net.minecraft.network.syncher.EntityDataAccessor<?> accessor,
                                            Object value, boolean force, CallbackInfo ci) {
        if (HealthDataGuard.block(this, accessor, value)) {
            ci.cancel();
        }
    }
}
