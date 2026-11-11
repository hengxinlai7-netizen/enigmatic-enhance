package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.2.6c:「瓶装战争迷雾」({@code enigmaticenhance:war_fog})。
 *
 * <p>来源:{@code 大地之瓶}放在<b>副手</b>时,用它累计击杀
 * {@value WarFogHandler#KILLS_REQUIRED} 个生物后转化
 * (见 {@link WarFogHandler#onDeath})。</p>
 *
 * <p>效果(背包/副手/古旧书袋里都生效):周围 10 格内的生物仇恨变成"无差别" ——
 * 它们会互相攻击,也会攻击玩家。</p>
 */
public class WarFogItem extends Item {

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.war_fog.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.war_fog.lore";
    private static final String EFFECT_KEY = "tooltip.enigmaticenhance.war_fog.effect";

    public WarFogItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * 4.6.2(内存):3 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static final Component HEADER_LINE = Component.translatable(HEADER_KEY).withStyle(ChatFormatting.RED);
    private static final Component LORE_LINE = Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY);
    private static final Component EFFECT_LINE = Component.translatable(EFFECT_KEY).withStyle(ChatFormatting.GRAY);

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER_LINE);
        tooltip.add(LORE_LINE);
        tooltip.add(EFFECT_LINE);
    }
}
