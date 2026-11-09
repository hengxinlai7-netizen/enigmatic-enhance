package com.hengx.enigmaticenhance;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.5.2:永暗之寂的运行时。
 *
 * <p>带该效果时,**任何可右键使用的物品全部失效**:</p>
 * <ul>
 *   <li>右键物品 / 右键方块 / 空挥右键 / 右键实体 → 取消;</li>
 *   <li>已经开始的使用流程(弓、药水、食物、望远镜等)→ 取消并中断。</li>
 * </ul>
 */
public final class EternalSilenceHandler {

    private EternalSilenceHandler() {
    }

    public static boolean silenced(LivingEntity entity) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.eternalSilenceEnabled()) {
            return false;
        }
        MobEffect effect = ModEffects.ETERNAL_SILENCE.isPresent() ? ModEffects.ETERNAL_SILENCE.get() : null;
        return effect != null && entity != null && entity.hasEffect(effect);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        deny(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        deny(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        deny(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        deny(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        deny(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        try {
            if (silenced(event.getEntity())) {
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        try {
            if (silenced(event.getEntity())) {
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    private static void deny(PlayerInteractEvent event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide) {
                return;
            }
            if (silenced(player)) {
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}