package com.hengx.enigmaticenhance;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;

/**
 * 4.18.1：伤害锁的"同步数据"守卫。
 *
 * <p>{@code setHealth} 那条路已经拦住了；但有些模组不走它，而是直接写
 * {@code SynchedEntityData} 里的 {@code DATA_HEALTH_ID}（我们自己补差额时也用过这条路）。
 * 这里在同一层把"上调写入"也拒掉。</p>
 *
 * <p>拿不到实体或血量访问器时一律放行 —— 守卫只做"锦上添花"，绝不因为反射失败而误伤。</p>
 */
public final class HealthDataGuard {

    private static Field entityField;
    private static boolean entityFieldResolved;
    private static EntityDataAccessor<Float> healthAccessor;
    private static boolean healthAccessorResolved;

    private HealthDataGuard() {
    }

    /** @return true = 应当拒绝这次同步数据写入 */
    public static boolean block(Object synchedData, Object accessor, Object value) {
        try {
            if (DamageLock.authorized()) {
                return false;
            }
            if (!(value instanceof Float incoming)) {
                return false;
            }
            EntityDataAccessor<Float> health = healthAccessor();
            if (health == null || accessor != health) {
                return false;
            }
            Entity owner = ownerOf(synchedData);
            if (!(owner instanceof LivingEntity living)) {
                return false;
            }
            if (!DamageLock.isLocked(living)) {
                return false;
            }
            // 只拦"往上写"
            if (incoming <= living.getHealth() + 1.0E-3F) {
                return false;
            }
            DebugLog.debug("[伤害锁] 拒绝同步回血写入 {} -> {}", living.getHealth(), incoming);
            return true;
        }
        catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Float> healthAccessor() {
        if (!healthAccessorResolved) {
            healthAccessorResolved = true;
            for (String name : new String[]{"f_20961_", "DATA_HEALTH_ID"}) {
                try {
                    Field f = LivingEntity.class.getDeclaredField(name);
                    f.setAccessible(true);
                    healthAccessor = (EntityDataAccessor<Float>) f.get(null);
                    break;
                }
                catch (Throwable ignored) {
                }
            }
        }
        return healthAccessor;
    }

    private static Entity ownerOf(Object synchedData) {
        if (synchedData == null) {
            return null;
        }
        try {
            if (!entityFieldResolved) {
                entityFieldResolved = true;
                for (Field f : synchedData.getClass().getDeclaredFields()) {
                    if (Entity.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        entityField = f;
                        break;
                    }
                }
            }
            if (entityField == null) {
                return null;
            }
            Object value = entityField.get(synchedData);
            return value instanceof Entity e ? e : null;
        }
        catch (Throwable t) {
            return null;
        }
    }
}
