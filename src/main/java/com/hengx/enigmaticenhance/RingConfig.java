package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 本模组的全部可配置项。
 * 分段:omni(全属性属性本身)、ring(救赎之戒)、scroll(卷轴,默认关闭)、engine(失落引擎)、debug。
 */
public final class RingConfig {

    public static final ResourceLocation RING_ID = new ResourceLocation("enigmaticaddons", "bless_ring");
    public static final ResourceLocation CURSED_RING_ID = new ResourceLocation("enigmaticlegacy", "cursed_ring");
    public static final ResourceLocation CURSED_SCROLL_ID = new ResourceLocation("enigmaticlegacy", "cursed_scroll");
    public static final ResourceLocation BROKEN_RING_ID = new ResourceLocation("enigmaticaddons", "broken_ring");
    public static final ResourceLocation ENGINE_ID = new ResourceLocation("enigmaticaddons", "lost_engine");
    /** 冒险者之证(item.enigmaticaddons.adventure_charm)。 */
    public static final ResourceLocation ADVENTURE_CHARM_ID = new ResourceLocation("enigmaticaddons", "adventure_charm");
    /** 绝望者证章(item.enigmaticaddons.despair_insignia)。 */
    public static final ResourceLocation DESPAIR_INSIGNIA_ID = new ResourceLocation("enigmaticaddons", "despair_insignia");
    /** 万钧之护卷轴(item.enigmaticaddons.thunder_scroll,类 auviotre...items.ThunderScroll)。 */
    public static final ResourceLocation THUNDER_SCROLL_ID = new ResourceLocation("enigmaticaddons", "thunder_scroll");
    /** 古旧书袋(扩展的"书袋",内容物其实挂在玩家身上的 capability 里)。 */
    public static final ResourceLocation ANTIQUE_BAG_ID = new ResourceLocation("enigmaticaddons", "antique_bag");
    /** 倒转之启(神秘遗物本体,书的武器形态)。 */
    public static final ResourceLocation THE_TWIST_ID = new ResourceLocation("enigmaticlegacy", "the_twist");
    /** 恩惠之典(神秘遗物扩展)。 */
    public static final ResourceLocation THE_BLESS_ID = new ResourceLocation("enigmaticaddons", "the_bless");
    /** 无止之言(神秘遗物本体)。 */
    public static final ResourceLocation THE_INFINITUM_ID = new ResourceLocation("enigmaticlegacy", "the_infinitum");
    /** 血战沙场之证(神秘遗物本体,类 BerserkEmblem,curios:charm 槽)。 */
    public static final ResourceLocation BERSERK_CHARM_ID = new ResourceLocation("enigmaticlegacy", "berserk_charm");
    /** 神秘佳肴(Enigmatic Delicacy)的「无止暴食之证」。注册名取自其 zh_cn.json 的 item.enigmaticdelicacy.gluttony_charm。 */
    public static final ResourceLocation GLUTTONY_CHARM_ID = new ResourceLocation("enigmaticdelicacy", "gluttony_charm");
    /** 神秘遗物的「附魔师的珍珠」。注册名取自其 zh_cn.json 的 item.enigmaticlegacy.enchanter_pearl。 */
    public static final ResourceLocation ENCHANTER_PEARL_ID = new ResourceLocation("enigmaticlegacy", "enchanter_pearl");

    /** 1.1.28b:本模组自己的「睡前读物」。 */
    public static final ResourceLocation BEDTIME_READING_ID =
        new ResourceLocation(EnigmaticEnhance.MODID, ModIds.BEDTIME_READING_PATH);

    /**
     * 1.2.2:神秘遗物本体的「大地之心」。
     * <p>
     * 「深渊侵蚀的大地之心」**不新增物品 id** —— 它是这一个物品 + 我们自己的一个标签,
     * 与本体「受污染的大地之心」的结构完全一致(那边也是同一个物品 + {@code isTainted} 标签)。
     * 因此凡是认 {@code enigmaticlegacy:earth_heart} 的配方(例如本体的无尽食物、采矿护符)
     * 都会自动兼容,不需要另做替身配方。
     */
    public static final ResourceLocation EARTH_HEART_ID = new ResourceLocation("enigmaticlegacy", "earth_heart");

    /** 1.2.2:神秘遗物本体的「深渊之心」(深渊侵蚀的来源之一)。 */
    public static final ResourceLocation ABYSSAL_HEART_ID = new ResourceLocation("enigmaticlegacy", "abyssal_heart");

    public static final java.util.UUID RING_OMNI_UUID = java.util.UUID.fromString("6f1a1b9e-4c2f-4d3b-9a77-0e9b2c5d8f41");

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue OMNI_ENABLED;
    public static final ForgeConfigSpec.BooleanValue OMNI_ATTACH_TO_PLAYERS;
    public static final ForgeConfigSpec.DoubleValue RING_OMNI_BONUS;
    public static final ForgeConfigSpec.BooleanValue RING_UNBREAKABLE;
    public static final ForgeConfigSpec.BooleanValue RING_FREEZE_BETRAYAL;
    public static final ForgeConfigSpec.BooleanValue RING_HIDE_BETRAYAL_BAR;
    public static final ForgeConfigSpec.BooleanValue RING_ALLOW_WITH_CURSED_RING;
    public static final ForgeConfigSpec.BooleanValue RING_ALLOW_WITH_BROKEN_RING;
    public static final ForgeConfigSpec.BooleanValue RING_KEEP_ON_RELOGIN;
    public static final ForgeConfigSpec.BooleanValue SCROLL_ALLOW_WITH_CURSED_SCROLL;
    public static final ForgeConfigSpec.ConfigValue<String> SCROLL_ENABLER_ID;
    public static final ForgeConfigSpec.BooleanValue SCALE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SCALE_LIGHTNING_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SCALE_CHARGE_SPREAD;
    public static final ForgeConfigSpec.BooleanValue SCALE_RESTORE_SWEEP_BOOST;
    public static final ForgeConfigSpec.BooleanValue BAG_TOOLTIP_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BLOODBATH_LOCK_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BLOODBATH_ALLOW_TOGGLE;
    public static final ForgeConfigSpec.DoubleValue BLOODBATH_LOCK_VALUE;

    /** 1.8.0:战斗无敌帧(所有实体,可配置)。 */
    public static final ForgeConfigSpec.BooleanValue COMBAT_INVULN_ENABLED;
    public static final ForgeConfigSpec.IntValue COMBAT_INVULN_TICKS;
    public static final ForgeConfigSpec.IntValue COMBAT_INVULN_OUTPUT;
    /** 2.7.0:地狱刃片保底 / 附魔师珍珠灌注开关。 */
    public static final ForgeConfigSpec.BooleanValue HELL_BLADE_GUARANTEED;

    /** 4.0.1:地狱刃片在每个箱子里的出现概率(0~1)。 */
    public static final ForgeConfigSpec.DoubleValue HELL_BLADE_CHANCE;
    /** 3.1.0:术石战利品加权。 */
    public static final ForgeConfigSpec.BooleanValue SPELLSTONE_BOOST_ENABLED;

    /** 3.9.0:术石池的出现概率(0~1)。 */
    public static final ForgeConfigSpec.DoubleValue SPELLSTONE_BOOST_CHANCE;
    public static final ForgeConfigSpec.DoubleValue SPELLSTONE_BOOST_ROLLS;
    public static final ForgeConfigSpec.DoubleValue SPELLSTONE_BOOST_WEIGHT;
    public static final ForgeConfigSpec.BooleanValue PEARL_INFUSION_ENABLED;

    /** 3.8.2:执行之手总开关(未完成功能,默认关闭)。 */
    public static final ForgeConfigSpec.BooleanValue HAND_OF_EXECUTION_ENABLED;

