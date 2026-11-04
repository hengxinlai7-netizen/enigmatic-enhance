package com.hengx.enigmaticenhance;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 「恶魔血杯」物品本体(1.1.28a)。
 * <p>
 * Curios 的 {@code curios:charm} 槽(槽位由 {@code data/curios/tags/items/charm.json} 声明,
 * 与大地护符 / 恶魔契约 / 旅行家帽子同一套做法),无耐久、不可堆叠、没有任何 NBT。
 * <p>
 * 行为全部委托给 {@link DemonBloodChaliceHandler}:
 * <ul>
 * <li>佩戴期间:所有药水效果**保留在状态栏**但**不产生任何实际作用**(属性型/每 tick 型/瞬时型都拦掉);</li>
 * <li>联动一:每个正面效果 ⇒「血战沙场之证」的已损失生命判定 +1%(上限 100%);</li>
 * <li>联动二:每个负面效果 ⇒ 视为 1 个诅咒附魔(千咒卷轴吃这个计数);</li>
 * <li>摘下:效果立刻恢复生效,不需要重新喝药。</li>
 * </ul>
 * 目前**没有合成配方**(用户没给);以后补配方时只需在
 * {@code src/main/resources/data/enigmaticenhance/recipes/} 下加一个 JSON,不用改 Java。
 */
public class DemonBloodChaliceItem extends Item implements ICurioItem {

    public DemonBloodChaliceItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack previousStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            DemonBloodChaliceHandler.onEquipped(player);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            DemonBloodChaliceHandler.onUnequipped(player);
        }
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_blood_chalice.header")
            .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_blood_chalice.lore")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_blood_chalice.detail")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_blood_chalice.ability")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_blood_chalice.restore")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
