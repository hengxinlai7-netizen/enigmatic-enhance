package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 4.18.0：伤害锁 —— 执行之手打出去的那一脚，不许被"事后"救回来。
 *
 * <p>结算时已经用"差额直写血量"覆盖了护甲/抗性/限伤/取消；但伤害线程之外仍可能有：
 * 延迟任务（下一 tick 结算）、异步回血、血量托管还原、护盾重算 —— 它们会在事件结束后
 * 把血加回来。这里做三道压制：</p>
 *
 * <ol>
 *   <li>{@code LivingHealEvent} 一律取消（锁定期间）；</li>
 *   <li>{@code LivingEntity#setHealth} 的"上调写入"直接被拒（mixin 调 {@link #guardSetHealth}）；</li>
 *   <li>每 tick 复查，血比"应到值"高就写回去（兜底，覆盖直接改字段/同步数据的路径）。</li>
 * </ol>
 *
 * <p>我们自己写血时用 {@link #authorize()} 开一个短作用域，避免被自己的守卫拦下。</p>
 */
public final class DamageLock {

    /** 锁定窗口（tick）。覆盖"下一 tick 结算"的延迟任务。 */
    private static final int TICKS = 40;

    private record Lock(float expected, long expireAt) {}

    private static final Map<UUID, Lock> LOCKS = new ConcurrentHashMap<>();

    /** 我们自己写血时置位，守卫一律放行。 */
    private static final ThreadLocal<Boolean> AUTHORIZED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DamageLock() {
    }

    /** 记录"目标应该停在多少血"，并在接下来若干 tick 压住回血/回写。 */
    public static void lock(LivingEntity target, float expectedHealth) {
        if (target == null || target.level().isClientSide) {
            return;
        }
        LOCKS.put(target.getUUID(), new Lock(expectedHealth, target.level().getGameTime() + TICKS));
    }

    public static void unlock(LivingEntity target) {
        if (target != null) {
            LOCKS.remove(target.getUUID());
        }
    }

    public static boolean isLocked(LivingEntity target) {
        if (target == null) {
            return false;
        }
        Lock lock = LOCKS.get(target.getUUID());
        if (lock == null) {
            return false;
        }
        if (target.level().getGameTime() > lock.expireAt()) {
            LOCKS.remove(target.getUUID());
            return false;
        }
        return true;
    }

    /** 我们自己的写入：作用域内守卫放行。 */
    public static void authorize(Runnable action) {
        AUTHORIZED.set(Boolean.TRUE);
        try {
            action.run();
        }
        finally {
            AUTHORIZED.set(Boolean.FALSE);
        }
    }

    public static boolean authorized() {
        return Boolean.TRUE.equals(AUTHORIZED.get());
    }

    /**
     * {@code LivingEntity#setHealth} 的守卫。
     *
     * @return true = 应当拒绝这次写入
     */
    public static boolean guardSetHealth(LivingEntity entity, float newHealth) {
        if (entity == null || authorized()) {
            return false;
        }
        Lock lock = LOCKS.get(entity.getUUID());
        if (lock == null) {
            return false;
        }
        if (entity.level().getGameTime() > lock.expireAt()) {
            LOCKS.remove(entity.getUUID());
            return false;
        }
        // 只挡"往上写"；继续掉血是允许的（别人补刀不影响我们）
        if (newHealth > lock.expected() + 1.0E-3F) {
            DebugLog.debug("[伤害锁] 拒绝外部回血写入 {} -> {}（应停在 {}）",
                    entity.getHealth(), newHealth, lock.expected());
            return true;
        }
        return false;
    }

    /** 我们自己的写血入口（带授权）。 */
    public static void write(LivingEntity entity, float health) {
        if (entity == null) {
            return;
        }
        authorize(() -> entity.setHealth(health));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHeal(LivingHealEvent event) {
        if (isLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || LOCKS.isEmpty()) {
            return;
        }
        for (var level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            for (UUID id : LOCKS.keySet()) {
                Lock lock = LOCKS.get(id);
                if (lock == null) {
                    continue;
                }
                if (now > lock.expireAt()) {
                    LOCKS.remove(id);
                    continue;
                }
                var entity = level.getEntity(id);
                if (!(entity instanceof LivingEntity living)) {
                    continue;
                }
                if (!living.isAlive()) {
                    LOCKS.remove(id);
                    continue;
                }
                if (living.getHealth() > lock.expected() + 1.0E-3F) {
                    write(living, lock.expected());
                    DebugLog.warn("[伤害锁] 回血被驳回: {} -> {}", living.getHealth(), lock.expected());
                }
            }
        }
    }
}
