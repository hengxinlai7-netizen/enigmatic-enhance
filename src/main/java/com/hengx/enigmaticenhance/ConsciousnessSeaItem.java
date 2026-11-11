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

    /**
     * 4.6.2(内存):4 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static Component tip(String key, net.minecraft.ChatFormatting style) {
        return Component.translatable("tooltip.enigmaticenhance.sea_of_consciousness." + key).withStyle(style);
    }

    private static final Component HEADER = tip("header", net.minecraft.ChatFormatting.GRAY);
    private static final Component LINE1 = tip("line1", net.minecraft.ChatFormatting.DARK_GRAY);
    private static final Component LINE2 = tip("line2", net.minecraft.ChatFormatting.DARK_GRAY);
    private static final Component NOTE = tip("note", net.minecraft.ChatFormatting.DARK_GRAY);

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER);
        tooltip.add(LINE1);
        tooltip.add(LINE2);
        tooltip.add(NOTE);
    }
}
