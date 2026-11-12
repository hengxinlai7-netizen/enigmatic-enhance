package com.hengx.enigmaticenhance;

import javax.annotation.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的物品与配方序列化器注册表(1.1.24 起)。
 * <p>
 * 注册名一律走 {@link ModIds} 里的**裸 path**,由 {@code DeferredRegister} 拼上
 * {@code enigmaticenhance} 命名空间 —— 这样 {@code ModIds.problems()} 能在离线阶段断言出
 * "enigmaticenhance:enigmaticenhance" 这类双命名空间错误(1.1.16 崩溃的根源)。
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, EnigmaticEnhance.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, EnigmaticEnhance.MODID);

    /** enigmaticenhance:earth_amulet —— 大地护符(Curios curios:charm)。 */
    public static final RegistryObject<Item> EARTH_AMULET =
        ITEMS.register(ModIds.EARTH_AMULET_PATH, EarthAmuletItem::new);

    /** 1.1.25:enigmaticenhance:demon_pact —— 恶魔契约(Curios curios:charm,无 NBT、无配方)。 */
    public static final RegistryObject<Item> DEMON_PACT =
        ITEMS.register(ModIds.DEMON_PACT_PATH, DemonPactItem::new);

    /** 1.1.25c:enigmaticenhance:traveler_hat —— 旅行家帽子(Curios curios:head,无配方)。 */
    public static final RegistryObject<Item> TRAVELER_HAT =
        ITEMS.register(ModIds.TRAVELER_HAT_PATH, TravelerHatItem::new);

    /** 1.1.28a:enigmaticenhance:demon_blood_chalice —— 恶魔血杯(Curios curios:charm,无配方)。 */
    public static final RegistryObject<Item> DEMON_BLOOD_CHALICE =
        ITEMS.register(ModIds.DEMON_BLOOD_CHALICE_PATH, DemonBloodChaliceItem::new);

    /** 1.1.28b:enigmaticenhance:bedtime_reading —— 睡前读物(普通书籍,书+纯净之心无序合成)。 */
    public static final RegistryObject<Item> BEDTIME_READING =
        ITEMS.register(ModIds.BEDTIME_READING_PATH, BedtimeReadingItem::new);

    /** 1.1.28e:enigmaticenhance:demon_hand_bone —— 恶魔的手骨(Curios curios:hands)。 */
    public static final RegistryObject<Item> DEMON_HAND_BONE =
        ITEMS.register(ModIds.DEMON_HAND_BONE_PATH, DemonHandBoneItem::new);

    /** 1.1.28c:enigmaticenhance:pure_rune —— 纯净符文(可堆叠 16 的投掷物)。 */
    public static final RegistryObject<Item> PURE_RUNE =
        ITEMS.register(ModIds.PURE_RUNE_PATH, PureRuneItem::new);

    /** 2.4.0:enigmaticenhance:fake_ledger —— 假账生成器(创造物品,编辑统计)。 */
    public static final RegistryObject<Item> FAKE_LEDGER =
        ITEMS.register(ModIds.FAKE_LEDGER_PATH, FakeLedgerItem::new);


    /** 1.2.5b:enigmaticenhance:earth_bottle —— 大地之瓶(玻璃瓶 + 大地之心碎片)。 */
    public static final RegistryObject<Item> EARTH_BOTTLE =
        ITEMS.register(ModIds.EARTH_BOTTLE_PATH, EarthBottleItem::new);

    /** 1.2.5c:enigmaticenhance:history_mist —— 瓶装历史迷雾(大地之瓶 0.5h 转化)。 */
    public static final RegistryObject<Item> HISTORY_MIST =
        ITEMS.register(ModIds.HISTORY_MIST_PATH, HistoryMistItem::new);

    /** 1.2.6a:enigmaticenhance:spirit_light —— 瓶装灵界之光(副手大地之瓶移动 2000 格转化)。 */
    public static final RegistryObject<Item> SPIRIT_LIGHT =
        ITEMS.register(ModIds.SPIRIT_LIGHT_PATH, SpiritLightItem::new);

    /** 1.2.6b:enigmaticenhance:styx_water —— 瓶装冥河之水(免死一次/每天刷新)。 */
    public static final RegistryObject<Item> STYX_WATER =
        ITEMS.register(ModIds.STYX_WATER_PATH, StyxWaterItem::new);

    /** 1.2.6c:enigmaticenhance:war_fog —— 瓶装战争迷雾(无差别仇恨)。 */
    public static final RegistryObject<Item> WAR_FOG =
        ITEMS.register(ModIds.WAR_FOG_PATH, WarFogItem::new);

    /** 1.2.6d:enigmaticenhance:bottled_sun —— 瓶装太阳(对亡灵 +10%,饱和/生命回复 I)。 */
    public static final RegistryObject<Item> SUN_BOTTLE =
        ITEMS.register(ModIds.SUN_BOTTLE_PATH, SunBottleItem::new);


    /** 4.1.0:enigmaticenhance:gluttony_book —— 贪食之书(负上限血条 + 强制死亡)。 */
    public static final RegistryObject<Item> GLUTTONY_BOOK =
        ITEMS.register(ModIds.GLUTTONY_BOOK_PATH, GluttonyBookItem::new);

    /** 1.7.2:enigmaticenhance:fishing_haul_bottle —— 渔获瓶(大地之瓶 + 海洋之心)。 */
    public static final RegistryObject<Item> FISHING_HAUL_BOTTLE =
        ITEMS.register(ModIds.FISHING_HAUL_BOTTLE_PATH, FishingHaulBottleItem::new);

    /** 1.2.11a:enigmaticenhance:uncertain_fog —— 瓶装不定之雾(禁止周围生物传送)。 */
    public static final RegistryObject<Item> UNCERTAIN_FOG =
        ITEMS.register(ModIds.UNCERTAIN_FOG_PATH, UncertainFogItem::new);

    /** 1.2.11a:enigmaticenhance:mirror_shadow —— 瓶装镜中之影(记录/回溯,消耗品)。 */
    public static final RegistryObject<Item> MIRROR_SHADOW =
        ITEMS.register(ModIds.MIRROR_SHADOW_PATH, MirrorShadowItem::new);
    /** 护符 + 任意药水 → 覆盖保存该药水的全部效果。 */

    /** 1.2.14:enigmaticenhance:life_meter —— 生命刻度(携带时显示数字血条)。 */
    public static final RegistryObject<Item> LIFE_METER =
        ITEMS.register(ModIds.LIFE_METER_PATH, LifeMeterItem::new);
    public static final RegistryObject<RecipeSerializer<EarthAmuletPotionRecipe>> EARTH_AMULET_POTION =
        RECIPE_SERIALIZERS.register(ModIds.RECIPE_EARTH_AMULET_POTION_PATH, EarthAmuletPotionRecipe.Serializer::new);

    /** 1.2.17:enigmaticenhance:sharpshooter_charm —— 神射手护符。 */
    public static final RegistryObject<Item> SHARPSHOOTER_CHARM =
        ITEMS.register(ModIds.SHARPSHOOTER_CHARM_PATH, SharpshooterCharmItem::new);


    /** 1.2.24:enigmaticenhance:war_banner —— 战争之旗(方块物品)。 */
    public static final RegistryObject<Item> WAR_BANNER =
        ITEMS.register("war_banner", () -> new net.minecraft.world.item.BlockItem(ModBlocks.WAR_BANNER.get(), new Item.Properties()));


    /** 1.3.1:enigmaticenhance:value_editor —— 数值编辑器(创造专用)。 */
    public static final RegistryObject<Item> SEA_OF_CONSCIOUSNESS =
        ITEMS.register("sea_of_consciousness", ConsciousnessSeaItem::new);

    public static final RegistryObject<Item> VALUE_EDITOR =
        ITEMS.register("value_editor", ValueEditorItem::new);
    /** 护符 + 生物头颅 → 解锁对应能力(可叠加)。 */
    public static final RegistryObject<RecipeSerializer<EarthAmuletHeadRecipe>> EARTH_AMULET_HEAD =
        RECIPE_SERIALIZERS.register(ModIds.RECIPE_EARTH_AMULET_HEAD_PATH, EarthAmuletHeadRecipe.Serializer::new);

    /** 已带凋灵骷髅头的护符 + 下界之星 → Empowered:1b。 */
    public static final RegistryObject<RecipeSerializer<EarthAmuletEmpowerRecipe>> EARTH_AMULET_EMPOWER =
        RECIPE_SERIALIZERS.register(ModIds.RECIPE_EARTH_AMULET_EMPOWER_PATH, EarthAmuletEmpowerRecipe.Serializer::new);

    /**
     * 1.2.2c:带"大地之心形态"要求的有序配方(旅行家帽子 / 恶魔的血杯 / 恶魔契约 / 恶魔的手骨 共用)。
     * 形状与材料交给原版 {@code crafting_shaped} 的解析器,只额外要求中间那颗心是深渊侵蚀的
     * (旅行家帽子那条写 {@code "heart": "any"} 就退回"任意大地之心")。
     */
    public static final RegistryObject<RecipeSerializer<HeartShapedRecipe>> HEART_SHAPED =
        RECIPE_SERIALIZERS.register(ModIds.RECIPE_HEART_SHAPED_PATH, HeartShapedRecipe.Serializer::new);

    private ModItems() {}

    /** 稳健取护符实例:注册表还没填时返回 null(调用方据此静默跳过)。 */
    @Nullable
    /** 2.4.0:假账生成器(注册顺序未到时返回 null)。 */
    /** 2.4.1:瓶装意识之海(JEI 信息页用)。 */
    public static Item seaOfConsciousnessOrNull() {
        try {
            return SEA_OF_CONSCIOUSNESS.get();
        }
        catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    public static Item fakeLedgerOrNull() {
        try {
            return FAKE_LEDGER.get();
        }
        catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    public static Item earthAmuletOrNull() {
        try {
            return EARTH_AMULET.isPresent() ? EARTH_AMULET.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 稳健取恶魔契约实例(同上)。 */
    @Nullable
    public static Item demonPactOrNull() {
        try {
            return DEMON_PACT.isPresent() ? DEMON_PACT.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 稳健取旅行家帽子实例(同上)。 */
    @Nullable
    public static Item travelerHatOrNull() {
        try {
            return TRAVELER_HAT.isPresent() ? TRAVELER_HAT.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 稳健取恶魔血杯实例(同上)。 */
    @Nullable
    public static Item demonBloodChaliceOrNull() {
        try {
            return DEMON_BLOOD_CHALICE.isPresent() ? DEMON_BLOOD_CHALICE.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 稳健取睡前读物实例(同上)。 */
    @Nullable
    public static Item bedtimeReadingOrNull() {
        try {
            return BEDTIME_READING.isPresent() ? BEDTIME_READING.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 稳健取恶魔的手骨实例(同上)。 */
    @Nullable
    public static Item demonHandBoneOrNull() {
        try {
            return DEMON_HAND_BONE.isPresent() ? DEMON_HAND_BONE.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 稳健取纯净符文实例(同上)。 */
    @Nullable
    public static Item pureRuneOrNull() {
        try {
            return PURE_RUNE.isPresent() ? PURE_RUNE.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.5b:大地之瓶(模组加载早期/未注册时返回 null)。 */
    @Nullable
    public static Item earthBottleOrNull() {
        try {
            return EARTH_BOTTLE.isPresent() ? EARTH_BOTTLE.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.5c:瓶装历史迷雾(同上)。 */
    @Nullable
    public static Item historyMistOrNull() {
        try {
            return HISTORY_MIST.isPresent() ? HISTORY_MIST.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.6a:瓶装灵界之光(同上)。 */
    @Nullable
    public static Item spiritLightOrNull() {
        try {
            return SPIRIT_LIGHT.isPresent() ? SPIRIT_LIGHT.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.6b:瓶装冥河之水(同上)。 */
    @Nullable
    public static Item styxWaterOrNull() {
        try {
            return STYX_WATER.isPresent() ? STYX_WATER.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.6c:瓶装战争迷雾(同上)。 */
    @Nullable
    public static Item warFogOrNull() {
        try {
            return WAR_FOG.isPresent() ? WAR_FOG.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.6d:瓶装太阳(同上)。 */
    @Nullable
    public static Item sunBottleOrNull() {
        try {
            return SUN_BOTTLE.isPresent() ? SUN_BOTTLE.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.11a:瓶装不定之雾(同上)。 */
    @Nullable
    public static Item uncertainFogOrNull() {
        try {
            return UNCERTAIN_FOG.isPresent() ? UNCERTAIN_FOG.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.11a:瓶装镜中之影(同上)。 */
    @Nullable
    public static Item mirrorShadowOrNull() {
        try {
            return MIRROR_SHADOW.isPresent() ? MIRROR_SHADOW.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.14:生命刻度(没注册时返回 null)。 */
    public static Item lifeMeterOrNull() {
        return LIFE_METER.isPresent() ? LIFE_METER.get() : null;
    }

    /** 1.2.17:神射手护符(没注册时返回 null)。 */
    public static Item sharpshooterCharmOrNull() {
        return SHARPSHOOTER_CHARM.isPresent() ? SHARPSHOOTER_CHARM.get() : null;
    }

    /** 1.7.2:渔获瓶(同上)。 */
    @Nullable
    public static Item fishingHaulBottleOrNull() {
        try {
            return FISHING_HAUL_BOTTLE.isPresent() ? FISHING_HAUL_BOTTLE.get() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.11c:这些物品应当能放进古旧书袋(标签被别的模组覆盖时的兜底)。 */
    public static boolean isBookBagItem(Item item) {
        if (item == null) {
            return false;
        }
        return item == earthBottleOrNull()
            || item == historyMistOrNull()
            || item == spiritLightOrNull()
            || item == styxWaterOrNull()
            || item == warFogOrNull()
            || item == sunBottleOrNull()
            || item == uncertainFogOrNull()
            || item == mirrorShadowOrNull()
            || item == fishingHaulBottleOrNull();
    }

    /** 2.6.1:复苏之叶术石(缺失返回 null)。 */
}