    /** 4.5.0:磁吸戒指 / 超级磁吸戒指 的吸取范围增强。 */
    public static final ForgeConfigSpec.BooleanValue MAGNET_RANGE_ENABLED;
    public static final ForgeConfigSpec.IntValue MAGNET_RANGE;

    /** 4.5.0:锻造者宝石(铁砧上升级附魔书)。 */
    public static final ForgeConfigSpec.BooleanValue FORGER_GEM_ANVIL_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FORGER_GEM_TOOLTIP_ENABLED;

    /** 4.5.0:其余原先写死、没有开关的增强。 */
    public static final ForgeConfigSpec.BooleanValue ALL_DAMAGE_CRIT_ENABLED;
    public static final ForgeConfigSpec.DoubleValue ALL_DAMAGE_CRIT_CHANCE;
    public static final ForgeConfigSpec.BooleanValue BEACON_ENHANCE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BASTION_TREASURE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SHARPSHOOTER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MIRROR_SHATTER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SUN_BOTTLE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue WAR_FOG_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SPIRIT_LIGHT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue STYX_WATER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FISHING_HAUL_ENABLED;
    public static final ForgeConfigSpec.BooleanValue EVIL_ESSENCE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue UNBREAKABLE_ENCHANT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ELEMENTAL_MAGIC_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ELEMENTAL_SPELL_CAST_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MERCURY_SERPENT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue TACZ_SPELL_CAST_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BLOOD_CALAMITY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CALAMITY_ORIGIN_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ETERNAL_SILENCE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue VALUE_EDITOR_ENABLED;

    /** 3.8.5:珍珠补掷是否只提升已有附魔(过滤书架兼容)。 */
    public static final ForgeConfigSpec.BooleanValue PEARL_EXTRA_ONLY_EXISTING;

    /** 1.8.4:不受无敌帧限制的伤害白名单。 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMBAT_INVULN_WHITELIST;
    public static final ForgeConfigSpec.DoubleValue SCALE_LIGHTNING_CHANCE;
    public static final ForgeConfigSpec.DoubleValue SCALE_ATTACK_DAMAGE_PERCENT;
    public static final ForgeConfigSpec.BooleanValue SCALE_TOGGLE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FOURTH_CURSE_FIX_ENABLED;
    public static final ForgeConfigSpec.DoubleValue FOURTH_CURSE_FIX_RESIDUAL_PERCENT;
    public static final ForgeConfigSpec.BooleanValue ENGINE_DISABLE_LIGHTNING_ITEM_CLEAR;
    public static final ForgeConfigSpec.BooleanValue GLUTTONY_ENABLED;
    public static final ForgeConfigSpec.IntValue GLUTTONY_LOCKED_FOOD_LEVEL;
    public static final ForgeConfigSpec.DoubleValue GLUTTONY_LOCKED_SATURATION;
    public static final ForgeConfigSpec.IntValue GLUTTONY_HEAL_INTERVAL_TICKS;
    public static final ForgeConfigSpec.DoubleValue GLUTTONY_HEAL_AMOUNT;
    public static final ForgeConfigSpec.BooleanValue PEARL_FIX_ENCHANTING_TABLE;
    public static final ForgeConfigSpec.BooleanValue PEARL_FREE_REFORGE;
    public static final ForgeConfigSpec.BooleanValue BOOKS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BOOKS_TOGGLE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ADVENTURE_CHARM_ALLOW_MINING;
    public static final ForgeConfigSpec.BooleanValue DESPAIR_INSIGNIA_ALLOW_MINING;
    public static final ForgeConfigSpec.BooleanValue MINING_TOOLTIP;
    public static final ForgeConfigSpec.BooleanValue CONVERT_ON_DEATH;
    public static final ForgeConfigSpec.BooleanValue CONVERT_HANDS_ONLY;
    public static final ForgeConfigSpec.BooleanValue CONVERT_NOTIFY;
    public static final ForgeConfigSpec.BooleanValue AUTO_SMELT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue AUTO_SMELT_DISABLED_WHILE_SNEAKING;
    public static final ForgeConfigSpec.BooleanValue AUTO_SMELT_LOOT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue AUTO_SMELT_YIELD_TO_WONDERENCHANT;
    /** 1.2.2b:自动熔炼是否退回"只有战利品附魔书"的旧获取途径。 */
    public static final ForgeConfigSpec.BooleanValue AUTO_SMELT_LOOT_ONLY;
    /** 1.1.25:恶魔契约的两种解释(true = 字面实现,任何伤害都致死)。 */
    public static final ForgeConfigSpec.BooleanValue DEMON_PACT_LETHAL;
    /** 1.1.25c:旅行家帽子。 */
    public static final ForgeConfigSpec.ConfigValue<String> TRAVELER_HAT_SPEED_MODE;
    public static final ForgeConfigSpec.DoubleValue TRAVELER_HAT_SPEED_VALUE;
    public static final ForgeConfigSpec.DoubleValue TRAVELER_HAT_HEALTH_PER_BIOME;
    public static final ForgeConfigSpec.DoubleValue TRAVELER_HAT_ATTACK_PER_DIMENSION;
    public static final ForgeConfigSpec.IntValue TRAVELER_HAT_MAX_BIOMES;

    /** 1.2.10a:旅行家帽最多能加多少生命上限(防止血量过大导致血条画不出来)。 */
    public static final ForgeConfigSpec.DoubleValue TRAVELER_HAT_MAX_HEALTH_BONUS;
    public static final ForgeConfigSpec.IntValue TRAVELER_HAT_MAX_DIMENSIONS;
    /** 1.1.28b:睡前读物是否修正七咒之戒的第七重咒(失眠)。 */
    public static final ForgeConfigSpec.BooleanValue BEDTIME_READING_FIX;
    /** 1.1.28e:三件套里恶魔的手骨的"禁疗"持续秒数(0 = 关闭)。 */
    public static final ForgeConfigSpec.IntValue DEMON_BONE_HEAL_BLOCK_SECONDS;

    /** 1.2.10:恶魔手骨一次最多给多少伤害吸收(默认 20 = 10 颗金心),防止吸收过多导致血条不渲染。 */
    public static final ForgeConfigSpec.DoubleValue DEMON_BONE_MAX_ABSORPTION;

    /** 1.2.11c:瓶装不定之雾的生效半径(格)。 */
    public static final ForgeConfigSpec.DoubleValue UNCERTAIN_FOG_RADIUS;
    /** 1.2.2:深渊侵蚀的大地之心。 */
    public static final ForgeConfigSpec.BooleanValue ABYSSAL_EARTH_HEART_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ABYSSAL_EARTH_HEART_AUTO_TRIGGERS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ABYSSAL_EARTH_HEART_EXTRA_TRIGGERS;
    public static final ForgeConfigSpec.BooleanValue LOG_STARTUP_LINE;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("神秘遗物增强:所有开关与数值都在这里。").push("enigmaticenhance");

        builder.comment("【全属性】:给所有属性加成的独立加成项。").push("omni");
        OMNI_ENABLED = builder.comment("是否启用这个加成项。默认开启。").define("enabled", true);
        OMNI_ATTACH_TO_PLAYERS = builder.comment("是否让它对玩家生效。默认开启。")
            .define("attachToPlayers", true);
        builder.pop();

