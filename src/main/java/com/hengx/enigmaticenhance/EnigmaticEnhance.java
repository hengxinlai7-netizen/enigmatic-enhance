package com.hengx.enigmaticenhance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 神秘遗物增强 (Enigmatic Legacy Enhance)
 * <p>
 * 内容:
 * <ol>
 * <li>新属性【全属性】{@code enigmaticenhance:omni} —— 对所有属性生效的**独立乘区**
 *     ({@link OmniAttribute} + {@code AttributeMapMixin});</li>
 * <li>救赎之戒 +20% 全属性({@link CurioCompat},Curios 事件);</li>
 * <li>救赎之戒无法破坏({@code BlessRingHelperMixin});</li>
 * <li>救赎之戒与七咒之戒可同戴({@code BlessRingMixin}),且去掉同戴惩罚
 *     ({@code SuperAddonHandlerMixin} 让 {@code isPunishedOne} 恒为 false);</li>
 * <li>千咒卷轴与「造物主的恩赐」可同戴({@code CursedScrollMixin});</li>
 * </ol>
 * 全部开关与数值见 {@code config/enigmaticenhance-common.toml}。
 */
@Mod(EnigmaticEnhance.MODID)
public class EnigmaticEnhance {

    public static final String MODID = "enigmaticenhance";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /** 模组事件总线(1.1.25a):构造期之后 {@code FMLJavaModLoadingContext.get()} 会变成 null,
     *  所以在这里存一份,给创造标签页自检重新投递事件用。 */
    public static IEventBus MOD_BUS;

