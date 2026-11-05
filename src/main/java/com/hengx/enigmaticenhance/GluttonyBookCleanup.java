package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 4.1.0:贪食之书的复活清理。
 *
 * <p>负上限的血条绝不能带到复活后的角色身上,所以在这里兜底移除。
 * 放在独立类里,避免物品类自己注册事件(物品是注册对象,不适合常驻监听)。</p>
 */
public final class GluttonyBookCleanup {

    private GluttonyBookCleanup() {
    }

    /**
     * 4.1.4:把被第三方拦掉的死亡<b>重新放开</b>。
     *
     * <p>实测这个整合包里有模组会取消 {@code LivingDeathEvent}(日志可见
     * {@code bmd_death_fix}、{@code difficulty_steam} 等)。用优先级 LOWEST +
     * {@code receiveCanceled = true} 抢在所有人之后:</p>
     *
     * <ul>
     *   <li>只对<b>正在使用贪食之书致死</b>的玩家生效(持久标记);</li>
     *   <li>事件已被取消 ⇒ 这里 {@code setCanceled(false)} 把它掰回来;</li>
     *   <li>其它任何死亡一律不碰。</li>
     * </ul>
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST,
        receiveCanceled = true)
    public static void forceDeath(LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            if (!GluttonyBookItem.isMarked(player)) {
                return;
            }
            if (event.isCanceled()) {
                event.setCanceled(false);
                DebugLog.warn("[GluttonyBook] 死亡事件被第三方取消,已强制放开:{}",
                    player.getName().getString());
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
    // ------------------------------------------------------------------ 4.1.5 死亡驱动

    /** 正在被"死亡驱动"处理的玩家 -> 已跑的 tick 数。 */
    private static final java.util.Map<java.util.UUID, Integer> DRIVING =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static final int DRIVE_TICKS = 100;

    /**
     * 4.1.5:禁止被"免死"回血。
     *
     * <p>实测有模组在致死时把血量兜到 1(表现就是"血停在 1")。这里直接取消该玩家的治疗事件,
     * 减少一条回血途径。</p>
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST,
        receiveCanceled = true)
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        try {
            if (event.getEntity() instanceof Player player && GluttonyBookItem.isMarked(player)) {
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /**
     * 4.1.5:死亡驱动 —— 每 tick 重新把血压到 0 并掉用 die(),直到真的死掉。
     *
     * <p>为什么需要它:免死类模组通常在伤害结算里把血兜到 1,我们单次 {@code setHealth(0)}
     * 会被它覆盖。持续压制 + 每 tick 调 {@code die()} 能保证总有某一 tick 是在血量为 0 的状态下
     * 走进死亡流程,而死亡结算本身<b>完全正常</b>(不绕过死亡信息/掉落/界面)。</p>
     *
     * <p>上限 {@value #DRIVE_TICKS} tick(约 5 秒),避免极端情况下无限刷。</p>
     */
    @SubscribeEvent
    public static void deathTickDriver(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
                return;
            }
            if (!GluttonyBookItem.isMarked(player)) {
                DRIVING.remove(player.getUUID());
                return;
            }
            if (!GluttonyBookItem.isMarked(player) || player.isDeadOrDying()) {
                return;
            }
            int ticks = DRIVING.merge(player.getUUID(), 1, Integer::sum);
            if (ticks > DRIVE_TICKS) {
                DRIVING.remove(player.getUUID());
                // 放弃时必须把状态洗干净:负上限修饰符 + 持久标记 + 禁疗
                // 否则玩家会带着「上限 1 + 永远回不了血」继续玩,那是真污染。
                GluttonyBookItem.cleanup(player);
                DebugLog.warn("[GluttonyBook] 死亡驱动超过 {} tick 仍未死亡,已放弃并清理状态", DRIVE_TICKS);
                return;
            }
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return;
            }
            serverPlayer.setHealth(0.0F);
            serverPlayer.die(serverPlayer.damageSources().genericKill());
            if (ticks == 1 || ticks % 20 == 0) {
                DebugLog.warn("[GluttonyBook] 死亡驱动 tick={} 血量={}", ticks, serverPlayer.getHealth());
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 死亡或复活后停止驱动。 */
    public static void stopDriving(Player player) {
        try {
            if (player != null) {
                DRIVING.remove(player.getUUID());
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
    /** 死亡复活:新实体会复制旧实体的持久数据,负上限修饰符必须在这里摘掉。 */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        try {
            GluttonyBookItem.cleanup(event.getEntity());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 兜底:死亡瞬间退出游戏再进。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            GluttonyBookItem.cleanup(event.getEntity());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 兜底:复活完成后再确认一次。 */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        try {
            GluttonyBookItem.cleanup(event.getEntity());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}