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

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 「恶魔的手骨」物品本体(1.1.28e):Curios 的 {@code curios:hands} 手部槽,EPIC,不可堆叠。
 * <p>
 * 全部行为在 {@link DemonBoneHandler}:受伤加倍、攻击侧三个条件、三件套共鸣、以及冒黑烟(客户端粒子)。
 * tooltip 只写"基础作用"的一句话(用户要求:不写数值/条件/公式),具体数值见
 * {@code Downloads\enigmaticenhance-1.1.28e-说明.txt}。
 */
public class DemonHandBoneItem extends Item implements ICurioItem {

    public DemonHandBoneItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_hand_bone.header")
            .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_hand_bone.lore")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_hand_bone.atk")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_hand_bone.curse")
            .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_hand_bone.dmg")
            .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_hand_bone.absorb")
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_hand_bone.true")
            .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.demon_hand_bone.ability")
            .withStyle(ChatFormatting.RED));
    }
}
