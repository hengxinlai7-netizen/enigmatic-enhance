package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 2.6.1:「复苏之叶术石」({@code enigmaticenhance:revival_leaf_stone})。
 *
 * <p>Curios 饰品(常驻 {@code curios:charm} 槽)。佩戴时可用 <b>Ctrl+左键</b> 切换
 * "是否允许龙首护符授予飞行" —— 关掉后即使戴着龙首也不会获得创造飞行,也不会穿方块。</p>
 */
public class RevivalLeafStoneItem extends Item implements ICurioItem {

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.revival_leaf_stone.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.revival_leaf_stone.lore";
    private static final String EFFECT_KEY = "tooltip.enigmaticenhance.revival_leaf_stone.effect";
    private static final String STATE_KEY = "tooltip.enigmaticenhance.revival_leaf_stone.state";
    private static final String FLIGHT_ON_KEY = "tooltip.enigmaticenhance.revival_leaf_stone.flight.on";
    private static final String FLIGHT_OFF_KEY = "tooltip.enigmaticenhance.revival_leaf_stone.flight.off";

    public RevivalLeafStoneItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(HEADER_KEY).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(EFFECT_KEY).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(STATE_KEY).withStyle(ChatFormatting.GRAY));
        // 2.6.2:实时显示飞行开关状态
        boolean flightOff = RevivalLeafStoneHandler.isDisabled(stack);
        tooltip.add(Component.translatable(flightOff ? FLIGHT_OFF_KEY : FLIGHT_ON_KEY)
            .withStyle(flightOff ? ChatFormatting.RED : ChatFormatting.GREEN));
    }
}
