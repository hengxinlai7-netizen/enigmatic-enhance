package com.hengx.enigmaticenhance;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 「恶魔契约」物品本体(1.1.25)。
 * <p>
 * Curios 的 {@code curios:charm} 槽,无耐久、不可堆叠、**没有 NBT**。
 * 效果全在 {@link DemonPactHandler#onDamage}(伤害数值 = 当前生命值),tooltip 如实写出这个代价。
 * 目前**没有合成配方**(用户要求"后面再说");要补配方时:在
 * {@code src/main/resources/data/enigmaticenhance/recipes/} 下加一个 JSON
 * (或照 {@link EarthAmuletHeadRecipe} 写一个 {@code RecipeSerializer})即可,物品已注册好。
 */
public class DemonPactItem extends Item implements ICurioItem {

    public DemonPactItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    /** 1.1.28l:恶魔契约占的是**奥秘卷轴栏**(curios:scroll),和宇宙卷轴(EnigmaticLegacy)同一种物品类别。 */
    public static final String SCROLL_SLOT = "scroll";

    @Override
    public boolean canEquipFromUse(top.theillusivec4.curios.api.SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /**
     * 1.1.28l:佩戴时**+1 个奥秘卷轴栏位**。
     * <p>
     * 用 Curios 官方的"槽位修饰符"机制(与 EnigmaticLegacy 的 CosmicScroll 完全一致):
     * {@code ICuriosHelper#addSlotModifier(multimap, "scroll", uuid, 1, ADDITION)}。
     * Curios 会在装备/卸下时自动应用与撤销这个修饰符,并且修饰符是**每件物品固定 UUID**,
     * 所以不会重复叠加、重登也不会多给 —— 不需要自己写"戴上加/摘下减"的对账逻辑。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext,
                                                                        UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
        try {
            CuriosApi.getCuriosHelper().addSlotModifier(map, SCROLL_SLOT, uuid, 1.0D,
                AttributeModifier.Operation.ADDITION);
        }
        catch (Throwable ignored) {
            // Curios 版本不提供该 API 时退化为"只当装饰品",不影响其它逻辑
        }
        return map;
    }

    /**
     * 4.6.2(内存):5 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static Component tip(String key, ChatFormatting style) {
        return Component.translatable("tooltip.enigmaticenhance.demon_pact." + key).withStyle(style);
    }

    private static final Component HEADER = tip("header", ChatFormatting.DARK_RED);
    private static final Component ABILITY = tip("ability", ChatFormatting.RED);
    private static final Component COST = tip("cost", ChatFormatting.GRAY);
    private static final Component SCROLL_SLOT_LINE = tip("scroll_slot", ChatFormatting.LIGHT_PURPLE);
    private static final Component SET = tip("set", ChatFormatting.DARK_RED);

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 1.1.28e:描述只留"基础作用",具体数值/开关状态不再写进 tooltip(见说明.txt)
        tooltip.add(HEADER);
        tooltip.add(ABILITY);
        tooltip.add(COST);
        tooltip.add(SCROLL_SLOT_LINE);
        tooltip.add(SET);
    }
}
