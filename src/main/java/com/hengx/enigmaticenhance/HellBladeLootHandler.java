package com.hengx.enigmaticenhance;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 地狱刃片:把下界地牢箱子里的出现概率提到 100%。
 *
 * <p>3.9.1 修正(用户指出的关键点):之前是"玩家右键箱子 → 直接塞一个进背包",那是赠予。
 * 现在改成往战利品表里追加一个 100% 出现的池子,刃片作为箱子的战利品正常生成。</p>
 *
 * <p>开关:{@code [lootGuarantee] hellBladeGuaranteed}(默认 true)。</p>
 */
public final class HellBladeLootHandler {

    private static final String HELL_BLADE_ID = "enigmaticaddons:hell_blade_charm";

    private static final String POOL_NAME = "enigmaticenhance_hell_blade";

    private static final java.util.Set<String> TABLES = java.util.Set.of(
        "minecraft:chests/nether_bridge",
        "minecraft:chests/bastion_treasure",
        "minecraft:chests/bastion_other",
        "minecraft:chests/bastion_bridge",
        "minecraft:chests/bastion_hoglin_stable",
        "minecraft:chests/ruined_portal");

    private HellBladeLootHandler() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        try {
            if (!RingConfig.hellBladeGuaranteed()) {
                return;
            }
            ResourceLocation name = event.getName();
            if (name == null || !TABLES.contains(name.toString())) {
                return;
            }
            Item blade = ForgeRegistries.ITEMS.getValue(new ResourceLocation(HELL_BLADE_ID));
            if (blade == null) {
                DebugLog.once("hellblade:missing", "[HellBlade] 找不到物品 {},跳过", HELL_BLADE_ID);
                return;
            }
            List<LootPool> pools = poolsOf(event.getTable());
            if (pools == null) {
                DebugLog.warn("[HellBlade] 无法访问 {} 的 pools,跳过", name);
                return;
            }
            for (LootPool p : pools) {
                if (POOL_NAME.equals(p.getName())) {
                    return;
                }
            }
            // 4.0.1:改成按概率出现。一个堡垒遗迹有多张表、几十个箱子,
            // 原来的 100% 会让一趟刷出十几个刃片。
            LootPool.Builder poolBuilder = LootPool.lootPool()
                .name(POOL_NAME)
                .setRolls(ConstantValue.exactly(1.0F));
            double chance = RingConfig.hellBladeChance();
            if (chance < 1.0D) {
                poolBuilder.when(LootItemRandomChanceCondition.randomChance((float) chance));
            }
            poolBuilder.add(LootItem.lootTableItem(blade).setWeight(1));
            LootPool pool = poolBuilder.build();
            if (!unfreeze(event.getTable())) {
                DebugLog.warn("[HellBlade] {} 的表处于冻结状态,无法追加池子", name);
                return;
            }
            pools.add(pool);
            DebugLog.once("hellblade:added:" + name,
                "[HellBlade] {} 的刃片出现概率 = {}", name, RingConfig.hellBladeChance());
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[HellBlade] 注入 {} 失败: {}", event.getName(), e.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<LootPool> poolsOf(net.minecraft.world.level.storage.loot.LootTable table) {
        for (String name : new String[]{"pools", "f_79109_"}) {
            try {
                Field f = net.minecraft.world.level.storage.loot.LootTable.class.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(table);
                if (v instanceof List<?> list) {
                    return (List<LootPool>) list;
                }
            }
            catch (ReflectiveOperationException | RuntimeException ignored) {
                // 试下一个名字
            }
        }
        return null;
    }

    private static boolean unfreeze(net.minecraft.world.level.storage.loot.LootTable table) {
        try {
            Field f = net.minecraft.world.level.storage.loot.LootTable.class.getDeclaredField("isFrozen");
            f.setAccessible(true);
            f.setBoolean(table, false);
            return true;
        }
        catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    // ------------------------------------------------------------------ 每名玩家只给一个

    /** 玩家「已经拿过地狱刃片」的持久标记(存档级,死亡/复活不丢)。 */
    private static final String LOOTED_KEY = "enigmaticenhance:lootedHellBlade";

    private static boolean alreadyLooted(Player player) {
        try {
            return player != null && player.getPersistentData().getBoolean(LOOTED_KEY);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static void markLooted(Player player) {
        try {
            if (player != null) {
                player.getPersistentData().putBoolean(LOOTED_KEY, true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /**
     * 4.0.2:与神秘遗物扩展一致 —— 每名玩家一辈子只能有一枚。
     *
     * <p>战利品表是"每箱"生成的,没法按玩家算,所以在<b>拾取</b>这一步拦:
     * 已经拿过的人捡不起来(地上那枚直接移除),第一次捡到的人打上持久标记。</p>
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPickup(net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent event) {
        try {
            Player player = event.getEntity();
            ItemStack stack = event.getStack();
            if (player == null || stack == null || stack.isEmpty() || !isHellBlade(stack)) {
                return;
            }
            if (alreadyLooted(player)) {
                if (event.getOriginalEntity() != null && !event.getOriginalEntity().level().isClientSide) {
                    event.getOriginalEntity().discard();
                }
                DebugLog.once("hellblade:dup:" + player.getUUID(),
                    "[HellBlade] {} 已经拿过刃片,本次拾取已拦截", player.getName().getString());
                return;
            }
            markLooted(player);
            DebugLog.once("hellblade:first:" + player.getUUID(),
                "[HellBlade] {} 获得第一枚刃片,已打标记", player.getName().getString());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯判定逻辑,失败就放行
        }
    }

    private static boolean isHellBlade(ItemStack stack) {
        try {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id != null && HELL_BLADE_ID.equals(id.toString());
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static List<String> tables() {
        return new ArrayList<>(TABLES);
    }
}