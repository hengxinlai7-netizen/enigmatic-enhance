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

    /**
     * 4.6.2(内存):这 8 行 tooltip <b>全是常量</b>(翻译 key + 颜色都固定)⇒ 只建一次。
     *
     * <p>{@code appendHoverText} 在鼠标悬停这件物品期间是<b>每帧</b>调用(60~200 次/秒)。
     * 原来每帧每行都要 new 一个 {@code TranslatableComponent},再 {@code withStyle} 复制一份
     * (每次 {@code withStyle} 返回一个新的 {@code MutableComponent})—— 8 行 ≈ 16 个短命对象/帧。
     * {@code Component} 不可变,一次建好后反复 add 进不同 tooltip 列表是安全的(渲染只读);
     * 文字仍是渲染时才按当前语言解析,所以切换语言不受影响。</p>
     */
    private static Component tip(String key, ChatFormatting style) {
        return Component.translatable("tooltip.enigmaticenhance.demon_hand_bone." + key).withStyle(style);
    }

    private static final Component HEADER = tip("header", ChatFormatting.DARK_RED);
    private static final Component LORE = tip("lore", ChatFormatting.GRAY);
    private static final Component ATK = tip("atk", ChatFormatting.RED);
    private static final Component CURSE = tip("curse", ChatFormatting.RED);
    private static final Component DMG = tip("dmg", ChatFormatting.DARK_PURPLE);
    private static final Component ABSORB = tip("absorb", ChatFormatting.GOLD);
    private static final Component TRUE_DAMAGE = tip("true", ChatFormatting.DARK_RED);
    private static final Component ABILITY = tip("ability", ChatFormatting.RED);

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER);
        tooltip.add(LORE);
        tooltip.add(ATK);
        tooltip.add(CURSE);
        tooltip.add(DMG);
        tooltip.add(ABSORB);
        tooltip.add(TRUE_DAMAGE);
        tooltip.add(ABILITY);
    }
}
