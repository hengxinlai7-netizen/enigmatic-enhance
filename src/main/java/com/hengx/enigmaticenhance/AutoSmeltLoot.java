package com.hengx.enigmaticenhance;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 【自动熔炼】的<b>获取途径</b>之一:往几张原版箱子战利品表里追加一个低概率的附魔书条目。
 * <p>
 * 为什么保留战利品这一条(1.2.2b 起附魔台/铁砧/村民也都能拿到,这里不再是唯一来源):
 * <ul>
 * <li>箱子战利品是"探索拿到的工具书"这类附魔最自然的来源,而且完全走原版数据、不需要新增物品;</li>
 * <li>物品本体是原版 {@code minecraft:enchanted_book},NBT 只写 {@code StoredEnchantments}
 *     (与 {@code EnchantedBookItem#addEnchantment} 的格式逐字节一致),因此铁砧/其它模组都能正常识别。</li>
 * </ul>
 * 只追加一个具名池({@value #POOL_NAME}),不改任何原版池,便于自检断言与出问题时定位;
 * 开关是配置里的 {@code autoSmelt.lootEnabled}。若把 {@code autoSmelt.lootOnlyAcquisition} 打开,
 * 这是唯一还会产出它的地方(等于退回旧版行为)。
 */
public final class AutoSmeltLoot {

    /** 追加的池名(同时进 {@link ModIds#problems()} 校验清单)。 */
    public static final String POOL_NAME = EnigmaticEnhance.MODID + ":" + ModIds.AUTO_SMELT_PATH;

    /** 目标箱子表 -> 出现概率。只挑"矿洞/地牢/要塞图书馆/远古城市"这类探索型箱子。 */
    private static final Map<ResourceLocation, Float> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put(new ResourceLocation("minecraft", "chests/abandoned_mineshaft"), 0.25F);
        TABLES.put(new ResourceLocation("minecraft", "chests/simple_dungeon"), 0.15F);
        TABLES.put(new ResourceLocation("minecraft", "chests/stronghold_library"), 0.15F);
        TABLES.put(new ResourceLocation("minecraft", "chests/ancient_city"), 0.10F);
    }

    private AutoSmeltLoot() {}

    /** 目标战利品表清单(自检用)。 */
    public static Map<ResourceLocation, Float> tables() {
        return TABLES;
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!RingConfig.autoSmeltLootEnabled()) {
            return;
        }
        Float chance = TABLES.get(event.getName());
        if (chance == null) {
            return;
        }
        try {
            if (event.getTable().getPool(POOL_NAME) != null) {
                return; // 已经加过(数据包重载/多次触发)
            }
            event.getTable().addPool(buildPool(chance));
            if (RingConfig.debugLog()) {
                DebugLog.info("[AutoSmelt] loot pool {} added to table {} (chance {})",
                    event.getName(), POOL_NAME, chance);
            }
        }
        catch (Throwable t) {
            // 战利品注入失败只影响"获取途径",绝不能让世界加载失败
            EnigmaticEnhance.LOGGER.warn("[AutoSmelt] failed to add loot pool to {} (skipped): {}", event.getName(), t.toString());
        }
    }

    /** 一个具名池:1 次抽取 + 概率条件 + 一本带 StoredEnchantments 的附魔书。 */
    private static LootPool buildPool(float chance) {
        CompoundTag enchantment = new CompoundTag();
        enchantment.putString("id", POOL_NAME);
        enchantment.putShort("lvl", (short) 1);
        ListTag stored = new ListTag();
        stored.add(enchantment);
        CompoundTag tag = new CompoundTag();
        tag.put("StoredEnchantments", stored);

        return LootPool.lootPool()
            .name(POOL_NAME)
            .setRolls(ConstantValue.exactly(1.0F))
            .when(LootItemRandomChanceCondition.randomChance(chance))
            .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK).apply(SetNbtFunction.setTag(tag)))
            .build();
    }
}
