package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 1.2.9a:「元素魔法」(enigmaticenhance:elemental_magic)。
 *
 * <p>限 1 级、宝藏附魔、只认剑、<b>只能由附魔台出</b>(不可交易、不进战利品)。</p>
 *
 * <p>效果:造成伤害时,按自身「学派强度」属性追加一段<b>法术属性伤害</b>:
 * 每 1% 学派强度 ⇒ 额外 1% 伤害。学派强度取玩家身上所有
 * {@code *_spell_power} 属性(铁魔法自带的火焰/冰霜/雷电…,以及其它扩展新增的学派属性,
 * 只要铁魔法注册了这个属性就一起吃),见 {@link ElementalMagicHandler#schoolPowerBonus}。
 * 魔法剑气命中时同样吃这份加成。</p>
 *
 * <p>铁魔法(Iron's Spells 'n Spellbooks)不在时不注册这个附魔 —— 见
 * {@link ModEnchantments#ELEMENTAL_MAGIC}。</p>
 */
public class ElementalMagicEnchantment extends Enchantment {

    /** 最高级(按需求限 1 级)。 */
    public static final int MAX_LEVEL = 1;

    public ElementalMagicEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinCost(int level) {
        return 25;
    }

    @Override
    public int getMaxCost(int level) {
        return 60;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return isSupportedWeapon(stack);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        // 附魔台判定:剑 / 弓 / 弩 / TACZ 枪都能出(见 isSupportedWeapon)。
        return isSupportedWeapon(stack) && !RingConfig.autoSmeltLootOnly();
    }

    /**
     * 1.2.15:把「元素魔法」的适用范围从"剑"扩到<b>剑 / 弓 / 弩 / TACZ 枪</b>。
     * <p>TACZ 的枪不是 {@code ProjectileWeaponItem},所以按 id 前缀 {@code tacz:} 额外放行。</p>
     */
    public static boolean isSupportedWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            if (stack.getItem() instanceof net.minecraft.world.item.SwordItem) {
                return true;
            }
            if (stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) {
                return true;
            }
            var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id != null && "tacz".equals(id.getNamespace());
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        // 可被附魔台抽到(真正决定"只出附魔台"的是 isTradeable=false + isTreasureOnly)
        return true;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}
