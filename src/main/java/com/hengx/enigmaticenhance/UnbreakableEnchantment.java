package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 1.2.9e:「无法破坏」(enigmaticenhance:unbreakable)。
 *
 * <p>宝藏附魔、限 1 级、**没有任何物品类型限制**;落到物品上时把自己的效果一次性结算完:
 * 给物品打原版 {@code Unbreakable:1b},然后把自己从物品上清除(见 {@link UnbreakableHandler})。</p>
 *
 * <p>因为效果是"一次性 + 自清除",它不会和别的附魔抢位置,也不会留在 tooltip 上。</p>
 */
public class UnbreakableEnchantment extends Enchantment {

    public UnbreakableEnchantment() {
        // 无限制:用 BREAKABLE 类别 + canEnchant 全放开(工具/武器/盔甲/盾都能吃)
        super(Rarity.VERY_RARE, EnchantmentCategory.BREAKABLE, EquipmentSlot.values());
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 30;
    }

    @Override
    public int getMaxCost(int level) {
        return 65;
    }

    /** 无附魔限制:任何物品都能附(包括本来就不可损坏的物品,附了也只是自清除)。 */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !RingConfig.autoSmeltLootOnly();
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}
