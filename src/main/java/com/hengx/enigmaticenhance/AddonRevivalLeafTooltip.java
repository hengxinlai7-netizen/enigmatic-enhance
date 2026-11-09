package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 4.5.0:给<b>神秘遗物扩展自带的</b>「复苏之叶」追加说明与飞行开关状态
 * (从 4.4.0 之前取回,并在主模组内独立提供)。
 *
 * <p>原物品本身不用替换,只通过 {@link ItemTooltipEvent} 往它的 tooltip 里追加几行。</p>
 */
public final class AddonRevivalLeafTooltip {

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.addon_revival_leaf.header";
    private static final String EFFECT_KEY = "tooltip.enigmaticenhance.addon_revival_leaf.effect";
    private static final String STATE_KEY = "tooltip.enigmaticenhance.addon_revival_leaf.state";
    private static final String FLIGHT_ON_KEY = "tooltip.enigmaticenhance.addon_revival_leaf.flight.on";
    private static final String FLIGHT_OFF_KEY = "tooltip.enigmaticenhance.addon_revival_leaf.flight.off";

    private AddonRevivalLeafTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        try {
            ItemStack stack = event.getItemStack();
            if (!RevivalLeafStoneHandler.isStone(stack)) {
                return;
            }
            List<Component> lines = event.getToolTip();
            lines.add(Component.translatable(HEADER_KEY).withStyle(ChatFormatting.GREEN));
            lines.add(Component.translatable(EFFECT_KEY).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(STATE_KEY).withStyle(ChatFormatting.GRAY));
            boolean flightOff = RevivalLeafStoneHandler.isDisabled(stack);
            lines.add(Component.translatable(flightOff ? FLIGHT_OFF_KEY : FLIGHT_ON_KEY)
                .withStyle(flightOff ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
        catch (RuntimeException | LinkageError ignored) {
            // 工具提示失败不影响物品
        }
    }
}
