package com.hengx.enigmaticenhance;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
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
            LootPool pool = LootPool.lootPool()
                .name(POOL_NAME)
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(blade).setWeight(1))
                .build();
            if (!unfreeze(event.getTable())) {
                DebugLog.warn("[HellBlade] {} 的表处于冻结状态,无法追加池子", name);
                return;
            }
            pools.add(pool);
            DebugLog.once("hellblade:added:" + name,
                "[HellBlade] 已把 {} 的刃片出现概率提到 100%", name);
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

    public static List<String> tables() {
        return new ArrayList<>(TABLES);
    }
}