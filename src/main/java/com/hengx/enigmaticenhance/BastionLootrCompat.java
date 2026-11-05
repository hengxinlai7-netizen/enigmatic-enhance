package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import noobanidus.mods.lootr.api.LootFiller;
import noobanidus.mods.lootr.api.LootrAPI;
import noobanidus.mods.lootr.api.blockentity.ILootBlockEntity;
import noobanidus.mods.lootr.api.inventory.ILootrInventory;

/**
 * Lootr 兼容层。
 *
 * <p>Lootr 的箱子<b>不是</b>普通容器:每个玩家看到的是自己的 {@link ILootrInventory},
 * 由 {@link LootrAPI#getInventory} 依据「箱子 id + 玩家 UUID」生成。直接往方块实体里塞物品
 * 没有意义(玩家打开时看到的是自己那份),所以必须走这里。</p>
 *
 * <p>本类只在 {@code lootr} 已加载时才被加载(由 {@link BastionTreasureHandler} 用
 * {@code ModList} 守卫),未安装 Lootr 时不会触发类加载,不会引发 {@code NoClassDefFoundError}。</p>
 */
final class BastionLootrCompat {

    private BastionLootrCompat() {
    }

    /** 目标方块是不是 Lootr 的箱子。 */
    static boolean isLootrChest(BlockEntity be) {
        return be instanceof ILootBlockEntity;
    }

    /**
     * 往"该玩家自己的"Lootr 容器里塞战利品。
     *
     * @return 实际塞入件数;&lt; 0 表示失败
     */
    static int insert(ServerLevel level, BlockPos pos, Player player, List<ItemStack> drops) {
        try {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return -1;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof ILootBlockEntity lootBe)
                || !(be instanceof RandomizableContainerBlockEntity rcbe)) {
                return -1;
            }
            ResourceLocation ownTable = lootBe.getTable();
            long seed = lootBe.getSeed();

            // 填充器:用箱子原本的表生成,保证玩家打开时看到的是原本的战利品
            LootFiller filler = (p, container, tableId, fillSeed) -> {
                try {
                    if (tableId == null || container == null) {
                        return;
                    }
                    LootTable table = level.getServer().getLootData().getLootTable(tableId);
                    if (table == LootTable.EMPTY) {
                        return;
                    }
                    LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, p)
                        .create(LootContextParamSets.CHEST);
                    table.fill(container, params, fillSeed);
                }
                catch (RuntimeException | LinkageError reported) {
                    DebugLog.warn("[Bastion] Lootr 填充原表失败: {}", reported.toString());
                }
            };

            ILootrInventory inv = LootrAPI.getInventory(level, player.getUUID(), pos, serverPlayer,
                rcbe, filler, () -> ownTable, () -> seed);
            if (inv == null) {
                return -1;
            }
            int added = 0;
            for (ItemStack stack : drops) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                ItemStack rest = insertInto(inv, stack);
                if (rest.isEmpty()) {
                    added++;
                }
            }
            inv.setChanged();
            return added;
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[Bastion] Lootr 插入失败: {}", e.toString());
            return -1;
        }
    }

    /** 把单件塞进容器:先叠加到同类空位,再找空格。返回剩余。 */
    private static ItemStack insertInto(net.minecraft.world.Container container, ItemStack stack) {
        ItemStack rest = stack.copy();
        int size = container.getContainerSize();
        for (int i = 0; i < size && !rest.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, rest)) {
                continue;
            }
            int max = Math.min(container.getMaxStackSize(), slot.getMaxStackSize());
            int space = max - slot.getCount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, rest.getCount());
            slot.grow(move);
            rest.shrink(move);
            container.setItem(i, slot);
        }
        for (int i = 0; i < size && !rest.isEmpty(); i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, rest.copy());
                rest.setCount(0);
            }
        }
        return rest;
    }
}
