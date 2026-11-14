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
 * <p>来源:大地之瓶 + 海洋之心无序合成。效果见 {@link FishingHaulBottleHandler}:
 * 携带时渔获数量 ×{@link RingConfig#fishingHaulCatchMultiplier() 携带倍数}(默认 2),
 * 放进古旧书袋再×{@link RingConfig#fishingHaulBagMultiplier() 书袋倍数}(默认 1.5,共 ×3),
 * 另外书袋里还+{@link FishingHaulBottleHandler#BAG_LUCK 2 点幸运}。</p>
 *
 * <p>4.8.2:「多倍渔获」改成<b>真的倍数</b>。原来按"幸运/10"换算:书袋里 2 点幸运
 * ⇒ 整数部分 0、只有 20% 概率多 1 条,所以用户实测"多倍渔获没生效"。现在倍数是配置项
 * ({@code fishingHaulCatchMultiplier} / {@code fishingHaulBagMultiplier}),与幸运完全解耦:
 * 幸运只管宝藏概率,倍数只管数量。</p>
 *
 * <p>4.8.1:书袋里那 +2 是**真正的 {@code generic.luck} 属性修饰符**(ADDITION +2,
 * 固定 UUID,transient 不写存档),玩家用 {@code /attribute} 能看到 2.0。
 * 见 {@link FishingHaulBottleHandler#BAG_LUCK}。</p>
 *
 * <p>4.8.0:堆叠上限由 16 改为 <b>1</b>,与其它五种瓶装产物(历史迷雾/灵界之光/冥河之水/
 * 战争迷雾/太阳/不定之雾/镜中之影)一致。它本身不存内容物,所以改 1 不涉及任何按数量
 * 分配的 NBT 逻辑:{@link FishingHaulBottleHandler} 只看"有没有",不看 {@code getCount()};
 * 书袋里一个槽位放一瓶也才是"书袋加成"的正解。</p>
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
