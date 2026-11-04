package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 2.9.0:「执行之手」—— 造成伤害时转为真实伤害(写在 {@link HandOfExecutionHandler})。
 *
 * <p>限 1 级;无附魔限制(任何物品都能上);目前<b>没有任何获取途径</b>,
 * 也不会出现在战利品箱里({@link #isDiscoverable()} / {@link #isTradeable()} 均为 false)。</p>
 */
public class HandOfExecutionEnchantment extends Enchantment {

    public HandOfExecutionEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.BREAKABLE, EquipmentSlot.values());
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 1;
    }

    @Override
    public int getMaxCost(int level) {
        return 51;
    }

    /** 无限制:任何物品都能附。 */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return true;
    }

    /** 无限制:附魔台也能刷出来(但正常附魔池里看不到,见 isDiscoverable)。 */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return true;
    }

    /** 不出现在战利品/随机附魔里。 */
    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}