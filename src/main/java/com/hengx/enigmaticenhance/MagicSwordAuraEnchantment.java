package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 1.1.29a:魔法剑气。只能附在剑上,最高 3 级。
 * <p>
 * 效果:近战伤害 -80%,右键释放剑气(见 {@link MagicSwordAuraEntity}),命中数 = 附魔等级。
 * 不设获取限制(可附魔台/铁砧/箱子)。
 */
public class MagicSwordAuraEnchantment extends Enchantment {

    /** 近战伤害系数。 */
    public static final float MELEE_FACTOR = 0.2F;
    /** 每级剑气基础伤害(1.1.29c 起不再使用,剑气吃剑本身的基础攻击力)。 */
    public static final float DAMAGE_PER_LEVEL = 4.0F;
    /** 冷却基准 tick(1.1.29e 起按攻速缩放:cooldown = round(基准 / 攻速))。 */
    public static final int COOLDOWN_TICKS = 16;
    /** 冷却下限 tick,防止攻速极高时右键变成连发。 */
    public static final int MIN_COOLDOWN_TICKS = 3;
    /** 最高级。 */
    public static final int MAX_LEVEL = 3;

    /**
     * 1.1.29e:冷却 = 基准 / 主手攻速(攻速越高冷却越短)。
     * <p>
     * 攻速取 {@link net.minecraft.world.entity.ai.attributes.Attributes#ATTACK_SPEED},
     * 剑通常 1.6 ⇒ 10 tick(与原固定值一致);2.0 ⇒ 8 tick;1.0 ⇒ 16 tick;下限 3 tick。
     */
    public static int cooldownTicks(double attackSpeed) {
        double speed = attackSpeed <= 0.1D ? 0.1D : attackSpeed;
        int ticks = (int) Math.round(COOLDOWN_TICKS / speed);
        return Math.max(MIN_COOLDOWN_TICKS, ticks);
    }

    public MagicSwordAuraEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinCost(int level) {
        return 5 + 9 * (level - 1);
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 25;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
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
