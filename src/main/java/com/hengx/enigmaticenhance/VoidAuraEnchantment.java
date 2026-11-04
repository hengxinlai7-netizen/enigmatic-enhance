package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 1.2.9a:「虚空剑气」(enigmaticenhance:void_aura)—— 魔法剑气的强化附魔。
 *
 * <p>限 1 级、宝藏、只认剑、只能由附魔台出。装备后释放的魔法剑气:</p>
 * <ul>
 *   <li>范围 +50%(飞行距离 ×1.5);</li>
 *   <li><b>无视实体</b>:不再被实体"挡停/吃掉命中数",路径上的所有实体都吃伤害;</li>
 *   <li>伤害无视无敌帧、无视无敌、无视限伤;</li>
 *   <li>伤害类型仍是 <b>非虚空</b>(indirectMagic,不是 void/out_of_world)。</li>
 * </ul>
 * 具体实现在 {@link MagicSwordAuraEntity}({@code voidAura} 标记)。
 */
public class VoidAuraEnchantment extends Enchantment {

    /** 范围倍率(+50%)。 */
    public static final double RANGE_MULTIPLIER = 1.5D;

    public VoidAuraEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
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

    /** 1.2.11a:这件物品是否已经有「魔法剑气」。 */
    public static boolean hasMagicSwordAura(ItemStack stack) {
        try {
            MagicSwordAuraEnchantment aura = ModEnchantments.magicSwordAuraOrNull();
            return aura != null && stack != null && !stack.isEmpty()
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(aura, stack) > 0;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }


    @Override
    public boolean canEnchant(ItemStack stack) {
        // 1.2.11a:只会与「魔法剑气」一起出现
        return stack.getItem() instanceof SwordItem && hasMagicSwordAura(stack);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack.getItem() instanceof SwordItem && hasMagicSwordAura(stack) && !RingConfig.autoSmeltLootOnly();
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
