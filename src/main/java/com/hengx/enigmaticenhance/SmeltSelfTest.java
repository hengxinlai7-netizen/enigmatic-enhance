package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.1.20 临时自检:【自动熔炼】掉落管线(-D 开关,默认不注册)。
 * <p>
 * 覆盖:
 * <ul>
 * <li>T0 定义/注册/兼容性/获取途径(含 {@code ModIds.problems()=[]});</li>
 * <li>T1 无附魔 ⇒ 原版掉落(铁矿石);</li>
 * <li><b>T2 只有新附魔 ⇒ 1 个铁锭(不是 2 个)</b>;</li>
 * <li><b>T3 同时带 wonderenchant:auto_smelt ⇒ 仍然 1 个铁锭</b>;</li>
 * <li>T4 不可熔炼物原样保留 + 非玩家挖掘不参与管线;</li>
 * <li>T5 精准采集对照(钻石矿保持钻石矿;丝触下的铁矿石物品仍按普通熔炉配方熔炼);</li>
 * <li>T6 时运对照(时运 3 的青金石矿 ⇒ 同数量的青金石)+ T6b 数量守恒(4 黏土球 ⇒ 4 砖);</li>
 * <li>T7 双保险:同一个堆只替换一次、产物不许再熔一次(并且作用域只在本方块本 tick);</li>
 * <li>T8 debugLog=false 零输出 / true 有输出(读 logs/latest.log 当探测器)。</li>
 * </ul>
 * 全部在模组自己的 classloader + 真实 Forge 服务端里跑,挖方块用的是 {@link FakePlayer} 的
 * {@code gameMode.destroyBlock} —— 与真实玩家同一条生产路径(所以能真正验证 Mixin 注入)。
 */
public final class SmeltSelfTest {

    private static final String TAG = "[AutoSmeltSelfTest]";
    /** 与 {@link AutoSmeltPipeline#TAG} 一致,用于日志探测。 */
    private static final String PIPELINE_TAG = AutoSmeltPipeline.TAG;
    private static final int TEST_Y = 100;

    /** -Denigmaticenhance.selftest.quiet=true:全程把日志压掉,用来测"debugLog=false 零输出"。 */
    private static final boolean QUIET = Boolean.getBoolean("enigmaticenhance.selftest.quiet");
    private static boolean allPass = true;

    /** 本次挖掘真正被生成到世界里的掉落物(EntityJoinLevelEvent 计数,与服务端 tick 无关)。 */
    private static final List<ItemStack> spawned = new ArrayList<>();
    private static boolean capturingSpawns = false;

