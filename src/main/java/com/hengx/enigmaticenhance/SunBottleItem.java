package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.2.6d:「瓶装太阳」({@code enigmaticenhance:bottled_sun})。
 *
 * <p>来源:手持{@code 大地之瓶}在<b>白天 10:00~14:00</b>时右键金块转化
 * (见 {@link SunBottleHandler#onRightClickBlock})。</p>
 *
 * <p>效果(背包/副手/古旧书袋里都生效):对亡灵生物造成的伤害 +10%;
 * 持续获得饱和与生命回复 I。</p>
 */
public class SunBottleItem extends Item {

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.bottled_sun.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.bottled_sun.lore";
    private static final String EFFECT_KEY = "tooltip.enigmaticenhance.bottled_sun.effect";

    public SunBottleItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * 4.6.2(内存):3 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static final Component HEADER_LINE = Component.translatable(HEADER_KEY).withStyle(ChatFormatting.GOLD);
    private static final Component LORE_LINE = Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY);
    private static final Component EFFECT_LINE = Component.translatable(EFFECT_KEY).withStyle(ChatFormatting.GRAY);

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER_LINE);
        tooltip.add(LORE_LINE);
        tooltip.add(EFFECT_LINE);
    }
}
