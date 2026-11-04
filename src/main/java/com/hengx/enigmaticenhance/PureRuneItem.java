package com.hengx.enigmaticenhance;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 「纯净符文」物品本体(1.1.28c):可堆叠 16 的**投掷物**。
 * <p>
 * 右键把 {@link PureRuneProjectile} 扔出去(速度 1.6、散布 0.6,和雪球同一档手感),
 * 命中方块/实体或飞满 3 秒就在落点结算 —— 见 {@link PureRuneDamage}:
 * 半径 3 格、5 点魔法伤害(亡灵 15 点)、会被方块阻挡,落点放一道 1 秒的白光({@link PureRuneFlash})。
 * <p>
 * 配方:{@code data/enigmaticenhance/recipes/pure_rune.json}(石头 ×4 + 灵液滴,有序,一次 4 个)。
 */
public class PureRuneItem extends Item {

    public PureRuneItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            PureRuneProjectile projectile = new PureRuneProjectile(level, player);
            // 先复制一份再扣数量,否则 projectile 里的 item 会跟着变成空堆(客户端就看不到符文了)
            projectile.setItem(stack.copy());
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.6F, 0.6F);
            level.addFreshEntity(projectile);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.NEUTRAL, 0.6F, 1.2F);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(this, 8);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.pure_rune.header")
            .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.pure_rune.effect")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.pure_rune.ability")
            .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.pure_rune.blocked")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
