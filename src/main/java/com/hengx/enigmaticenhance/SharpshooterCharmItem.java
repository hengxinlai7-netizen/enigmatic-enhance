package com.hengx.enigmaticenhance;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.2.17:「神射手护符」(enigmaticenhance:sharpshooter_charm)。
 *
 * <p>佩戴在 Curios 护符位时,对<b>弹射物</b>(箭/弩矢)生效:</p>
 * <ul>
 *   <li>弹射物伤害 +20%;</li>
 *   <li>箭矢初速固定为 2(不再受拉弓力度影响);</li>
 *   <li>必定暴击(必出暴击粒子/伤害);</li>
 *   <li>取消箭矢偏移(精度拉满,不再有散布)。</li>
 * </ul>
 *
 * <p>判定与实现见 {@link SharpshooterHandler} 与 {@code ArrowSharpshooterMixin}。</p>
 */
public class SharpshooterCharmItem extends Item implements top.theillusivec4.curios.api.type.capability.ICurioItem {

    public SharpshooterCharmItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    /**
     * 4.6.2(内存):6 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static Component tip(String key, ChatFormatting style) {
        return Component.translatable("tooltip.enigmaticenhance.sharpshooter_charm." + key).withStyle(style);
    }

    private static final Component HEADER = tip("header", ChatFormatting.GOLD);
    private static final Component LORE = tip("lore", ChatFormatting.GRAY);
    private static final Component DAMAGE = tip("damage", ChatFormatting.AQUA);
    private static final Component SPEED = tip("speed", ChatFormatting.AQUA);
    private static final Component CRIT = tip("crit", ChatFormatting.AQUA);
    private static final Component ACCURACY = tip("accuracy", ChatFormatting.AQUA);

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER);
        tooltip.add(LORE);
        tooltip.add(DAMAGE);
        tooltip.add(SPEED);
        tooltip.add(CRIT);
        tooltip.add(ACCURACY);
    }
}