package com.hengx.enigmaticenhance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 4.12.2：斩杀后的「死亡锁」——确保目标彻底死亡，任何东西都插不上手。
 *
 * <p>为什么需要它：执行之手的斩杀把血写到 0 之后，以下情况仍可能让目标"活过来"：</p>
 * <ul>
 *   <li>别的模组在 {@code LivingDeathEvent} 里取消死亡（复活/免死/护符）。</li>
 *   <li>别的模组在 {@code LivingHealEvent} 或 tick 里把血加回来。</li>
 *   <li>血量托管类模组把 {@code setHealth(0)} 还原。</li>
 * </ul>
 *
 * <p>做法：斩杀时给目标打一个短时"死刑标记"，然后在三个位置压住：</p>
 * <ol>
 *   <li>{@code LivingDeathEvent}(LOWEST, receiveCanceled)：强行解除取消；</li>
 *   <li>{@code LivingHealEvent}：标记期间禁止回血；</li>
 *   <li>每秒复查：还活着且血 &gt; 0 → 重新打到 0 并 {@code die()}。</li>
 * </ol>
 */
public final class DeathLock {

    /** 标记有效期（tick）。够长以覆盖各种"下一 tick 复活"的实现。 */
    private static final int TICKS = 100;

    private record Doom(DamageSource source, long expireAt) {}

    private static final Map<UUID, Doom> DOOMED = new ConcurrentHashMap<>();

    private DeathLock() {
    }

    /** 给目标打上"必须死"的标记，并立即执行一次致死。 */
    public static void doom(LivingEntity target, DamageSource source) {
        if (target == null || target.level().isClientSide) {
            return;
        }
        long expire = target.level().getGameTime() + TICKS;
        DOOMED.put(target.getUUID(), new Doom(source, expire));
        ensureDead(target, source);
    }

    public static boolean isDoomed(LivingEntity target) {
        Doom doom = target == null ? null : DOOMED.get(target.getUUID());
        if (doom == null) {
            return false;
        }
        if (target.level().getGameTime() > doom.expireAt()) {
            DOOMED.remove(target.getUUID());
            return false;
        }
        return true;
    }

    /** 立即执行一次"彻底死亡"：清血 → die → 复查。 */
    public static void ensureDead(LivingEntity target, DamageSource source) {
        if (target == null || target.level().isClientSide) {
            return;
        }
        try {
            target.invulnerableTime = 0;
            DamageLock.write(target, 0.0F);
            if (target.isDeadOrDying() || !target.isAlive()) {
                return;
            }
            if (source != null) {
                target.die(source);
            }
            // 4.17.1: 还被拦着就补一次掉落
            if (!target.isDeadOrDying() && target.isAlive() && source != null) {
                DeathDrops.force(target, source);
            }
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[死亡锁] 强制致死失败: {}", e.toString());
        }
    }

    /** 死亡事件：不管谁取消，标记期间一律驳回。 */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isDoomed(entity)) {
            return;
        }
        if (event.isCanceled()) {
            event.setCanceled(false);
            DebugLog.warn("[死亡锁] 驳回了一次外部取消死亡: {}", entity.getName().getString());
        }
        entity.setHealth(0.0F);
    }

    /** 回血事件：标记期间禁止。 */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHeal(LivingHealEvent event) {
        if (isDoomed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 每秒复查：还被复活就再杀一次。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DOOMED.isEmpty()) {
            return;
        }
        long now = -1;
        for (UUID id : DOOMED.keySet()) {
            Doom doom = DOOMED.get(id);
            if (doom == null) {
                continue;
            }
            LivingEntity entity = null;
            for (ServerLevel level : event.getServer().getAllLevels()) {
                var e = level.getEntity(id);
                if (e instanceof LivingEntity living) {
                    entity = living;
                    now = level.getGameTime();
                    break;
                }
            }
            if (entity == null) {
                DOOMED.remove(id);      // 已经彻底没了
                continue;
            }
            if (now > doom.expireAt()) {
                DOOMED.remove(id);
                continue;
            }
            if (entity.isAlive() && entity.getHealth() > 0.0F) {
                DebugLog.warn("[死亡锁] 目标被复活，重新致死: {}", entity.getName().getString());
                ensureDead(entity, doom.source());
            }
        }
    }
}