        builder.comment("救赎之戒:让它不会损坏、可与七咒之戒同戴。").push("ring");
        RING_OMNI_BONUS = builder.comment("全属性加成;0.2 = 所有属性 +20%(默认)。")
            .defineInRange("omniBonus", 0.2D, -10.0D, 10.0D);
        RING_UNBREAKABLE = builder.comment("让戒指永远不会损坏的总开关。关闭后下面两项也不再生效。")
            .define("unbreakable", true);
        RING_FREEZE_BETRAYAL = builder
            .comment("让戒指永远不会损坏。开启后背叛值不再增长,也就不会触发\"戒指破损\"。")
            .define("freezeBetrayal", true);
        RING_HIDE_BETRAYAL_BAR = builder
            .comment("不在物品栏里显示那条假的耐久条。默认开启。")
            .define("hideBetrayalBar", true);
        RING_ALLOW_WITH_CURSED_RING = builder
            .comment("允许和七咒之戒一起戴,并且不再受同戴的惩罚。默认开启。")
            .define("allowWithCursedRing", true);
        RING_ALLOW_WITH_BROKEN_RING = builder.comment("允许和破损之戒一起戴。默认开启。")
            .define("allowWithBrokenRing", true);
        RING_KEEP_ON_RELOGIN = builder
            .comment("重进游戏后把戒指留在饰品栏,而不是掉进背包(不会丢也不会复制)。默认开启。")
            .define("keepRingOnRelogin", true);
        builder.pop();

        builder.comment("卷轴相关:两本卷轴的同戴开关,以及万钧之护卷轴的改动。").push("scroll");
        SCROLL_ALLOW_WITH_CURSED_SCROLL = builder.comment("是否放行造物主的恩赐与千咒卷轴同戴。默认关闭。")
            .define("allowWithCursedScroll", false);
        SCROLL_ENABLER_ID = builder.comment("放行所需的物品(注册 id)。只在上一条开启时有用。")
            .define("enablerItemId", "enigmaticlegacy:fabulous_scroll");
        SCALE_ENABLED = builder
            .comment("万钧之护卷轴的总开关:开启后改为加攻击伤害、雷击可开关。默认开启。")
            .define("scaleOfProtectionEnabled", true);
        SCALE_LIGHTNING_ENABLED = builder
            .comment("卷轴的雷击效果开关。关闭后命中不再累积电荷,也就不会落雷。默认开启。")
            .define("scaleOfProtectionLightningEnabled", true);
        SCALE_LIGHTNING_CHANCE = builder
            .comment("每次命中累积电荷的概率(百分比)。100 = 每击都累积(默认)。")
            .defineInRange("scaleOfProtectionLightningChance", 100.0D, 0.0D, 100.0D);
        SCALE_ATTACK_DAMAGE_PERCENT = builder
            .comment("卷轴提供的攻击伤害加成(百分比)。100 = +100%(默认);0 = 不加成。")
            .defineInRange("scaleOfProtectionAttackDamagePercent", 100.0D, -1000.0D, 10000.0D);
        SCALE_CHARGE_SPREAD = builder
            .comment("命中时把雷击电荷像横扫一样带给附近目标(不造成伤害)。默认开启。")
            .define("scaleOfProtectionChargeSpread", true);
        SCALE_RESTORE_SWEEP_BOOST = builder
            .comment("恢复卷轴原本的\"横扫造成 100% 伤害\"。默认关闭,改用攻击伤害加成。")
            .define("scaleOfProtectionRestoreSweepBoost", false);
        SCALE_TOGGLE_ENABLED = builder
            .comment("允许用 Ctrl+左键 逐件开关卷轴的雷击。默认开启。")
            .define("scaleOfProtectionToggleEnabled", true);
        builder.pop();

        builder.comment("古旧书袋:把袋子里的加成写进物品说明(只显示,不改任何效果)。")
            .push("bagHud");
        BAG_TOOLTIP_ENABLED = builder.comment("是否显示这些说明。默认开启。")
            .define("bagTooltipEnabled", true);
        builder.pop();
        builder.comment("血战沙场之证:佩戴时把当前生命值锁定(只往下夹,不改它原本的作用)。")
            .push("bloodbath");
        BLOODBATH_LOCK_ENABLED = builder
            .comment("是否启用锁血。只往下夹、不会把血量写成 0,摘下后立刻恢复。默认开启。")
            .define("bloodbathLockEnabled", true);
        BLOODBATH_ALLOW_TOGGLE = builder
            .comment("允许用 Ctrl+左键 逐件开关锁血。默认开启。")
            .define("bloodbathAllowToggle", true);
        BLOODBATH_LOCK_VALUE = builder
            .comment("锁定到多少点生命值。默认 1,最低 0.5。")
            .defineInRange("bloodbathLockValue", 1.0D, 0.5D, 1024.0D);
        builder.pop();

        builder.comment("战斗无敌帧:受伤后一段时间免疫任何形式的伤害,末尾可放开若干 tick 作为输出窗口。").push("combatInvuln");
        COMBAT_INVULN_ENABLED = builder
            .comment("是否启用战斗无敌帧。默认开启。")
            .define("combatInvulnEnabled", true);
        COMBAT_INVULN_TICKS = builder
            .comment("无敌帧持续 tick 数(20 tick = 1 秒)。默认 12。")
            .defineInRange("combatInvulnTicks", 12, 0, 200);
        COMBAT_INVULN_OUTPUT = builder
            .comment("无敌帧末尾放开的输出窗口 tick 数(让目标仍能被持续输出)。默认 1。")
            .defineInRange("combatInvulnOutput", 1, 0, 200);
        COMBAT_INVULN_WHITELIST = builder
            .comment("不受无敌帧限制的伤害白名单:可填伤害类型 id(如 tacz:bullet)或实体类名片段(如 org.confluence.terraentity)。")
            .defineListAllowEmpty("combatInvulnWhitelist",
                List.of("tacz:bullet", "tacz:bullet_ignore_armor", "tacz:bullet_void", "tacz:bullet_void_ignore_armor",
                    "terra_entity:summon", "terra_entity:summoner",
                    "com.tacz.guns", "org.confluence.terraentity"),
                o -> o instanceof String);
        builder.pop();

        builder.comment("战利品保底 / 灌注联动").push("lootGuarantee");
        HELL_BLADE_GUARANTEED = builder
            .comment("战利品表里含「地狱刃片护符」时,每名玩家首次开箱必得一份。默认开启。")
            .define("hellBladeGuaranteed", true);
        HELL_BLADE_CHANCE = builder
            .comment("4.0.1:地狱刃片在【每个】下界地牢箱子里的出现概率(0~1)。",
                "一个堡垒遗迹有多张表、几十个箱子,设成 1.0 会一次刷出十几个。",
                "默认 0.15(15%)。想要每箱必出就设 1.0。")
            .defineInRange("hellBladeChance", 0.15D, 0.0D, 1.0D);
        PEARL_INFUSION_ENABLED = builder
            .comment("启用「附魔台位阶 ≥ 15 时,大地之心可灌注成附魔师珍珠」的神化灌注配方。默认开启。")
            .define("pearlInfusionEnabled", true);
        PEARL_EXTRA_ONLY_EXISTING = builder
            .comment("3.8.5:珍珠在神化附魔台上的补掷,是否只提升输入物品【已有】的附魔。",
                "true = 不新增任何新附魔(过滤书架/海洋过滤书架的限制不会被绕过);",
                "false = 旧行为,允许补掷带出新附魔。默认 true。")
            .define("pearlExtraEnchantOnlyExisting", true);

        builder.comment("【执行之手】未完成功能:对 Boss 的破防(跳过死亡动画)与真实伤害转化。",
            "实测会导致部分 Boss 无法被击杀,默认【关闭】。需要时再打开。").push("handOfExecution");
        HAND_OF_EXECUTION_ENABLED = builder
            .comment("执行之手总开关。false = 完全失效(既不改真实伤害、也不破防 Boss)。默认 false。")
            .define("enabled", false);
        builder.pop();
        SPELLSTONE_BOOST_ENABLED = builder
            .comment("3.1.0:给神秘遗物/扩展的战利品表追加一个术石池,大幅提升术石被抽到的概率。默认开启。")
            .define("spellstoneBoostEnabled", true);
        SPELLSTONE_BOOST_CHANCE = builder
            .comment("3.9.0:每个符合条件的箱子,出现术石的概率(0.15 = 15%)。",
                "落在 0~1 之间;0 = 永不出现,1 = 每箱必出。默认 0.15。")
            .defineInRange("spellstoneBoostChance", 0.15D, 0.0D, 1.0D);
        SPELLSTONE_BOOST_ROLLS = builder
            .comment("追加术石池的 rolls(每箱抽几次)。1.0 = 每个符合条件的箱子必抽一次。")
            .defineInRange("spellstoneBoostRolls", 1.0D, 0.0D, 8.0D);
        SPELLSTONE_BOOST_WEIGHT = builder
            .comment("术石条目权重倍数。1.0 = 与神秘遗物原权重一致。")
            .defineInRange("spellstoneBoostWeight", 1.0D, 0.1D, 100.0D);
        builder.pop();

