package com.hengx.enigmaticenhance;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 1.2.13g:龙首「飞行穿方块」的**共享状态**。
 *
 * <h2>为什么需要这个类(以及之前为什么一直不生效)</h2>
 * 1.20.1 的 {@code Player#tick()} 第一句就是 {@code this.noPhysics = this.isSpectator();}。
 * 之前我们在 {@code ClientTickEvent.END}(整个客户端 tick 的最后)把 {@code noPhysics} 设成 true,
 * 但**下一 tick 一开始**就被 {@code Player#tick()} 抹成 false,而移动/碰撞就发生在同一 tick 的
 * {@code Entity#move} 里 —— 也就是说我们设的值永远赶不上判定。
 *
 * <p>现在的做法:{@code EntityMoveMixin} 在 {@code Entity#move} 的 HEAD 检查本类的状态,
 * 命中就<b>当场</b>把 {@code noPhysics} 置 true,再交给原版方法继续(原版 move 第一句就判断它)。</p>
 *
 * <p>状态来源两边各一份:
 * <ul>
 *   <li>服务端:{@link #serverActive}(由 {@code EarthAmuletHandler} 维护);</li>
 *   <li>客户端:{@link #clientActive}(由 {@code NoClipClientHandler} 根据 S2C 包设置)。</li>
 * </ul>
 * 本类故意不 import 任何客户端类,服务端也能安全加载。</p>
 */
public final class NoClipState {

    /** 服务端认定了"该玩家此刻可穿方块"的 UUID。 */
    public static final Set<UUID> serverActive = ConcurrentHashMap.newKeySet();

    /** 客户端:服务端最近一次下发的开关。 */
    public static volatile boolean clientActive;

    private NoClipState() {
    }

    /** 这个实体此刻是否应当无视方块碰撞(只对玩家生效)。 */
    public static boolean isActive(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        return clientActive || serverActive.contains(player.getUUID());
    }

    /** 服务端登记/注销。 */
    public static boolean mark(Player player, boolean active) {
        if (player == null) {
            return false;
        }
        if (active) {
            return serverActive.add(player.getUUID());
        }
        return serverActive.remove(player.getUUID());
    }

    public static boolean isMarked(Player player) {
        return player != null && serverActive.contains(player.getUUID());
    }

    // ------------------------------------------------------------------ 4.2.2 复原支持

    /** 被我们强行打开 noPhysics 的实体(用 id 记,避免强引用实体)。 */
    private static final java.util.Set<Integer> FORCED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 记下"这个实体的 noPhysics 是我们打开的"。 */
    public static void markForced(net.minecraft.world.entity.Entity entity) {
        try {
            if (entity != null) {
                FORCED.add(entity.getId());
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /**
     * 实体不再需要穿墙:返回 true 表示"它确实是我们要还原的",调用方据此把 noPhysics 设回 false。
     */
    public static boolean releaseForced(net.minecraft.world.entity.Entity entity) {
        try {
            return entity != null && FORCED.remove(entity.getId());
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
