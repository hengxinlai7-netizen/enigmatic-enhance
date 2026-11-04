package com.hengx.enigmaticenhance;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * 1.3.1:数值编辑器(创造专用)。
 *
 * <p>右键 = 编辑自己;左键攻击实体 = 编辑目标(见 {@link ValueEditorHandler})。
 * 打开的是 {@link ValueEditorMenu} + 客户端 {@code ValueEditorScreen},列出该实体
 * **实际拥有的全部属性**(含模组属性)。</p>
 */
public class ValueEditorItem extends Item {

    public ValueEditorItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            open(player, player);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    /** 打开编辑器(服务端调用)。 */
    public static void open(Player player, LivingEntity target) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        int targetId = target.getId();
        net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer, new net.minecraft.world.SimpleMenuProvider(
            (id, inventory, p) -> new ValueEditorMenu(id, targetId),
            Component.translatable("container.enigmaticenhance.value_editor")),
            buf -> buf.writeVarInt(targetId));
    }

    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack, Player player,
        LivingEntity target, InteractionHand hand) {
        if (!player.level().isClientSide) {
            open(player, target);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
}
