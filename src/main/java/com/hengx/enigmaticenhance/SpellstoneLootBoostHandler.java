package com.hengx.enigmaticenhance;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 3.1.0:大幅提升术石在战利品箱里的出现概率(含神秘遗物与扩展)。
 *
 * <h2>做法</h2>
 * <p>神秘遗物与扩展都是在自己注入的池子里按权重放术石,而那个池子的 {@code setRolls}
 * 是「负数 ~ 1」的均匀分布,实际每箱几乎抽不到。直接改它们的池子会依赖内部实现,
 * 所以这里改为<b>给同一张表追加一个我们自己的池子</b>:</p>
 *
 * <ul>
 *   <li>池名 {@code enigmaticenhance_spellstone_boost},幂等(重复加载不会叠加);</li>
 *   <li>{@code rolls} 由配置 {@code spellstoneBoostRolls} 决定(默认 1.0 = 每箱必抽一次);</li>
 *   <li>条目沿用神秘遗物 / 扩展原本给该表配置的术石与相对权重(再乘 {@code spellstoneBoostWeight});</li>
 *   <li>只在原版本来就有术石池的表上追加。</li>
 * </ul>
 *
 * <h2>表 → 术石 映射来源</h2>
 * <p>反编译 {@code EnigmaticEventHandler.onLootTablesLoaded}(本体)与
 * {@code AddonEventHandler.onLootTablesLoaded} + {@code SuperAddonHandler.getXxDungeons()}(扩展),
 * 再用映射表把 SRG 字段还原成战利品表 id。</p>
 *
 * <p>扩展的四个名单:</p>
 * <pre>
 *   getIceDungeons    → 冰屋 / 远古城市冰盒 / 丛林神庙 / 地牢 / 海底废墟(大·小) / 要塞(走廊·路口) / 堡垒(藏宝·其他)
 *   getLeafDungeons   → 丛林神庙 / 地牢 / 海底废墟(大·小) / 要塞(走廊·路口) / 堡垒(藏宝·其他)
 *   getEngineDungeons → 地牢 / 海底废墟(大·小) / 要塞(走廊·路口) / 堡垒(藏宝·其他)
 *   getSoulDungeons   → 堡垒(藏宝·其他)
 * </pre>
 */
public final class SpellstoneLootBoostHandler {

    /** 我们追加的池名。 */
    private static final String POOL_NAME = "enigmaticenhance_spellstone_boost";

    // ---- 物品 id ----
    private static final String GOLEM_HEART = "enigmaticlegacy:golem_heart";
    private static final String ANGEL_BLESSING = "enigmaticlegacy:angel_blessing";
    private static final String OCEAN_STONE = "enigmaticlegacy:ocean_stone";
    private static final String BLAZING_CORE = "enigmaticlegacy:blazing_core";
    private static final String EYE_OF_NEBULA = "enigmaticlegacy:eye_of_nebula";
    private static final String VOID_PEARL = "enigmaticlegacy:void_pearl";

    private static final String FORGOTTEN_ICE = "enigmaticaddons:forgotten_ice";
    private static final String REVIVAL_LEAF = "enigmaticaddons:revival_leaf";
    private static final String LOST_ENGINE = "enigmaticaddons:lost_engine";
    private static final String ILLUSION_LANTERN = "enigmaticaddons:illusion_lantern";

    /** 一条术石条目:物品 id + 原版权重。 */
    private record Entry(String itemId, int weight) {
    }

    /** 表 id → 该表的术石条目。 */
    private static final java.util.Map<String, List<Entry>> TABLE_MAP = build();