        builder.comment("磁吸戒指:把「磁吸戒指」与「超级磁吸戒指」的吸取范围改成指定值。")
            .push("magnet");
        MAGNET_RANGE_ENABLED = builder
            .comment("是否启用范围增强。关闭后完全保留神秘遗物原本的吸取范围。默认开启。")
            .define("enabled", true);
        MAGNET_RANGE = builder
            .comment("吸取范围(格)。默认 36(神秘遗物原本远小于这个数)。")
            .defineInRange("range", 36, 1, 256);
        builder.pop();

        builder.comment("锻造者宝石:铁砧里两本同附魔同级的书可以直接升到最高等级。")
            .push("forgerGem");
        FORGER_GEM_ANVIL_ENABLED = builder
            .comment("是否启用铁砧升级。关闭后铁砧完全恢复原版行为。默认开启。")
            .define("anvilUpgradeEnabled", true);
        FORGER_GEM_TOOLTIP_ENABLED = builder
            .comment("是否在宝石/晶体的说明里追加这一行提示。默认开启。")
            .define("tooltipEnabled", true);
        builder.pop();

        builder.comment("其他增强:原先写死、没有开关的功能,这里统一补上开关(默认全部开启,保持升级前行为)。")
            .push("enhancements");
        ALL_DAMAGE_CRIT_ENABLED = builder
            .comment("玩家造成任何伤害都有概率触发暴击,并沿用各模组自己加的爆伤。默认开启。")
            .define("allDamageCritEnabled", true);
        ALL_DAMAGE_CRIT_CHANCE = builder
            .comment("上面那条的触发概率(百分比)。默认 50。")
            .defineInRange("allDamageCritChance", 50.0D, 0.0D, 100.0D);
        BEACON_ENHANCE_ENABLED = builder
            .comment("信标增强(扩大作用范围与效果等级)。默认开启。")
            .define("beaconEnhanceEnabled", true);
        BASTION_TREASURE_ENABLED = builder
            .comment("堡垒遗迹宝箱的扭曲之心抽取。默认开启。")
            .define("bastionTreasureEnabled", true);
        SHARPSHOOTER_ENABLED = builder
            .comment("神射手护符:弹射物 +20%。默认开启。")
            .define("sharpshooterEnabled", true);
        MIRROR_SHATTER_ENABLED = builder
            .comment("镜碎(受伤 +100%)与镜中之影的运行时。默认开启。")
            .define("mirrorShatterEnabled", true);
        SUN_BOTTLE_ENABLED = builder
            .comment("瓶装太阳(金块+正午转化 / 亡灵增伤 / 饱和与生命回复)。默认开启。")
            .define("sunBottleEnabled", true);
        WAR_FOG_ENABLED = builder
            .comment("瓶装战争迷雾(击杀转化 + 无差别仇恨)。默认开启。")
            .define("warFogEnabled", true);
        SPIRIT_LIGHT_ENABLED = builder
            .comment("瓶装灵界之光(副手移动转化 + 额外掉落)。默认开启。")
            .define("spiritLightEnabled", true);
        STYX_WATER_ENABLED = builder
            .comment("瓶装冥河之水(图腾转化 + 免死一次)。默认开启。")
            .define("styxWaterEnabled", true);
        FISHING_HAUL_ENABLED = builder
            .comment("渔获瓶(按幸运值提升钓鱼产出)。默认开启。")
            .define("fishingHaulEnabled", true);
        EVIL_ESSENCE_ENABLED = builder
            .comment("邪恶精髓复制。默认开启。")
            .define("evilEssenceEnabled", true);
        UNBREAKABLE_ENCHANT_ENABLED = builder
            .comment("「不毁」附魔的生效逻辑。默认开启。")
            .define("unbreakableEnchantEnabled", true);
        ELEMENTAL_MAGIC_ENABLED = builder
            .comment("元素魔法附魔的伤害追加。关闭后附魔还在,但不再追加属性伤害。默认开启。")
            .define("elementalMagicEnabled", true);
        ELEMENTAL_SPELL_CAST_ENABLED = builder
            .comment("攻击时自动施放武器注入/魔法书选中的法术。默认开启。")
            .define("elementalSpellCastEnabled", true);
        MERCURY_SERPENT_ENABLED = builder
            .comment("水银之蛇效果(护盾/濒死/掉落)。默认开启。")
            .define("mercurySerpentEnabled", true);
        TACZ_SPELL_CAST_ENABLED = builder
            .comment("TACZ 开枪连携施法(需要装 TACZ)。默认开启。")
            .define("taczSpellCastEnabled", true);
        BLOOD_CALAMITY_ENABLED = builder
            .comment("血色之灾效果(每层负面 +10% 受伤)。默认开启。")
            .define("bloodCalamityEnabled", true);
        CALAMITY_ORIGIN_ENABLED = builder
            .comment("根源之祸效果(下一次受伤结算 40% 上限伤害)。默认开启。")
            .define("calamityOriginEnabled", true);
        ETERNAL_SILENCE_ENABLED = builder
            .comment("永暗之寂效果(禁止一切右键使用)。默认开启。")
            .define("eternalSilenceEnabled", true);
        VALUE_EDITOR_ENABLED = builder
            .comment("数值编辑器(创造专用)。默认开启。")
            .define("valueEditorEnabled", true);
        builder.pop();

        builder.comment("七咒之戒:修正第四重咒的伤害削减(不改那三本书本身)。").push("cursedRing");
        FOURTH_CURSE_FIX_ENABLED = builder
            .comment("是否修正:戴着七咒之戒、身上带着古旧书袋、袋里有倒转之启/恩惠之典/无止之言时生效。默认开启。")
            .define("fourthCurseFixEnabled", true);
        FOURTH_CURSE_FIX_RESIDUAL_PERCENT = builder
            .comment("修正后仍保留的惩罚百分比。0 = 完全不削减(默认);100 = 等于不修正。")
            .defineInRange("fourthCurseFixResidualPercent", 0.0D, 0.0D, 100.0D);
        builder.pop();

        builder.comment("失落引擎:拦下它的惩罚雷击。").push("engine");
        ENGINE_DISABLE_LIGHTNING_ITEM_CLEAR = builder
            .comment("佩戴时取消附近的惩罚雷击(不会被点燃、不会掉血、物品不会被清)。默认开启。")
            .define("disableLightningItemClear", true);
        builder.pop();

        builder.comment("无止暴食之证(神秘佳肴):饱食度锁定与自动回血。")
            .push("gluttony");
        GLUTTONY_ENABLED = builder.comment("是否启用佩戴效果。默认开启。")
            .define("enabled", true);
        GLUTTONY_LOCKED_FOOD_LEVEL = builder
            .comment("佩戴时强制保持的饱食度。默认 0(饥饿条常空)。")
            .defineInRange("lockedFoodLevel", 0, 0, 20);
        GLUTTONY_LOCKED_SATURATION = builder
            .comment("佩戴时强制保持的饱和度。默认 20(消耗只扣饱和度)。")
            .defineInRange("lockedSaturation", 20.0D, 0.0D, 20.0D);
        GLUTTONY_HEAL_INTERVAL_TICKS = builder
            .comment("每多少游戏刻回一次血。默认 10 刻(约 0.5 秒)。")
            .defineInRange("healIntervalTicks", 10, 1, 200);
        GLUTTONY_HEAL_AMOUNT = builder
            .comment("每次回血恢复多少生命。默认 1 点。")
            .defineInRange("healAmount", 1.0D, 0.0D, 20.0D);
        builder.pop();

