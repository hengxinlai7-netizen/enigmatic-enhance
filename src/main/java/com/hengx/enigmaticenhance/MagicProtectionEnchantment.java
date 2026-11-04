package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 1.1.29a:魔法保护。每级减少 4% 魔法伤害(上限 4 级 = 16%)。
 * <p>
 * 不设获取限制:不是 treasure、不是 curse,附魔台/铁砧/战利品箱都能出。
 * 数值计算在 {@link MagicCombatHandler} 里,这里只描述"能附在什么上、几级、多贵"。
 */
public class MagicProtectionEnchantment extends Enchantment {

    /** 每级减伤比例。 */
    public static final float PER_LEVEL = 0.04F;
    /** 最高级(4 级)时的减伤上限。 */
    public static final float CAP = PER_LEVEL * 4.0F;

    public MagicProtectionEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR,
            new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET });
    }

    @Override
    public int getMaxLevel() {
        return 4;
    }

    @Override
    public int getMinCost(int level) {
        return 1 + 11 * (level - 1);
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

    /**
     * 附魔台只给护甲(1.2.2c)。
     * <p>
     * 原来这里一律返回 {@code true},而附魔台的候选判定看的就是这个方法 ——
     * 原版 Forge 的补丁如此,装了神化更甚:神化的
     * {@code RealEnchantmentHelper#getAvailableEnchantmentResults} 只调这一个,不调 {@code canEnchant}。
     * 结果就是自检里实测到的「镐·能量15 → 魔法保护 = true」,即附魔台会给镐子提供护甲附魔。
     * 现在改成与 {@link #canEnchant} 同一套判定(即 {@link EnchantmentCategory#ARMOR}),只认护甲。
     */
    @Override
    public boolean canApplyAtEnchantingTable(net.minecraft.world.item.ItemStack stack) {
        // 注意:不能写成 canEnchant(stack) —— Forge 把 Enchantment#canEnchant 反过来接到了
        // canApplyAtEnchantingTable 上,两者互叫会 StackOverflow(1.2.2c 实跑撞到过)。
        // 直接问类别最安全:ARMOR 只认护甲。
        return stack != null && !stack.isEmpty() && this.category.canEnchant(stack.getItem());
    }

    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public boolean isTradeable() {
        return true;
    }
}
