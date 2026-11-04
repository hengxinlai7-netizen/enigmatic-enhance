package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.7.2:「渔获瓶」({@code enigmaticenhance:fishing_haul_bottle})。
 *
 * <p>来源:大地之瓶 + 海洋之心无序合成。携带/手持时,每点幸运值 +10% 额外钓鱼产物,
 * 超过 100% 允许多次结算;放入古旧书袋时额外 +2 幸运。实际效果见 {@link FishingHaulBottleHandler}。</p>
 */
public class FishingHaulBottleItem extends Item {

    public static final int STACK_SIZE = 16;

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.fishing_haul_bottle.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.fishing_haul_bottle.lore";
    private static final String EFFECT_KEY = "tooltip.enigmaticenhance.fishing_haul_bottle.effect";
    private static final String STATE_KEY = "tooltip.enigmaticenhance.fishing_haul_bottle.state";

    public FishingHaulBottleItem() {
        super(new Item.Properties().stacksTo(STACK_SIZE));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(HEADER_KEY).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(EFFECT_KEY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(STATE_KEY).withStyle(ChatFormatting.GRAY));
    }
}