    private SmeltSelfTest() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(SmeltSelfTest.class);
        DebugLog.info(TAG + " registered");
    }

    /** 只在"这一挖"期间数新掉落的实体:证明真的生成了 1 个铁锭(而不是 2 个)。 */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!capturingSpawns || event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        if (event.getEntity() instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty()) {
            spawned.add(itemEntity.getItem().copy());
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        final MinecraftServer server = event.getServer();
        try {
            run(server);
        }
        catch (Throwable t) {
            allPass = false;
            EnigmaticEnhance.LOGGER.error(TAG + " ABORTED", t);
        }
        finally {
            AutoSmeltLedger.reset();
            setBool(RingConfig.DEBUG_LOG, false);
            RingConfig.SPEC.save();
            log(" ALL-RESULT " + (allPass ? "PASS" : "FAIL"));
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "stop"));
        }
    }

    // ------------------------------------------------------------------ 主流程

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getChunk(0, 0); // 保证测试区块已加载
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ea_smelt_test"));

        Enchantment ours = ModEnchantments.autoSmeltOrNull();
        Enchantment theirs = AutoSmeltCompat.wonderAutoSmelt();
        log(" 加载状态: 本模组附魔=" + name(ours) + "; wonderenchant 附魔=" + name(theirs)
            + "; wonderenchant mod=" + AutoSmeltCompat.wonderenchantLoaded());
        if (QUIET) {
            setBool(RingConfig.DEBUG_LOG, false);
            setBool(RingConfig.LOG_STARTUP_LINE, false);
        }
        else {
            setBool(RingConfig.DEBUG_LOG, true);
            setBool(RingConfig.LOG_STARTUP_LINE, true);
        }
        hold(200L);
        log(" 初始 debugLog=" + RingConfig.debugLog() + " [quiet=" + QUIET + "]");

        boolean t0 = testDefinition(server, ours);
        boolean t1 = testNoEnchant(level, player);
        boolean t2 = testOnlyOurs(level, player, ours);
        boolean t3 = testBothMods(level, player, ours, theirs);
        boolean t4 = testNonSmeltable(level, player, ours);
        boolean t5 = testSilkTouch(level, player, ours);
        boolean t6 = testFortuneAndCount(level, player, ours);
        boolean t7 = testDoubleGuards(level, player, ours);
        boolean t8 = testDebugLog(level, player, ours);
        boolean t9 = testOtherModBoundaries(level, player, ours, theirs);
        boolean t10 = testRecipe(level, player);
        boolean t11 = testCommandEnchant(level, player, ours);
        boolean t12 = testBypassSpawn(level, player, ours);
        boolean t13 = testVanillaPickaxe(level, player, ours);

        // 放/挖一块"聚晶"(紫水晶块):覆盖"放置方块 + 挖掘 + 无熔炼配方"这条真实操作
        mineAndCount(level, player, new BlockPos(24, TEST_Y, 1), Blocks.AMETHYST_BLOCK.defaultBlockState(),
            pickaxe(ours, null, false, 0));

        allPass = allPass && t0 && t1 && t2 && t3 && t4 && t5 && t6 && t7 && t8 && t9 && t10 && t11 && t12 && t13;
        clearItems(level);
        log(" [AutoSmeltSelfTest] summary T0=" + t0 + " T1=" + t1 + " T2=" + t2 + " T3=" + t3 + " T4=" + t4
            + " T5=" + t5 + " T6=" + t6 + " T7=" + t7 + " T8=" + t8 + " T9=" + t9 + " T10=" + t10
            + " T11=" + t11 + " T12=" + t12 + " T13=" + t13);
    }

    // ------------------------------------------------------------------ T0 定义 / 获取途径

    private static boolean testDefinition(MinecraftServer server, Enchantment ours) {
        log(" --- T0 附魔定义 / 获取途径");
        boolean ok = ours != null;
        ResourceLocation id = ours == null ? null : ForgeRegistries.ENCHANTMENTS.getKey(ours);
        ok &= "enigmaticenhance:auto_smelt".equals(String.valueOf(id));
        log(" T0 id=" + id + " [应为 enigmaticenhance:auto_smelt]");
        if (ours == null) {
            check("T0", false);
            return false;
        }
        log(" T0 数据字段: max_level=" + ours.getMaxLevel() + " rarity=" + ours.getRarity()
            + "(weight=" + ours.getRarity().getWeight() + ") category=" + ours.category
            + " minCost=" + ours.getMinCost(1) + " maxCost=" + ours.getMaxCost(1)
            + " minLevel=" + ours.getMinLevel());
        ok &= ours.getMaxLevel() == 1;
        ok &= ours.getRarity() == Enchantment.Rarity.UNCOMMON;
        ok &= ours.category == net.minecraft.world.item.enchantment.EnchantmentCategory.DIGGER;
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        boolean canPick = ours.canEnchant(pick);
        boolean canSword = ours.canEnchant(new ItemStack(Items.IRON_SWORD));
        boolean canAxe = ours.canEnchant(new ItemStack(Items.IRON_AXE));
        boolean canShovel = ours.canEnchant(new ItemStack(Items.IRON_SHOVEL));
        boolean canHoe = ours.canEnchant(new ItemStack(Items.IRON_HOE));
        log(" T0 supported_items(1.2.5a 起只认镐/斧/锹): 镐=" + canPick + "[true];斧=" + canAxe
            + "[true];锹=" + canShovel + "[true];锄=" + canHoe + "[false];铁剑=" + canSword + "[false]");
        ok &= canPick && canAxe && canShovel && !canHoe && !canSword;
        // 纯函数判定(附魔台第二道门也走它,避免 canEnchant 互相递归)
        boolean helper = AutoSmeltEnchantment.isAllowedTool(new ItemStack(Items.DIAMOND_PICKAXE))
            && AutoSmeltEnchantment.isAllowedTool(new ItemStack(Items.NETHERITE_AXE))
            && AutoSmeltEnchantment.isAllowedTool(new ItemStack(Items.DIAMOND_SHOVEL))
            && !AutoSmeltEnchantment.isAllowedTool(new ItemStack(Items.DIAMOND_HOE))
            && !AutoSmeltEnchantment.isAllowedTool(ItemStack.EMPTY);
        log(" T0 isAllowedTool: 镐/斧/锹=true,锄=false,空手=false => " + helper);
        ok &= helper;
        // 1.2.2b:获取途径已放开(与 1.2.1 说明对齐);1.2.5a 起物品类型只限镐/斧/锹(排除锄),由 canEnchant 负责
        boolean table = ours.canApplyAtEnchantingTable(pick);
        log(" T0 获取途径(放开): canApplyAtEnchantingTable(钻石镐)=" + table + " [true];isDiscoverable="
            + ours.isDiscoverable() + " [true];isTreasureOnly=" + ours.isTreasureOnly()
            + " [false];isTradeable=" + ours.isTradeable() + " [true];isAllowedOnBooks="
            + ours.isAllowedOnBooks() + " [true]");
        ok &= table && ours.isDiscoverable() && !ours.isTreasureOnly() && ours.isTradeable()
            && ours.isAllowedOnBooks();
        boolean eff = ours.isCompatibleWith(Enchantments.BLOCK_EFFICIENCY);
        boolean fort = ours.isCompatibleWith(Enchantments.BLOCK_FORTUNE);
        boolean silk = ours.isCompatibleWith(Enchantments.SILK_TOUCH);
        boolean unb = ours.isCompatibleWith(Enchantments.UNBREAKING);
        boolean mend = ours.isCompatibleWith(Enchantments.MENDING);
        boolean loyalty = ours.isCompatibleWith(Enchantments.LOYALTY);
        log(" T0 兼容性: 效率=" + eff + " 时运=" + fort + " 精准采集=" + silk + " 耐久=" + unb
            + " 经验修补=" + mend + " [以上都应 true];忠诚(三叉戟)=" + loyalty + " [应 false]");
        ok &= eff && fort && silk && unb && mend && !loyalty;

        // 获取途径:战利品池
        for (Map.Entry<ResourceLocation, Float> entry : AutoSmeltLoot.tables().entrySet()) {
            LootTable table2 = server.getLootData().getLootTable(entry.getKey());
            LootPool pool = table2 == null ? null : table2.getPool(AutoSmeltLoot.POOL_NAME);
            log(" T0 获取途径: 战利品表 " + entry.getKey() + " 里的池 " + AutoSmeltLoot.POOL_NAME + " = "
                + (pool == null ? "缺失" : "存在(概率 " + entry.getValue() + ")"));
            ok &= pool != null;
        }
        boolean hook = AutoSmeltDiagnostics.hookPresent();
        log(" T0 mixin hook Block#getDrops = " + (hook ? "PRESENT" : "MISSING") + " [必须 PRESENT]");
        ok &= hook;
        List<String> problems = ModIds.problems();
        log(" T0 ModIds.problems()=" + problems + " [必须空]");
        ok &= problems.isEmpty();
        check("T0", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T1 无附魔对照

    private static boolean testNoEnchant(ServerLevel level, FakePlayer player) {
        log(" --- T1 无附魔对照(铁矿石,钻石镐无附魔)");
        BlockPos pos = new BlockPos(1, TEST_Y, 1);
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        Counts counts = mineAndCount(level, player, pos, Blocks.IRON_ORE.defaultBlockState(), tool);
        log(" T1 掉落=" + counts + " [应为 raw_iron=1]");
        boolean ok = counts.total() == 1 && counts.count(Items.RAW_IRON) == 1 && counts.count(Items.IRON_INGOT) == 0;
        check("T1", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T2 只有新附魔(用户现场)

    private static boolean testOnlyOurs(ServerLevel level, FakePlayer player, Enchantment ours) {
        log(" --- T2 只有 enigmaticenhance:auto_smelt(铁矿石)= 用户现场:整合包里装了 wonderenchant,"
            + "但工具上只有我们的附魔");
        BlockPos pos = new BlockPos(2, TEST_Y, 1);
        ItemStack tool = pickaxe(ours, null, false, 0);
        int theirLevel = AutoSmeltCompat.wonderLevel(tool);
        boolean takeover = AutoSmeltCompat.otherModTakesOver(tool);
        log(" T2 互斥判定(逐工具): wonderenchant mod=" + AutoSmeltCompat.wonderenchantLoaded()
            + " 对方附魔=" + name(AutoSmeltCompat.wonderAutoSmelt()) + " 本工具上对方等级=" + theirLevel
            + " [应 0];对方接管=" + takeover + " [应 false];action="
            + AutoSmeltHandler.decide(player, tool) + " [应 SMELT]");
        boolean ok = theirLevel == 0 && !takeover && AutoSmeltHandler.decide(player, tool) == AutoSmeltHandler.Action.SMELT;
        Counts counts = mineAndCount(level, player, pos, Blocks.IRON_ORE.defaultBlockState(), tool);
        log(" T2 掉落=" + counts + " [铁锭必须 =1,原矿 =0,总数 =1 而不是 2]");
        ok &= counts.total() == 1 && counts.count(Items.IRON_INGOT) == 1 && counts.count(Items.RAW_IRON) == 0;
        check("T2", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T3 与 wonderenchant 同持

    private static boolean testBothMods(ServerLevel level, FakePlayer player, Enchantment ours, Enchantment theirs) {
        log(" --- T3 两个附魔在同一把工具上(铁矿石):默认模式 + 让位模式都只能出 1 个铁锭");
        if (ours == null || theirs == null) {
            log(" T3-RESULT SKIP:本次 run 没有对方模组(wonderenchant mod="
                + AutoSmeltCompat.wonderenchantLoaded() + ", 附魔=" + name(theirs)
                + ");\"对方不存在时仍熔炼\"由 T9 覆盖");
            return true;
        }
        ItemStack tool = pickaxe(ours, theirs, false, 0);
        log(" T3 工具附魔: 本模组=" + AutoSmeltHandler.hasEnchant(tool) + " 对方(等级)="
            + AutoSmeltCompat.wonderLevel(tool));

        // 阶段 a:yieldToWonderenchant=false ⇒ 本模组替换掉落表,对方拿到的是产物
        setBool(RingConfig.AUTO_SMELT_YIELD_TO_WONDERENCHANT, false);
        AutoSmeltHandler.Action actionA = AutoSmeltHandler.decide(player, tool);
        log(" T3a yieldToWonderenchant=false action=" + actionA + " [应 SMELT,对方等级="
            + AutoSmeltCompat.wonderLevel(tool) + "]");
        boolean ok = actionA == AutoSmeltHandler.Action.SMELT;
        // 这一段特意打开 debugLog,好让报告里有"两个附魔同持"时的 decision 行原文(quiet 模式除外)
        if (!QUIET) {
            setBool(RingConfig.DEBUG_LOG, true);
            hold(200L);
        }
        log(" T3a debugLog=" + RingConfig.debugLog() + " [非 quiet 时必须 true,用来抓 decision 行]");
        mineAndCount(level, player, new BlockPos(3, TEST_Y, 1), Blocks.IRON_ORE.defaultBlockState(), tool);
        setBool(RingConfig.DEBUG_LOG, false);
        hold(150L);
        spawned.clear();
        capturingSpawns = true;
        int settledA = settleWonderenchant();
        capturingSpawns = false;
        Counts afterSettleA = takeCounts(level);
        log(" T3a 对方结算窗口调用=" + settledA + " 次;最终掉落=" + afterSettleA
            + " [必须 iron_ingot=1,raw_iron=0,总数 1 而不是 2]");
        ok &= afterSettleA.total() == 1 && afterSettleA.count(Items.IRON_INGOT) == 1
            && afterSettleA.count(Items.RAW_IRON) == 0;

        // 阶段 b(1.1.22 默认 true):让位 ⇒ 对方管线出产物,仍然只能 1 个
        setBool(RingConfig.AUTO_SMELT_YIELD_TO_WONDERENCHANT, true);
        AutoSmeltHandler.Action actionB = AutoSmeltHandler.decide(player, tool);
        log(" T3b yieldToWonderenchant=true action=" + actionB + " [应 YIELD]");
        ok &= actionB == AutoSmeltHandler.Action.YIELD;
        mineAndCount(level, player, new BlockPos(21, TEST_Y, 1), Blocks.IRON_ORE.defaultBlockState(), tool);
        spawned.clear();
        capturingSpawns = true;
        int settledB = settleWonderenchant();
        capturingSpawns = false;
        Counts afterSettleB = takeCounts(level);
        log(" T3b 对方结算窗口调用=" + settledB + " 次;最终掉落=" + afterSettleB
            + " [必须 iron_ingot=1,raw_iron=0,总数 1 而不是 2]");
        ok &= afterSettleB.total() == 1 && afterSettleB.count(Items.IRON_INGOT) == 1
            && afterSettleB.count(Items.RAW_IRON) == 0;
        setBool(RingConfig.AUTO_SMELT_YIELD_TO_WONDERENCHANT, false);
        check("T3", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T4 不可熔炼物 / 非玩家

    private static boolean testNonSmeltable(ServerLevel level, FakePlayer player, Enchantment ours) {
        log(" --- T4 不可熔炼物(泥土,铁锹)+ 非玩家来源");
        BlockPos pos = new BlockPos(4, TEST_Y, 1);
        ItemStack tool = new ItemStack(Items.IRON_SHOVEL);
        if (ours != null) {
            tool.enchant(ours, 1);
        }
        Counts counts = mineAndCount(level, player, pos, Blocks.DIRT.defaultBlockState(), tool);
        log(" T4 掉落=" + counts + " [应为 dirt=1,原样保留]");
        boolean ok = counts.total() == 1 && counts.count(Items.DIRT) == 1;

        // 非玩家(爆炸/命令/活塞走 Level#destroyBlock)⇒ 管线必须完全不动
        List<ItemStack> input = List.of(new ItemStack(Items.IRON_ORE));
        List<ItemStack> out = AutoSmeltPipeline.process(level, new BlockPos(5, TEST_Y, 1), null, tool, input);
        log(" T4 非玩家来源: 返回同一个列表=" + (out == input) + " [应为 true]");
        ok &= out == input;
        check("T4", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T5 精准采集

    private static boolean testSilkTouch(ServerLevel level, FakePlayer player, Enchantment ours) {
        log(" --- T5 精准采集对照(丝触不特殊处理:先由原版算出掉落,再逐个试熔)");
        ItemStack silkPick = pickaxe(ours, null, true, 0);
        ItemStack silkOnly = new ItemStack(Items.DIAMOND_PICKAXE);
        silkOnly.enchant(Enchantments.SILK_TOUCH, 1);
        log(" T5 工具附魔表: 本模组+精准采集=" + enchantments(silkPick) + ";仅精准采集=" + enchantments(silkOnly)
            + ";silk_touch id=" + ForgeRegistries.ENCHANTMENTS.getKey(Enchantments.SILK_TOUCH));

        // 干跑:同一个方块状态,先看纯原版掉落表(不动世界),再带本模组附魔跑一次
        BlockPos probePos = new BlockPos(6, TEST_Y + 3, 1);
        level.setBlockAndUpdate(probePos, Blocks.DIAMOND_ORE.defaultBlockState());
        List<ItemStack> drySilkOnly = Block.getDrops(level.getBlockState(probePos), level, probePos, null, player,
            silkOnly);
        List<ItemStack> dryOurs = Block.getDrops(level.getBlockState(probePos), level, probePos, null, player,
            silkPick);
        log(" T5 干跑 Block.getDrops(钻石矿): 仅精准采集=" + describe(drySilkOnly)
            + ";本模组+精准采集=" + describe(dryOurs)
            + " [钻石矿物品本身有普通熔炉配方 diamond_from_smelting_diamond_ore,所以会被熔成钻石]");
        level.setBlockAndUpdate(probePos, Blocks.AIR.defaultBlockState());

        // (a) 精准采集生效且产物"没有熔炼配方"⇒ 原样保留:玻璃丝触掉玻璃块(无丝触则不掉落)
        BlockPos glassPos = new BlockPos(6, TEST_Y, 1);
        Counts glass = mineAndCount(level, player, glassPos, Blocks.GLASS.defaultBlockState(), silkPick);
        log(" T5 (a) 玻璃(精准采集+自动熔炼)=" + glass + " [应为 glass=1:丝触保住了玻璃块,玻璃没有熔炼配方]");
        boolean ok = glass.total() == 1 && glass.count(Items.GLASS) == 1;

        // (b) 精准采集到的"原矿方块"本身可熔炼 ⇒ 照常熔炼(与 wonderenchant 口径一致)
        Counts diamond = mineAndCount(level, player, new BlockPos(7, TEST_Y, 1),
            Blocks.DIAMOND_ORE.defaultBlockState(), silkPick);
        log(" T5 (b) 钻石矿(精准采集+自动熔炼)=" + diamond
            + " [应为 diamond=1:钻石矿方块有熔炼配方,丝触不特殊处理]");
        ok &= diamond.total() == 1 && diamond.count(Items.DIAMOND) == 1 && diamond.count(Items.DIAMOND_ORE) == 0;

        Counts iron = mineAndCount(level, player, new BlockPos(8, TEST_Y, 1), Blocks.IRON_ORE.defaultBlockState(),
            silkPick);
        log(" T5 (c) 铁矿石(精准采集+自动熔炼)=" + iron
            + " [应为 iron_ingot=1:丝触掉的原矿物品本身可熔炼]");
        ok &= iron.total() == 1 && iron.count(Items.IRON_INGOT) == 1 && iron.count(Items.IRON_ORE) == 0;

        // 对照:同样的丝触镐但不带本模组附魔 ⇒ 玻璃矿掉落原样(证明丝触本身没被我们改动)
        ItemStack plainSilk = new ItemStack(Items.DIAMOND_PICKAXE);
        plainSilk.enchant(Enchantments.SILK_TOUCH, 1);
        Counts glassControl = mineAndCount(level, player, new BlockPos(9, TEST_Y, 1),
            Blocks.GLASS.defaultBlockState(), plainSilk);
        log(" T5 对照: 玻璃(仅精准采集,无自动熔炼)=" + glassControl + " [应为 glass=1]");
        ok &= glassControl.total() == 1 && glassControl.count(Items.GLASS) == 1;
        check("T5", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T6 时运 / 数量守恒

    private static boolean testFortuneAndCount(ServerLevel level, FakePlayer player, Enchantment ours) {
        log(" --- T6 时运对照 + 数量守恒");
        ItemStack fortunePick = pickaxe(ours, null, false, 3);
        BlockPos lapisPos = new BlockPos(10, TEST_Y, 1);
        Counts lapis = mineAndCount(level, player, lapisPos, Blocks.LAPIS_ORE.defaultBlockState(), fortunePick);
        log(" T6 青金石矿(时运3+自动熔炼)=" + lapis + " [应为 lapis_lazuli>=4,且不是 1]");
        boolean ok = lapis.count(Items.LAPIS_LAZULI) >= 4 && lapis.count(Items.LAPIS_ORE) == 0;

        // 时运下的原版数量对照(同一种工具但不带本模组附魔):数量应落在同一量级
        BlockPos lapisControlPos = new BlockPos(11, TEST_Y, 1);
        ItemStack fortuneOnly = new ItemStack(Items.DIAMOND_PICKAXE);
        fortuneOnly.enchant(Enchantments.BLOCK_FORTUNE, 3);
        Counts lapisControl = mineAndCount(level, player, lapisControlPos, Blocks.LAPIS_ORE.defaultBlockState(),
            fortuneOnly);
        log(" T6 青金石矿(仅时运3,无自动熔炼)=" + lapisControl + " [原版掉落,不熔炼]");
        ok &= lapisControl.count(Items.LAPIS_LAZULI) >= 4 && lapisControl.count(Items.LAPIS_ORE) == 0;

        // 数量守恒:黏土块固定掉 4 个黏土球,黏土球熔炼成砖 ⇒ 必须是 4 个砖(不是 1 个)
        BlockPos clayPos = new BlockPos(12, TEST_Y, 1);
        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        if (ours != null) {
            shovel.enchant(ours, 1);
        }
        Counts clay = mineAndCount(level, player, clayPos, Blocks.CLAY.defaultBlockState(), shovel);
        log(" T6b 黏土块(自动熔炼)=" + clay + " [应为 brick=4(4 个黏土球 → 4 个砖,数量守恒)]");
        ok &= clay.total() == 4 && clay.count(Items.BRICK) == 4;
        check("T6", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T7 双保险

    private static boolean testDoubleGuards(ServerLevel level, FakePlayer player, Enchantment ours) {
        log(" --- T7 只替换一次:一次性标记 + 产物去重");
        ItemStack tool = pickaxe(ours, null, false, 0);
        boolean ok = AutoSmeltHandler.hasEnchant(tool);

        // (1) 一次性标记:同一批堆喂两次,第二次必须原样返回
        BlockPos guardPos = new BlockPos(13, TEST_Y, 1);
        List<ItemStack> oreBatch = new ArrayList<>();
        oreBatch.add(new ItemStack(Items.IRON_ORE));
        List<ItemStack> first = AutoSmeltPipeline.processForTest(level, guardPos, player, tool, oreBatch);
        List<ItemStack> second = AutoSmeltPipeline.processForTest(level, guardPos, player, tool, oreBatch);
        log(" T7 第一次=" + describe(first) + ";第二次=" + describe(second)
            + ";第二次是否原表=" + (second == oreBatch) + " [应为 true ⇒ 同一次挖掘只熔炼一次]");
        ok &= first != oreBatch && second == oreBatch;
        ok &= countInList(first, Items.IRON_INGOT) == 1;

        // (2) 产物去重:产物(石头)再喂回来不许变成平滑石头;换个方块坐标则照常熔炼
        BlockPos productPos = new BlockPos(14, TEST_Y, 1);
        List<ItemStack> cobble = new ArrayList<>();
        cobble.add(new ItemStack(Items.COBBLESTONE));
        List<ItemStack> stone = AutoSmeltPipeline.processForTest(level, productPos, player, tool, cobble);
        List<ItemStack> reFeed = new ArrayList<>();
        reFeed.add(new ItemStack(Items.STONE));
        List<ItemStack> reFeedOut = AutoSmeltPipeline.processForTest(level, productPos, player, tool, reFeed);
        List<ItemStack> otherPosIn = new ArrayList<>();
        otherPosIn.add(new ItemStack(Items.STONE));
        List<ItemStack> otherPosOut = AutoSmeltPipeline.processForTest(level, new BlockPos(15, TEST_Y, 1), player,
            tool, otherPosIn);
        log(" T7 圆石→" + describe(stone) + "[应为 stone=1];同坐标再喂石头→" + describe(reFeedOut)
            + "[应原样 stone=1];换坐标喂石头→" + describe(otherPosOut) + "[应为 smooth_stone=1]");
        ok &= countInList(stone, Items.STONE) == 1;
        ok &= reFeedOut == reFeed && countInList(reFeedOut, Items.STONE) == 1;
        ok &= countInList(otherPosOut, Items.SMOOTH_STONE) == 1;

        // (3) 没配方时原样返回同一个列表实例(绝不吞物品)
        List<ItemStack> dirt = new ArrayList<>();
        dirt.add(new ItemStack(Items.DIRT));
        List<ItemStack> dirtOut = AutoSmeltPipeline.processForTest(level, new BlockPos(16, TEST_Y, 1), player, tool, dirt);
        log(" T7 无配方(泥土)返回原表=" + (dirtOut == dirt) + " [应为 true]");
        ok &= dirtOut == dirt;
        check("T7", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T8 debugLog 门控

    private static boolean testDebugLog(ServerLevel level, FakePlayer player, Enchantment ours) {
        if (QUIET) {
            log(" T8 SKIP (quiet mode:整个 run 的日志都已经压掉)");
            return true;
        }
        log(" --- T8 debugLog 门控(默认 false 零输出)");
        ItemStack tool = pickaxe(ours, null, false, 0);
        setBool(RingConfig.DEBUG_LOG, false);
        hold(250L);
        int quietBefore = logHits();
        mineAndCount(level, player, new BlockPos(17, TEST_Y, 1), Blocks.IRON_ORE.defaultBlockState(), tool);
        hold(350L);
        int quietAfter = logHits();
        log(" T8 debugLog=false 时同一动作新增日志行=" + (quietAfter - quietBefore) + " [应为 0]");
        boolean ok = quietBefore >= 0 && quietAfter >= 0 && quietAfter - quietBefore == 0;
        if (quietBefore < 0 || quietAfter < 0) {
            log(" T8 警告: 读不到 logs/latest.log,日志门控无法判定");
        }

        setBool(RingConfig.DEBUG_LOG, true);
        hold(150L);
        int loudBefore = logHits();
        mineAndCount(level, player, new BlockPos(18, TEST_Y, 1), Blocks.IRON_ORE.defaultBlockState(), tool);
        hold(350L);
        int loudAfter = logHits();
        log(" T8 debugLog=true 时同一动作新增日志行=" + (loudAfter - loudBefore) + " [应 >=1,证明探测器有效]");
        ok &= loudBefore >= 0 && loudAfter >= 0 && loudAfter - loudBefore >= 1;
        // 保持 debugLog=true 到 run 结束(否则后面的 T9..T13 与汇总都会被门控掉,报告里就没有证据了);
        // 收尾的 finally 会把它设回 false 并保存配置。
        check("T8", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T9 互斥边界(逐工具)

    /**
     * 互斥边界:只有"这把工具上真的有对方的附魔"才让位。
     * <ul>
     * <li>本次 run 装了 wonderenchant ⇒ 验证"工具上没有对方附魔时本模组照常熔炼"(用户现场);</li>
     * <li>本次 run 没装 wonderenchant ⇒ 验证"对方模组不存在时照常熔炼"(反射不得误判为命中)。</li>
     * </ul>
     */
    private static boolean testOtherModBoundaries(ServerLevel level, FakePlayer player, Enchantment ours,
        Enchantment theirs) {
        boolean loaded = AutoSmeltCompat.wonderenchantLoaded();
        log(" --- T9 互斥边界(逐工具):" + (loaded ? "本 run 装了 wonderenchant ⇒ 验证\"对方在场但工具没有对方附魔仍熔炼\""
            : "本 run 没装 wonderenchant ⇒ 验证\"对方模组不存在时仍熔炼\""));
        BlockPos pos = new BlockPos(19, TEST_Y, 1);
        ItemStack tool = pickaxe(ours, null, false, 0);
        int theirLevel = AutoSmeltCompat.wonderLevel(tool);
        boolean takeover = AutoSmeltCompat.otherModTakesOver(tool);
        AutoSmeltHandler.Action action = AutoSmeltHandler.decide(player, tool);
        log(" T9 mod=" + loaded + " 对方附魔实例=" + name(AutoSmeltCompat.wonderAutoSmelt())
            + " 本工具上对方等级=" + theirLevel + " [应 0];对方接管=" + takeover + " [应 false];action="
            + action + " [应 SMELT]");
        boolean ok = theirLevel == 0 && !takeover && action == AutoSmeltHandler.Action.SMELT;
        Counts counts = mineAndCount(level, player, pos, Blocks.IRON_ORE.defaultBlockState(), tool);
        log(" T9 掉落=" + counts + " [必须 iron_ingot=1]");
        ok &= counts.total() == 1 && counts.count(Items.IRON_INGOT) == 1 && counts.count(Items.RAW_IRON) == 0;
        check("T9", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T10 合成配方

    private static boolean testRecipe(ServerLevel level, FakePlayer player) {
        log(" --- T10 合成配方:1 书 + 8 煤炭块(无序)→ 带 auto_smelt 的附魔书");
        java.util.Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> byId =
            level.getRecipeManager().byKey(AutoSmeltRecipe.RECIPE_ID);
        log(" T10 按 id 查配方 " + AutoSmeltRecipe.RECIPE_ID + " = " + byId.isPresent() + " [应 true]");
        boolean ok = byId.isPresent();

        // 3×3 合成格:0 号放书,1..8 放煤炭块。TransientCraftingContainer 需要一个菜单(它的 setChanged
        // 会回调 menu.slotsChanged),用 ContainerLevelAccess.NULL 的 CraftingMenu 让这次回调变成空操作。
        net.minecraft.world.inventory.CraftingMenu menu =
            new net.minecraft.world.inventory.CraftingMenu(0, player.getInventory());
        net.minecraft.world.inventory.CraftingContainer grid =
            new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        grid.setItem(0, new ItemStack(Items.BOOK));
        for (int i = 1; i < 9; i++) {
            grid.setItem(i, new ItemStack(Items.COAL_BLOCK));
        }
        java.util.Optional<net.minecraft.world.item.crafting.CraftingRecipe> found =
            level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level);
        log(" T10 用 3×3(1 书 + 8 煤炭块)匹配配方 = " + found.map(r -> r.getId().toString()).orElse("缺失")
            + " [应 enigmaticenhance:auto_smelt_book]");
        ok &= found.isPresent() && AutoSmeltRecipe.RECIPE_ID.equals(found.get().getId());
        if (!found.isPresent()) {
            check("T10", false);
            return false;
        }
        net.minecraft.world.item.crafting.CraftingRecipe recipe = found.get();
        int book = 0;
        int coal = 0;
        for (net.minecraft.world.item.crafting.Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) {
                continue;
            }
            if (items[0].getItem() == Items.BOOK) {
                ++book;
            }
            else if (items[0].getItem() == Items.COAL_BLOCK) {
                ++coal;
            }
        }
        ItemStack result = recipe.assemble(grid, level.registryAccess());
        log(" T10 材料: 书=" + book + " [应 1],煤炭块=" + coal + " [应 8];配方结果=" + result
            + " NBT=" + (result.getTag() == null ? "null" : result.getTag().toString()));
        ok &= book == AutoSmeltRecipe.BOOK_COUNT && coal == AutoSmeltRecipe.COAL_BLOCK_COUNT;
        ok &= AutoSmeltRecipe.isAutoSmeltBook(result);
        ok &= result.getCount() == 1;
        check("T10", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T11 命令附魔(/enchant)

    /**
     * 复现"创造模式用 {@code /enchant @s enigmaticenhance:auto_smelt 1} 给镐附魔,然后挖铁矿石"。
     * <p>
     * 说明:创造模式下挖方块<b>原版就没有任何掉落</b>(所以如果在创造里一边挖一边看,永远看不到熔炼产物);
     * 这里按真实操作序列跑:创造 → /enchant → 切回生存 → 挖,断言 1 个铁锭。
     */
    private static boolean testCommandEnchant(ServerLevel level, FakePlayer player, Enchantment ours) {
        log(" --- T11 命令附魔(/enchant)后挖矿");
        if (ours == null) {
            check("T11", false);
            return false;
        }
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        String command = "enchant @s " + AutoSmeltRecipe.enchantmentId() + " 1";
        int result = 0;
        try {
            result = player.getServer().getCommands().performPrefixedCommand(
                player.getServer().createCommandSourceStack().withSuppressedOutput().withEntity(player), command);
        }
        catch (Throwable t) {
            log(" T11 命令执行异常: " + t);
        }
        ItemStack enchanted = player.getMainHandItem().copy();
        int ourLevel = AutoSmeltHandler.levelOf(enchanted, ours);
        log(" T11 命令=\"/" + command + "\" 返回=" + result + ";主手=" + enchanted.getItem()
            + " 本模组附魔等级=" + ourLevel + " [应 1]");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        boolean ok = ourLevel == 1;
        Counts counts = mineAndCount(level, player, new BlockPos(20, TEST_Y, 1),
            Blocks.IRON_ORE.defaultBlockState(), enchanted);
        log(" T11 掉落=" + counts + " [应 iron_ingot=1(创造模式挖矿原版就没有掉落,必须切回生存再挖)]");
        ok &= counts.total() == 1 && counts.count(Items.IRON_INGOT) == 1 && counts.count(Items.RAW_IRON) == 0;
        check("T11", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T12 旁路模组(Tetra 式)

    /**
     * 复现 Tetra 模块化工具那种"掉落不走 {@code getDrops} 返回值"的情形:
     * 破坏一个<b>没有熔炼配方</b>的方块(泥土)之后,由"旁路模组"自己 spawn 一个 {@code raw_iron} 掉落物。
     * 1.1.22 的第二条注入点({@link AutoSmeltItemWatcher},挂在 {@code EntityJoinLevelEvent} HIGHEST)
     * 必须在它进入世界的那一刻就地换掉 ⇒ 仍然只能出 1 个铁锭。
     * <p>
     * 说明:测试服没有安装 Tetra(也就没有 {@code tetra:modular_double} 这件物品),
     * 所以这里复现的是<b>同一机制</b>:掉落物由模组自己 spawn、完全不经过 {@code Block#getDrops} 的返回值。
     */
    private static boolean testBypassSpawn(ServerLevel level, FakePlayer player, Enchantment ours) {
        log(" --- T12 旁路模组(模拟 Tetra 模块化工具):掉落不经过 getDrops 的返回值");
        ItemStack tool = pickaxe(ours, null, false, 0);
        BlockPos pos = new BlockPos(22, TEST_Y, 1);
        mineAndCount(level, player, pos, Blocks.DIRT.defaultBlockState(), tool); // 泥土没有熔炼配方
        // 模拟旁路模组:自己 spawn 一个 raw_iron(等价于 Tetra 自己算掉落并生成 ItemEntity)。
        // 注意:本测试环境里 world 实体扫描不可靠(区块不是 entity-ticking),所以统一用
        // EntityJoinLevelEvent 抓到的"真正 spawn 的堆"来计数(与其它用例同源)。
        spawned.clear();
        capturingSpawns = true;
        net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(Items.RAW_IRON));
        capturingSpawns = false;
        Counts counts = spawnedCounts();
        log(" T12 旁路 spawn raw_iron(在刚挖过的坐标)=" + counts + " [应 iron_ingot=1,raw_iron=0]");
        boolean ok = counts.count(Items.IRON_INGOT) == 1 && counts.count(Items.RAW_IRON) == 0;

        BlockPos far = new BlockPos(30, TEST_Y, 1);
        spawned.clear();
        capturingSpawns = true;
        net.minecraft.world.level.block.Block.popResource(level, far, new ItemStack(Items.RAW_IRON));
        capturingSpawns = false;
        Counts farCounts = spawnedCounts();
        log(" T12 对照(该坐标没有任何挖掘记录)=" + farCounts + " [应 raw_iron=1,不被熔炼]");
        ok &= farCounts.count(Items.RAW_IRON) == 1 && farCounts.count(Items.IRON_INGOT) == 0;
        check("T12", ok);
        return ok;
    }

    /** 把"这一段时间真正 spawn 的堆"汇总成计数(自检统一口径)。 */
    private static Counts spawnedCounts() {
        Counts counts = new Counts();
        for (ItemStack stack : spawned) {
            counts.add(stack);
        }
        return counts;
    }

    // ------------------------------------------------------------------ T13 原版铁镐对照

    private static boolean testVanillaPickaxe(ServerLevel level, FakePlayer player, Enchantment ours) {
        log(" --- T13 原版铁镐(带本模组附魔)对照");
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        if (ours != null) {
            tool.enchant(ours, 1);
        }
        Counts counts = mineAndCount(level, player, new BlockPos(23, TEST_Y, 1),
            Blocks.IRON_ORE.defaultBlockState(), tool);
        log(" T13 掉落=" + counts + " [应 iron_ingot=1]");
        boolean ok = counts.total() == 1 && counts.count(Items.IRON_INGOT) == 1
            && counts.count(Items.RAW_IRON) == 0;
        check("T13", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 挖方块 / 计数

    /** 放方块 → 用 FakePlayer 真正挖掉(与真实玩家同一条生产路径)→ 数掉落。 */
    private static Counts mineAndCount(ServerLevel level, FakePlayer player, BlockPos pos, BlockState state,
        ItemStack tool) {
        clearItems(level);
        AutoSmeltLedger.reset();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState()); // 垫一层地板,掉落物不往下掉
        level.setBlockAndUpdate(pos, state);
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
        player.setShiftKeyDown(false);
        spawned.clear();
        capturingSpawns = true;
        try {
            boolean broken = player.gameMode.destroyBlock(pos);
            if (!broken) {
                log(" 警告: gameMode.destroyBlock 返回 false,改用 Block#playerDestroy 复核");
                BlockState current = level.getBlockState(pos);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                current.getBlock().playerDestroy(level, player, pos, current, null, player.getMainHandItem());
            }
        }
        catch (Throwable t) {
            log(" 警告: destroyBlock 抛异常,改用 Block#playerDestroy 复核:" + t);
            BlockState current = level.getBlockState(pos);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            current.getBlock().playerDestroy(level, player, pos, current, null, player.getMainHandItem());
        }
        finally {
            capturingSpawns = false;
        }
        return takeCounts(level);
    }

    /** 收账:先推进两 tick 让实体挂进世界,再对数"生成本数"与"世界扫描结果"。 */
    private static Counts takeCounts(ServerLevel level) {
        tickLevel(level);
        Counts world = countsNow(level);
        Counts spawnedCounts = new Counts();
        for (ItemStack stack : spawned) {
            spawnedCounts.add(stack);
        }
        log(" 生成本次掉落实体 " + spawned.size() + " 个 = " + spawnedCounts + ";世界里查到 " + world);
        if (world.total() != spawnedCounts.total()) {
            log(" 警告: 世界里查到的数量与生成本数不一致,按生成本数判定(tick 时机影响世界扫描)");
            return spawnedCounts;
        }
        return world;
    }

    /**
     * 手动推进两 tick,让服务端把新生成的 ItemEntity 真正挂进世界
     * (自检把服务端线程占住了,等不到正常的 tick 循环)。
     */
    private static void tickLevel(ServerLevel level) {
        for (int i = 0; i < 2; i++) {
            try {
                level.tick(() -> true);
            }
            catch (Throwable t) {
                log(" 警告: 手动 tick 关卡失败:" + t);
                break;
            }
        }
    }

    private static Counts countsNear(ServerLevel level, BlockPos pos, double radius) {
        Counts counts = new Counts();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(radius))) {
            counts.add(entity.getItem());
        }
        return counts;
    }

    private static Counts countsNow(ServerLevel level) {
        Counts counts = new Counts();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, testBox())) {
            counts.add(entity.getItem());
        }
        return counts;
    }

    private static void clearItems(ServerLevel level) {
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, testBox())) {
            entity.discard();
        }
    }

    private static AABB testBox() {
        return new AABB(-3.0D, TEST_Y - 4.0D, -3.0D, 24.0D, TEST_Y + 5.0D, 8.0D);
    }

    private static ItemStack pickaxe(Enchantment ours, Enchantment theirs, boolean silkTouch, int fortune) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        if (ours != null) {
            stack.enchant(ours, 1);
        }
        if (theirs != null) {
            stack.enchant(theirs, 1);
        }
        if (silkTouch) {
            stack.enchant(Enchantments.SILK_TOUCH, 1);
        }
        if (fortune > 0) {
            stack.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        }
        return stack;
    }

    /** 强制让对方(wonderenchant)结算它的掉落窗口:自检把服务端线程占住了,等不到 tick。 */
    private static int settleWonderenchant() {
        try {
            Class<?> collector = Class.forName("com.hengx.wonderenchant.DropCollector", true,
                SmeltSelfTest.class.getClassLoader());
            collector.getMethod("settleAll", String.class).invoke(null, "selftest");
            return 1;
        }
        catch (Throwable t) {
            log(" 警告: 调用 wonderenchant DropCollector#settleAll 失败:" + t);
            return 0;
        }
    }

    // ------------------------------------------------------------------ 小工具

    private static int countInList(List<ItemStack> list, Item item) {
        int total = 0;
        if (list != null) {
            for (ItemStack stack : list) {
                if (stack != null && !stack.isEmpty() && stack.getItem() == item) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static String describe(List<ItemStack> list) {
        if (list == null) {
            return "null";
        }
        Counts counts = new Counts();
        for (ItemStack stack : list) {
            counts.add(stack);
        }
        return "[" + counts + "]";
    }

    /** 打印一个工具的附魔表(自检诊断用)。 */
    private static String enchantments(ItemStack stack) {
        StringBuilder builder = new StringBuilder("{");
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(name(entry.getKey())).append('=').append(entry.getValue());
        }
        return builder.append('}').toString();
    }

    private static String name(Enchantment enchantment) {        if (enchantment == null) {
            return "缺失";
        }
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        return id == null ? enchantment.getDescriptionId() : id.toString();
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }

    /** 掉落统计(按物品 id 汇总数量)。 */
    private static final class Counts {

        private final Map<Item, Integer> byItem = new LinkedHashMap<>();

        void add(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            this.byItem.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }

        int count(Item item) {
            return this.byItem.getOrDefault(item, 0);
        }

        int total() {
            int total = 0;
            for (int count : this.byItem.values()) {
                total += count;
            }
            return total;
        }

        @Override
        public String toString() {
            if (this.byItem.isEmpty()) {
                return "(无掉落)";
            }
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<Item, Integer> entry : this.byItem.entrySet()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(entry.getKey());
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(id).append('=').append(entry.getValue());
            }
            builder.append(" (总 ").append(total()).append(" 件)");
            return builder.toString();
        }
    }

    // ------------------------------------------------------------------ 配置 / 日志探测

    /** 配置值写入(自检专用;Forge 的配置写入是异步的,这里带重试)。 */
    private static void setBool(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value, boolean expected) {
        for (int i = 0; i < 10; i++) {
            try {
                value.set(expected);
                if (value.get() == expected) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 配置还没加载完:等一会儿再试
            }
            hold(150L);
        }
    }

    /** 数 logs/latest.log 里 "[自动熔炼]" 的行数(文件不存在返回 -1)。 */
    private static int logHits() {
        try {
            java.nio.file.Path path = java.nio.file.Path.of("logs", "latest.log");
            if (!java.nio.file.Files.isRegularFile(path)) {
                return -1;
            }
            int hits = 0;
            for (String line : java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8)) {
                if (line.contains(PIPELINE_TAG)) {
                    ++hits;
                }
            }
            return hits;
        }
        catch (Throwable t) {
            return -1;
        }
    }

    private static void hold(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
