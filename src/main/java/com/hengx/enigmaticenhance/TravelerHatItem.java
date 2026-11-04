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
 * 「旅行家帽子」物品本体(1.1.25c):Curios 的 {@code curios:head} 头饰槽,无耐久、不可堆叠。
 * <p>
 * 三个加成(速度 / 生命上限 / 攻击力)全部由 {@link TravelerHatHandler} 用固定 UUID 的
 * {@link net.minecraft.world.entity.ai.attributes.AttributeModifier} 挂在**佩戴者**身上:
 * 戴上就加、取下立即删,数值随"已探索群系/维度"计数变化。tooltip 里的计数来自
 * {@link TravelerHatSync}(服务端 S2C 同步过来的客户端缓存)。
 * <p>
 * 目前**没有合成配方**(用户没给);以后补:在
 * {@code src/main/resources/data/enigmaticenhance/recipes/} 下加一个 JSON(无序/有序都行),
 * 物品与头饰槽已注册好,不需要再改 Java。
 */
public class TravelerHatItem extends Item implements ICurioItem {

    public TravelerHatItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            TravelerHatHandler.onWornTick(player, stack);
        }
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack previousStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            TravelerHatHandler.recordCurrent(player);
            TravelerHatHandler.applyBonuses(player);
            TravelerHatHandler.syncIfChanged(player);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            TravelerHatHandler.removeBonuses(player);
        }
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.traveler_hat.header")
            .withStyle(ChatFormatting.GOLD));
        double speed = RingConfig.travelerHatSpeedValue();
        if (RingConfig.travelerHatSpeedIsPercent()) {
            tooltip.add(Component.translatable("tooltip.enigmaticenhance.traveler_hat.speed_percent",
                percent(speed)).withStyle(ChatFormatting.AQUA));
        }
        else {
            tooltip.add(Component.translatable("tooltip.enigmaticenhance.traveler_hat.speed_flat", speed)
                .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.traveler_hat.biome",
            format(RingConfig.travelerHatHealthPerBiome())).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.traveler_hat.dimension",
            format(RingConfig.travelerHatAttackPerDimension())).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.traveler_hat.head_slot")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
    private static String percent(double value) {
        return format(value * 100.0D);
    }

    /** 去掉多余的小数(2.0 -> 2,2.5 -> 2.5),tooltip 更干净。 */
    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
