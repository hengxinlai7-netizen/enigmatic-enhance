package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 与神秘遗物本体的联动查询(反射,缺模组时安全返回 false)。 */
public final class Superposition {

    private Superposition() {}

    /**
     * 取实体饰品栏里某个物品的 ItemStack(反射查 {@code SuperpositionHandler.getCurioStack})。
     * 没戴 / 缺模组时返回 {@link ItemStack#EMPTY},不抛异常。
     */
    public static ItemStack getCurioStack(LivingEntity entity, Item item) {
        if (entity == null || item == null) {
            return ItemStack.EMPTY;
        }
        try {
            Class<?> handler = Class.forName("com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler");
            Object result = handler.getMethod("getCurioStack", LivingEntity.class, Item.class).invoke(null, entity, item);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }
        catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 饰品栏是否佩戴(curios)。 */
    public static boolean hasCurio(LivingEntity entity, Item item) {
        if (entity == null || item == null) {
            return false;
        }
        try {
            Class<?> handler = Class.forName("com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler");
            Object result = handler.getMethod("hasCurio", LivingEntity.class, Item.class).invoke(null, entity, item);
            return result instanceof Boolean && (Boolean) result;
        }
        catch (Throwable t) {
            return false;
        }
    }

    /** 饰品栏 / 双手 / 背包 任一位置。 */
    public static boolean hasItem(Player player, Item item) {
        if (player == null || item == null) {
            return false;
        }
        if (hasCurio(player, item)) {
            return true;
        }
        if (player.getMainHandItem().is(item) || player.getOffhandItem().is(item)) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }
    /**
     * 扩展自己的"被惩罚者"判定:SuperAddonHandler.isPunishedOne = hasBlessRing && isTheCursedOne。
     * 其中 hasBlessRing = 玩家持久数据含 BlessNextSpawn **或** 佩戴救赎之戒 ——
     * 所以只在饰品栏找戒指会漏判(戒指已经被清/在背包时),必须直接问它。
     */
    public static boolean isPunishedOne(net.minecraft.world.entity.LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        try {
            Class<?> handler = Class.forName("auviotre.enigmatic.addon.handlers.SuperAddonHandler");
            Object result = handler.getMethod("isPunishedOne", net.minecraft.world.entity.LivingEntity.class)
                .invoke(null, entity);
            return result instanceof Boolean && (Boolean) result;
        }
        catch (Throwable t) {
            return false;
        }
    }
}