    private static java.util.Map<String, List<Entry>> build() {
        java.util.Map<String, List<Entry>> m = new java.util.HashMap<>();

        // 地牢 / 废弃矿井 / 村庄制甲师 —— 魔像之心
        m.put("minecraft:chests/simple_dungeon", List.of(
            new Entry(GOLEM_HEART, 100), new Entry(FORGOTTEN_ICE, 20),
            new Entry(REVIVAL_LEAF, 50), new Entry(LOST_ENGINE, 75)));
        m.put("minecraft:chests/abandoned_mineshaft", List.of(new Entry(GOLEM_HEART, 100)));
        m.put("minecraft:chests/village/village_armorer", List.of(new Entry(GOLEM_HEART, 100)));

        // 海底废墟 / 沉船 / 埋藏宝藏 —— 海洋意志
        List<Entry> water = List.of(
            new Entry(OCEAN_STONE, 100), new Entry(FORGOTTEN_ICE, 20),
            new Entry(REVIVAL_LEAF, 50), new Entry(LOST_ENGINE, 75));
        m.put("minecraft:chests/underwater_ruin_big", water);
        m.put("minecraft:chests/underwater_ruin_small", water);
        m.put("minecraft:chests/shipwreck_treasure", List.of(new Entry(OCEAN_STONE, 100)));
        m.put("minecraft:chests/buried_treasure", List.of(new Entry(OCEAN_STONE, 100)));

        // 下界要塞 / 堡垒 / 废弃传送门 —— 烈焰之核
        m.put("minecraft:chests/nether_bridge", List.of(new Entry(BLAZING_CORE, 100)));
        m.put("minecraft:chests/bastion_bridge", List.of(new Entry(BLAZING_CORE, 100)));
        m.put("minecraft:chests/bastion_hoglin_stable", List.of(new Entry(BLAZING_CORE, 100)));
        m.put("minecraft:chests/ruined_portal", List.of(new Entry(BLAZING_CORE, 100)));
        // 堡垒藏宝 / 其他 —— 烈焰之核 + 扩展四块中的四块(虚幻冥灯只在这两张)
        List<Entry> bastion = List.of(
            new Entry(BLAZING_CORE, 100), new Entry(FORGOTTEN_ICE, 20),
            new Entry(REVIVAL_LEAF, 50), new Entry(LOST_ENGINE, 75),
            new Entry(ILLUSION_LANTERN, 75));
        m.put("minecraft:chests/bastion_treasure", bastion);
        m.put("minecraft:chests/bastion_other", bastion);

        // 村庄神庙 —— 天使之祝
        m.put("minecraft:chests/village/village_temple", List.of(new Entry(ANGEL_BLESSING, 100)));

        // 末地城 —— 星云之眼 / 虚空珍珠
        m.put("minecraft:chests/end_city_treasure", List.of(
            new Entry(EYE_OF_NEBULA, 90), new Entry(VOID_PEARL, 10)));

        // 沙漠神殿 / 丛林神庙 —— 魔像之心 35 / 天使之祝 65(+ 扩展)
        m.put("minecraft:chests/desert_pyramid", List.of(
            new Entry(GOLEM_HEART, 35), new Entry(ANGEL_BLESSING, 65)));
        m.put("minecraft:chests/jungle_temple", List.of(
            new Entry(GOLEM_HEART, 35), new Entry(ANGEL_BLESSING, 65),
            new Entry(FORGOTTEN_ICE, 20), new Entry(REVIVAL_LEAF, 50)));

        // 要塞走廊 / 十字路口 —— 星云之眼 35 / 魔像之心 65(+ 扩展)
        List<Entry> stronghold = List.of(
            new Entry(EYE_OF_NEBULA, 35), new Entry(GOLEM_HEART, 65),
            new Entry(FORGOTTEN_ICE, 20), new Entry(REVIVAL_LEAF, 50),
            new Entry(LOST_ENGINE, 75));
        m.put("minecraft:chests/stronghold_corridor", stronghold);
        m.put("minecraft:chests/stronghold_crossing", stronghold);

        // 冰屋 / 远古城市冰盒 —— 忘却冰晶
        m.put("minecraft:chests/igloo_chest", List.of(new Entry(FORGOTTEN_ICE, 20)));
        m.put("minecraft:chests/ancient_city_ice_box", List.of(new Entry(FORGOTTEN_ICE, 20)));

        return java.util.Map.copyOf(m);
    }

    private SpellstoneLootBoostHandler() {
    }

