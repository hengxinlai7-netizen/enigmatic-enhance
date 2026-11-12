package com.hengx.enigmaticenhance;

import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.2.5c:「瓶装历史迷雾」({@code enigmaticenhance:history_mist})。
 *
 * <p>由「大地之瓶」在背包里放满 {@value HistoryMistHandler#TRANSFORM_TICKS} tick(0.5 小时)转化而来
 * (见 {@link HistoryMistHandler#tickTransformation}）。</p>
 *
 * <p>右键一个活体实体:把它所有已有的数值属性按
 * {@code min(目标该属性 × 0.1, 自身该属性 × 0.5)} 记进瓶子,单个瓶子**只能记录一次**;
 * 之后只要瓶子在背包/副手里,这些值就作为玩家的属性加成生效。</p>
 */
public class HistoryMistItem extends Item {

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.history_mist.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.history_mist.lore";
    private static final String EMPTY_KEY = "tooltip.enigmaticenhance.history_mist.empty";
    private static final String RECORDED_KEY = "tooltip.enigmaticenhance.history_mist.recorded";
    private static final String ACTIVE_KEY = "tooltip.enigmaticenhance.history_mist.active";

    /** tooltip 里最多列出多少条记录(多了只显示总数)。 */
    private static final int MAX_TOOLTIP_ENTRIES = 8;

    public HistoryMistItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return HistoryMistHandler.record(player, target, stack) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /**
     * 4.6.2(内存):4 行"纯常量"tooltip 只建一次(理由见 {@link DemonHandBoneItem})。
     * <p>{@code recorded} 行带记录条数、每条记录行带属性名与数值,都是动态内容,保持每帧重建
     * (记录数据是原地改 NBT 的,没有版本号可以拿来失效缓存)。</p>
     */
    private static final Component HEADER =
        Component.translatable(HEADER_KEY).withStyle(ChatFormatting.LIGHT_PURPLE);
    private static final Component LORE =
        Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY);
    private static final Component EMPTY_LINE =
        Component.translatable(EMPTY_KEY).withStyle(ChatFormatting.DARK_GRAY);
    private static final Component ACTIVE =
        Component.translatable(ACTIVE_KEY).withStyle(ChatFormatting.DARK_GRAY);

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER);
        tooltip.add(LORE);
        Map<ResourceLocation, Double> records = HistoryMistHandler.readRecords(stack);
        if (records.isEmpty()) {
            tooltip.add(EMPTY_LINE);
            return;
        }
        tooltip.add(Component.translatable(RECORDED_KEY, records.size()).withStyle(ChatFormatting.AQUA));
        int shown = 0;
        for (Map.Entry<ResourceLocation, Double> entry : records.entrySet()) {
            if (shown++ >= MAX_TOOLTIP_ENTRIES) {
                break;
            }
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(entry.getKey());
            Component name = attribute == null
                ? Component.literal(entry.getKey().toString())
                : Component.translatable(attribute.getDescriptionId());
            tooltip.add(Component.literal("· ").withStyle(ChatFormatting.GRAY)
                .append(name.copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" +" + format(entry.getValue())).withStyle(ChatFormatting.GREEN)));
        }
        tooltip.add(ACTIVE);
    }

    /** 数值显示:去掉多余的 0(1.20 → 1.2,7.0 → 7)。 */
    private static String format(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