        builder.comment("附魔师的珍珠:在神化附魔台与重铸台(神化/远古)上同样生效。").push("pearl");
        PEARL_FIX_ENCHANTING_TABLE = builder
            .comment("神化附魔台:不需要青金石,并额外多掷一次附魔。默认开启。")
            .define("fixEnchantingTable", true);
        PEARL_FREE_REFORGE = builder
            .comment("神化/远古重铸台:不消耗新生符石。默认开启。")
            .define("freeReforge", true);
        builder.pop();

        builder.comment("书籍:在物品栏里用 Ctrl+左键 禁止/恢复翻阅。").push("books");
        BOOKS_ENABLED = builder.comment("总开关。关闭后不能再禁止翻阅,已经禁止的也会恢复。默认开启。")
            .define("enabled", true);
        BOOKS_TOGGLE_ENABLED = builder
            .comment("允许用 Ctrl+左键 切换。关闭后已禁止的书仍不能翻阅,但不能再改。默认开启。")
            .define("toggleEnabled", true);
        builder.pop();

        ADVENTURE_CHARM_ALLOW_MINING = builder
            .comment("佩戴冒险者之证时仍可正常挖掘方块。关闭后恢复它原本\"无法挖掘\"的负面。")
            .define("adventureCharmAllowMining", true);
        DESPAIR_INSIGNIA_ALLOW_MINING = builder
            .comment("佩戴绝望者证章时仍可正常挖掘方块。关闭后恢复原本\"无法挖掘\"的负面。")
            .define("despairInsigniaAllowMining", true);
        MINING_TOOLTIP = builder
            .comment("在上面两件饰品的说明里加一行\"可正常挖掘方块\"。默认开启。")
            .define("miningTooltip", true);
        CONVERT_ON_DEATH = builder
            .comment("死亡时把手里的冒险者之证变成绝望者证章(1 换 1)。默认开启。")
            .define("convertOnDeath", true);
        CONVERT_HANDS_ONLY = builder
            .comment("只算主手/副手(默认);关闭后背包与饰品栏里的第一枚也会一起转化。")
            .define("convertHandsOnly", true);
        CONVERT_NOTIFY = builder.comment("转化时给玩家一条聊天提示。默认开启。")
            .define("convertNotify", true);
        builder.comment("自动熔炼:挖到的掉落先按普通熔炉烧一遍再掉出来(新附魔)。")
            .push("autoSmelt");
        AUTO_SMELT_ENABLED = builder.comment("总开关。关闭后附魔还在,但不再替换任何掉落。默认开启。")
            .define("enabled", true);
        AUTO_SMELT_DISABLED_WHILE_SNEAKING = builder
            .comment("按住潜行键时,这次挖掘不熔炼。默认开启。")
            .define("disabledWhileSneaking", true);
        AUTO_SMELT_LOOT_ENABLED = builder
            .comment("是否把附魔书加进一些原版箱子的战利品。默认开启。")
            .define("lootEnabled", true);
        AUTO_SMELT_YIELD_TO_WONDERENCHANT = builder
            .comment("和奇妙附魔的同类附魔同时存在时让位给对方,保证只烧一次。默认开启。")
            .define("yieldToWonderenchant", true);
        AUTO_SMELT_LOOT_ONLY = builder
            .comment("是否退回\"只有战利品附魔书才拿得到\"的旧行为。默认关闭 = 附魔台/铁砧/村民也能拿到。")
            .define("lootOnlyAcquisition", false);
        builder.pop();

        builder.comment("恶魔契约:佩戴者受伤时,这次伤害的数值被改成当前生命值。").push("demonPact");
        DEMON_PACT_LETHAL = builder
            .comment("开启(默认)= 任何伤害都会当场致死;关闭 = 只把伤害上限压到当前生命值。")
            .define("lethal", true);
        builder.pop();

        builder.comment("旅行家帽子:移动速度、生命上限、攻击力三项加成。").push("travelerHat");
        TRAVELER_HAT_SPEED_MODE = builder
            .comment("速度加成方式:PERCENT(默认)= 按比例 +50%;FLAT = 直接在原速度上加 0.5。")
            .define("speedMode", "PERCENT");
        TRAVELER_HAT_SPEED_VALUE = builder
            .comment("速度加成的数值(默认 0.5,含义由上一项决定)。")
            .defineInRange("speedValue", 0.5D, 0.0D, 100.0D);
        TRAVELER_HAT_HEALTH_PER_BIOME = builder
            .comment("每探索一个群系增加多少生命上限。默认 2。")
            .defineInRange("healthPerBiome", 2.0D, 0.0D, 1024.0D);
        TRAVELER_HAT_ATTACK_PER_DIMENSION = builder
            .comment("每去一个维度增加多少攻击力。默认 2。")
            .defineInRange("attackPerDimension", 2.0D, 0.0D, 1024.0D);
        TRAVELER_HAT_MAX_BIOMES = builder
            .comment("群系最多算多少个。默认 256。")
            .defineInRange("maxBiomes", 256, 0, 4096);
        TRAVELER_HAT_MAX_DIMENSIONS = builder
            .comment("维度最多算多少个。默认 32。")
            .defineInRange("maxDimensions", 32, 0, 4096);
        TRAVELER_HAT_MAX_HEALTH_BONUS = builder
            .comment("生命上限最多加多少(默认 40 = 20 心)。太大时血条会画到屏幕外。")
            .defineInRange("maxHealthBonus", 40.0D, 0.0D, 4096.0D);
        builder.pop();

        builder.comment("恶魔的手骨:三件套共鸣里的禁疗时长。").push("demonBone");
        DEMON_BONE_HEAL_BLOCK_SECONDS = builder
            .comment("被手骨命中过的目标多少秒内不能回血(三件套时生效)。默认 5 秒,0 = 关闭。")
            .defineInRange("healBlockSeconds", 5, 0, 60);
        DEMON_BONE_MAX_ABSORPTION = builder
            .comment("一次最多给多少伤害吸收(默认 20 = 10 颗金心)。数值过大时原版/多排 HUD 会画不出来。")
            .defineInRange("maxAbsorption", 20.0D, 0.0D, 1024.0D);
        UNCERTAIN_FOG_RADIUS = builder
            .comment("瓶装不定之雾的生效半径(格)。默认 16。")
            .defineInRange("radius", 16.0D, 1.0D, 128.0D);
        builder.pop();

        builder.comment("睡前读物:修正七咒之戒的第七重咒(失眠)。").push("bedtimeReading");
        BEDTIME_READING_FIX = builder
            .comment("书在古旧书袋里或拿在手中时,第七重咒不再打断睡眠。默认开启。")
            .define("fixSeventhCurse", true);
        builder.pop();

        builder.comment("深渊侵蚀的大地之心:身上带着深渊之心时,大地之心会被侵蚀。").push("abyssalEarthHeart");
        ABYSSAL_EARTH_HEART_ENABLED = builder
            .comment("是否启用这种形态的转化。默认开启。")
            .define("enabled", true);
        ABYSSAL_EARTH_HEART_AUTO_TRIGGERS = builder
            .comment("自动把\"用深渊之心做出来的东西\"也算作来源(按已加载的配方推导)。默认开启。")
            .define("autoDeriveTriggers", true);
        ABYSSAL_EARTH_HEART_EXTRA_TRIGGERS = builder
            .comment("额外追加的来源物品(填注册名,例如 enigmaticlegacy:desolation_ring)。留空即可。")
            .defineList("extraTriggerItems", java.util.List.of(), element -> element instanceof String);
        builder.pop();

