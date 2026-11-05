package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.6.15:瓶装意识之海。
 *
 * <p>手持/副手时:无法造成伤害、无法受到伤害、生物不会仇恨你。
 * 由手持「大地之瓶」睡觉获得。</p>
 */
public class ConsciousnessSeaItem extends Item {

    public ConsciousnessSeaItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sea_of_consciousness.header")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sea_of_consciousness.line1")
            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sea_of_consciousness.line2")
            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.sea_of_consciousness.note")
            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
