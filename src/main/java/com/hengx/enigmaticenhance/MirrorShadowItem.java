package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 1.2.11a:「瓶装镜中之影」({@code enigmaticenhance:mirror_shadow})。
 *
 * <p>获得:手持大地之瓶右键**两格高玻璃类方块的下半块**({@link MirrorShadowHandler}),
 * 两格玻璃破碎、大地之瓶变成它,并在这一刻记录玩家位置/状态/血量/饱食度/饱和度。</p>
 *
 * <p>使用:手持右键 **1 秒**(药水饮用动作)⇒ 回溯到记录位置并把状态替换成记录值,
 * 同时获得 3 分钟「镜碎」(受伤 +100%)。它是**消耗品**,且同一个玩家同时只能持有一个未使用的
 * (再想获得会被打断,见 handler)。</p>
 */
public class MirrorShadowItem extends Item {

    /** 饮用时长:1 秒。 */
    public static final int USE_TICKS = 20;

    public MirrorShadowItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);   // 刚获得时的冷却:不许立刻触发
        }
        if (!MirrorShadowHandler.hasRecord(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player) {
            if (!level.isClientSide()) {
                MirrorShadowHandler.restore(player, stack);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.mirror_shadow.lore")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.mirror_shadow.use")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.enigmaticenhance.mirror_shadow.cost")
            .withStyle(ChatFormatting.RED));
        if (MirrorShadowHandler.hasRecord(stack)) {
            tooltip.add(Component.translatable("tooltip.enigmaticenhance.mirror_shadow.recorded",
                MirrorShadowHandler.recordedText(stack)).withStyle(ChatFormatting.DARK_AQUA));
        }
        else {
            tooltip.add(Component.translatable("tooltip.enigmaticenhance.mirror_shadow.empty")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
