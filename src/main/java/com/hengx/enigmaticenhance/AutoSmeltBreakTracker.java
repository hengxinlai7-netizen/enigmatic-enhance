package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.1.22:记录"刚刚由玩家挖掘过、且该玩家此刻应当熔炼"的方块,给
 * {@link AutoSmeltItemWatcher} 的<b>兜底路径</b>用(不掉落表那条路)。
 * <p>
 * 为什么需要它:有的模组(Tetra 的模块化工具是实证案例)会自己处理方块破坏/掉落 ——
 * 它可能调用 {@code Block#getDrops} 只是算一份表,真正生成的却是它自己 spawn 的物品 ⇒
 * 我们在 {@code getDrops} RETURN 上替换的那一份<b>不会被使用</b>。兜底路径改成盯
 * {@code EntityJoinLevelEvent}(掉落物实体真正出现的时刻),把实体里的那一堆就地换掉,
 * 这样不管上游是谁 spawn 的都能生效。
 * <p>
 * 每个玩家破方块是同步发生的,所以只需要一个很短(2 tick)的小窗口;完全走 Forge 事件
 * ({@link BlockEvent.BreakEvent}),不需要再往 {@code ServerPlayerGameMode} 里注 mixin。
 * 只记录"决定要熔炼"的破坏(SMELT),所以潜行/工具没附魔/关掉总开关都不会留下记录。
 */
public final class AutoSmeltBreakTracker {

    /** 记录有效 tick 数(破坏 → 掉落物实体出现通常在同一 tick;留 5 tick 给稍晚 spawn 的模组)。 */
    private static final long WINDOW_TICKS = 5L;
    /** 掉落物实体与方块坐标的容差(popResource 有 ±0.25 的随机偏移)。 */
    private static final int POS_TOLERANCE = 2;

    /** 一条"该熔炼的挖掘"记录。 */
    public record Mining(ResourceLocation dimension, BlockPos pos, UUID player, String toolId, long tick) {}

    private static final List<Mining> RECENT = new ArrayList<>();

    private AutoSmeltBreakTracker() {}

    /** 玩家破坏方块(未取消)时登记;不适用的场景不留记录。 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            Player player = event.getPlayer();
            ItemStack tool = player.getMainHandItem();
            if (AutoSmeltHandler.decide(player, tool) != AutoSmeltHandler.Action.SMELT) {
                return;
            }
            record(level, event.getPos(), player, AutoSmeltPipeline.idOf(tool));
        }
        catch (Throwable t) {
            if (RingConfig.debugLog()) {
                DebugLog.info("[AutoSmelt] break-tracker failed: {}", String.valueOf(t));
            }
        }
    }

    /** 自检用:直接登记一条(模拟"刚才有人用附魔工具挖了这个方块")。 */
    public static void record(ServerLevel level, BlockPos pos, Player player, String toolId) {
        long now = level.getGameTime();
        prune(now);
        RECENT.add(new Mining(level.dimension().location(), pos.immutable(), player.getUUID(), toolId, now));
    }

    /** 掉落物实体出现时,找"对应那次挖掘"的记录;找不到 ⇒ null。 */
    @Nullable
    public static Mining find(ServerLevel level, BlockPos entityPos, long now) {
        prune(now);
        ResourceLocation dimension = level.dimension().location();
        for (int i = RECENT.size() - 1; i >= 0; i--) {
            Mining mining = RECENT.get(i);
            if (!mining.dimension().equals(dimension) || now - mining.tick() > WINDOW_TICKS) {
                continue;
            }
            if (Math.abs(mining.pos().getX() - entityPos.getX()) <= POS_TOLERANCE
                && Math.abs(mining.pos().getY() - entityPos.getY()) <= POS_TOLERANCE
                && Math.abs(mining.pos().getZ() - entityPos.getZ()) <= POS_TOLERANCE) {
                return mining;
            }
        }
        return null;
    }

    /** 自检用:当前记录数。 */
    public static int size() {
        return RECENT.size();
    }

    /** 自检用:清空。 */
    public static void reset() {
        RECENT.clear();
    }

    private static void prune(long now) {
        if (RECENT.isEmpty()) {
            return;
        }
        RECENT.removeIf(mining -> now - mining.tick() > WINDOW_TICKS);
    }
}
