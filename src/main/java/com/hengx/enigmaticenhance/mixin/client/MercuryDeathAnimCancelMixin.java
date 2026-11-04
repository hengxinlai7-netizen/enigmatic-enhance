package com.hengx.enigmaticenhance.mixin.client;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;

/**
 * 1.6.47:带水银之蛇的实体被"拦下死亡"后,客户端可能仍残留 deathTime > 0 而继续播放
 * 死亡动画(抽搐)。这里在 tick 头部兜底:只要血量 > 0 就不允许 deathTime/dead 保持。
 *
 * <p>用反射按 mojmap/SRG 两个名字写字段,避免 @Shadow 命名不一致导致混入失败。</p>
 */
@Mixin(LivingEntity.class)
public class MercuryDeathAnimCancelMixin {

    @Inject(method = "m_8119_", at = @At("HEAD"), remap = false, require = 0)
    private void enigmaticenhance$cancelDeathAnim(CallbackInfo ci) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (self.level().isClientSide == false) {
                return;   // 1.6.49:只在客户端改,绝不能碰服务端的死亡判定(否则会"原地复活")
            }
            if (self.getHealth() <= 0.0F) {
                return;
            }
            setField(self, "deathTime", "f_20919_", 0);
            setField(self, "dead", "f_20922_", false);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    private static void setField(Object entity, String moj, String srg, Object value) {
        try {
            for (Class<?> cls = entity.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                Field f = null;
                try {
                    f = cls.getDeclaredField(moj);
                }
                catch (NoSuchFieldException ignored) {
                    try {
                        f = cls.getDeclaredField(srg);
                    }
                    catch (NoSuchFieldException ignored2) {
                        continue;
                    }
                }
                f.setAccessible(true);
                if (value instanceof Integer i) {
                    f.setInt(entity, i);
                }
                else if (value instanceof Boolean b) {
                    f.setBoolean(entity, b);
                }
                return;
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }
}