    /** LOWEST:必须排在神秘遗物 / 扩展注入之后。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLootTableLoad(LootTableLoadEvent event) {
        try {
            if (!RingConfig.spellstoneBoostEnabled()) {
                return;
            }
            ResourceLocation name = event.getName();
            if (name == null) {
                return;
            }
            List<Entry> entries = TABLE_MAP.get(name.toString());
            if (entries == null || entries.isEmpty()) {
                return;
            }
            LootTable table = event.getTable();
            if (table == null) {
                return;
            }
            List<LootPool> pools = poolsOf(table);
            if (pools == null) {
                return;
            }
            for (LootPool p : pools) {
                if (POOL_NAME.equals(p.getName())) {
                    return;   // 已经加过
                }
            }
            LootPool boost = buildPool(entries);
            if (boost == null) {
                return;
            }
            unfreeze(table);
            pools.add(boost);
            DebugLog.info("[Spellstone] {} 已追加术石池(rolls={}, 条目={})",
                name, RingConfig.spellstoneBoostRolls(), entries.size());
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[Spellstone] 追加术石池失败: {}", e.toString());
        }
    }

    /** 构造我们自己的池子。 */
    private static LootPool buildPool(List<Entry> entries) {
        double weightMul = RingConfig.spellstoneBoostWeight();
        // 3.9.0:改成【按概率出现】—— 每个符合条件的箱子有 spellstoneBoostChance(默认 15%)的概率
        // 抽一次术石池;命中后按下面的权重决定具体是哪一颗。
        LootPool.Builder builder = LootPool.lootPool()
            .name(POOL_NAME)
            .setRolls(ConstantValue.exactly((float) RingConfig.spellstoneBoostRolls()))
            .when(LootItemRandomChanceCondition.randomChance((float) RingConfig.spellstoneBoostChance()));
        int added = 0;
        List<String> missing = new ArrayList<>();
        for (Entry e : entries) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(e.itemId()));
            if (item == null) {
                missing.add(e.itemId());
                continue;
            }
            int weight = Math.max(1, (int) Math.round(e.weight() * weightMul));
            builder.add(LootItem.lootTableItem(item).setWeight(weight));
            added++;
        }
        if (!missing.isEmpty()) {
            DebugLog.warn("[Spellstone] 以下物品不存在,已跳过: {}", missing);
        }
        if (added == 0) {
            return null;
        }
        return builder.build();
    }

    /**
     * 反射拿 {@code LootTable.pools}。
     *
     * <p>开发名是 {@code pools},生产环境是 SRG 名 <b>{@code f_79109_}</b>
     * (映射表 srg_to_official_1.20.1.tsrg 第 206204 行:{@code f_79109_ pools}),
     * 所以两个名字都要试 —— 只写 {@code pools} 在生产环境会静默失败。</p>
     */
    @SuppressWarnings("unchecked")
    private static List<LootPool> poolsOf(LootTable table) {
        for (String name : new String[]{"pools", "f_79109_"}) {
            try {
                Field f = LootTable.class.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(table);
                if (v instanceof List<?> list) {
                    return (List<LootPool>) list;
                }
            }
            catch (NoSuchFieldException ignored) {
                // 试下一个名字
            }
            catch (Throwable t) {
                DebugLog.warn("[Spellstone] 读取 LootTable.{} 失败: {}", name, t.toString());
                return null;
            }
        }
        DebugLog.warn("[Spellstone] 未能定位 LootTable.pools(pools / f_79109_)");
        return null;
    }

    /** 清掉冻结标记,否则列表不可变。 */
    private static void unfreeze(Object target) {
        try {
            Field f = target.getClass().getDeclaredField("isFrozen");
            f.setAccessible(true);
            f.setBoolean(target, false);
        }
        catch (Throwable ignored) {
            // 字段名变了也不致命:后面 add 会抛异常,已被外层捕获
        }
    }

    /** 供自检:该表是否被我们覆盖。 */
    public static boolean covers(ResourceLocation table) {
        return table != null && TABLE_MAP.containsKey(table.toString());
    }
}
