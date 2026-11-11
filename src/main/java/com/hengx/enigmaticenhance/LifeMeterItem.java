package com.hengx.enigmaticenhance;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.2.14:「生命刻度」(enigmaticenhance:life_meter)。
 *
 * <p>把原来"最大生命 > 60 就自动覆盖心形血条"的兜底(HUD)改成一个**可选道具**:
 * 放在背包/副手(或饰品栏)里时,客户端会在心形血条**上方**多画一条
 * 「当前 / 最大」的数字血条;没有这件道具就完全是原版心形,不介入任何渲染。</p>
 *
 * <p>为什么做成道具:高生命值(神化词条、莱特兰等级、模组成长)时数字更直观,
 * 但大多数时候玩家并不想要多出来的一条血条 —— 让玩家自己决定。</p>
 */
public class LifeMeterItem extends Item {

    public LifeMeterItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    /**
     * 4.6.2(内存):3 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static Component tip(String key, ChatFormatting style) {
        return Component.translatable("tooltip.enigmaticenhance.life_meter." + key).withStyle(style);
    }

    private static final Component HEADER = tip("header", ChatFormatting.GOLD);
    private static final Component EFFECT = tip("effect", ChatFormatting.AQUA);
    private static final Component NOTE = tip("note", ChatFormatting.DARK_GRAY);

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER);
        tooltip.add(EFFECT);
        tooltip.add(NOTE);
    }
}
