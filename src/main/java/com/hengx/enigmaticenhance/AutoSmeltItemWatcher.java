package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.1.22:掉落的<b>兜底注入点</b> —— 掉落物实体真正出现的时刻。
 * <p>
 * 为什么还要第二条路:实证(Tetra 的模块化工具 {@code modular_double})说明,
 * 有的模组会自己处理方块破坏/掉落,{@code Block#getDrops} 的返回值<b>没有被用来生成物品</b>
 * ⇒ 在 {@code getDrops} RETURN 上替换的那一份被旁路了。
 * 这里改盯 {@link EntityJoinLevelEvent}(priority = HIGHEST,抢在其它收集管线之前),
 * 把即将进入世界的那一堆**就地换掉**:
 * <ul>
 *   <li>不取消事件、不额外 spawn 一份 ⇒ 与"谁 spawn 的"无关,也不会出现两份;</li>
 *   <li>只处理"刚刚有玩家用附魔工具挖过"的坐标({@link AutoSmeltBreakTracker} 的 2 tick 窗口),
 *       所以箱子/怪物/掉落物交易等来源完全不受影响;</li>
 *   <li>同一坐标同一 tick 的账本({@link AutoSmeltLedger})会跳过 {@code getDrops} 那条路
 *       已经产出的东西 ⇒ 两条路加起来仍然<b>只替换一次</b>;</li>
 *   <li>玩家自己丢出来的(有 owner)不处理。</li>
 * </ul>
 * 数量守恒:输入 N 个、产物超过一堆时,第一堆就地写进实体,其余堆用抑制标记 spawn(不会被自己再处理)。
 */
public final class AutoSmeltItemWatcher {

    private static final String TAG = "[AutoSmelt]";
    /** 防止我们自己 spawn 的堆被自己再处理一次。 */
    private static final ThreadLocal<Integer> SUPPRESSED = ThreadLocal.withInitial(() -> 0);
    /** 第三层去重:同一 tick 已经处理过的实体 UUID。 */
    private static long processedTick = Long.MIN_VALUE;
    private static final java.util.Set<java.util.UUID> PROCESSED = new java.util.HashSet<>();

    private AutoSmeltItemWatcher() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        try {
            handle(event);
        }
        catch (Throwable t) {
            if (RingConfig.debugLog()) {
                DebugLog.info("{} item-watcher failed: {}", TAG, String.valueOf(t));
            }
        }
    }

    private static void handle(EntityJoinLevelEvent event) {
        if (SUPPRESSED.get() > 0 || event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        if (itemEntity.getOwner() != null) {
            // 玩家自己丢出来的不算挖掘掉落
            return;
        }
        AutoSmeltBreakTracker.Mining mining = AutoSmeltBreakTracker.find(level, itemEntity.blockPosition(),
            level.getGameTime());
        if (mining == null) {
            return;
        }
        AutoSmeltLedger.Session session = AutoSmeltLedger.session(level, mining.pos());
        boolean producedByUs = session.isOurProduct(stack);
        boolean seen = session.alreadySeen(stack);
        boolean processed = isProcessedThisTick(level, itemEntity);
        if (producedByUs || seen || processed) {
            // 第二层:产物识别 / getDrops 那条路已经处理过 / 本 tick 已处理过这个实体
            if (RingConfig.debugLog()) {
                DebugLog.info(
                    "{} watcher: skip {}x{} at {} (product={} seen={} processed={})", TAG,
                    stack.getCount(), AutoSmeltPipeline.idOf(stack), mining.pos(), producedByUs, seen, processed);
            }
            return;
        }
        // 第二层(形态二):这一堆本身就是"某条熔炼配方的产物"(如 iron_ingot)⇒ 认定已经熔过,跳过
        if (AutoSmeltHandler.isSmeltingProduct(level, stack)) {
            if (RingConfig.debugLog()) {
                DebugLog.info("{} watcher: skip {}x{} at {} (already a SMELTING output)", TAG,
                    stack.getCount(), AutoSmeltPipeline.idOf(stack), mining.pos());
            }
            return;
        }
        List<ItemStack> smelted = AutoSmeltHandler.smeltOne(level, stack);
        if (smelted.size() == 1 && smelted.get(0) == stack) {
            if (RingConfig.debugLog()) {
                DebugLog.info("{} watcher: no SMELTING recipe for {}x{} at {}", TAG, stack.getCount(),
                    AutoSmeltPipeline.idOf(stack), mining.pos());
            }
            return;
        }
        // 就地替换:第一个堆写回实体,其余(极少数超堆情况)另 spawn,并带抑制标记
        ItemStack first = smelted.get(0);
        String from = stack.getCount() + "x" + AutoSmeltPipeline.idOf(stack);
        for (ItemStack produced : smelted) {
            session.recordProduced(produced);
        }
        itemEntity.setItem(first.copy());
        for (int i = 1; i < smelted.size(); i++) {
            spawn(level, itemEntity.position(), smelted.get(i));
        }
        if (RingConfig.debugLog()) {
            int total = 0;
            for (ItemStack produced : smelted) {
                total += produced.getCount();
            }
            DebugLog.info(
                "{} fired(item) player={} tool={} dropsBefore=[{}] smeltLookup ok -> dropsAfter=[{}x{}] written=true pos={}",
                TAG, mining.player(), mining.toolId(), from, total, AutoSmeltPipeline.idOf(first), mining.pos());
        }
    }

    /** 自带抑制标记的 spawn(这些堆已经记进账本,不会被再处理)。 */
    private static void spawn(ServerLevel level, Vec3 position, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, position.x, position.y, position.z, stack);
        entity.setDefaultPickUpDelay();
        int depth = SUPPRESSED.get();
        SUPPRESSED.set(depth + 1);
        try {
            level.addFreshEntity(entity);
        }
        finally {
            SUPPRESSED.set(depth);
        }
    }

    /** 第三层去重:这个实体本 tick 是否已经处理过。 */
    private static boolean isProcessedThisTick(ServerLevel level, ItemEntity entity) {
        long tick = level.getGameTime();
        if (tick != processedTick) {
            processedTick = tick;
            PROCESSED.clear();
        }
        return !PROCESSED.add(entity.getUUID());
    }

    /** 自检用:清空 tick 去重状态。 */
    public static void reset() {
        PROCESSED.clear();
        processedTick = Long.MIN_VALUE;
        SUPPRESSED.set(0);
    }
}
