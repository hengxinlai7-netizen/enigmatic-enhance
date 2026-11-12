package com.hengx.enigmaticenhance;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Lootr 兼容层(4.6.4 起<b>零硬引用</b>)。
 *
 * <p>Lootr 的箱子不是普通容器:每个玩家看到的是自己的 {@code ILootrInventory},
 * 由 {@code LootrAPI.getInventory} 依据「箱子 id + 玩家 UUID」生成。直接往方块实体里
 * 塞物品没有意义,所以必须走它的 API。</p>
 *
 * <p>这里不再 {@code import} 任何 Lootr 类型:接口/{@code LootFiller} 全部按名字
 * {@code Class.forName} 取得,{@code LootFiller} 用 {@link Proxy} 在运行时实现。
 * Lootr 缺失或版本不符时所有方法静默返回失败值,不会抛 {@code NoClassDefFoundError}。</p>
 */
final class BastionLootrCompat {

    private static final String API = "noobanidus.mods.lootr.api.LootrAPI";
    private static final String FILLER = "noobanidus.mods.lootr.api.LootFiller";
    private static final String INVENTORY = "noobanidus.mods.lootr.api.inventory.ILootrInventory";
    private static final String BLOCK_ENTITY = "noobanidus.mods.lootr.api.blockentity.ILootBlockEntity";

    private BastionLootrCompat() {
    }

    private static ClassLoader loader() {
        return BastionLootrCompat.class.getClassLoader();
    }

    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name, false, loader());
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 目标方块是不是 Lootr 的箱子。 */
    static boolean isLootrChest(BlockEntity be) {
        Class<?> iface = classOrNull(BLOCK_ENTITY);
        return iface != null && iface.isInstance(be);
    }

    /**
     * 往"该玩家自己的"Lootr 容器里塞战利品。
     *
     * @return 实际塞入件数;&lt; 0 表示失败(Lootr 缺失/版本不符也走这里,静默)
     */
    static int insert(ServerLevel level, BlockPos pos, Player player, List<ItemStack> drops) {
        try {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return -1;
            }
            Class<?> fillerType = classOrNull(FILLER);
            Class<?> apiType = classOrNull(API);
            Class<?> invType = classOrNull(INVENTORY);
            Class<?> beType = classOrNull(BLOCK_ENTITY);
            if (fillerType == null || apiType == null || invType == null || beType == null) {
                return -1;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (!beType.isInstance(be) || !(be instanceof RandomizableContainerBlockEntity rcbe)) {
                return -1;
            }
            ResourceLocation ownTable = (ResourceLocation) beType.getMethod("getTable").invoke(be);
            long seed = ((Number) beType.getMethod("getSeed").invoke(be)).longValue();

            // 填充器:用箱子原本的表生成,保证玩家打开时看到的是原本的战利品
            Object filler = Proxy.newProxyInstance(loader(), new Class<?>[] { fillerType },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("unpackLootTable".equals(name) && args != null && args.length == 4) {
                        try {
                            Player p = (Player) args[0];
                            Container container = (Container) args[1];
                            ResourceLocation tableId = (ResourceLocation) args[2];
                            long fillSeed = ((Number) args[3]).longValue();
                            if (tableId == null || container == null) {
                                return null;
                            }
                            LootTable table = level.getServer().getLootData().getLootTable(tableId);
                            if (table == LootTable.EMPTY) {
                                return null;
                            }
                            LootParams params = new LootParams.Builder(level)
                                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                                .withOptionalParameter(LootContextParams.THIS_ENTITY, p)
                                .create(LootContextParamSets.CHEST);
                            table.fill(container, params, fillSeed);
                        }
                        catch (Throwable ignored) {
                            // 填充失败不影响插入
                        }
                        return null;
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(name)) {
                        return "enigmaticenhance:lootr_filler";
                    }
                    return null;
                });

            Method getInventory = apiType.getMethod("getInventory",
                Level.class, UUID.class, BlockPos.class, ServerPlayer.class,
                RandomizableContainerBlockEntity.class, fillerType, Supplier.class, LongSupplier.class);
            Supplier<ResourceLocation> tableSupplier = () -> ownTable;
            LongSupplier seedSupplier = () -> seed;
            Object inv = getInventory.invoke(null, level, player.getUUID(), pos, serverPlayer, rcbe,
                filler, tableSupplier, seedSupplier);
            if (!(inv instanceof Container container)) {
                return -1;
            }
            int added = 0;
            for (ItemStack stack : drops) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                ItemStack rest = insertInto(container, stack);
                if (rest.isEmpty()) {
                    added++;
                }
            }
            container.setChanged();
            return added;
        }
        catch (Throwable t) {
            DebugLog.debug("[Bastion] Lootr 联动跳过: {}", t.toString());
            return -1;
        }
    }

    /** 把单件塞进容器:先叠加到同类空位,再找空格。返回剩余。 */
    private static ItemStack insertInto(Container container, ItemStack stack) {
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