    public EnigmaticEnhance() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MOD_BUS = modBus;

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RingConfig.SPEC, "enigmaticenhance-common.toml");
        // 2.5.0:物品级开关(禁用表 + 大地护符记录上限)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ItemConfig.SPEC, "enigmaticenhance-items.toml");

        // 属性注册 + 挂到玩家身上
        OmniAttribute.ATTRIBUTES.register(modBus);
        modBus.register(OmniAttribute.class);

        // 1.1.20:附魔【自动熔炼】enigmaticenhance:auto_smelt + 战利品获取途径
        ModEnchantments.ENCHANTMENTS.register(modBus);
        // 1.1.24:饰品【大地护符】enigmaticenhance:earth_amulet + 3 个自定义配方序列化器
        ModItems.ITEMS.register(modBus);
        ModItems.RECIPE_SERIALIZERS.register(modBus);
        // 1.2.11a:状态效果(镜碎)
        ModEffects.EFFECTS.register(modBus);
        // 1.2.24:方块注册(战争之旗)
        ModBlocks.BLOCKS.register(modBus);
        ModMenus.MENUS.register(modBus);   // 1.3.1:数值编辑器菜单
        // 1.2.26:药水注册(让 JEI 效果页能查到我们的两个效果)
        ModPotions.POTIONS.register(modBus);
        // 1.1.25a:把本模组物品挂进创造模式物品栏(TOOLS_AND_UTILITIES);这是 mod 总线事件
        modBus.register(CreativeTabContents.class);
        MinecraftForge.EVENT_BUS.register(EarthAmuletHandler.class);
        // 1.1.25:恶魔契约 enigmaticenhance:demon_pact(伤害数值 = 当前生命值)
        MinecraftForge.EVENT_BUS.register(DemonPactHandler.class);
        // 1.1.25c:旅行家帽子 enigmaticenhance:traveler_hat(速度/生命上限/攻击力 + 探索数据)
        MinecraftForge.EVENT_BUS.register(TravelerHatHandler.class);
        // 1.1.28a:恶魔血杯 enigmaticenhance:demon_blood_chalice(效果保留但不生效 + 两条联动)
        MinecraftForge.EVENT_BUS.register(DemonBloodChaliceHandler.class);
        // 1.1.28c:纯净符文的投掷物与落点白光实体 enigmaticenhance:pure_rune / pure_rune_flash
        ModEntities.ENTITY_TYPES.register(modBus);
        // 1.1.28e:恶魔的手骨(hands 槽)的伤害结算与三件套
        MinecraftForge.EVENT_BUS.register(DemonBoneHandler.class);
        MinecraftForge.EVENT_BUS.register(AutoSmeltLoot.class);
        // 1.1.22:启动时一条 ASCII 自检日志(附魔是否注册 / mixin 注入是否 PRESENT / 对方模组状态)
        MinecraftForge.EVENT_BUS.register(AutoSmeltDiagnostics.class);
        // 1.1.22:兜底注入点 —— 掉落物实体真正出现的时刻(任何工具/任何模组算出来的掉落最终都要 spawn ItemEntity)
        MinecraftForge.EVENT_BUS.register(AutoSmeltBreakTracker.class);
        MinecraftForge.EVENT_BUS.register(AutoSmeltItemWatcher.class);
        // 1.2.2:深渊侵蚀的大地之心 —— 身上带着深渊之心(或它的产物)时,背包里的大地之心被侵蚀
        MinecraftForge.EVENT_BUS.register(AbyssalEarthHeartHandler.class);

        // 1.2.5c:瓶装历史迷雾(大地之瓶计时转化 + 记录属性生效)
        MinecraftForge.EVENT_BUS.register(HistoryMistHandler.class);
        // 救赎之戒的 +20% 全属性(需要 Curios)
        // 1.2.6a:瓶装灵界之光(副手移动转化 + 额外掉落 + 强制战利品大爆发)
        MinecraftForge.EVENT_BUS.register(SpiritLightHandler.class);
        boolean curios = ModList.get().isLoaded("curios");
        // 1.2.6b:瓶装冥河之水(图腾转化 + 免死一次/每天刷新)
        MinecraftForge.EVENT_BUS.register(StyxWaterHandler.class);
        if (curios) {
        // 1.2.6c:瓶装战争迷雾(击杀 50 只转化 + 无差别仇恨)
        MinecraftForge.EVENT_BUS.register(WarFogHandler.class);
            MinecraftForge.EVENT_BUS.register(new CurioCompat());
        // 1.2.6d:瓶装太阳(金块+正午转化 / 亡灵增伤 / 饱和与生命回复)
        MinecraftForge.EVENT_BUS.register(SunBottleHandler.class);
        }
        // 1.2.9a:元素魔法(有铁魔法才生效;附魔本身也只在装了铁魔法时注册)
        MinecraftForge.EVENT_BUS.register(ElementalMagicHandler.class);

        // 1.2.9e:无法破坏(附魔生效即写入 Unbreakable 并自清除)
        MinecraftForge.EVENT_BUS.register(UnbreakableHandler.class);
        // 书籍 Ctrl+左键 开关:需要一条 C2S 通道(客户端拿不到 Ctrl 状态的反向需求)
        // 1.2.11a:镜碎(受伤 +100%)与镜中之影
        MinecraftForge.EVENT_BUS.register(MirrorShatterHandler.class);
        // 1.2.15:元素魔法 —— 攻击时顺手释放武器注入/魔法书选中的法术
        MinecraftForge.EVENT_BUS.register(ElementalSpellCastHandler.class);
        // 1.2.17:神射手护符 —— 弹射物 +20%(箭矢初速/暴击/偏移由 mixin 处理)
        MinecraftForge.EVENT_BUS.register(SharpshooterHandler.class);
        // 1.2.19:冥河之水 × Goety 灵魂联动(每秒 +10 灵魂、可吸魂)
        MinecraftForge.EVENT_BUS.register(StyxSoulHandler.class);
        // 1.2.23:血色之灾 —— 每层负面 +10% 受伤
        MinecraftForge.EVENT_BUS.register(BloodCalamityHandler.class);
        MinecraftForge.EVENT_BUS.register(ValueEditorHandler.class);   // 1.3.1:数值编辑器
        MinecraftForge.EVENT_BUS.register(EvilEssenceBonusHandler.class);   // 1.3.2:邪恶精髓复制
        MinecraftForge.EVENT_BUS.register(MercurySerpentHandler.class);   // 1.3.3:水银之蛇
        // 2.6.0:Boss 增强(凋零/监守者/末影龙)已拆分为独立模组,本模组不再注册。
        MinecraftForge.EVENT_BUS.register(CalamityOriginHandler.class);   // 1.5.1:根源之祸
        MinecraftForge.EVENT_BUS.register(EternalSilenceHandler.class);   // 1.5.2:永暗之寂
        ModGameRules.bootstrap();   // 1.6.4:宿命的修正 世界规则
        MinecraftForge.EVENT_BUS.register(FateCorrectionHandler.class);   // 1.6.4:宿命的修正
        // 2.6.0:宿命的试炼已拆分为独立模组(enhancetrial),本模组不再注册。
        MinecraftForge.EVENT_BUS.register(FateWorldApplier.class);   // 1.6.6:创建世界选项写入 gamerule
        MinecraftForge.EVENT_BUS.register(ConsciousnessSeaHandler.class);   // 1.6.15:瓶装意识之海
        MinecraftForge.EVENT_BUS.register(EarthHeartLootHandler.class);
        MinecraftForge.EVENT_BUS.register(PlayerCritHandler.class);
        MinecraftForge.EVENT_BUS.register(GluttonyBookCleanup.class);   // 4.1.0:贪食之书复活清理
        MinecraftForge.EVENT_BUS.register(HellBladeLootHandler.class);
        MinecraftForge.EVENT_BUS.register(SpellstoneLootBoostHandler.class);   // 3.1.0:术石战利品加权   // 2.7.0:地狱刃片保底
        MinecraftForge.EVENT_BUS.register(HandOfExecutionHandler.class);
        MinecraftForge.EVENT_BUS.register(BeaconEnhanceHandler.class);
        MinecraftForge.EVENT_BUS.register(BastionTreasureHandler.class);   // 3.0.0:扭曲之心堡垒遗迹抽取   // 2.11.15:信标增强   // 2.9.6:执行之手
        MinecraftForge.EVENT_BUS.register(ItemUseGuard.class);   // 2.5.0:禁用物品统一拦截   // 2.3.0:大地之心记录战利品表
        MinecraftForge.EVENT_BUS.register(ExperienceOverflowGuard.class);   // 1.6.17:经验溢出保护
        MinecraftForge.EVENT_BUS.register(OmniContaminationGuard.class);   // 1.6.23:清除生物身上的全属性污染
        MinecraftForge.EVENT_BUS.register(ForgerGemAnvilHandler.class);   // 1.6.33:锻造者宝石铁砧升级
        MinecraftForge.EVENT_BUS.register(FishingHaulBottleHandler.class);   // 1.7.2:渔获瓶
        MinecraftForge.EVENT_BUS.register(CombatInvulnHandler.class);   // 1.8.0:战斗无敌帧(正常游戏)
        // 1.2.27:TACZ 开枪连携施法(装了 TACZ 才注册,不装不加载这个类)
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
            MinecraftForge.EVENT_BUS.register(TaczCompat.class);
        }
        EnigmaticNetwork.register();

        // 万钧之护卷轴:配置改动后给在线玩家重挂/摘掉攻击伤害修饰符(不重启即生效)
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) ->
            ScrollHandler.reapplyAll());

        // 1.1.17:静态校验我们自己造的 id/path(1.1.16 的 HUD overlay id 崩溃就是这类问题);正常时零输出
        java.util.List<String> idProblems = ModIds.problems();
        if (!idProblems.isEmpty()) {
            LOGGER.warn("神秘遗物增强:发现非法 id/path {}", idProblems);
        }
        // 临时自检(只在 -D 开关下注册;1.1.19 起 selftest=ring 跑旧的书袋 tooltip 自检,其它值跑石英花自检)
        String selftest = System.getProperty("enigmaticenhance.selftest");
        if (selftest != null) {
            if (selftest.contains("amulet")) {
                // 1.1.24:【大地护符】配方/NBT/效果的自检(见 EarthAmuletSelfTest)
                EarthAmuletSelfTest.register();
            }
            else if (selftest.contains("smelt")) {
                // 1.1.20:【自动熔炼】掉落管线自检(含与 wonderenchant:auto_smelt 同持的断言)
                SmeltSelfTest.register();
            }
            else if (selftest.contains("ring")) {
                RingSelfTest.register();
            }
            else {
                FlowerSelfTest.register();
            }
        }

        DebugLog.startup("神秘遗物增强 已加载 (modId={}, 属性 {}:omni, Curios={}, 神秘遗物扩展={})",
            MODID, MODID, curios ? "存在" : "缺失",
            ModList.get().isLoaded("enigmaticaddons") ? "存在" : "缺失");
    }
}
