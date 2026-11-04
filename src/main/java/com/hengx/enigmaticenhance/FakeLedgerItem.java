package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 2.4.0:假账生成器 {@code enigmaticenhance:fake_ledger}（创造物品）。
 *
 * <p>右键打开"统计信息编辑器"，可以直接改自己的统计数据（跳跃次数、死亡次数、挖掘量……）。</p>
 *
 * <p><b>防滥用</b>（按"别做成能玩的东西"处理）：</p>
 * <ul>
 *   <li>只能在<b>创造模式</b>下打开与修改，生存/冒险拿在手里右键只会提示；</li>
 *   <li>服务端二次校验：每个改动包都要求 {@code player.isCreative()}，否则直接丢弃；</li>
 *   <li>只改<b>自己</b>的统计，不能指名别人；数值夹在 0 ~ 2,000,000,000。</li>
 * </ul>
 */
public class FakeLedgerItem extends Item {

    private static final String HEADER_KEY = "tooltip.enigmaticenhance.fake_ledger.header";
    private static final String LORE_KEY = "tooltip.enigmaticenhance.fake_ledger.lore";

    public FakeLedgerItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCreative()) {
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.enigmaticenhance.fake_ledger.creative_only")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.hengx.enigmaticenhance.client.FakeLedgerScreen.open());
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(HEADER_KEY).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(LORE_KEY).withStyle(ChatFormatting.GRAY));
    }
}
