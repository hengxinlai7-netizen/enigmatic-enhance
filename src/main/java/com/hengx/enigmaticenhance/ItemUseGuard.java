package com.hengx.enigmaticenhance;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 2.5.0:统一拦截被禁用的物品。
 *
 * <p>配置见 {@code config/enigmaticenhance-items.toml} 的 {@code [items].disabledItemIds}。
 * 被禁用的物品:右键无效、右键方块无效、右键实体无效 —— 但**不会被删掉**，
 * 已经存在的物品还在，只是用不了；把 id 从列表里删掉就立刻恢复。</p>
 *
 * <p>做法是拦 {@link PlayerInteractEvent}，不碰物品注册，所以对存档安全，也不影响配方。</p>
 */
public final class ItemUseGuard {

    private ItemUseGuard() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isDisabled(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isDisabled(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isDisabled(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    private static boolean isDisabled(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id != null && ItemConfig.isItemDisabled(id.toString());
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
