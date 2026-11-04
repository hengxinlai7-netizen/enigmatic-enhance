package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.2.6b:「瓶装冥河之水」({@code enigmaticenhance:styx_water})。
 *
 * <p>来源:{@code 大地之瓶}在<b>副手</b>时,玩家触发不死图腾的那一刻转化
 * (见 {@link StyxWaterHandler#onTotemUse})。</p>
 *
 * <p>效果(背包/副手/古旧书袋里都生效):受到致命伤害时不死,把生命恢复到一半;
 * 每天(游戏内一天)只能免死一次,跨天自动刷新。</p>
 */
public class StyxWaterItem extends Item {

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.styx_water.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.styx_water.lore";
    private static final String EFFECT_KEY = "tooltip.enigmaticenhance.styx_water.effect";
    private static final String STATE_KEY = "tooltip.enigmaticenhance.styx_water.state";

    public StyxWaterItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(HEADER_KEY).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(EFFECT_KEY).withStyle(ChatFormatting.GRAY));
        if (level != null) {
            // 展示今天是否还能用(只能在有玩家上下文时算,这里用世界时间粗略显示)
            tooltip.add(Component.translatable(STATE_KEY).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
