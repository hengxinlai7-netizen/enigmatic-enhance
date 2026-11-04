package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.MutableHashedLinkedMap;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.1.24 临时自检:【大地护符】(-D 开关,默认不注册)。
 * <p>
 * 覆盖(全部在模组自己的 classloader + 真实 Forge 服务端里跑):
 * <ul>
 * <li>T0 定义:物品 id / 不可堆叠 / 无耐久 / curios:charm 槽 / 4 条配方存在 / {@code ModIds.problems()=[]};</li>
 * <li>T1 基础配方:污染大地之心 + 3 金粒 ⇒ 1 个大地护符;未污染的同名物品 ⇒ **不**合成;</li>
 * <li>T2 药水:力量 II 后覆盖成跳跃提升 I(NBT 里只剩跳跃提升);水瓶 ⇒ 不合成;</li>
 * <li>T3 头颅:逐个写入 Heads;三个头颅可叠加;同一颗重复注入 ⇒ 不合成;</li>
 * <li>T4 强化:无头颅 / 僵尸头 / 骷髅头 + 下界之星 ⇒ 不合成;凋灵骷髅头 ⇒ Empowered=1b;已强化再合 ⇒ 不合成;</li>
 * <li>T5 效果:亡灵不锁定 / 视为亡灵 / 空背包射箭 / 爆炸免疫 / 凋零免疫 + 攻击附凋零 / 下界不锁定 /
 *     龙首飞行授予与回收 / 强化后免疫亡灵伤害 / 佩戴药水效果生效;</li>
 * <li>T6 debugLog=false 零输出 / true 有输出(读 logs/latest.log 当探测器)。</li>
 * </ul>
 * 说明:{@link FakePlayer#isInvulnerableTo} 恒为 {@code true}(Forge 的实现),所以**伤害免疫**类断言
 * 直接投递 {@link LivingAttackEvent}(vanilla {@code hurt} 的同一道闸门)并断言 "已被取消";
 * 其余(射箭/凋零施加/目标锁定)都走真实实体路径。
 */
public final class EarthAmuletSelfTest {

    private static final String TAG = "[EarthAmuletSelfTest]";
    /** 运行时日志标记(与 {@link EarthAmuletHandler#LOG_TAG} 一致,用于日志门控探测)。 */
    private static final String PROBE = EarthAmuletHandler.LOG_TAG;
    private static final int Y = 110;

    private static final boolean QUIET = Boolean.getBoolean("enigmaticenhance.selftest.quiet");
    private static boolean allPass = true;

    /** 本次射箭真正 spawn 出来的箭(EntityJoinLevelEvent 计数:addFreshEntity 立即触发,
     *  比 getEntitiesOfClass 可靠 —— 服务端把实体挂进可见列表要等 tick)。 */
    private static int arrowSpawns = 0;
    private static boolean captureArrows = false;

    private EarthAmuletSelfTest() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(EarthAmuletSelfTest.class);
        DebugLog.info(TAG + " registered");
    }

    @SubscribeEvent
    public static void onEntityJoin( net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!captureArrows || event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        if (event.getEntity() instanceof AbstractArrow) {
            ++arrowSpawns;
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
            setBool(RingConfig.DEBUG_LOG, false);
            RingConfig.SPEC.save();
            // 自检专用的一行结果(只有 -Denigmaticenhance.selftest=amulet 时才会出现)
            System.out.println(TAG + " ALL-RESULT " + (allPass ? "PASS" : "FAIL"));
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "stop"));
        }
    }

    // ------------------------------------------------------------------ 主流程

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getChunk(0, 0);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ea_amulet_test"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(8.5D, Y, 8.5D, 0.0F, 0.0F);
        player.getInventory().clearContent();
        for (int x = 4; x <= 14; x++) {
            for (int z = 4; z <= 14; z++) {
                level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, Y, z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, Y + 1, z), Blocks.AIR.defaultBlockState());
            }
        }

        if (QUIET) {
            setBool(RingConfig.DEBUG_LOG, false);
            setBool(RingConfig.LOG_STARTUP_LINE, false);
        }
        else {
            setBool(RingConfig.DEBUG_LOG, true);
        }
        hold(200L);
        log(" 初始 debugLog=" + RingConfig.debugLog() + " [quiet=" + QUIET + "]");

        boolean t0 = testDefinition(level, player);
        boolean t1 = testBaseRecipe(level, player);
        boolean t2 = testPotionRecipe(level, player);
        boolean t3 = testHeadRecipes(level, player);
        boolean t4 = testEmpowerRecipe(level, player);
        boolean t5 = testEffects(level, player);
        boolean t6 = testDebugGate(level, player);
        boolean t7 = DemonPactSelfTest.run(level);
        boolean t8 = testCreativeTab(level);
        boolean t13 = testModelStates();
        boolean t14 = testNoClip(level, player);
        boolean t15 = TravelerHatSelfTest.run(level, player);
        boolean t16 = TooltipSelfTest.run(level, player);
        boolean t22 = ConfigCommentSelfTest.run();
        // 1.1.27a:附魔师的珍珠 × 远古重铸台(ancientreforging)免新生符石
        boolean t25 = PearlReforgeSelfTest.run(level);
        // 1.1.28a:恶魔血杯(效果保留但不生效 + 两条联动)
        boolean t26 = DemonBloodChaliceSelfTest.run(level, player);
        // 1.1.28b:睡前读物(七咒之戒第七重咒修正)
        boolean t27 = BedtimeReadingSelfTest.run(level, player);
        // 1.1.28c:纯净符文(投掷物 + 落点白光 + 范围魔法伤害)
        boolean t28 = PureRuneSelfTest.run(level, player);
        // 1.1.28d:穷举所有已注册 MobEffect,确认封印无漏网
        boolean t29 = EffectMatrixSelfTest.run(level, player);
        // 1.1.28e:恶魔的手骨 + 三件套 + 契约直伤
        boolean t30 = DemonBoneSelfTest.run(level, player);
        // 1.1.28h:物品名/tooltip 的 lang 完整性
        boolean t31 = LangSelfTest.run(level, player);
        // 1.1.28k:大地护符动态染色 + 分头颅边框
        boolean t32 = AmuletTintSelfTest.run(level, player);
        // 1.1.28l:恶魔契约 = 奥秘卷轴(+1 卷轴栏位)
        boolean t33 = PactScrollSelfTest.run(level, player);
        // 1.1.28o:睡前读物进书袋(bookshelf_books 标签)
        boolean t34 = testBookTag();
        // 1.1.29a:三个附魔
        boolean t35 = EnchantSelfTest.run(level, player);
        // 1.2.2:深渊侵蚀的大地之心(标签 / 来源 / 四处容器转化 / 不干涉原版污染 / 配方桥接)
        boolean t36 = AbyssalEarthHeartSelfTest.run(level, player);
        // 1.2.5c:瓶装历史迷雾(大地之瓶转化 / 双重限制记录 / 携带生效)
        boolean t43 = HistoryMistSelfTest.run(level, player);
        // 1.2.6:四个瓶子(灵界之光 / 冥河之水 / 战争迷雾 / 太阳)
        boolean t44 = BottleChainSelfTest.run(level, player);

        allPass = allPass && t0 && t1 && t2 && t3 && t4 && t5 && t6 && t7 && t8 && t13 && t14 && t15
            && t16 && t22 && t25 && t26 && t27 && t28 && t29 && t30 && t31 && t32 && t33 && t34 && t35 && t36 && t43 && t44;
        log(" summary T0=" + t0 + " T1=" + t1 + " T2=" + t2 + " T3=" + t3 + " T4=" + t4 + " T5=" + t5
            + " T6=" + t6 + " T7(恶魔契约)=" + t7 + " T12(创造标签页)=" + t8 + " T13(四态模型)=" + t13
            + " T14(飞行穿方块)=" + t14 + " T15(旅行家帽子)=" + t15 + " T16(tooltip 精简)=" + t16
            + " T22(配置注释)=" + t22 + " T25(珍珠·远古重铸)=" + t25 + " T26(恶魔血杯)=" + t26
            + " T27(睡前读物)=" + t27 + " T28(纯净符文)=" + t28 + " T29(全效果穷举)=" + t29
            + " T30(恶魔的手骨)=" + t30
            + " T31(lang 完整性)=" + t31
            + " T34(书袋标签)=" + t34 + " T35(附魔)=" + t35 + " T36(深渊侵蚀之心)=" + t36
            + " T43(瓶装历史迷雾)=" + t43
            + " T44(四个瓶子)=" + t44
            );
    }

    // ------------------------------------------------------------------ T0 定义

    private static boolean testDefinition(ServerLevel level, FakePlayer player) {
        log(" --- T0 定义 / 注册 / 槽位 / 配方存在性");
        boolean ok = true;
        Item item = ModItems.earthAmuletOrNull();
        ok &= item != null;
        ResourceLocation id = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
        log(" T0 物品 id=" + id + " [应为 enigmaticenhance:earth_amulet]");
        ok &= "enigmaticenhance:earth_amulet".equals(String.valueOf(id));
        if (item == null) {
            check("T0", false);
            return false;
        }
        ItemStack stack = new ItemStack(item);
        log(" T0 最大堆叠=" + stack.getMaxStackSize() + " [应 1];最大耐久=" + stack.getMaxDamage() + " [应 0]");
        ok &= stack.getMaxStackSize() == 1 && stack.getMaxDamage() == 0;
        boolean charm = false;
        try {
            charm = CuriosApi.getItemStackSlots(stack, level).containsKey(ModIds.CURIO_SLOT_CHARM);
        }
        catch (Throwable t) {
            log(" T0 Curios 槽位查询异常: " + t);
        }
        log(" T0 curios:charm 槽=" + charm + " [应 true]");
        ok &= charm;

        ResourceLocation heartId = new ResourceLocation("enigmaticlegacy", "earth_heart");
        boolean heart = ForgeRegistries.ITEMS.containsKey(heartId);
        log(" T0 大地之心 " + heartId + " 存在=" + heart + " [应 true]");
        ok &= heart;

        for (ResourceLocation recipeId : recipeIds()) {
            boolean present = level.getRecipeManager().byKey(recipeId).isPresent();
            log(" T0 配方 " + recipeId + " 存在=" + present + " [应 true]");
            ok &= present;
        }
        List<String> problems = ModIds.problems();
        log(" T0 ModIds.problems()=" + problems + " [必须空]");
        ok &= problems.isEmpty();
        check("T0", ok);
        return ok;
    }

    private static List<ResourceLocation> recipeIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(new ResourceLocation(EnigmaticEnhance.MODID, ModIds.RECIPE_EARTH_AMULET_PATH));
        ids.add(new ResourceLocation(EnigmaticEnhance.MODID, ModIds.RECIPE_EARTH_AMULET_POTION_PATH));
        ids.add(new ResourceLocation(EnigmaticEnhance.MODID, ModIds.RECIPE_EARTH_AMULET_HEAD_PATH));
        ids.add(new ResourceLocation(EnigmaticEnhance.MODID, ModIds.RECIPE_EARTH_AMULET_EMPOWER_PATH));
        // 1.2.2:深渊侵蚀的大地之心也能做护符的那条(与上一条只差材料的标记)
        ids.add(new ResourceLocation(EnigmaticEnhance.MODID, ModIds.RECIPE_EARTH_AMULET_ABYSSAL_PATH));
        return ids;
    }

    // ------------------------------------------------------------------ T1 基础配方

    private static boolean testBaseRecipe(ServerLevel level, FakePlayer player) {
        log(" --- T1 基础配方:污染大地之心 + 3 金粒 → 1 大地护符");
        Item heart = ForgeRegistries.ITEMS.getValue(new ResourceLocation("enigmaticlegacy", "earth_heart"));
        if (heart == null) {
            check("T1", false);
            return false;
        }
        ItemStack tainted = new ItemStack(heart);
        tainted.getOrCreateTag().putBoolean("isTainted", true);
        Crafted crafted = craft(level, player, tainted, goldNugget(), goldNugget(), goldNugget());
        boolean ok = crafted != null;
        String id = crafted == null ? "缺失" : crafted.recipe().getId().toString();
        log(" T1 污染之心 + 3 金粒 → 配方=" + id + " [应 enigmaticenhance:earth_amulet];产物="
            + (crafted == null ? "-" : describe(crafted.output())));
        ok &= crafted != null
            && "enigmaticenhance:earth_amulet".equals(id)
            && crafted.output().getItem() == ModItems.EARTH_AMULET.get()
            && crafted.output().getCount() == 1;

        ItemStack clean = new ItemStack(heart);
        Crafted bad = craft(level, player, clean, goldNugget(), goldNugget(), goldNugget());
        log(" T1 反例:未污染大地之心 + 3 金粒 → 配方=" + (bad == null ? "无(正确)" : bad.recipe().getId())
            + " [必须无]");
        ok &= bad == null;

        Crafted bad2 = craft(level, player, tainted.copy(), goldNugget(), goldNugget(), new ItemStack(Items.COPPER_INGOT));
        log(" T1 反例:污染之心 + 2 金粒 + 铜锭 → 配方=" + (bad2 == null ? "无(正确)" : bad2.recipe().getId())
            + " [必须无]");
        ok &= bad2 == null;
        check("T1", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T2 药水覆盖

    private static boolean testPotionRecipe(ServerLevel level, FakePlayer player) {
        log(" --- T2 药水注入:只保留最新一次(力量 II → 跳跃提升 I)");
        ItemStack strengthPotion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.STRONG_STRENGTH);
        Crafted first = craft(level, player, amulet(), strengthPotion);
        boolean ok = first != null;
        log(" T2 第一次:护符 + 力量 II 药水 → 配方=" + (first == null ? "缺失" : first.recipe().getId())
            + ";PotionEffects=" + (first == null ? "-" : effectText(first.output())));
        ok &= first != null
            && idIs(first, ModIds.RECIPE_EARTH_AMULET_POTION_PATH)
            && EarthAmuletData.effects(first.output()).size() == 1
            && effectMatches(first.output(), 0, "minecraft:strength", 2);

        ItemStack leapingPotion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LEAPING);
        Crafted second = first == null ? null : craft(level, player, first.output().copy(), leapingPotion);
        log(" T2 第二次:带力量 II 的护符 + 跳跃提升 I 药水 → 配方="
            + (second == null ? "缺失" : second.recipe().getId()) + ";PotionEffects="
            + (second == null ? "-" : effectText(second.output())) + " [必须只剩 jump_boost I]");
        ok &= second != null
            && idIs(second, ModIds.RECIPE_EARTH_AMULET_POTION_PATH)
            && EarthAmuletData.effects(second.output()).size() == 1
            && effectMatches(second.output(), 0, "minecraft:jump_boost", 1);

        Crafted water = craft(level, player, amulet(), new ItemStack(Items.POTION));
        log(" T2 反例:护符 + 水瓶(无效果)→ 配方=" + (water == null ? "无(正确)" : water.recipe().getId())
            + " [必须无]");
        ok &= water == null;
        check("T2", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T3 头颅

    private static boolean testHeadRecipes(ServerLevel level, FakePlayer player) {
        log(" --- T3 头颅注入:逐个解锁 + 可叠加");
        boolean ok = true;
        for (String headId : EarthAmuletData.HEADS) {
            Item head = EarthAmuletData.headItem(headId);
            ItemStack headStack = head == null ? ItemStack.EMPTY : new ItemStack(head);
            Crafted crafted = craft(level, player, amulet(), headStack);
            boolean flag = crafted != null && EarthAmuletData.hasHead(crafted.output(), headId);
            log(" T3 " + headId + " → 配方=" + (crafted == null ? "缺失" : crafted.recipe().getId())
                + ";Heads=" + (crafted == null ? "-" : EarthAmuletData.heads(crafted.output())) + " [应含该头颅]");
            ok &= flag;
        }

        ItemStack chained = amulet();
        String[] chainHeads = { EarthAmuletData.HEAD_ZOMBIE, EarthAmuletData.HEAD_SKELETON,
                EarthAmuletData.HEAD_CREEPER };
        for (String headId : chainHeads) {
            Crafted crafted = craft(level, player, chained, new ItemStack(EarthAmuletData.headItem(headId)));
            if (crafted == null) {
                log(" T3 叠加链在 " + headId + " 处断裂 [FAIL]");
                ok = false;
                break;
            }
            chained = crafted.output();
        }
        List<String> heads = EarthAmuletData.heads(chained);
        log(" T3 叠加三次后的 Heads=" + heads + " [应为 3 个且包含僵尸/骷髅/苦力怕]");
        ok &= heads.size() == 3 && heads.containsAll(List.of(chainHeads));

        Crafted duplicate = craft(level, player, chained.copy(),
            new ItemStack(EarthAmuletData.headItem(EarthAmuletData.HEAD_ZOMBIE)));
        log(" T3 反例:已带僵尸头再注入僵尸头 → 配方="
            + (duplicate == null ? "无(正确)" : duplicate.recipe().getId()) + " [必须无]");
        ok &= duplicate == null;
        check("T3", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T4 强化

    private static boolean testEmpowerRecipe(ServerLevel level, FakePlayer player) {
        log(" --- T4 下界之星强化:必须先有凋灵骷髅头");
        boolean ok = true;

        Crafted noHead = craft(level, player, amulet(), new ItemStack(Items.NETHER_STAR));
        log(" T4 无头颅 + 下界之星 → 配方=" + (noHead == null ? "无(正确)" : noHead.recipe().getId())
            + " [必须无]");
        ok &= noHead == null;

        Crafted zombie = craft(level, player,
            EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_ZOMBIE), new ItemStack(Items.NETHER_STAR));
        log(" T4 僵尸头 + 下界之星 → 配方=" + (zombie == null ? "无(正确)" : zombie.recipe().getId())
            + " [必须无]");
        ok &= zombie == null;

        Crafted skeleton = craft(level, player,
            EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_SKELETON), new ItemStack(Items.NETHER_STAR));
        log(" T4 骷髅头 + 下界之星 → 配方=" + (skeleton == null ? "无(正确)" : skeleton.recipe().getId())
            + " [必须无]");
        ok &= skeleton == null;

        ItemStack wither = EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_WITHER_SKELETON);
        Crafted empowered = craft(level, player, wither, new ItemStack(Items.NETHER_STAR));
        log(" T4 凋灵骷髅头 + 下界之星 → 配方=" + (empowered == null ? "缺失" : empowered.recipe().getId())
            + ";Empowered=" + (empowered == null ? "-" : EarthAmuletData.isEmpowered(empowered.output()))
            + " [应 true]");
        ok &= empowered != null && idIs(empowered, ModIds.RECIPE_EARTH_AMULET_EMPOWER_PATH)
            && EarthAmuletData.isEmpowered(empowered.output());

        Crafted again = empowered == null ? null
            : craft(level, player, empowered.output().copy(), new ItemStack(Items.NETHER_STAR));
        log(" T4 反例:已强化 + 下界之星 → 配方=" + (again == null ? "无(正确)" : again.recipe().getId())
            + " [必须无]");
        ok &= again == null;
        check("T4", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T5 效果

    private static boolean testEffects(ServerLevel level, FakePlayer player) {
        boolean ok = true;
        ok &= testZombieHead(level, player);
        ok &= testSkeletonHead(level, player);
        ok &= testSkeletonHeadKeepsArrows(level, player);
        ok &= testCreeperHead(level, player);
        ok &= testWitherHead(level, player);
        ok &= testPiglinHead(level, player);
        ok &= testDragonHead(level, player);
        ok &= testEmpowered(level, player);
        ok &= testPotionEffects(level, player);
        check("T5", ok);
        return ok;
    }

    /** T5a 僵尸头:亡灵生物不锁定 + 玩家被视为亡灵。 */
    private static boolean testZombieHead(ServerLevel level, FakePlayer player) {
        log(" --- T5a 僵尸头:亡灵生物不锁定 + 玩家被视为亡灵");
        Zombie zombie = spawnZombie(level, player);
        boolean ok = zombie != null;
        if (zombie == null) {
            log(" T5a 无法生成僵尸 [FAIL]");
            return false;
        }
        equip(player, EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_ZOMBIE));
        zombie.setTarget(player);
        boolean blocked = zombie.getTarget() == null;
        MobType type = player.getMobType();
        boolean inverted = player.isInvertedHealAndHarm();
        log(" T5a 佩戴后:僵尸目标=" + (zombie.getTarget() == null ? "null(正确)" : zombie.getTarget())
            + ";玩家是亡灵=" + (type == MobType.UNDEAD) + " [应 true];isInvertedHealAndHarm=" + inverted
            + " [应 true]");
        ok &= blocked && type == MobType.UNDEAD && inverted;

        unequip(player);
        zombie.setTarget(player);
        boolean notBlocked = zombie.getTarget() == player;
        MobType plainType = player.getMobType();
        log(" T5a 反例(未佩戴):僵尸目标=" + (zombie.getTarget() == null ? "null" : "玩家")
            + " [应为玩家];玩家是亡灵=" + (plainType == MobType.UNDEAD) + " [应 false]");
        ok &= notBlocked && plainType != MobType.UNDEAD;
        zombie.setTarget(null);
        zombie.discard();
        return ok;
    }

    /** T5b 骷髅头:空背包射箭不报错、箭矢数不减。 */
    private static boolean testSkeletonHead(ServerLevel level, FakePlayer player) {
        log(" --- T5b 骷髅头:背包没有箭也能射");
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        equip(player, EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_SKELETON));
        arrowSpawns = 0;
        captureArrows = true;
        boolean ok = true;
        try {
            ((BowItem) Items.BOW).releaseUsing(player.getMainHandItem(), level, player, 72000 - 20);
        }
        catch (Throwable t) {
            log(" T5b 射箭抛异常: " + t + " [FAIL]");
            ok = false;
        }
        finally {
            captureArrows = false;
        }
        int spawned = arrowSpawns;
        int arrows = countArrowsInInventory(player);
        log(" T5b 佩戴骷髅头:射出的箭实体=" + spawned + " [应 1];背包箭数=" + arrows + " [应 0]");
        ok &= spawned == 1 && arrows == 0;

        unequip(player);
        arrowSpawns = 0;
        captureArrows = true;
        try {
            ((BowItem) Items.BOW).releaseUsing(player.getMainHandItem(), level, player, 72000 - 20);
        }
        catch (Throwable t) {
            log(" T5b 反例射箭抛异常: " + t);
        }
        finally {
            captureArrows = false;
        }
        int spawned2 = arrowSpawns;
        int arrows2 = countArrowsInInventory(player);
        log(" T5b 反例(未佩戴):射出的箭实体=" + spawned2 + " [应 0];背包箭数=" + arrows2 + " [应 0]");
        ok &= spawned2 == 0 && arrows2 == 0;

        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, testBox(player))) {
            arrow.discard();
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return ok;
    }

    /** T5e 骷髅头:背包里有箭时也不消耗(1.2.12a 修)。 */
    private static boolean testSkeletonHeadKeepsArrows(ServerLevel level, FakePlayer player) {
        log(" --- T5i 骷髅头:有箭时射箭不扣箭");
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        player.getInventory().add(new ItemStack(Items.ARROW, 16));
        equip(player, EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_SKELETON));
        boolean ok = true;
        int before = countArrowsInInventory(player);
        try {
            ((BowItem) Items.BOW).releaseUsing(player.getMainHandItem(), level, player, 72000 - 20);
        }
        catch (Throwable t) {
            log(" T5i 射箭抛异常: " + t + " [FAIL]");
            ok = false;
        }
        int after = countArrowsInInventory(player);
        log(" T5i 佩戴骷髅头:射箭前=" + before + " 射箭后=" + after + " [应相等]");
        ok &= before == after && before > 0;

        // 反例:取下护符后射一箭应当真的扣 1
        unequip(player);
        int mid = countArrowsInInventory(player);
        try {
            ((BowItem) Items.BOW).releaseUsing(player.getMainHandItem(), level, player, 72000 - 20);
        }
        catch (Throwable t) {
            log(" T5i 反例射箭抛异常: " + t);
        }
        int afterNoAmulet = countArrowsInInventory(player);
        log(" T5i 反例(未佩戴):射箭前=" + mid + " 射箭后=" + afterNoAmulet + " [应少 1]");
        ok &= afterNoAmulet == mid - 1;
        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, testBox(player))) {
            arrow.discard();
        }
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return ok;
    }

    /** T5c 苦力怕头:爆炸伤害被拦(断言 LivingAttackEvent 被取消 = 0 伤害)。 */
    private static boolean testCreeperHead(ServerLevel level, FakePlayer player) {
        log(" --- T5c 苦力怕头:免疫爆炸伤害");
        var explosion = level.damageSources().explosion(null, null);
        equip(player, EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_CREEPER));
        LivingAttackEvent blocked = new LivingAttackEvent(player, explosion, 6.0F);
        MinecraftForge.EVENT_BUS.post(blocked);
        log(" T5c 佩戴后爆炸伤害 6.0 的 LivingAttackEvent 被取消=" + blocked.isCanceled() + " [应 true]");
        boolean ok = blocked.isCanceled();

        unequip(player);
        LivingAttackEvent plain = new LivingAttackEvent(player, explosion, 6.0F);
        MinecraftForge.EVENT_BUS.post(plain);
        log(" T5c 反例(未佩戴)被取消=" + plain.isCanceled() + " [应 false]");
        ok &= !plain.isCanceled();
        return ok;
    }

    /** T5d 凋灵骷髅头:攻击附凋零 + 自身免疫凋零。 */
    private static boolean testWitherHead(ServerLevel level, FakePlayer player) {
        log(" --- T5d 凋灵骷髅头:攻击附带凋零 + 免疫凋零");
        equip(player, EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_WITHER_SKELETON));
        boolean applied = player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
        boolean has = player.hasEffect(MobEffects.WITHER);
        log(" T5d 自身 addEffect(WITHER) 返回=" + applied + " [应 false];hasEffect=" + has + " [应 false]");
        boolean ok = !applied && !has;

        Zombie victim = spawnZombie(level, player);
        if (victim == null) {
            log(" T5d 无法生成目标 [FAIL]");
            return false;
        }
        victim.invulnerableTime = 0;
        victim.hurt(level.damageSources().playerAttack(player), 4.0F);
        boolean wither = victim.hasEffect(MobEffects.WITHER);
        log(" T5d 玩家攻击后目标 hasEffect(WITHER)=" + wither + " [应 true]");
        ok &= wither;

        unequip(player);
        MobEffectInstance forced = new MobEffectInstance(MobEffects.WITHER, 200, 1);
        boolean applied2 = player.addEffect(forced);
        log(" T5d 反例(未佩戴)addEffect(WITHER) 返回=" + applied2 + " [应 true]");
        ok &= applied2;
        player.removeEffect(MobEffects.WITHER);
        victim.discard();
        return ok;
    }

    /** T5e 猪灵头:下界生物不锁定。 */
    private static boolean testPiglinHead(ServerLevel level, FakePlayer player) {
        log(" --- T5e 猪灵头:下界生物不锁定");
        ZombifiedPiglin piglin = EntityType.ZOMBIFIED_PIGLIN.create(level);
        boolean ok = piglin != null;
        if (piglin == null) {
            log(" T5e 无法生成僵尸猪灵 [FAIL]");
            return false;
        }
        piglin.moveTo(player.getX() + 2.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(piglin);
        equip(player, EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_PIGLIN));
        piglin.setTarget(player);
        log(" T5e 佩戴后僵尸猪灵目标=" + (piglin.getTarget() == null ? "null(正确)" : piglin.getTarget()));
        ok &= piglin.getTarget() == null;

        unequip(player);
        piglin.setTarget(player);
        log(" T5e 反例(未佩戴)目标=" + (piglin.getTarget() == null ? "null" : "玩家") + " [应为玩家]");
        ok &= piglin.getTarget() == player;
        piglin.setTarget(null);
        piglin.discard();
        return ok;
    }

    /** T5f 龙首:授予创造飞行 + 取下后恢复。 */
    private static boolean testDragonHead(ServerLevel level, FakePlayer player) {
        log(" --- T5f 龙首:创造飞行授予/回收");
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        equip(player, EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_DRAGON));
        EarthAmuletHandler.onWornTick(player, EarthAmuletData.equippedAmulet(player));
        boolean granted = player.getAbilities().mayfly;
        log(" T5f 佩戴后 mayfly=" + granted + " [应 true];flightGranted="
            + EarthAmuletHandler.flightGranted(player) + " [应 true]");
        boolean ok = granted && EarthAmuletHandler.flightGranted(player);

        unequip(player);
        EarthAmuletHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        boolean revoked = !player.getAbilities().mayfly;
        log(" T5f 取下后 mayfly=" + player.getAbilities().mayfly + " [应 false];flightGranted="
            + EarthAmuletHandler.flightGranted(player) + " [应 false]");
        ok &= revoked && !EarthAmuletHandler.flightGranted(player);
        return ok;
    }

    /** T5g 强化:免疫亡灵生物造成的伤害。 */
    private static boolean testEmpowered(ServerLevel level, FakePlayer player) {
        log(" --- T5g 强化:免疫亡灵生物伤害");
        Zombie zombie = spawnZombie(level, player);
        if (zombie == null) {
            log(" T5g 无法生成僵尸 [FAIL]");
            return false;
        }
        var undeadHit = level.damageSources().mobAttack(zombie);
        equip(player, EarthAmuletData.empowered(
            EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_WITHER_SKELETON)));
        LivingAttackEvent blocked = new LivingAttackEvent(player, undeadHit, 5.0F);
        MinecraftForge.EVENT_BUS.post(blocked);
        log(" T5g 已强化:亡灵 5.0 伤害的 LivingAttackEvent 被取消=" + blocked.isCanceled() + " [应 true]");
        boolean ok = blocked.isCanceled();

        equip(player, EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_ZOMBIE));
        LivingAttackEvent plain = new LivingAttackEvent(player, undeadHit, 5.0F);
        MinecraftForge.EVENT_BUS.post(plain);
        log(" T5g 反例(未强化)被取消=" + plain.isCanceled() + " [应 false]");
        ok &= !plain.isCanceled();

        // 非亡灵伤害在强化后也不该被拦(作用域边界)
        LivingAttackEvent notUndead = new LivingAttackEvent(player, level.damageSources().magic(), 5.0F);
        MinecraftForge.EVENT_BUS.post(notUndead);
        log(" T5g 边界:强化护符 + 非亡灵魔法伤害 被取消=" + notUndead.isCanceled() + " [应 false]");
        ok &= !notUndead.isCanceled();
        zombie.discard();
        return ok;
    }

    /** T5h 佩戴时药水效果持续生效。 */
    private static boolean testPotionEffects(ServerLevel level, FakePlayer player) {
        log(" --- T5h 药水效果持续生效");
        ItemStack amulet = amulet();
        EarthAmuletData.setEffects(amulet, List.of(new EarthAmuletData.StoredEffect("minecraft:jump_boost", 2)));
        equip(player, amulet);
        player.removeEffect(MobEffects.JUMP);
        EarthAmuletHandler.onWornTick(player, amulet);
        MobEffectInstance jump = player.getEffect(MobEffects.JUMP);
        log(" T5h 佩戴后 jump_boost=" + (jump == null ? "无"
            : ("amplifier=" + jump.getAmplifier() + ",duration=" + jump.getDuration()))
            + " [应 amplifier=1,duration>0 且 <= " + EarthAmuletHandler.EFFECT_DURATION_TICKS + "]");
        boolean ok = jump != null && jump.getAmplifier() == 1 && jump.getDuration() > 0
            && jump.getDuration() <= EarthAmuletHandler.EFFECT_DURATION_TICKS;

        unequip(player);
        int before = player.getEffect(MobEffects.JUMP) == null ? 0 : player.getEffect(MobEffects.JUMP).getDuration();
        EarthAmuletHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        log(" T5h 取下后剩余时长=" + before + " tick(短时长设计:最多 3 秒自然过期)");
        player.removeEffect(MobEffects.JUMP);
        return ok;
    }

    // ------------------------------------------------------------------ T6 日志门控

    private static boolean testDebugGate(ServerLevel level, FakePlayer player) {
        if (QUIET) {
            log(" T6 SKIP (quiet run:整个 run 的日志都已压掉)");
            return true;
        }
        log(" --- T6 debugLog 门控(默认 false 零输出)");
        var explosion = level.damageSources().explosion(null, null);
        ItemStack creeperAmulet = EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_CREEPER);

        setBool(RingConfig.DEBUG_LOG, false);
        hold(300L);
        int quietBefore = logHits(PROBE);
        equip(player, creeperAmulet);
        MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(player, explosion, 3.0F));
        unequip(player);
        hold(450L);
        int quietAfter = logHits(PROBE);
        log(" T6 debugLog=false 同一动作新增日志行=" + (quietAfter - quietBefore) + " [应为 0]");
        boolean ok = quietBefore >= 0 && quietAfter >= 0 && quietAfter - quietBefore == 0;
        if (quietBefore < 0) {
            log(" T6 警告: 读不到 logs/latest.log,日志门控无法判定");
        }

        setBool(RingConfig.DEBUG_LOG, true);
        hold(300L);
        int loudBefore = logHits(PROBE);
        equip(player, creeperAmulet);
        MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(player, explosion, 3.0F));
        unequip(player);
        hold(450L);
        int loudAfter = logHits(PROBE);
        log(" T6 debugLog=true 同一动作新增日志行=" + (loudAfter - loudBefore) + " [应 >=1,证明探测器有效]");
        ok &= loudBefore >= 0 && loudAfter >= 0 && loudAfter - loudBefore >= 1;
        check("T6", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T12 创造标签页

    /**
     * T8:创造模式物品栏里能不能找到这两件饰品(1.1.25a 修复)。
     * 服务端没有真实的"玩家打开创造栏"流程,所以这里**自己构造**一个
     * {@link BuildCreativeModeTabContentsEvent}(官方构造器 + 空的 hashed 表),
     * 投递到**模组事件总线**上,再读 {@code getEntries()} 断言两件物品都在;
     * 反例:换一个标签页(INGREDIENTS)投递,两件物品都不许出现。
     */
    private static boolean testCreativeTab(ServerLevel level) {
        log(" --- T12 创造模式物品栏挂载");
        boolean ok = true;
        List<Item> items = CreativeTabContents.items();
        StringBuilder list = new StringBuilder();
        for (Item item : items) {
            if (list.length() > 0) {
                list.append(", ");
            }
            list.append(ForgeRegistries.ITEMS.getKey(item));
        }
        log(" T12 本模组已注册物品=" + list + " [共 " + items.size() + " 件,全部应挂上]");
        ok &= items.size() >= 2;
        boolean target = CreativeTabContents.isTargetTab(CreativeModeTabs.TOOLS_AND_UTILITIES);
        boolean notTarget = CreativeTabContents.isTargetTab(CreativeModeTabs.INGREDIENTS);
        log(" T12 目标标签页=TOOLS_AND_UTILITIES:" + target + " [应 true];INGREDIENTS 不是目标:" + !notTarget
            + " [应 true]");
        ok &= target && !notTarget;

        BuildCreativeModeTabContentsEvent event = fakeTabEvent(level, CreativeModeTabs.TOOLS_AND_UTILITIES);
        try {
            postToModBus(event);
        }
        catch (Throwable t) {
            log(" T12 模组总线投递异常: " + t);
        }
        boolean busAmulet = containsItem(event, ModItems.earthAmuletOrNull());
        boolean busPact = containsItem(event, ModItems.demonPactOrNull());
        if (!busAmulet || !busPact) {
            log(" T12 总线投递后缺条目(护符=" + busAmulet + ",契约=" + busPact + "),直接调用 handler 复核");
            CreativeTabContents.addTo(event);
        }
        boolean hasAmulet = containsItem(event, ModItems.earthAmuletOrNull());
        boolean hasPact = containsItem(event, ModItems.demonPactOrNull());
        log(" T12 TOOLS_AND_UTILITIES 条目总数=" + countEntries(event) + ";大地护符=" + hasAmulet
            + " 恶魔契约=" + hasPact + " [都应 true]");
        ok &= hasAmulet && hasPact;

        BuildCreativeModeTabContentsEvent other = fakeTabEvent(level, CreativeModeTabs.INGREDIENTS);
        try {
            postToModBus(other);
        }
        catch (Throwable ignored) {
            // 反例投递失败不影响断言
        }
        boolean otherAmulet = containsItem(other, ModItems.earthAmuletOrNull());
        boolean otherPact = containsItem(other, ModItems.demonPactOrNull());
        log(" T12 反例 INGREDIENTS:大地护符=" + otherAmulet + " 恶魔契约=" + otherPact + " [都应为 false]");
        ok &= !otherAmulet && !otherPact;
        check("T12", ok);
        return ok;
    }

    /** 造一个"创造标签页正在收集物品"的假事件(空表 + 一个独立 CreativeModeTab 实例)。 */
    private static BuildCreativeModeTabContentsEvent fakeTabEvent(ServerLevel level,
        ResourceKey<CreativeModeTab> key) {
        CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.literal("enigmaticenhance selftest")).build();
        CreativeModeTab.ItemDisplayParameters params =
            new CreativeModeTab.ItemDisplayParameters(FeatureFlagSet.of(), true, level.registryAccess());
        return new BuildCreativeModeTabContentsEvent(tab, key, params, new MutableHashedLinkedMap<>());
    }

    private static boolean containsItem(BuildCreativeModeTabContentsEvent event, Item item) {
        if (item == null) {
            return false;
        }
        for (var entry : event.getEntries()) {
            if (entry.getKey().getItem() == item) {
                return true;
            }
        }
        return false;
    }

    /** 往模组事件总线上投递(1.1.25a:构造期之后 FMLJavaModLoadingContext.get() 为 null,改用保存的 bus)。 */
    private static void postToModBus(Object event) {
        IEventBus bus = EnigmaticEnhance.MOD_BUS;
        if (bus == null) {
            log(" T12 模组事件总线不可用");
            return;
        }
        bus.post((net.minecraftforge.eventbus.api.Event) event);
    }

    private static int countEntries(BuildCreativeModeTabContentsEvent event) {
        int count = 0;
        for (var ignored : event.getEntries()) {
            ++count;
        }
        return count;
    }

    // ------------------------------------------------------------------ T13 四态模型

    /**
     * T13:大地护符按 NBT 换贴图的状态函数 + 模型 {@code overrides} 是否配套。
     * 四态映射(高优先级先判):Empowered(3) > Heads(2) > PotionEffects(1) > 空(0)。
     */
    private static boolean testModelStates() {
        log(" --- T13 大地护符四态模型");
        boolean ok = true;
        ItemStack empty = amulet();
        ItemStack potion = amulet();
        EarthAmuletData.setEffects(potion, List.of(new EarthAmuletData.StoredEffect("minecraft:strength", 2)));
        ItemStack heads = EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_ZOMBIE);
        ItemStack both = EarthAmuletData.withHead(potion.copy(), EarthAmuletData.HEAD_SKELETON);
        ItemStack empowered = EarthAmuletData.empowered(
            EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_WITHER_SKELETON));
        ItemStack empoweredBoth = EarthAmuletData.empowered(both.copy());
        ItemStack notAmulet = new ItemStack(Items.BOOK);

        int sEmpty = EarthAmuletState.state(empty);
        int sPotion = EarthAmuletState.state(potion);
        int sHeads = EarthAmuletState.state(heads);
        int sBoth = EarthAmuletState.state(both);
        int sEmpowered = EarthAmuletState.state(empowered);
        int sEmpoweredBoth = EarthAmuletState.state(empoweredBoth);
        int sOther = EarthAmuletState.state(notAmulet);
        log(" T13 状态索引:空=" + sEmpty + "[0] 药水=" + sPotion + "[1] 头颅=" + sHeads + "[2] 药水+头颅="
            + sBoth + "[2] 强化=" + sEmpowered + "[3] 强化+药水+头颅=" + sEmpoweredBoth + "[3] 非护符="
            + sOther + "[0]");
        ok &= sEmpty == EarthAmuletState.EMPTY && sPotion == EarthAmuletState.POTION
            && sHeads == EarthAmuletState.HEADS && sBoth == EarthAmuletState.HEADS
            && sEmpowered == EarthAmuletState.EMPOWERED && sEmpoweredBoth == EarthAmuletState.EMPOWERED
            && sOther == EarthAmuletState.EMPTY;
        float pred = EarthAmuletState.modelState(empowered);
        log(" T13 谓词值(强化)=" + pred + " [应 3.0];模型名(头颅态)="
            + EarthAmuletState.modelName(EarthAmuletState.HEADS) + " [应 earth_amulet_heads]");
        ok &= pred == 3.0F && "earth_amulet_heads".equals(EarthAmuletState.modelName(EarthAmuletState.HEADS));

        try (var in = EarthAmuletSelfTest.class
            .getResourceAsStream("/assets/enigmaticenhance/models/item/earth_amulet.json")) {
            if (in == null) {
                log(" T13 读不到 earth_amulet.json [FAIL]");
                ok = false;
            }
            else {
                String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                var overrides = root.getAsJsonArray("overrides");
                int count = overrides == null ? 0 : overrides.size();
                String key = "-";
                if (count > 0) {
                    key = overrides.get(0).getAsJsonObject().getAsJsonObject("predicate").keySet().iterator().next();
                }
                boolean hasEmpowered = json.contains("earth_amulet_empowered");
                boolean hasHeads = true;
                for (int i = 0; i <= 6; i++) {
                    hasHeads &= json.contains("enigmaticenhance:item/" + EarthAmuletState.headModelName(i))
                        || EarthAmuletState.headModelName(i).equals("earth_amulet_none");
                }
                log(" T13 模型 overrides 数量=" + count + " [应 7:6 头颅 + 强化];首个谓词名=" + key
                    + " [应 enigmaticenhance:amulet_head];含强化 override=" + hasEmpowered
                    + ";含 6 个头颅模型=" + hasHeads);
                ok &= count == 7 && EarthAmuletState.HEAD_PROPERTY.toString().equals(key)
                    && hasEmpowered && hasHeads && json.contains("earth_amulet_core");
            }
        }
        catch (Throwable t) {
            log(" T13 解析模型 json 异常: " + t + " [FAIL]");
            ok = false;
        }
        check("T13", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T14 龙首飞行穿方块

    /** T14:只有"佩戴龙首 + 护符给的飞行 + 正在飞行"才开 noPhysics;停飞/取下即关;卡方块先推出来。 */
    private static boolean testNoClip(ServerLevel level, FakePlayer player) {
        log(" --- T14 龙首:飞行时可穿过方块");
        boolean ok = true;
        ItemStack dragon = EarthAmuletData.withHead(amulet(), EarthAmuletData.HEAD_DRAGON);
        player.getAbilities().flying = false;
        player.getAbilities().mayfly = false;
        player.noPhysics = false;
        EarthAmuletHandler.clearNoClip(player);
        player.moveTo(8.5D, Y, 8.5D, 0.0F, 0.0F);

        equip(player, dragon);
        EarthAmuletHandler.onWornTick(player, EarthAmuletData.equippedAmulet(player));
        player.getAbilities().flying = true;
        tickPlayer(player);
        boolean on = player.noPhysics && EarthAmuletHandler.noClipActive(player);
        log(" T14 佩戴+飞行:noPhysics=" + player.noPhysics + " [应 true];noClipActive="
            + EarthAmuletHandler.noClipActive(player) + " [应 true];已通知客户端="
            + EarthAmuletHandler.lastNoClipSent(player) + " [应 true]");
        ok &= on && Boolean.TRUE.equals(EarthAmuletHandler.lastNoClipSent(player));

        player.getAbilities().flying = false;
        tickPlayer(player);
        boolean off = !player.noPhysics && !EarthAmuletHandler.noClipActive(player);
        log(" T14 停飞后:noPhysics=" + player.noPhysics + " [应 false];noClipActive="
            + EarthAmuletHandler.noClipActive(player) + " [应 false];已通知客户端="
            + EarthAmuletHandler.lastNoClipSent(player) + " [应 false]");
        ok &= off && Boolean.FALSE.equals(EarthAmuletHandler.lastNoClipSent(player));

        unequip(player);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        tickPlayer(player);
        log(" T14 未佩戴但飞行:noPhysics=" + player.noPhysics + " [应 false,只对佩戴者]");
        ok &= !player.noPhysics && !EarthAmuletHandler.noClipActive(player);
        player.getAbilities().flying = false;
        player.getAbilities().mayfly = false;

        int bx = 30;
        int bz = 30;
        clearArea(level, bx, bz);
        player.moveTo(bx + 0.5D, Y, bz + 0.5D, 0.0F, 0.0F);
        equip(player, dragon);
        EarthAmuletHandler.onWornTick(player, EarthAmuletData.equippedAmulet(player));
        player.getAbilities().flying = true;
        tickPlayer(player);
        boolean insideOn = player.noPhysics;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(new BlockPos(bx + dx, Y + dy, bz + dz),
                        Blocks.STONE.defaultBlockState());
                }
            }
        }
        boolean trapped = !level.noCollision(player, player.getBoundingBox());
        player.getAbilities().flying = false;
        tickPlayer(player);
        boolean freed = level.noCollision(player, player.getBoundingBox());
        double newY = player.getY();
        log(" T14 卡方块:开穿方块=" + insideOn + " [应 true];被石头包住=" + trapped + " [应 true];停飞后恢复碰撞="
            + !player.noPhysics + " [应 true];位置已安全=" + freed + " [应 true];y " + Y + " -> " + newY
            + " [应被上推]");
        ok &= insideOn && trapped && !player.noPhysics && freed && newY > Y;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 6; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlockAndUpdate(new BlockPos(bx + dx, Y + dy, bz + dz),
                        Blocks.AIR.defaultBlockState());
                }
            }
        }
        unequip(player);
        EarthAmuletHandler.clearNoClip(player);
        player.noPhysics = false;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.moveTo(8.5D, Y, 8.5D, 0.0F, 0.0F);
        check("T14", ok);
        return ok;
    }

    private static void tickPlayer(FakePlayer player) {
        EarthAmuletHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    }

    private static void clearArea(ServerLevel level, int bx, int bz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 6; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlockAndUpdate(new BlockPos(bx + dx, Y + dy, bz + dz),
                        Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlockAndUpdate(new BlockPos(bx + dx, Y - 1, bz + dz), Blocks.STONE.defaultBlockState());
            }
        }
    }

    // ------------------------------------------------------------------ 工具

    /** 一次合成:配方 + 产物(配方不存在返回 null)。 */
    private record Crafted(CraftingContainer grid, CraftingRecipe recipe, ItemStack output) {}

    private static Crafted craft(ServerLevel level, Player player, ItemStack... items) {
        CraftingContainer grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
        for (int i = 0; i < items.length && i < grid.getContainerSize(); i++) {
            grid.setItem(i, items[i].copy());
        }
        Optional<CraftingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        if (found.isEmpty()) {
            return null;
        }
        CraftingRecipe recipe = found.get();
        ItemStack output = recipe.assemble(grid, level.registryAccess());
        return new Crafted(grid, recipe, output);
    }

    private static ItemStack amulet() {
        return new ItemStack(ModItems.EARTH_AMULET.get());
    }

    private static ItemStack goldNugget() {
        return new ItemStack(Items.GOLD_NUGGET);
    }

    private static boolean idIs(Crafted crafted, String path) {
        return crafted != null
            && (EnigmaticEnhance.MODID + ":" + path).equals(crafted.recipe().getId().toString());
    }

    private static boolean effectMatches(ItemStack stack, int index, String id, int level) {
        List<EarthAmuletData.StoredEffect> effects = EarthAmuletData.effects(stack);
        if (index >= effects.size()) {
            return false;
        }
        EarthAmuletData.StoredEffect effect = effects.get(index);
        return id.equals(effect.id()) && level == effect.level();
    }

    private static String effectText(ItemStack stack) {
        StringBuilder builder = new StringBuilder("[");
        for (EarthAmuletData.StoredEffect effect : EarthAmuletData.effects(stack)) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(effect.id()).append(" lvl=").append(effect.level());
        }
        return builder.append(']').toString();
    }

    private static String describe(ItemStack stack) {
        ResourceLocation id = stack == null || stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        return (id == null ? "空" : id.toString()) + " x" + (stack == null ? 0 : stack.getCount());
    }

    /** 把护符放进 curios:charm 的第 0 格(FakePlayer 不会被 Curios 的 tick 驱动,测试里显式调用处理函数)。 */
    private static boolean equip(Player player, ItemStack stack) {
        try {
            return CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> {
                    var charm = handler.getCurios().get(ModIds.CURIO_SLOT_CHARM);
                    if (charm == null) {
                        return false;
                    }
                    charm.getStacks().setStackInSlot(0, stack.copy());
                    return !EarthAmuletData.equippedAmulet(player).isEmpty();
                })
                .orElse(false);
        }
        catch (Throwable t) {
            log(" 装备护符失败: " + t);
            return false;
        }
    }

    private static void unequip(Player player) {
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                var charm = handler.getCurios().get(ModIds.CURIO_SLOT_CHARM);
                if (charm != null) {
                    charm.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                }
            });
        }
        catch (Throwable ignored) {
            // 自检清理,忽略
        }
    }

    private static Zombie spawnZombie(ServerLevel level, Player player) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return null;
        }
        zombie.moveTo(player.getX() + 2.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static int countArrowsInInventory(Player player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.ARROW)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int countArrowsInWorld(ServerLevel level, Player player) {
        return level.getEntitiesOfClass(AbstractArrow.class, testBox(player)).size();
    }

    private static AABB testBox(Player player) {
        return new AABB(player.getX() - 12.0D, Y - 8.0D, player.getZ() - 12.0D,
            player.getX() + 12.0D, Y + 12.0D, player.getZ() + 12.0D);
    }

    /** 数 logs/latest.log 里包含 probe 的行数(文件不存在返回 -1)。 */
    static int logHits(String probe) {
        try {
            java.nio.file.Path path = java.nio.file.Path.of("logs", "latest.log");
            if (!java.nio.file.Files.isRegularFile(path)) {
                return -1;
            }
            int hits = 0;
            for (String line : java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8)) {
                if (line.contains(probe)) {
                    ++hits;
                }
            }
            return hits;
        }
        catch (Throwable t) {
            return -1;
        }
    }

    /** 自检里临时改 debugLog(让别的自检文件也能复用)。 */
    static void setDebugLog(boolean value) {
        setBool(RingConfig.DEBUG_LOG, value);
    }

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

    static void hold(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }

    // ------------------------------------------------------------------ T34 书袋标签

    /**
     * 1.1.28o:睡前读物必须是 {@code minecraft:bookshelf_books} 的一员,
     * 否则 enigmaticaddons 的 {@code AntiqueBag.isBook()} 不会把它收进古旧书袋。
     */
    private static boolean testBookTag() {
        net.minecraft.world.item.Item book = ModItems.bedtimeReadingOrNull();
        boolean ok = book != null
            && new net.minecraft.world.item.ItemStack(book).is(net.minecraft.tags.ItemTags.BOOKSHELF_BOOKS);
        net.minecraft.resources.ResourceLocation id = book == null ? null
            : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(book);
        DebugLog.info("[EarthAmuletSelfTest] T34 bookshelf_books 含 " + id + " =" + ok
            + " [必须 true,等价于 AntiqueBag.isBook()]");
        DebugLog.info("[EarthAmuletSelfTest] T34-RESULT " + (ok ? "PASS" : "FAIL"));
        return ok;
    }

}
