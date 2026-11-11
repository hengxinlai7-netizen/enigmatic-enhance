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

    /**
     * 4.6.2(内存):这 5 行全是常量(开关状态只有两种,各建一份)⇒ 只建一次。
     *
     * <p>{@link #onTooltip} 在鼠标悬停该物品期间<b>每帧</b>触发(60~200 次/秒),原来每帧要新建
     * 4 个 {@code TranslatableComponent} 再各 {@code withStyle} 复制一份。
     * {@code Component} 不可变,复用安全(理由同 {@link DemonHandBoneItem})。</p>
     */
    private static final Component HEADER_LINE = Component.translatable(HEADER_KEY).withStyle(ChatFormatting.GREEN);
    private static final Component EFFECT_LINE = Component.translatable(EFFECT_KEY).withStyle(ChatFormatting.GRAY);
    private static final Component STATE_LINE = Component.translatable(STATE_KEY).withStyle(ChatFormatting.GRAY);
    private static final Component FLIGHT_ON_LINE =
        Component.translatable(FLIGHT_ON_KEY).withStyle(ChatFormatting.GREEN);
    private static final Component FLIGHT_OFF_LINE =
        Component.translatable(FLIGHT_OFF_KEY).withStyle(ChatFormatting.RED);

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        try {
            ItemStack stack = event.getItemStack();
            if (!RevivalLeafStoneHandler.isStone(stack)) {
                return;
            }
            List<Component> lines = event.getToolTip();
            lines.add(HEADER_LINE);
            lines.add(EFFECT_LINE);
            lines.add(STATE_LINE);
            boolean flightOff = RevivalLeafStoneHandler.isDisabled(stack);
            lines.add(flightOff ? FLIGHT_OFF_LINE : FLIGHT_ON_LINE);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 工具提示失败不影响物品
        }
    }
}