        LOG_STARTUP_LINE = builder
            .comment("是否在启动时打印一行加载提示(便于确认模组装上了)。默认关闭。")
            .define("logStartupLine", false);
        DEBUG_LOG = builder.comment("是否输出调试日志(排查问题时才需要打开)。默认关闭。")
            .define("debugLog", false);

        builder.pop();
        SPEC = builder.build();
    }

    private RingConfig() {}

    public static boolean omniEnabled() {
        return OMNI_ENABLED.get();
    }

    public static boolean omniAttachToPlayers() {
        return OMNI_ATTACH_TO_PLAYERS.get();
    }

    public static double ringOmniBonus() {
        return RING_OMNI_BONUS.get();
    }

    public static boolean ringUnbreakable() {
        return RING_UNBREAKABLE.get();
    }

    /** 是否冻结背叛值(BlessBetrayal)。总开关关掉时一律为 false。 */
    public static boolean freezeBetrayal() {
        return RING_UNBREAKABLE.get() && RING_FREEZE_BETRAYAL.get();
    }

    /** 是否隐藏救赎之戒的假耐久条。总开关关掉时一律为 false。 */
    public static boolean hideBetrayalBar() {
        return RING_UNBREAKABLE.get() && RING_HIDE_BETRAYAL_BAR.get();
    }

    public static boolean allowRingWithCursedRing() {
        return RING_ALLOW_WITH_CURSED_RING.get();
    }

    public static boolean allowRingWithBrokenRing() {
        return RING_ALLOW_WITH_BROKEN_RING.get();
    }

    /** 是否把被 Curios 槽位收缩挤出来的救赎之戒放回 ring 槽(而不是进背包)。 */
    public static boolean keepRingOnRelogin() {
        return RING_KEEP_ON_RELOGIN.get();
    }

    public static boolean allowScrollWithCursedScroll() {
        return SCROLL_ALLOW_WITH_CURSED_SCROLL.get();
    }

    public static boolean disableEngineLightningItemClear() {
        return ENGINE_DISABLE_LIGHTNING_ITEM_CLEAR.get();
    }

    public static boolean gluttonyEnabled() {
        return GLUTTONY_ENABLED.get();
    }

    /** 佩戴时被钉住的饱食度(默认 0)。 */
    public static int gluttonyLockedFoodLevel() {
        return GLUTTONY_LOCKED_FOOD_LEVEL.get();
    }

    /** 佩戴时被钉住的饱和度(默认 20)。 */
    public static float gluttonyLockedSaturation() {
        return GLUTTONY_LOCKED_SATURATION.get().floatValue();
    }

    /** 回血间隔(tick,默认 10)。 */
    public static int gluttonyHealIntervalTicks() {
        return GLUTTONY_HEAL_INTERVAL_TICKS.get();
    }

    /** 每次治疗的生命值(默认 1.0)。 */
    public static float gluttonyHealAmount() {
        return GLUTTONY_HEAL_AMOUNT.get().floatValue();
    }

    /** 是否修复珍珠在神化附魔台上的失效(默认 true)。 */
    public static boolean pearlFixEnchantingTable() {
        return PEARL_FIX_ENCHANTING_TABLE.get();
    }

    /** 是否让珍珠免除重铸的新生符石消耗(默认 true)。 */
    public static boolean pearlFreeReforge() {
        return PEARL_FREE_REFORGE.get();
    }

    /** 书籍 Ctrl+左键 禁用/启用 功能的总开关(默认 true)。 */
    public static boolean booksEnabled() {
        return BOOKS_ENABLED.get();
    }

    /** 是否允许切换书籍的禁用状态(默认 true)。 */
    public static boolean booksToggleEnabled() {
        return BOOKS_TOGGLE_ENABLED.get();
    }

    public static boolean debugLog() {
        return DEBUG_LOG.get();
    }

    /** 恶魔契约:true = 字面实现(伤害 = 当前生命值,任何伤害都致死)。 */
    public static boolean demonPactLethal() {
        return DEMON_PACT_LETHAL.get();
    }

    /** 旅行家帽子:速度模式是否为百分比(默认 true;配置写 "FLAT" 则改为直接加算)。 */
    public static boolean travelerHatSpeedIsPercent() {
        String mode = TRAVELER_HAT_SPEED_MODE.get();
        return !"FLAT".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    public static double travelerHatSpeedValue() {
        return TRAVELER_HAT_SPEED_VALUE.get();
    }

    public static double travelerHatHealthPerBiome() {
        return TRAVELER_HAT_HEALTH_PER_BIOME.get();
    }

    public static double travelerHatAttackPerDimension() {
        return TRAVELER_HAT_ATTACK_PER_DIMENSION.get();
    }

    public static int travelerHatMaxBiomes() {
        return TRAVELER_HAT_MAX_BIOMES.get();
    }

    /** 1.2.10:生命上限封顶值(默认 40)。 */
    public static double travelerHatMaxHealthBonus() {
        return TRAVELER_HAT_MAX_HEALTH_BONUS.get();
    }

    public static int travelerHatMaxDimensions() {
        return TRAVELER_HAT_MAX_DIMENSIONS.get();
    }

    /** 启动那一行 INFO 是否输出(默认 false = 彻底零输出)。 */
    public static boolean logStartupLine() {
        return LOG_STARTUP_LINE.get();
    }

    /** 【自动熔炼】总开关(默认 true)。 */
    public static boolean autoSmeltEnabled() {
        return AUTO_SMELT_ENABLED.get();
    }

    /** 【自动熔炼】潜行时是否停用(默认 true,与奇妙附魔一致)。 */
    public static boolean autoSmeltDisabledWhileSneaking() {
        return AUTO_SMELT_DISABLED_WHILE_SNEAKING.get();
    }

    /** 【自动熔炼】获取途径:是否注入箱子战利品表(默认 true)。 */
    public static boolean autoSmeltLootEnabled() {
        return AUTO_SMELT_LOOT_ENABLED.get();
    }

    /** 【自动熔炼】同持 wonderenchant 时是否让位(默认 false:本模组照常替换掉落表)。 */
    public static boolean yieldToWonderenchant() {
        return AUTO_SMELT_YIELD_TO_WONDERENCHANT.get();
    }

    /** 【自动熔炼】是否退回"只有战利品附魔书"的旧获取途径(默认 false = 附魔台/铁砧/村民也能拿到)。 */
    public static boolean autoSmeltLootOnly() {
        return AUTO_SMELT_LOOT_ONLY.get();
    }

    /** 万钧之护卷轴的总开关。 */
    public static boolean scaleEnabled() {
        return SCALE_ENABLED.get();
    }

    /** 万钧之护卷轴:雷击子效果是否启用(逐件开关见物品 NBT)。 */
    public static boolean scaleLightningEnabled() {
        return SCALE_ENABLED.get() && SCALE_LIGHTNING_ENABLED.get();
    }

    /** 每次命中累积电荷的概率(百分比,100 = 原版行为)。 */
    public static double scaleLightningChance() {
        return SCALE_LIGHTNING_CHANCE.get();
    }

    /** 攻击伤害加成(百分比,100 = +100%)。 */
    public static double scaleAttackDamagePercent() {
        return SCALE_ATTACK_DAMAGE_PERCENT.get();
    }

    /** 是否允许 Ctrl+左键 切换雷击开关。 */
    public static boolean scaleToggleEnabled() {
        return SCALE_ENABLED.get() && SCALE_TOGGLE_ENABLED.get();
    }

    /** 1.1.14:命中时把雷击电荷扩散给附近目标(修复横扫改动导致的落雷失效)。 */
    public static boolean scaleChargeSpread() {
        return SCALE_ENABLED.get() && SCALE_LIGHTNING_ENABLED.get() && SCALE_CHARGE_SPREAD.get();
    }

    /** 是否恢复扩展原本的「横扫攻击造成 100% 伤害」。 */
    public static boolean scaleRestoreSweepBoost() {
        return SCALE_RESTORE_SWEEP_BOOST.get();
    }

    /** 万钧之护卷轴(缺失返回 null,不抛异常)。 */
    public static Item thunderScrollItem() {
        return itemOrNull(THUNDER_SCROLL_ID);
    }

    public static boolean isThunderScroll(Item item) {
        Item scroll = thunderScrollItem();
        return scroll != null && item == scroll;
    }

    /** 所有本模组引用的物品 id(给 {@link ModIds} 做静态校验用)。 */
    public static java.util.List<ResourceLocation> allItemIds() {
        return java.util.List.of(RING_ID, CURSED_RING_ID, CURSED_SCROLL_ID, BROKEN_RING_ID, ENGINE_ID,
            ADVENTURE_CHARM_ID, DESPAIR_INSIGNIA_ID, THUNDER_SCROLL_ID, ANTIQUE_BAG_ID, THE_TWIST_ID,
            THE_BLESS_ID, THE_INFINITUM_ID, BERSERK_CHARM_ID, GLUTTONY_CHARM_ID, ENCHANTER_PEARL_ID,
            BEDTIME_READING_ID, EARTH_HEART_ID, ABYSSAL_HEART_ID);
    }

    /** 古旧书袋 tooltip 总开关(1.1.21:HUD 已删除,这个开关只控制 tooltip 与同步)。 */
    public static boolean bagTooltipEnabled() {
        return BAG_TOOLTIP_ENABLED.get();
    }

    /** 古旧书袋物品本体(用于 tooltip 判定)。 */
    public static Item antiqueBagItem2() {
        return antiqueBagItem();
    }

    /** 血战沙场之证:锁血总开关。 */
    public static boolean bloodbathLockEnabled() {
        return BLOODBATH_LOCK_ENABLED.get();
    }

    /** 血战沙场之证:是否允许 Ctrl+左键 切换。 */
    public static boolean bloodbathAllowToggle() {
        return BLOODBATH_LOCK_ENABLED.get() && BLOODBATH_ALLOW_TOGGLE.get();
    }

    /** 血战沙场之证:锁定到多少点生命值。 */
    public static double bloodbathLockValue() {
        return BLOODBATH_LOCK_VALUE.get();
    }

    /** 1.8.0:战斗无敌帧总开关。 */
    public static boolean combatInvulnEnabled() {
        try {
            return COMBAT_INVULN_ENABLED.get();
        }
        catch (Throwable t) {
            return true;
        }
    }

    /** 1.8.0:无敌帧 tick 数(默认 12)。 */
    public static int combatInvulnTicks() {
        try {
            return COMBAT_INVULN_TICKS.get();
        }
        catch (Throwable t) {
            return 12;
        }
    }

    /** 1.8.0:无敌帧末尾放开的输出窗口 tick 数(默认 1)。 */
    public static int combatInvulnOutput() {
        try {
            return COMBAT_INVULN_OUTPUT.get();
        }
        catch (Throwable t) {
            return 1;
        }
    }

    /** 3.1.0:术石战利品加权开关。 */
    /** 3.9.0:术石池出现概率(0~1),默认 0.15。 */
    public static double spellstoneBoostChance() {
        try {
            return SPELLSTONE_BOOST_CHANCE.get();
        }
        catch (RuntimeException | LinkageError e) {
            return 0.15D;
        }
    }

    public static boolean spellstoneBoostEnabled() {
        try { return SPELLSTONE_BOOST_ENABLED.get(); } catch (Throwable t) { return true; }
    }

    public static double spellstoneBoostRolls() {
        try { return SPELLSTONE_BOOST_ROLLS.get(); } catch (Throwable t) { return 1.0D; }
    }

    public static double spellstoneBoostWeight() {
        try { return SPELLSTONE_BOOST_WEIGHT.get(); } catch (Throwable t) { return 1.0D; }
    }

    /** 2.7.0:地狱刃片保底开关。 */
    /** 4.0.1:地狱刃片在每个箱子里的出现概率,默认 0.15。 */
    public static double hellBladeChance() {
        try {
            return HELL_BLADE_CHANCE.get();
        }
        catch (RuntimeException | LinkageError e) {
            return 0.15D;
        }
    }

    public static boolean hellBladeGuaranteed() {
        try {
            return HELL_BLADE_GUARANTEED.get();
        }
        catch (Throwable t) {
            return true;
        }
    }
    /** 3.8.2:执行之手是否启用(默认 false)。 */
    public static boolean handOfExecutionEnabled() {
        try {
            return HAND_OF_EXECUTION_ENABLED.get();
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** 4.5.0:磁吸戒指范围增强的总开关(默认 true)。 */
    public static boolean magnetRangeEnabled() {
        try {
            return MAGNET_RANGE_ENABLED.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    /** 4.5.0:磁吸戒指 / 超级磁吸戒指的吸取范围(格,默认 36)。 */
    public static int magnetRange() {
        try {
            return MAGNET_RANGE.get();
        }
        catch (RuntimeException | LinkageError e) {
            return MagnetRangeHandler.DEFAULT_RANGE;
        }
    }

    /** 4.5.0:锻造者宝石的铁砧升级开关(默认 true)。 */
    public static boolean forgerGemAnvilEnabled() {
        try {
            return FORGER_GEM_ANVIL_ENABLED.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    /** 4.5.0:锻造者宝石的说明行开关(默认 true)。 */
    public static boolean forgerGemTooltipEnabled() {
        try {
            return FORGER_GEM_TOOLTIP_ENABLED.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // 4.5.0:其余"原先写死"的增强开关(默认全部 true = 升级后行为不变)
    // ------------------------------------------------------------------

    /** 全伤暴击(默认 true)。 */
    public static boolean allDamageCritEnabled() {
        try {
            return ALL_DAMAGE_CRIT_ENABLED.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    /** 全伤暴击的触发概率(百分比,默认 50)。 */
    public static double allDamageCritChance() {
        try {
            return ALL_DAMAGE_CRIT_CHANCE.get();
        }
        catch (RuntimeException | LinkageError e) {
            return 50.0D;
        }
    }

    public static boolean beaconEnhanceEnabled() {
        try { return BEACON_ENHANCE_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean bastionTreasureEnabled() {
        try { return BASTION_TREASURE_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean sharpshooterEnabled() {
        try { return SHARPSHOOTER_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean mirrorShatterEnabled() {
        try { return MIRROR_SHATTER_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean sunBottleEnabled() {
        try { return SUN_BOTTLE_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean warFogEnabled() {
        try { return WAR_FOG_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean spiritLightEnabled() {
        try { return SPIRIT_LIGHT_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean styxWaterEnabled() {
        try { return STYX_WATER_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean fishingHaulEnabled() {
        try { return FISHING_HAUL_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean evilEssenceEnabled() {
        try { return EVIL_ESSENCE_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean unbreakableEnchantEnabled() {
        try { return UNBREAKABLE_ENCHANT_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean elementalMagicEnabled() {
        try { return ELEMENTAL_MAGIC_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean elementalSpellCastEnabled() {
        try { return ELEMENTAL_SPELL_CAST_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean mercurySerpentEnabled() {
        try { return MERCURY_SERPENT_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean taczSpellCastEnabled() {
        try { return TACZ_SPELL_CAST_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean bloodCalamityEnabled() {
        try { return BLOOD_CALAMITY_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean calamityOriginEnabled() {
        try { return CALAMITY_ORIGIN_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean eternalSilenceEnabled() {
        try { return ETERNAL_SILENCE_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    public static boolean valueEditorEnabled() {
        try { return VALUE_EDITOR_ENABLED.get(); } catch (RuntimeException | LinkageError e) { return true; }
    }

    /** 3.8.5:珍珠补掷是否只提升已有附魔(默认 true)。 */
    public static boolean pearlExtraEnchantOnlyExisting() {
        try {
            return PEARL_EXTRA_ONLY_EXISTING.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    /** 2.7.0:大地之心→附魔师珍珠 灌注开关。 */
    public static boolean pearlInfusionEnabled() {
        try {
            return PEARL_INFUSION_ENABLED.get();
        }
        catch (Throwable t) {
            return true;
        }
    }

    /** 1.8.4:不受无敌帧限制的伤害白名单(伤害类型 id 或实体类名片段)。 */
    public static List<String> combatInvulnWhitelist() {
        try {
            List<String> out = new java.util.ArrayList<>();
            for (Object o : COMBAT_INVULN_WHITELIST.get()) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        catch (Throwable t) {
            return List.of();
        }
    }

    /** 血战沙场之证(缺失返回 null,不抛异常)。 */
    public static Item berserkCharmItem() {
        return itemOrNull(BERSERK_CHARM_ID);
    }

    public static boolean isBerserkCharm(Item item) {
        Item charm = berserkCharmItem();
        return charm != null && item == charm;
    }

    /** 第四重咒修正的总开关。 */
    public static boolean fourthCurseFixEnabled() {
        return FOURTH_CURSE_FIX_ENABLED.get();
    }

    /** 三件套:手骨的禁疗秒数。 */
    public static int demonBoneHealBlockSeconds() {
        return DEMON_BONE_HEAL_BLOCK_SECONDS.get();
    }

    /** 1.2.11c:不定之雾半径。 */
    public static double uncertainFogRadius() {
        return UNCERTAIN_FOG_RADIUS.get();
    }

    /** 1.2.10:伤害吸收上限(默认 20 = 10 颗金心)。 */
    public static double demonBoneMaxAbsorption() {
        return DEMON_BONE_MAX_ABSORPTION.get();
    }

    /** 睡前读物是否修正第七重咒(失眠)。 */
    public static boolean bedtimeReadingFixesSeventhCurse() {
        return BEDTIME_READING_FIX.get();
    }

    /** 修正后仍保留的惩罚百分比(0 = 完全修正)。 */
    public static double fourthCurseFixResidualPercent() {
        return FOURTH_CURSE_FIX_RESIDUAL_PERCENT.get();
    }

    public static boolean isAntiqueBag(Item item) {
        Item bag = antiqueBagItem();
        return bag != null && item == bag;
    }

    public static Item antiqueBagItem() {
        return itemOrNull(ANTIQUE_BAG_ID);
    }

    public static Item theTwistItem() {
        return itemOrNull(THE_TWIST_ID);
    }

    public static Item theBlessItem() {
        return itemOrNull(THE_BLESS_ID);
    }

    public static Item theInfinitumItem() {
        return itemOrNull(THE_INFINITUM_ID);
    }

    /** 冒险者之证:是否允许挖掘方块(默认 true)。 */    public static boolean adventureCharmAllowMining() {
        return ADVENTURE_CHARM_ALLOW_MINING.get();
    }

    /** 绝望者证章:是否允许挖掘方块(默认 true)。 */
    public static boolean despairInsigniaAllowMining() {
        return DESPAIR_INSIGNIA_ALLOW_MINING.get();
    }

    /** 是否给这两件饰品补"已可正常挖掘方块"的 tooltip。 */
    public static boolean miningTooltip() {
        return MINING_TOOLTIP.get();
    }

    /** 死亡时是否把手里的冒险者之证转化成绝望者证章。 */
    public static boolean convertOnDeath() {
        return CONVERT_ON_DEATH.get();
    }

    /** 转化是否只算主手/副手(默认 true)。 */
    public static boolean convertHandsOnly() {
        return CONVERT_HANDS_ONLY.get();
    }

    /** 转化成功时是否发提示。 */
    public static boolean convertNotify() {
        return CONVERT_NOTIFY.get();
    }

    /** 冒险者之证(缺失返回 null,不抛异常)。 */
    public static Item adventureCharmItem() {
        return itemOrNull(ADVENTURE_CHARM_ID);
    }

    /** 绝望者证章(缺失返回 null,不抛异常)。 */
    public static Item despairInsigniaItem() {
        return itemOrNull(DESPAIR_INSIGNIA_ID);
    }

    public static boolean isAdventureCharm(Item item) {
        Item charm = adventureCharmItem();
        return charm != null && item == charm;
    }

    public static boolean isDespairInsignia(Item item) {
        Item insignia = despairInsigniaItem();
        return insignia != null && item == insignia;
    }

    private static Item itemOrNull(ResourceLocation id) {
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : item;
    }

    public static Item ringItem() {
        return itemOrNull(RING_ID);
    }

    public static Item cursedRingItem() {
        return itemOrNull(CURSED_RING_ID);
    }

    public static Item cursedScrollItem() {
        return itemOrNull(CURSED_SCROLL_ID);
    }

    public static Item brokenRingItem() {
        return itemOrNull(BROKEN_RING_ID);
    }

    public static Item engineItem() {
        return itemOrNull(ENGINE_ID);
    }

    /**
     * 无止暴食之证。严格按注册表 id 精确匹配:模组或物品缺失时返回 null(不抛异常),
     * 调用方据此直接不生效。
     */
    public static Item gluttonyCharmItem() {
        return itemOrNull(GLUTTONY_CHARM_ID);
    }

    public static boolean isGluttonyCharm(Item item) {
        Item charm = gluttonyCharmItem();
        return charm != null && item == charm;
    }

    /** 附魔师的珍珠(严格按注册表 id;缺失返回 null,不抛异常)。 */
    public static Item enchanterPearlItem() {
        return itemOrNull(ENCHANTER_PEARL_ID);
    }

    public static boolean isEnchanterPearl(Item item) {
        Item pearl = enchanterPearlItem();
        return pearl != null && item == pearl;
    }

    public static Item enablerItem() {
        return itemOrNull(ResourceLocation.tryParse(SCROLL_ENABLER_ID.get()));
    }

    public static boolean isRing(Item item) {
        Item ring = ringItem();
        return ring != null && item == ring;
    }

    public static AttributeModifier ringOmniModifier(Attribute omni) {
        return new AttributeModifier(RING_OMNI_UUID, "EnigmaticEnhance Ring Omni",
            ringOmniBonus(), AttributeModifier.Operation.ADDITION);
    }

    /** 【深渊侵蚀的大地之心】总开关(默认 true)。 */
    public static boolean abyssalEarthHeartEnabled() {
        return ABYSSAL_EARTH_HEART_ENABLED.get();
    }

    /** 【深渊侵蚀的大地之心】是否自动推导来源物品(默认 true)。 */
    public static boolean abyssalEarthHeartAutoTriggers() {
        return ABYSSAL_EARTH_HEART_AUTO_TRIGGERS.get();
    }

    /** 【深渊侵蚀的大地之心】配置里额外追加的来源物品 id(默认空)。 */
    public static java.util.List<? extends String> abyssalEarthHeartExtraTriggers() {
        return ABYSSAL_EARTH_HEART_EXTRA_TRIGGERS.get();
    }

    /** 大地之心本体(神秘遗物;缺失返回 null,不抛异常)。 */
    public static Item earthHeartItem() {
        return itemOrNull(EARTH_HEART_ID);
    }

    /** 深渊之心本体(神秘遗物;缺失返回 null,不抛异常)。 */
    public static Item abyssalHeartItem() {
        return itemOrNull(ABYSSAL_HEART_ID);
    }

    public static boolean isEarthHeart(Item item) {
        Item heart = earthHeartItem();
        return heart != null && item == heart;
    }
}
