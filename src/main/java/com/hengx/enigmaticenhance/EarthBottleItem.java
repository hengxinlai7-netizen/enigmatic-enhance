package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.2.5b:「大地之瓶」({@code enigmaticenhance:earth_bottle})。
 *
 * <p>来源:玻璃瓶 + 大地之心碎片({@code enigmaticaddons:earth_heart_fragment})无序合成。
 * 目前是普通物品(堆叠 16、无耐久),材质是在原版玻璃瓶内部加了一簇蓝白像素。</p>
 *
 * <p>单独建类而不是直接 {@code new Item(...)}:以后要加"喝/倒/存能量"之类的行为时,
 * 只需要往这个类里加逻辑,注册与资源路径都不用动。</p>
 */
public class EarthBottleItem extends Item {

    /** 一个瓶子的最大堆叠数(与玻璃瓶一致)。 */
    public static final int STACK_SIZE = 16;

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.earth_bottle.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.earth_bottle.lore";

    public EarthBottleItem() {
        super(new Item.Properties().stacksTo(STACK_SIZE));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(HEADER_KEY).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY));
    }
}
