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
 * 4.13.0：伤害锁 —— 执行之手打出去的那一脚，不许被"事后"救回来。
 *
 * <p>结算时已经用"差额直写血量"覆盖了护甲/抗性/限伤/取消；
 * 但仍有模组会在同一 tick 之后把血加回去（自动回血、护盾重算、血量托管还原）。
 * 这里给目标打一个 10 tick 的"掉血锁定"：</p>
 * <ul>
 *   <li>锁定期间 {@code LivingHealEvent} 一律取消；</li>
 *   <li>每 tick 复查，血比"应到值"高就写回去。</li>
 * </ul>
 */
public final class DamageLock {

    private static final int TICKS = 10;

    private record Lock(float expected, long expireAt) {}

    private static final Map<UUID, Lock> LOCKS = new ConcurrentHashMap<>();

    private DamageLock() {
    }

    /** 记录"目标应该停在多少血"，并在接下来若干 tick 压住回血。 */
    public static void lock(LivingEntity target, float expectedHealth) {
        if (target == null || target.level().isClientSide) {
            return;
        }
        LOCKS.put(target.getUUID(), new Lock(expectedHealth, target.level().getGameTime() + TICKS));
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
                    living.setHealth(lock.expected());
                    DebugLog.warn("[伤害锁] 回血被驳回: {} -> {}", living.getHealth(), lock.expected());
                }
            }
        }
    }
}
