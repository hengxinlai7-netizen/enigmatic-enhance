package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 2.5.0:物品级配置（config/enigmaticenhance-items.toml）。
 *
 * <p>解决"所有物品都要能开关、内容也要能配"的问题，但**不给每个物品写一段样板**：
 * 用一份全局禁用表 + 少数需要细调的物品单独小段。</p>
 *
 * <ul>
 *   <li>{@code [items].disabledItemIds} —— 全局禁用列表，填物品 id；被禁用的物品右键无效、
 *       从创造标签页隐藏（可关）。</li>
 *   <li>{@code [items.earthAmulet]} —— 大地护符：一瓶药水最多记多少种效果、
 *       一个护符最多记多少条，0 = 无上限（默认）。</li>
 * </ul>
 */
public final class ItemConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_ITEM_IDS;
    public static final ForgeConfigSpec.BooleanValue HIDE_DISABLED_FROM_TAB;
    public static final ForgeConfigSpec.IntValue EARTH_AMULET_MAX_POTION_TYPES;
    public static final ForgeConfigSpec.IntValue EARTH_AMULET_MAX_CAPTURED_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EARTH_AMULET_EFFECT_BLACKLIST;

    private static final Set<String> DISABLED_CACHE = new LinkedHashSet<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("物品级开关(2.5.0)。").push("items");
        DISABLED_ITEM_IDS = builder
            .comment("全局禁用列表:填物品注册 id,例如 \"enigmaticenhance:earth_bottle\"。",
                     "被禁用的物品右键无效、无法当材料使用,并默认从创造标签页隐藏。")
            .defineList("disabledItemIds", new ArrayList<String>(),
                o -> o instanceof String s && s.contains(":"));
        HIDE_DISABLED_FROM_TAB = builder
            .comment("是否把被禁用的物品从创造标签页隐藏。默认开启。")
            .define("hideDisabledFromCreativeTab", true);

        builder.comment("大地护符:灌注药水时记录多少。" ).push("earthAmulet");
        EARTH_AMULET_MAX_POTION_TYPES = builder
            .comment("一瓶药水最多记录多少种效果。0 = 无上限(默认,一瓶里有多少记多少)。")
            .defineInRange("maxPotionTypes", 1, 0, 64);
        UNLIMITED_POTION_TYPES = builder
            .comment("4.3.7:开启后忽略 maxPotionTypes,一瓶药水能记录多少种就记多少(旧行为)。默认 false。")
            .define("unlimitedPotionTypes", false);
        EARTH_AMULET_MAX_CAPTURED_ITEMS = builder
            .comment("一个护符最多记录多少条效果(累计上限)。0 = 无上限(默认)。")
            .defineInRange("maxCapturedItems", 0, 0, 256);
        ALLOW_INSTANT_EFFECTS = builder
            .comment("4.3.0:是否允许灌注【瞬时效果】(如瞬间治疗/瞬间伤害)。默认 false。")
            .define("allowInstantEffects", false);
        MAX_EFFECT_LEVEL = builder
            .comment("4.3.0:灌注药水的效果等级上限。0 = 无上限。默认 1。")
            .defineInRange("maxEffectLevel", 1, 0, 255);
        EARTH_AMULET_EFFECT_BLACKLIST = builder
            .comment("buff 黑名单:填效果 id,大地护符灌注时**不会**记录这些效果。",
                     "例:\"minecraft:instant_damage\"、\"irons_spellbooks:oakskin\"。",
                     "默认空 = 什么都记。")
            .defineList("effectBlacklist", new ArrayList<String>(List.of("minecraft:resistance", "irons_spellbooks:evasion")),
                o -> o instanceof String s && s.contains(":"));
        builder.pop();

        builder.pop();

        SPEC = builder.build();
    }

    private ItemConfig() {
    }

    /** 被禁用的物品 id 集合(带缓存,配置重载时清空)。 */
    public static Set<String> disabledItemIds() {
        try {
            DISABLED_CACHE.clear();
            for (String s : DISABLED_ITEM_IDS.get()) {
                if (s != null && !s.isBlank()) {
                    DISABLED_CACHE.add(s.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        catch (Throwable t) {
            return Set.of();
        }
        return DISABLED_CACHE;
    }

    public static boolean isItemDisabled(String itemId) {
        return itemId != null && disabledItemIds().contains(itemId.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean hideDisabledFromTab() {
        try { return HIDE_DISABLED_FROM_TAB.get(); } catch (Throwable t) { return true; }
    }

    public static final ForgeConfigSpec.BooleanValue ALLOW_INSTANT_EFFECTS;
    public static final ForgeConfigSpec.IntValue MAX_EFFECT_LEVEL;

    public static final ForgeConfigSpec.BooleanValue UNLIMITED_POTION_TYPES;

    /** 4.3.7:是否不限种类。 */
    public static boolean earthAmuletUnlimitedPotionTypes() {
        try {
            return UNLIMITED_POTION_TYPES.get();
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public static int earthAmuletMaxPotionTypes() {
        try { return EARTH_AMULET_MAX_POTION_TYPES.get(); } catch (Throwable t) { return 0; }
    }

    /** 4.3.2:永远生效的内置黑名单 —— 只放「极高风险」效果(永久减伤/闪避类)。 */
    private static final java.util.Set<String> BUILTIN_BLACKLIST = java.util.Set.of(
        "minecraft:resistance",              // 抗性提升(原版/神化可制作)
        "irons_spellbooks:evasion",          // 铁魔法 闪避
        "irons_spellbooks:oakskin",          // 铁魔法 树皮(减伤)
        "goety:iron_hide",                   // 诡厄巫法 铁皮
        "goety:soul_armor",                  // 诡厄巫法 灵魂护甲
        "enigmaticaddons:pure_resistance",  // 神秘遗物扩展 纯净抗性
        "minecraft:absorption",             // 伤害吸收(金心)
        "goety:frosty_aura",                 // 诡厄巫法 凛寒气场
        "youkaisfeasts:earthy",               // 妖怪盛宴 醇厚
        "irons_spellbooks:true_invisibility"); // 铁魔法 真实隐身

    /** 大地护符的 buff 黑名单(小写 effect id)。 */
    public static java.util.Set<String> earthAmuletBlacklistedEffects() {
        // 4.3.1:这两条做成【内置默认】,永远生效。
        // 原因:Forge 不会覆盖已存在的配置值,旧存档里的 effectBlacklist = [] 会让
        // 新版默认值完全不生效(实测就是这样)。
        java.util.Set<String> out = new java.util.LinkedHashSet<>(BUILTIN_BLACKLIST);
        try {
            for (String s : EARTH_AMULET_EFFECT_BLACKLIST.get()) {
                if (s != null && !s.isBlank()) {
                    out.add(s.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        catch (Throwable t) {
            return java.util.Set.of();
        }
        return out;
    }

    /** 这个效果是否被黑名单挡掉。 */
    public static boolean isEffectBlacklisted(String effectId) {
        return effectId != null
            && earthAmuletBlacklistedEffects().contains(effectId.toLowerCase(java.util.Locale.ROOT));
    }

    /** 4.3.0:是否允许灌注瞬时效果。 */
    public static boolean earthAmuletAllowInstantEffects() {
        try {
            return ALLOW_INSTANT_EFFECTS.get();
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** 4.3.0:灌注药水的等级上限(0 = 无上限)。 */
    public static int earthAmuletMaxEffectLevel() {
        try {
            return MAX_EFFECT_LEVEL.get();
        }
        catch (RuntimeException | LinkageError e) {
            return 1;
        }
    }

    public static int earthAmuletMaxCapturedItems() {
        try { return EARTH_AMULET_MAX_CAPTURED_ITEMS.get(); } catch (Throwable t) { return 0; }
    }
}
