package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 3.0.0:扭曲之心 · 堡垒遗迹藏宝抽取。
 *
 * <p><b>用法</b>:在<b>堡垒遗迹</b>结构范围内,手持「扭曲之心」对<b>箱子</b>潜行 + 右键,
 * 按 {@code minecraft:chests/bastion_treasure}(藏宝室战利品表)抽一次,塞进该箱子。</p>
 *
 * <ul>
 *   <li>每次成功消耗 <b>1 个</b>扭曲之心(创造模式不消耗);</li>
 *   <li>不在堡垒遗迹内 → 不触发,箱子照常打开;</li>
 *   <li>箱子里塞不下 → 不消耗、不抽取,直接提示;</li>
 *   <li><b>兼容 Lootr</b>:Lootr 箱子塞进"该玩家自己那一份",不是方块实体本身。</li>
 * </ul>
 *
 * <p>结构判定用 {@link net.minecraft.world.level.StructureManager#getStructureWithPieceAt},
 * 以 {@link BuiltinStructures#BASTION_REMNANT} 为准,所以堡垒遗迹的<b>任意房间</b>都算。</p>
 */
public final class BastionTreasureHandler {

    /** 扭曲之心(神秘遗物)。用注册表按 id 取,避免硬依赖编译期符号。 */
    private static final ResourceLocation TWISTED_HEART_ID =
        new ResourceLocation("enigmaticlegacy", "twisted_heart");

    /** 堡垒遗迹藏宝室战利品表。 */
    private static final ResourceLocation BASTION_TREASURE =
        new ResourceLocation("minecraft", "chests/bastion_treasure");

    private BastionTreasureHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.bastionTreasureEnabled()) {
            return;
        }
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide) {
                return;
            }
            if (!player.isShiftKeyDown()) {
                return;
            }
            Item heart = ForgeRegistries.ITEMS.getValue(TWISTED_HEART_ID);
            if (heart == null) {
                return;   // 没装神秘遗物
            }
            ItemStack held = event.getItemStack();
            if (held.isEmpty() || held.getItem() != heart) {
                return;
            }
            Level level = event.getLevel();
            BlockPos pos = event.getPos();
            if (!isChestLike(level, pos)) {
                return;
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }
            // 必须在堡垒遗迹内
            StructureStart start = serverLevel.structureManager()
                .getStructureWithPieceAt(pos, BuiltinStructures.BASTION_REMNANT);
            if (start == null || !start.isValid()) {
                return;
            }

            // 4.8.2:抽之前记一下地狱刃片的一次性标记有没有打过 ——
            // 刃片是「生成即消耗唯一性」的,如果最后箱子塞不下,要把标记退回去。
            boolean hadHellBladeMark = HellBladeLootHandler.hasLooted(player);

            // 抽一次藏宝室表
            List<ItemStack> drops = roll(serverLevel, pos, player, held);
            if (drops.isEmpty()) {
                DebugLog.warn("[Bastion] 藏宝室表没有产出,跳过");
                if (!hadHellBladeMark) {
                    HellBladeLootHandler.revokeLooted(player);
                }
                return;
            }

            // 塞进箱子
            int added = insert(serverLevel, pos, player, drops);
            if (added <= 0) {
                if (!hadHellBladeMark) {
                    HellBladeLootHandler.revokeLooted(player);
                }
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c箱子里没有空间，未消耗扭曲之心"), true);
                return;
            }

            // 消耗
            if (!player.isCreative()) {
                held.shrink(1);
            }
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§6扭曲之心共鸣，从堡垒遗迹藏宝室抽取了战利品"), true);
            level.playSound(null, pos, SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.7F, 0.6F);

            // 阻止箱子被打开
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
            DebugLog.info("[Bastion] {} 在 {} 抽取藏宝室战利品 {} 件",
                player.getName().getString(), pos, added);
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.error("[Bastion] 抽取异常", e);
        }
    }

    /** 只认箱子 / 木桶这类容器方块。 */
    private static boolean isChestLike(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof ChestBlock || block instanceof BarrelBlock;
    }

    /** 按藏宝室表抽一次。 */
    private static List<ItemStack> roll(ServerLevel level, BlockPos pos, Player player, ItemStack tool) {
        List<ItemStack> out = new ArrayList<>();
        try {
            LootTable table = level.getServer().getLootData().getLootTable(BASTION_TREASURE);
            if (table == LootTable.EMPTY) {
                return out;
            }
            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withOptionalParameter(LootContextParams.TOOL, tool)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack != null && !stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.error("[Bastion] 抽取战利品表失败", e);
        }
        return out;
    }

    /** 塞进容器;返回成功放入的件数。 */
    private static int insert(ServerLevel level, BlockPos pos, Player player, List<ItemStack> drops) {
        // Lootr 箱子:走玩家专属容器
        if (ModList.get().isLoaded("lootr")) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && BastionLootrCompat.isLootrChest(be)) {
                return BastionLootrCompat.insert(level, pos, player, drops);
            }
        }
        // 普通箱子
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) {
            return -1;
        }
        // 若箱子还没生成自己的战利品,先生成,避免我们塞的东西覆盖原表
        if (container instanceof RandomizableContainerBlockEntity rcbe) {
            try {
                rcbe.unpackLootTable(player);
            }
            catch (RuntimeException | LinkageError ignored) {
                // 忽略
            }
        }
        int added = 0;
        for (ItemStack stack : drops) {
            ItemStack rest = insertOne(container, stack);
            if (rest.isEmpty()) {
                added++;
            }
        }
        container.setChanged();
        return added;
    }

    /** 单件插入:先叠加,再找空格。 */
    private static ItemStack insertOne(Container container, ItemStack stack) {
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
