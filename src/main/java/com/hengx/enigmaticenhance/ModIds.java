package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * 所有"本模组自己造"的 id/path 的**静态校验**(1.1.17 新增)。
 * <p>
 * 起因:1.1.16 的客户端 HUD 在 {@code RegisterGuiOverlaysEvent#registerAboveAll} 里传了
 * {@code "enigmaticenhance:bag_hud"}(带冒号),而 Forge 会自己再拼一次命名空间
 * ({@code new ResourceLocation(modid, id)}),于是 path 变成 {@code enigmaticenhance:bag_hud} → 抛
 * {@code ResourceLocationException: Non [a-z0-9/._-] character} → 客户端一启动就崩。
 * <p>
 * 本类把这类"纯字符串 id"集中成常量,并提供 {@link #problems()} 做**离线可断言**的校验
 * (自检直接调用;mod 初始化时若有问题会打一条 WARN,正常情况下零输出)。
 * 规则:两段式 id 的 <b>path 里绝不能含冒号</b>,且必须能被 {@link ResourceLocation} 解析、
 * {@code toString()} 能原样回读。
 */
public final class ModIds {

    /** 网络通道名(两段式:enigmaticenhance + 这个 path)。 */
    public static final String CHANNEL_PATH = "main";

    /**
     * 1.1.21:左上角 HUD **整块删除**(不再注册任何 GuiOverlay)。
     * 历史上那处 {@code RegisterGuiOverlaysEvent#registerAboveAll} 传带冒号的 path 正是 1.1.16 崩溃的根源;
     * 现在这个 overlay 已经不存在,所以它不再参与 {@link #problems()} 校验(校验一个不存在的东西没有意义)。
     * 常量仅作历史记录保留,标记 {@code @Deprecated}。
     */
    @Deprecated
    public static final String OVERLAY_PATH_REMOVED = "bag_hud";

    /**
     * 1.1.20 新增附魔 enigmaticenhance:auto_smelt 的**裸 path**(注册名由 DeferredRegister 拼命名空间)。
     * 同一个 path 也被战利品池名 {@link AutoSmeltLoot#POOL_NAME} 与配方 id 复用。
     */
    public static final String AUTO_SMELT_PATH = "auto_smelt";

    /** 1.1.24 新增饰品「大地护符」的裸 path(注册名 = enigmaticenhance + 这个 path)。 */
    public static final String EARTH_AMULET_PATH = "earth_amulet";

    /** 基础配方 id 的 path(污染大地之心 + 3 金粒)。 */
    public static final String RECIPE_EARTH_AMULET_PATH = "earth_amulet";

    /** 药水注入配方的 path。 */
    public static final String RECIPE_EARTH_AMULET_POTION_PATH = "earth_amulet_potion";

    /** 头颅注入配方的 path。 */
    public static final String RECIPE_EARTH_AMULET_HEAD_PATH = "earth_amulet_head";

    /** 下界之星强化配方的 path。 */
    public static final String RECIPE_EARTH_AMULET_EMPOWER_PATH = "earth_amulet_empower";

    /**
     * 1.2.2:「深渊侵蚀的大地之心」做护符的配方 path。
     * 与 {@link #RECIPE_EARTH_AMULET_PATH} 只差材料的标记({@code isTainted:1b} vs {@code abyssalTaint:1b}),
     * 因为 {@code forge:partial_nbt} 这类材料判定没有"或"的写法,只能各写一条。
     */
    public static final String RECIPE_EARTH_AMULET_ABYSSAL_PATH = "earth_amulet_abyssal";

    /** Curios 的饰品槽 id(护符走 curios:charm)。 */
    public static final String CURIO_SLOT_CHARM = "charm";

    /**
     * 1.2.2c:「需要特定形态大地之心」的配方序列化器 path({@code enigmaticenhance:heart_shaped})。
     * 旅行家帽子 / 恶魔的血杯 / 恶魔契约 / 恶魔的手骨 四条注册配方共用它。
     */
    public static final String RECIPE_HEART_SHAPED_PATH = "heart_shaped";

    /** 1.2.2c:旅行家帽子配方(任意大地之心)的 path。 */
    public static final String RECIPE_TRAVELER_HAT_PATH = "traveler_hat";

    /** 1.2.2c:恶魔的血杯配方(深渊侵蚀大地之心)的 path。 */
    public static final String RECIPE_DEMON_BLOOD_CHALICE_PATH = "demon_blood_chalice";

    /** 1.2.2c:恶魔契约配方(深渊侵蚀大地之心)的 path。 */
    public static final String RECIPE_DEMON_PACT_PATH = "demon_pact";

    /** 1.2.2c:恶魔的手骨配方(深渊侵蚀大地之心)的 path。 */
    public static final String RECIPE_DEMON_HAND_BONE_PATH = "demon_hand_bone";

    /** 1.1.25 新增饰品「恶魔契约」的裸 path(注册名 = enigmaticenhance + 这个 path)。 */
    public static final String DEMON_PACT_PATH = "demon_pact";

    /** 1.1.25c 新增饰品「旅行家帽子」的裸 path(Curios curios:head)。 */
    public static final String TRAVELER_HAT_PATH = "traveler_hat";

    /** 1.1.28a 新增饰品「恶魔血杯」的裸 path(注册名 = enigmaticenhance + 这个 path)。 */
    public static final String DEMON_BLOOD_CHALICE_PATH = "demon_blood_chalice";

    /** 1.1.28b 新增书籍「睡前读物」的裸 path(注册名 = enigmaticenhance + 这个 path)。 */
    public static final String BEDTIME_READING_PATH = "bedtime_reading";

    /** 1.1.28c 新增投掷物「纯净符文」的裸 path(物品 id 与实体 id 共用这个 path,两个注册表互不影响)。 */
    public static final String PURE_RUNE_PATH = "pure_rune";

    /** 2.4.0:enigmaticenhance:fake_ledger —— 假账生成器(创造物品)。 */
    public static final String FAKE_LEDGER_PATH = "fake_ledger";

    /** 1.1.28c 落点白光视觉体的实体 path(纯客户端视觉,无存档数据)。 */
    public static final String PURE_RUNE_FLASH_PATH = "pure_rune_flash";


    /** 1.2.5b 新增「大地之瓶」(玻璃瓶 + 大地之心碎片无序合成)。 */
    public static final String EARTH_BOTTLE_PATH = "earth_bottle";

    /** 1.2.5c 新增「瓶装历史迷雾」(大地之瓶在背包 0.5 小时后转化)。 */
    public static final String HISTORY_MIST_PATH = "history_mist";

    /** 1.2.6a 新增「瓶装灵界之光」(副手大地之瓶移动 2000 格转化)。 */
    public static final String SPIRIT_LIGHT_PATH = "spirit_light";

    /** 1.2.6b 新增「瓶装冥河之水」(副手大地之瓶触发不死图腾时转化)。 */
    public static final String STYX_WATER_PATH = "styx_water";

    /** 1.2.6c 新增「瓶装战争迷雾」(副手大地之瓶击杀 50 只转化)。 */
    public static final String WAR_FOG_PATH = "war_fog";

    /** 1.2.6d 新增「瓶装太阳」(白天 10~14 点手持大地之瓶右键金块转化)。 */
    public static final String SUN_BOTTLE_PATH = "bottled_sun";

    /** 2.6.1 新增「复苏之叶术石」(Curios charm,Ctrl+左键 开关龙首飞行)。 */
    public static final String REVIVAL_LEAF_STONE_PATH = "revival_leaf_stone";

    /** 1.7.2 新增「渔获瓶」(大地之瓶 + 海洋之心;每点幸运 +10% 钓鱼产物,古旧书袋里 +2 幸运)。 */
    public static final String FISHING_HAUL_BOTTLE_PATH = "fishing_haul_bottle";

    /** 1.2.11a:(大地之瓶右键下界传送门)。 */
    public static final String UNCERTAIN_FOG_PATH = "uncertain_fog";
    /** 1.2.11a:(大地之瓶右键两格高玻璃的下半块)。 */
    public static final String MIRROR_SHADOW_PATH = "mirror_shadow";

    /** 1.2.11a:书袋判定用的完整 id。 */
    public static final ResourceLocation UNCERTAIN_FOG_ID = new ResourceLocation("enigmaticenhance", UNCERTAIN_FOG_PATH);
    public static final ResourceLocation MIRROR_SHADOW_ID = new ResourceLocation("enigmaticenhance", MIRROR_SHADOW_PATH);
    /** 1.1.28e 新增饰品「恶魔的手骨」的裸 path(Curios curios:hands)。 */
    /** 1.2.14:「生命刻度」—— 携带时显示数字血条的可选道具。 */
    /** 1.2.17:「神射手护符」—— 弹射物 +20% / 固定初速 / 必暴击 / 零偏移。 */
    public static final String SHARPSHOOTER_CHARM_PATH = "sharpshooter_charm";

    public static final String LIFE_METER_PATH = "life_meter";

    public static final String DEMON_HAND_BONE_PATH = "demon_hand_bone";

    private ModIds() {}

    /** 返回所有"我们自己写的 id"的问题清单(空 = 全部合法)。 */
    public static List<String> problems() {
        List<String> out = new ArrayList<>();
        check(out, "network channel", EnigmaticEnhance.MODID, CHANNEL_PATH);
        check(out, "auto smelt enchantment", EnigmaticEnhance.MODID, AUTO_SMELT_PATH);
        // 1.1.24:大地护符的物品 id / 饰品槽 / 4 条配方 id(全部两段式,逐个断言)
        check(out, "earth amulet item", EnigmaticEnhance.MODID, EARTH_AMULET_PATH);
        check(out, "earth amulet curio slot", "curios", CURIO_SLOT_CHARM);
        check(out, "demon pact item", EnigmaticEnhance.MODID, DEMON_PACT_PATH);
        check(out, "traveler hat item", EnigmaticEnhance.MODID, TRAVELER_HAT_PATH);
        check(out, "traveler hat curio slot", "curios", "head");
        // 1.1.28a:恶魔血杯(同样走 curios:charm,物品 id 逐个断言)
        check(out, "demon blood chalice item", EnigmaticEnhance.MODID, DEMON_BLOOD_CHALICE_PATH);
        check(out, "demon blood chalice curio slot", "curios", CURIO_SLOT_CHARM);
        // 1.1.28b:睡前读物(普通书籍物品,走自建配方)
        check(out, "bedtime reading item", EnigmaticEnhance.MODID, BEDTIME_READING_PATH);
        // 1.1.28c:纯净符文(物品 id + 投掷物实体 id + 视觉体实体 id)
        check(out, "pure rune item", EnigmaticEnhance.MODID, PURE_RUNE_PATH);
        check(out, "pure rune projectile entity", EnigmaticEnhance.MODID, PURE_RUNE_PATH);
        check(out, "pure rune flash entity", EnigmaticEnhance.MODID, PURE_RUNE_FLASH_PATH);
        // 1.1.28e:恶魔的手骨(curios:hands)
        check(out, "demon hand bone item", EnigmaticEnhance.MODID, DEMON_HAND_BONE_PATH);
        check(out, "demon hand bone curio slot", "curios", "hands");
        // 1.2.5b:大地之瓶
        check(out, "earth bottle item", EnigmaticEnhance.MODID, EARTH_BOTTLE_PATH);
        // 1.2.5c:瓶装历史迷雾
        check(out, "history mist item", EnigmaticEnhance.MODID, HISTORY_MIST_PATH);
        // 1.2.6a:瓶装灵界之光
        check(out, "spirit light item", EnigmaticEnhance.MODID, SPIRIT_LIGHT_PATH);
        // 1.2.6b:瓶装冥河之水
        check(out, "styx water item", EnigmaticEnhance.MODID, STYX_WATER_PATH);
        // 1.2.6c:瓶装战争迷雾
        check(out, "war fog item", EnigmaticEnhance.MODID, WAR_FOG_PATH);
        // 1.2.6d:瓶装太阳
        check(out, "sun bottle item", EnigmaticEnhance.MODID, SUN_BOTTLE_PATH);
        check(out, "fishing haul bottle item", EnigmaticEnhance.MODID, FISHING_HAUL_BOTTLE_PATH);
        check(out, "revival leaf stone item", EnigmaticEnhance.MODID, REVIVAL_LEAF_STONE_PATH);
        check(out, "uncertain fog item", EnigmaticEnhance.MODID, UNCERTAIN_FOG_PATH);
        check(out, "mirror shadow item", EnigmaticEnhance.MODID, MIRROR_SHADOW_PATH);
        checkSingle(out, "earth amulet recipe", EnigmaticEnhance.MODID + ":" + RECIPE_EARTH_AMULET_PATH);
        checkSingle(out, "earth amulet potion recipe", EnigmaticEnhance.MODID + ":" + RECIPE_EARTH_AMULET_POTION_PATH);
        checkSingle(out, "earth amulet head recipe", EnigmaticEnhance.MODID + ":" + RECIPE_EARTH_AMULET_HEAD_PATH);
        checkSingle(out, "earth amulet empower recipe", EnigmaticEnhance.MODID + ":" + RECIPE_EARTH_AMULET_EMPOWER_PATH);
        checkSingle(out, "earth amulet abyssal recipe", EnigmaticEnhance.MODID + ":" + RECIPE_EARTH_AMULET_ABYSSAL_PATH);
        // 1.2.2c:四条「需要特定形态大地之心」的注册配方 + 它们共用的序列化器 id
        check(out, "heart shaped recipe serializer", EnigmaticEnhance.MODID, RECIPE_HEART_SHAPED_PATH);
        checkSingle(out, "traveler hat recipe", EnigmaticEnhance.MODID + ":" + RECIPE_TRAVELER_HAT_PATH);
        checkSingle(out, "demon blood chalice recipe", EnigmaticEnhance.MODID + ":" + RECIPE_DEMON_BLOOD_CHALICE_PATH);
        checkSingle(out, "demon pact recipe", EnigmaticEnhance.MODID + ":" + RECIPE_DEMON_PACT_PATH);
        checkSingle(out, "demon hand bone recipe", EnigmaticEnhance.MODID + ":" + RECIPE_DEMON_HAND_BONE_PATH);
        checkSingle(out, "auto smelt loot pool", AutoSmeltLoot.POOL_NAME);
        checkSingle(out, "auto smelt recipe", EnigmaticEnhance.MODID + ":" + AutoSmeltRecipe.RECIPE_PATH);
        for (Map.Entry<ResourceLocation, Float> table : AutoSmeltLoot.tables().entrySet()) {
            checkSingle(out, "auto smelt loot table", table.getKey().toString());
        }
        for (String id : BagHudEffects.bookIds()) {
            checkSingle(out, "bag hud book", id);
        }
        for (ResourceLocation id : RingConfig.allItemIds()) {
            checkSingle(out, "item id", id.toString());
        }
        return out;
    }

    /** 两段式:namespace + path,path 不许含冒号/大写等非法字符。 */
    private static void check(List<String> out, String what, String namespace, String path) {
        try {
            ResourceLocation rl = new ResourceLocation(namespace, path);
            if (rl.getPath().contains(":")) {
                out.add(what + ": path 含冒号 -> " + rl);
            }
            if (!rl.toString().equals(namespace + ":" + path)) {
                out.add(what + ": toString 回读不一致 -> " + rl);
            }
        }
        catch (Throwable t) {
            out.add(what + ": 非法 id " + namespace + ":" + path + " (" + t + ")");
        }
    }

    /** 单段式 "ns:path"(物品 id 用这种写法)。 */
    private static void checkSingle(List<String> out, String what, String id) {
        try {
            ResourceLocation rl = new ResourceLocation(id);
            if (rl.getPath().contains(":")) {
                out.add(what + ": path 含冒号 -> " + rl);
            }
            if (!rl.toString().equals(id)) {
                out.add(what + ": toString 回读不一致 -> " + rl);
            }
        }
        catch (Throwable t) {
            out.add(what + ": 非法 id " + id + " (" + t + ")");
        }
    }
}
