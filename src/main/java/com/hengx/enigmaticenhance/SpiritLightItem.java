package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.2.6a:「瓶装灵界之光」({@code enigmaticenhance:spirit_light})。
 *
 * <p>来源:{@code 大地之瓶}放在<b>副手</b>(仅副手)且玩家带着它累计移动超过
 * {@value SpiritLightHandler#TRANSFORM_DISTANCE} 格后转化(见
 * {@link SpiritLightHandler#tickOffhandBottle})。</p>
 *
 * <p>效果(在背包/副手/古旧书袋里都生效):击杀生物时,每一份掉落物额外再来一份
 * (数量固定为该份掉落减去抢夺等级的近似值,所以额外这份<b>不吃抢夺</b>);
 * 隐藏效果:手持武器带神化「战利品大爆发」词缀时,击杀<b>必定触发</b>该词缀。</p>
 */
public class SpiritLightItem extends Item {

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.spirit_light.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.spirit_light.lore";
    private static final String EXTRA_KEY = "tooltip.enigmaticenhance.spirit_light.extra";
    private static final String XP_KEY = "tooltip.enigmaticenhance.spirit_light.xp";

    public SpiritLightItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * 4.6.2(内存):4 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static final Component HEADER_LINE = Component.translatable(HEADER_KEY).withStyle(ChatFormatting.AQUA);
    private static final Component LORE_LINE = Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY);
    private static final Component EXTRA_LINE = Component.translatable(EXTRA_KEY).withStyle(ChatFormatting.GRAY);
    private static final Component XP_LINE = Component.translatable(XP_KEY).withStyle(ChatFormatting.GRAY);

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER_LINE);
        tooltip.add(LORE_LINE);
        tooltip.add(EXTRA_LINE);
        tooltip.add(XP_LINE);
    }
}
