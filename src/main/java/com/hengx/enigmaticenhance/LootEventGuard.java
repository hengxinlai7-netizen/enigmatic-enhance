package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 4.17.1：掉落物事件的"抢跑"保护。
 *
 * <p>斩杀补掉落时会调用 {@code dropAllDeathLoot}，它内部仍会派发 {@code LivingDropsEvent}。
 * 只要别的模组在事件里取消或清空掉落，补发就白做。</p>
 *
 * <p>本类在 <b>HIGHEST</b> 先把掉落列表拷走（早于所有其它处理器），
 * 在 <b>LOWEST + receiveCanceled</b> 强制解除取消并把列表还原 —— 这样"插手掉落事件"的模组
 * 无论何时注册、无论取消与否，都改不掉这次补发的结果。</p>
 */
public final class LootEventGuard {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<List<ItemEntity>> SNAPSHOT = new ThreadLocal<>();
    private static volatile boolean registered = false;

    private LootEventGuard() {
    }

    public static void ensureRegistered() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(LootEventGuard.class);
        }
    }

    public static void begin() {
        ACTIVE.set(Boolean.TRUE);
        SNAPSHOT.remove();
    }

    public static void end() {
        ACTIVE.set(Boolean.FALSE);
        SNAPSHOT.remove();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDropsHighest(LivingDropsEvent event) {
        if (Boolean.TRUE.equals(ACTIVE.get())) {
            SNAPSHOT.set(new ArrayList<>(event.getDrops()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDropsLowest(LivingDropsEvent event) {
        if (!Boolean.TRUE.equals(ACTIVE.get())) {
            return;
        }
        List<ItemEntity> snapshot = SNAPSHOT.get();
        if (snapshot == null) {
            return;
        }
        if (event.isCanceled()) {
            event.setCanceled(false);
        }
        java.util.Collection<ItemEntity> current = event.getDrops();
        current.clear();
        current.addAll(snapshot);
    }
}
