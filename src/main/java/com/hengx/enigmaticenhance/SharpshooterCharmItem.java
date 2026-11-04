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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sharpshooter_charm.header")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sharpshooter_charm.lore")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sharpshooter_charm.damage")
            .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sharpshooter_charm.speed")
            .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sharpshooter_charm.crit")
            .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sharpshooter_charm.accuracy")
            .withStyle(ChatFormatting.AQUA));
    }
}