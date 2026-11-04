package com.hengx.enigmaticenhance;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 「大地护符」物品本体(1.1.24)。
 * <p>
 * 无耐久、不可堆叠、走 Curios 的 {@code curios:charm} 槽(槽位由
 * {@code data/curios/tags/items/charm.json} 声明,Curios 5.x 的标准做法)。
 * 初始没有任何效果,NBT 结构见 {@link EarthAmuletData}。
 * <p>
 * 佩戴效果全部委托给 {@link EarthAmuletHandler}(事件优先,仅 3 处 mixin)。
 */
public class EarthAmuletItem extends Item implements ICurioItem {

    public EarthAmuletItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    // ------------------------------------------------------------------ 佩戴

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            EarthAmuletHandler.onWornTick(player, stack);
        }
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack previousStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            EarthAmuletHandler.onWornTick(player, stack);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            EarthAmuletHandler.revokeFlight(player);
        }
    }

    /**
     * 1.2.13g:大地护符**只能佩戴一个**。
     * <p>Curios 本身只按"槽位格子"判空,魅力槽被别的模组加宽后能同时塞两枚,
     * 两枚的效果会叠着走(回血/头颅强化重复触发)。这里在装备入口直接拦掉第二枚。</p>
     */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        try {
            if (!(slotContext.entity() instanceof Player player)) {
                return true;
            }
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(
                    worn -> worn.getItem() == this && worn != stack).isEmpty())
                .orElse(true);
        }
        catch (Throwable t) {
            return true;
        }
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.earth_amulet.header")
            .withStyle(ChatFormatting.GOLD));

        List<EarthAmuletData.StoredEffect> effects = EarthAmuletData.effects(stack);
        List<String> heads = EarthAmuletData.heads(stack);
        boolean empowered = EarthAmuletData.isEmpowered(stack);
        boolean any = false;

        // 1.1.25d:只显示**当前真实状态**,最多 3 行(heads 每颗一行);没有任何状态时才显示"可合成"提示。
        if (!effects.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (EarthAmuletData.StoredEffect stored : effects) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(effectName(stored.id()).getString()).append(' ').append(roman(stored.level()));
            }
            tooltip.add(Component.translatable("tooltip.enigmaticenhance.earth_amulet.potion", text.toString())
                .withStyle(ChatFormatting.AQUA));
            any = true;
        }
        for (String head : heads) {
            tooltip.add(Component.translatable(
                "tooltip.enigmaticenhance.earth_amulet.head." + EarthAmuletData.headLangPath(head))
                .withStyle(ChatFormatting.YELLOW));
            any = true;
        }
        if (empowered) {
            tooltip.add(Component.translatable("tooltip.enigmaticenhance.earth_amulet.empowered")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            any = true;
        }
        if (!any) {
            tooltip.add(Component.translatable("tooltip.enigmaticenhance.earth_amulet.empty")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 药水效果显示名(本地化;取不到就退回注册表 id)。 */
    private static Component effectName(String effectId) {
        MobEffect effect = EarthAmuletData.effect(effectId);
        return effect == null ? Component.literal(effectId) : effect.getDisplayName();
    }

    /** 等级 → 罗马数字(I..X,再往上直接用数字)。 */
    private static String roman(int level) {
        String[] numerals = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        if (level >= 1 && level <= numerals.length) {
            return numerals[level - 1];
        }
        return Integer.toString(level);
    }
}
