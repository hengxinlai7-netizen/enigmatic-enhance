package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 1.2.11a:「瓶装不定之雾」({@code enigmaticenhance:uncertain_fog})。
 *
 * <p>获得:手持大地之瓶右键**下界传送门方块**转化({@link UncertainFogHandler})。</p>
 * <p>放进古旧书袋/背包即生效:周围生物**无法传送**(末影人、诡厄巫法的 BOSS、觉醒的无名者
 * 以及其它百科里带传送能力的生物都会被拦住)。</p>
 */
public class UncertainFogItem extends Item {

    public UncertainFogItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.uncertain_fog.lore")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.uncertain_fog.effect")
            .withStyle(ChatFormatting.GRAY));
    }
}
