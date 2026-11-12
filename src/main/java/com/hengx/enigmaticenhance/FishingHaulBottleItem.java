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
 *
 * <p>4.8.1:书袋里那 +2 现在是**真正的 {@code generic.luck} 属性修饰符**(ADDITION +2,
 * 固定 UUID,transient 不写存档),不再是只活在一次结算里的局部变量 —— 玩家用
 * {@code /attribute} 能直接看到 2.0。见 {@link FishingHaulBottleHandler#BAG_LUCK}。</p>
 *
 * <p>4.8.0:堆叠上限由 16 改为 <b>1</b>,与其它五种瓶装产物(历史迷雾/灵界之光/冥河之水/
 * 战争迷雾/太阳/不定之雾/镜中之影)一致。它本身不存内容物,所以改 1 不涉及任何按数量
 * 分配的 NBT 逻辑:{@link FishingHaulBottleHandler} 只看"有没有",不看 {@code getCount()};
 * 书袋里一个槽位放一瓶也才是"+2 幸运"的正解。</p>
 */
public class FishingHaulBottleItem extends Item {

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.fishing_haul_bottle.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.fishing_haul_bottle.lore";
    private static final String EFFECT_KEY = "tooltip.enigmaticenhance.fishing_haul_bottle.effect";
    private static final String STATE_KEY = "tooltip.enigmaticenhance.fishing_haul_bottle.state";

    public FishingHaulBottleItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * 4.6.2(内存):4 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static final Component HEADER_LINE = Component.translatable(HEADER_KEY).withStyle(ChatFormatting.AQUA);
    private static final Component LORE_LINE = Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY);
    private static final Component EFFECT_LINE = Component.translatable(EFFECT_KEY).withStyle(ChatFormatting.GRAY);
    private static final Component STATE_LINE = Component.translatable(STATE_KEY).withStyle(ChatFormatting.GRAY);

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER_LINE);
        tooltip.add(LORE_LINE);
        tooltip.add(EFFECT_LINE);
        tooltip.add(STATE_LINE);
    }
}
