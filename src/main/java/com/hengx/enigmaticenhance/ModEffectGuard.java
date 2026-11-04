package com.hengx.enigmaticenhance;

/** 1.5.16:本模组效果的"允许内部移除"旁路开关。 */
public final class ModEffectGuard {

    public static final ThreadLocal<Boolean> ALLOW_REMOVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ModEffectGuard() {
    }

    public static void removeEffect(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.effect.MobEffect effect) {
        ALLOW_REMOVE.set(Boolean.TRUE);
        try {
            entity.removeEffect(effect);
            entity.getActiveEffectsMap().remove(effect);
        }
        finally {
            ALLOW_REMOVE.set(Boolean.FALSE);
        }
    }
}