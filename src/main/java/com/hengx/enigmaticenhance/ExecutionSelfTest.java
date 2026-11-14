package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 4.8.3 临时自检({@code -Denigmaticenhance.selftest=execution}):<b>模拟验证</b>两组新功能,
 * 全部用**真实数字**断言,不看日志。
 *
 * <h2>A 组:执行之手「不被减免」</h2>
 * <p>每次都打一发<b>真实攻击</b>({@code target.hurt(playerAttack, 20)}),让它走
 * {@code LivingAttackEvent} → 接管 → 生成一脚 {@code enigmaticenhance:execution} 伤害,
 * 再用「血条实际掉了多少」判定。每条都配一个<b>同条件的普通攻击对照</b>(攻击者手上没有附魔),
 * 用来证明"减免本来会发生、是执行之手把它绕过去了"。</p>
 *
 * <h2>B 组:渔获瓶的额外渔获改成掉落物</h2>
 * <p>用 {@code EntityJoinLevelEvent} 数"真的进了世界的 ItemEntity"
 * (自检环境新实体对 {@code getEntitiesOfClass} 要等 tick 才可见,所以生成瞬间用加入事件数,
 * 之后用实体查询数),再跨 tick 验证 10 tick 拾取延迟与"不会被再乘一次"。</p>
 */
public final class ExecutionSelfTest {

    private static final String TAG = "[ExecutionSelfTest]";

    /** 与 {@code EarthAmuletSelfTest} 同一块场地高度。 */
    private static final int Y = 110;

    /** 目标血量 20 时的一击伤害。 */
    private static final float HIT = 20.0F;

    private static boolean allPass = true;

    /** 复用的假玩家(自检开始建一次)。 */
    private static FakePlayer player;

    /** 服务器实例(收尾时停服用)。 */
    private static MinecraftServer server;

    // ---------------------------------------------------------------- 破坏者
    // 模拟"另一个模组"在 LOWEST 削减/取消我们的伤害。注册顺序在 HandOfExecutionHandler 之后
    // ⇒ 它的处理器排在我们后面执行 —— 这正是最难的顺序(我们的还原已经做完了)。

    private static volatile boolean sabotage = false;
    private static volatile boolean sabotageCancel = false;
    private static volatile UUID sabotageTarget = null;

    // ---------------------------------------------------------------- 生成计数

    private static boolean capturing = false;
    private static int capturedFish = 0;
    private static int capturedOther = 0;
    private static final List<ItemEntity> capturedItems = new ArrayList<>();

    // ---------------------------------------------------------------- tick 阶段

    private static boolean phaseActive = false;
    private static ServerLevel phaseLevel;
    private static int phaseTick;
    private static int b3Spawned;
    private static int s5Ground;
    private static int s5Inv;
    private static int s5Delayed;
    private static int s5InvAfterTouch;
    private static int s20Ground;
    private static int s20Inv;
    private static int s20Delayed;
    private static int s20InvAfterTouch;
    private static int s45Ground;
    private static int s45Inv;

    private ExecutionSelfTest() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(ExecutionSelfTest.class);
        DebugLog.info(TAG + " registered");
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        run(event.getServer());
    }

    public static void run(MinecraftServer srv) {
        server = srv;
        try {
            ServerLevel level = srv.overworld();
            level.getChunk(0, 0);
            // 没有真人玩家时,区块不在"实体 tick"范围里 —— 掉落物会被实体管理器直接卸回磁盘。
            // 强制加载 (0,0) 让掉落物真的留在世界里,跨 tick 观察才有意义。
            level.setChunkForced(0, 0, true);
            player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ea_exec_test"));
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(8.5D, Y, 8.5D, 0.0F, 0.0F);
            player.getInventory().clearContent();
            buildArena(level);

            setBool(RingConfig.HAND_OF_EXECUTION_ENABLED, true);
            setBool(RingConfig.DEBUG_LOG, true);
            // A 组全程关掉"全伤害暴击":它会把对照数值随机 ×1.5(实测 12.16 → 20.00),
            // 让"普通攻击吃了护甲"看不出来。执行之手本身不吃这条暴击,不受影响。
            setBool(RingConfig.ALL_DAMAGE_CRIT_ENABLED, false);
            setBool(RingConfig.FISHING_HAUL_ENABLED, true);
            setBool(RingConfig.FISHING_HAUL_CATCH_MULTIPLIER, 2.0D);
            setBool(RingConfig.FISHING_HAUL_BAG_MULTIPLIER, 1.5D);
            hold(200L);
            log(" 开关: 执行之手=" + RingConfig.handOfExecutionEnabled() + " debugLog=" + RingConfig.debugLog()
                + " 渔获倍率=" + RingConfig.fishingHaulCatchMultiplier() + " 书袋倍率="
                + RingConfig.fishingHaulBagMultiplier());

            testDataLayer(level);
            testA(level);
            testB(level);
            log(" summary => " + (allPass ? "PASS" : "FAIL"));
            stop(server);
        }
        catch (Throwable t) {
            allPass = false;
            EnigmaticEnhance.LOGGER.error(TAG + " ABORTED", t);
            log(" ABORTED " + t);
            stop(server);
        }
    }

    private static void stop(MinecraftServer server) {
        phaseActive = false;
        System.out.println(TAG + " ALL-RESULT " + (allPass ? "PASS" : "FAIL"));
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "stop"));
    }

    // ------------------------------------------------------------------ 数据层

    /** 不打架也能判定的一半:7 个 tag 是不是真的挂在这个伤害类型上。 */
    private static void testDataLayer(ServerLevel level) {
        boolean ok = true;
        var src = ExecutionDamage.source(player);
        ok &= check("A0-a", "伤害类型 = " + (src == null ? "null" : src.getMsgId()), src != null
            && "enigmaticenhance.execution".equals(src.getMsgId()));
        StringBuilder sb = new StringBuilder();
        ok &= tag("A0-armor", sb, "bypasses_armor", src, DamageTypeTags.BYPASSES_ARMOR);
        ok &= tag("A0-shield", sb, "bypasses_shield", src, DamageTypeTags.BYPASSES_SHIELD);
        ok &= tag("A0-invuln", sb, "bypasses_invulnerability", src, DamageTypeTags.BYPASSES_INVULNERABILITY);
        ok &= tag("A0-cooldown", sb, "bypasses_cooldown", src, DamageTypeTags.BYPASSES_COOLDOWN);
        ok &= tag("A0-resist", sb, "bypasses_resistance", src, DamageTypeTags.BYPASSES_RESISTANCE);
        ok &= tag("A0-effects", sb, "bypasses_effects", src, DamageTypeTags.BYPASSES_EFFECTS);
        ok &= tag("A0-ench", sb, "bypasses_enchantments", src, DamageTypeTags.BYPASSES_ENCHANTMENTS);
        log(" A0 七个 bypass tag: " + sb);
        allPass &= ok;
        log(" A0-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static boolean tag(String name, StringBuilder sb, String label,
                               net.minecraft.world.damagesource.DamageSource src,
                               net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> key) {
        boolean in = src != null && src.is(key);
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(label).append('=').append(in);
        return in;
    }

    // ------------------------------------------------------------------ A 组

    private static void testA(ServerLevel level) {
        ItemStack blade = new ItemStack(Items.DIAMOND_SWORD);
        blade.enchant(ModEnchantments.handOfExecutionOrNull(), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);

        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);

        // ---- A1 钻石全套 ----
        Zombie z = target(level);
        diamondSet(z, false);
        float control1 = withPlain(plain, () -> hit(z, HIT));
        z.discard();
        Zombie z1 = target(level);
        diamondSet(z1, false);
        int armor1 = z1.getArmorValue();
        float a1 = hit(z1, HIT);
        pass("A1", "钻石全套(护甲=" + armor1 + ") 普通=" + f(control1) + " 执行之手=" + f(a1), full(a1));
        z1.discard();

        // ---- A2 保护 IV 全套 ----
        Zombie z2c = target(level);
        diamondSet(z2c, true);
        float control2 = withPlain(plain, () -> hit(z2c, HIT));
        z2c.discard();
        Zombie z2 = target(level);
        diamondSet(z2, true);
        float a2 = hit(z2, HIT);
        pass("A2", "钻石全套+保护IV 普通=" + f(control2) + " 执行之手=" + f(a2), full(a2));
        z2.discard();

        // ---- A3 抗性提升 V ----
        Zombie z3c = target(level);
        z3c.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 4));
        float control3 = withPlain(plain, () -> hit(z3c, HIT));
        z3c.discard();
        Zombie z3 = target(level);
        z3.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 4));
        float a3 = hit(z3, HIT);
        pass("A3", "抗性提升V 普通=" + f(control3) + " 执行之手=" + f(a3), full(a3));
        z3.discard();

        // ---- A4 无敌帧:先被打过 ----
        Zombie z4c = toughTarget(level);
        float c4a = withPlain(plain, () -> hitRaw(z4c, HIT));
        float c4b = withPlain(plain, () -> hitRaw(z4c, HIT));
        z4c.discard();
        Zombie z4 = toughTarget(level);
        float a4a = hitRaw(z4, HIT);
        float a4b = hitRaw(z4, HIT);
        pass("A4", "无敌帧内第二击 普通=" + f(c4b) + "(第一击 " + f(c4a) + ") 执行之手=" + f(a4b)
            + "(第一击 " + f(a4a) + ")", full(a4a) && full(a4b) && c4b == 0.0F);
        z4.discard();

        // ---- A5 举盾(目标要"面朝攻击者"才判定为格挡) ----
        Zombie z5c = shieldTarget(level);
        boolean blockingC = makeBlocking(z5c);
        float control5 = withPlain(plain, () -> hit(z5c, HIT));
        z5c.discard();
        Zombie z5 = shieldTarget(level);
        boolean blocking = makeBlocking(z5);
        float a5 = hit(z5, HIT);
        pass("A5", "举盾(真的 isBlocking=" + blocking + ",正面来袭) 普通=" + f(control5) + " 执行之手="
            + f(a5), blocking && blockingC && control5 == 0.0F && full(a5));
        z5.discard();

        // ---- A6 不死图腾 ----
        Zombie z6c = target(level);
        z6c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        float control6 = withPlain(plain, () -> hit(z6c, 100.0F));
        boolean totemWorks = z6c.isAlive();
        if (!z6c.isRemoved()) {
            z6c.discard();
        }
        Zombie z6 = target(level);
        z6.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        float a6 = hit(z6, 100.0F);
        boolean died = !z6.isAlive();
        pass("A6", "图腾:普通 100 伤害后存活=" + totemWorks + "(掉 " + f(control6) + ",图腾正常生效)"
            + " / 执行之手 100 伤害后死亡=" + died + "(掉 " + f(a6) + ")", totemWorks && died);
        if (!z6.isRemoved()) {
            z6.discard();
        }

        // ---- A7 伤害吸收(黄心)20 ----
        Zombie z7 = target(level);
        z7.setAbsorptionAmount(20.0F);
        float a7 = hit(z7, HIT);
        float absorbLeft = z7.getAbsorptionAmount();
        pass("A7", "黄心20:血条掉=" + f(a7) + " 黄心剩=" + f(absorbLeft) + "(黄心先被扣掉,总伤害 ≥ 20)",
            absorbLeft == 0.0F && a7 + (20.0F - absorbLeft) >= HIT - 1.0E-3F);
        z7.discard();

        // ---- A8 别的模组在 LOWEST 把伤害改成 1 ----
        Zombie z8 = target(level);
        sabotageTarget = z8.getUUID();
        sabotageCancel = false;
        sabotage = true;
        float a8;
        try {
            a8 = hit(z8, HIT);
        }
        finally {
            sabotage = false;
            sabotageTarget = null;
        }
        pass("A8", "LOWEST 把伤害改 1(排在我们后面) 实际掉血=" + f(a8), full(a8));
        if (!z8.isRemoved()) {
            z8.discard();
        }

        // ---- A9 别的模组取消事件 ----
        Zombie z9 = target(level);
        sabotageTarget = z9.getUUID();
        sabotageCancel = true;
        sabotage = true;
        float a9;
        try {
            a9 = hit(z9, HIT);
        }
        finally {
            sabotage = false;
            sabotageTarget = null;
        }
        pass("A9", "LOWEST 取消事件 实际掉血=" + f(a9), full(a9));
        if (!z9.isRemoved()) {
            z9.discard();
        }

        // ---- A10 负向对照:不带执行之手的普通攻击 ----
        Zombie z10 = target(level);
        diamondSet(z10, false);
        float a10 = withPlain(plain, () -> hit(z10, HIT));
        pass("A10", "负向对照(无附魔+钻石全套) 掉血=" + f(a10) + " 应 < 20(确实吃护甲)",
            a10 > 0.0F && a10 < HIT);
        z10.discard();
    }

    /** 临时把主手换成没有附魔的剑,跑完再换回来。 */
    private static float withPlain(ItemStack plain, java.util.function.Supplier<Float> body) {
        ItemStack previous = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, plain.copy());
        try {
            return body.get();
        }
        finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    /** 走完整攻击管线的一发:先清无敌帧,再 hurt(会派发 LivingAttackEvent → 被接管)。 */
    private static float hit(LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        return hitRaw(target, amount);
    }

    /** 不清无敌帧(用来验证"无敌帧内也能打满")。 */
    private static float hitRaw(LivingEntity target, float amount) {
        float before = target.getHealth();
        target.hurt(player.damageSources().playerAttack(player), amount);
        return before - target.getHealth();
    }

    private static Zombie target(ServerLevel level) {
        return targetAt(level, 9.5D, 9.5D);
    }

    private static Zombie targetAt(ServerLevel level, double px, double pz) {
        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.moveTo(px, Y, pz, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);
        zombie.setHealth(zombie.getMaxHealth());
        return zombie;
    }

    /** 血量 200 的目标(验证无敌帧要连打两发,20 点血不够)。 */
    private static Zombie toughTarget(ServerLevel level) {
        Zombie z = target(level);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        z.setHealth(200.0F);
        return z;
    }

    /**
     * 举盾目标:甲(防日晒烧血,否则手动 tick 时会先掉几点血,掉血差被 clamp)+ 200 血。
     *
     * <p>位置在玩家正前方:原版格挡判定要求攻击来自目标视线方向。</p>
     */
    private static Zombie shieldTarget(ServerLevel level) {
        Zombie z = targetAt(level, 8.5D, 7.5D);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        z.setHealth(200.0F);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        return z;
    }

    private static void diamondSet(Zombie z, boolean protection4) {
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        ItemStack[] pieces = {
            new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)
        };
        for (int i = 0; i < 4; i++) {
            if (protection4) {
                pieces[i].enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
            }
            z.setItemSlot(slots[i], pieces[i]);
        }
        // 原版把"装备 → ARMOR 属性修饰符"的同步放在 LivingEntity#tick → detectEquipmentUpdates,
        // 不 tick 一次的话护甲根本不进属性表(自检里前后差 4 点伤害就是这么来的)。
        int before = z.getArmorValue();
        for (int i = 0; i < 5 && z.getArmorValue() == before; i++) {
            z.tick();
        }
    }

    /** 让僵尸真的"举着盾"({@code isBlocking()} 需要 useItemRemaining 递减 5 tick)。 */
    private static boolean makeBlocking(Zombie z) {
        z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
        z.startUsingItem(InteractionHand.MAIN_HAND);
        for (int i = 0; i < 8 && !z.isBlocking(); i++) {
            z.tick();
        }
        return z.isBlocking();
    }

    // ------------------------------------------------------------------ B 组

    private static void testB(ServerLevel level) {
        Item bottle = ModItems.fishingHaulBottleOrNull();
        if (bottle == null) {
            fail("B", "渔获瓶缺失");
            return;
        }
        player.getInventory().clearContent();
        clearGroundFish(level);

        // ---- B1 携带(×2):一竿 2 鱼 → 地上 2 个 ItemEntity,背包不增加 ----
        player.getInventory().add(new ItemStack(bottle));
        int inv0 = countInvFish();
        int[] spawn1 = catchOnce(level);
        int inv1 = countInvFish();
        boolean b1 = spawn1[0] == 2 && spawn1[1] == 0 && inv1 == inv0;
        pass("B1", "携带×2:新增鱼实体=" + spawn1[0] + " 新增杂物实体=" + spawn1[1] + " 背包鱼变化="
            + (inv1 - inv0) + " (应 2 / 0 / 0)", b1);

        // ---- B2 书袋(×2×1.5=×3):一竿 2 鱼 → 地上 4 个 ----
        player.getInventory().clearContent();
        clearGroundFish(level);
        Item bag = RingConfig.antiqueBagItem();
        boolean inBag = false;
        int[] spawn2 = { -1, -1 };
        if (bag != null) {
            inBag = bagSetBook(bag, bottle);
            if (inBag) {
                spawn2 = catchOnce(level);
            }
        }
        boolean b2 = inBag && spawn2[0] == 4 && spawn2[1] == 0;
        pass("B2", "书袋×" + f((float) (RingConfig.fishingHaulCatchMultiplier()
            * RingConfig.fishingHaulBagMultiplier())) + ":书袋生效=" + inBag + " 新增鱼实体=" + spawn2[0]
            + " 新增杂物实体=" + spawn2[1] + " (应 4 / 0)", b2);
        if (bag != null) {
            bagSetBook(bag, null);
            removeBagItem(bag);
        }

        // ---- B3/B4:拾取延迟 + 不会被再乘一次 ----
        // 环境限制:专用服没有真人玩家时,区块不在"实体 tick"范围里,ItemEntity 的 age 永远是 0
        // (实测:实体活着,但 age=0、pickupDelay 不减)。所以这里对捕获到的实体**手动 tick**,
        // 等价于服务器跑 10 / 50 tick;判定用的是实体自己的状态与背包内容,不看日志。
        player.getInventory().clearContent();
        clearGroundFish(level);
        player.getInventory().add(new ItemStack(bottle));
        int[] spawn3 = catchOnce(level);
        int spawned = spawn3[0];
        int onGround = capturedItems.size();
        boolean delayAtSpawn = allDelayed(capturedItems);
        int invBefore = countInvFish();
        touchCaptured();
        int invAfterEarlyTouch = countInvFish();     // 10 tick 内:捡不起来
        int ageBefore = capturedItems.isEmpty() ? -1 : capturedItems.get(0).getAge();

        for (ItemEntity item : capturedItems) {
            for (int i = 0; i < 10; i++) {
                item.tick();
            }
        }
        boolean delayAfter10 = allDelayed(capturedItems);
        int ageAfter10 = capturedItems.isEmpty() ? -1 : capturedItems.get(0).getAge();
        touchCaptured();                              // 延迟过后:能捡
        int invAfterTouch = countInvFish();
        boolean pickedUp = allRemoved(capturedItems);

        // 再跑 40 tick(≈2 秒):不允许多出任何新的鱼实体(防指数增长)
        capturing = true;
        capturedFish = 0;
        capturedOther = 0;
        for (ItemEntity item : capturedItems) {
            for (int i = 0; i < 40; i++) {
                item.tick();
            }
        }
        capturing = false;
        int grownDuringTicks = capturedFish;
        int totalAfter = countInvFish();
        pass("B3", "防重复乘:本竿新增=" + spawned + " 个(非 4/8/16),捕获引用=" + onGround
            + ",10+40 tick 期间新冒出的鱼实体=" + grownDuringTicks + ",最终总数=" + totalAfter
            + "(背包,应 2)", spawned == 2 && onGround == 2 && grownDuringTicks == 0 && totalAfter == 2);
        pass("B4", "拾取延迟:生成时仍延迟=" + delayAtSpawn + "(age=" + ageBefore + ") 立刻 playerTouch 背包="
            + invAfterEarlyTouch + "/" + invBefore + " ; 10 tick 后仍延迟=" + delayAfter10 + "(age=" + ageAfter10
            + ") playerTouch 背包=" + invAfterTouch + " 实体已回收=" + pickedUp,
            delayAtSpawn && invAfterEarlyTouch == invBefore && !delayAfter10 && invAfterTouch == invBefore + 2 && pickedUp);
    }

    private static boolean allDelayed(java.util.List<ItemEntity> items) {
        if (items.isEmpty()) {
            return false;
        }
        for (ItemEntity item : items) {
            if (!item.hasPickUpDelay()) {
                return false;
            }
        }
        return true;
    }

    private static boolean allRemoved(java.util.List<ItemEntity> items) {
        for (ItemEntity item : items) {
            if (!item.isRemoved()) {
                return false;
            }
        }
        return !items.isEmpty();
    }

    private static void touchCaptured() {
        for (ItemEntity item : capturedItems) {
            item.playerTouch(player);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!capturing || event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        if (!(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        ItemStack stack = item.getItem();
        if (stack.isEmpty()) {
            return;
        }
        if (stack.is(ItemTags.FISHES)) {
            capturedFish += stack.getCount();
            capturedItems.add(item);
        }
        else {
            capturedOther += stack.getCount();
        }
    }

    // ------------------------------------------------------------------ B 组工具

    /** 发一竿假的渔获(2 条鱼 + 0 杂物),返回 {新增鱼实体, 新增杂物实体}。 */
    private static int[] catchOnce(ServerLevel level) {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(Items.COD));
        drops.add(new ItemStack(Items.SALMON));
        capturedFish = 0;
        capturedOther = 0;
        capturedItems.clear();
        capturing = true;
        int droppedBefore = FishingHaulBottleHandler.DROPPED_CATCH;
        try {
            FishingHook hook = new FishingHook(player, level, 0, 0);
            hook.setPos(player.getX(), player.getY(), player.getZ());
            MinecraftForge.EVENT_BUS.post(new ItemFishedEvent(drops, 1, hook));
        }
        finally {
            capturing = false;
        }
        log(" (本竿 addFreshEntity 成功 " + (FishingHaulBottleHandler.DROPPED_CATCH - droppedBefore)
            + " 次;捕获到 " + capturedItems.size() + " 个鱼 ItemEntity)");
        return new int[] { capturedFish, capturedOther };
    }

    private static void buildArena(ServerLevel level) {
        for (int x = 4; x <= 14; x++) {
            for (int z = 4; z <= 14; z++) {
                level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, Y, z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, Y + 1, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void clearGroundFish(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
            player.getBoundingBox().inflate(12.0D))) {
            if (item.getItem().is(ItemTags.FISHES)) {
                item.discard();
            }
        }
    }

    private static int countGroundFish(ServerLevel level) {
        int n = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
            player.getBoundingBox().inflate(12.0D))) {
            if (item.getItem().is(ItemTags.FISHES)) {
                n += item.getItem().getCount();
            }
        }
        return n;
    }

    private static int countDelayedFish(ServerLevel level) {
        int n = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
            player.getBoundingBox().inflate(12.0D))) {
            if (item.getItem().is(ItemTags.FISHES) && item.hasPickUpDelay()) {
                n += item.getItem().getCount();
            }
        }
        return n;
    }

    private static void touchAllFish(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
            player.getBoundingBox().inflate(12.0D))) {
            if (item.getItem().is(ItemTags.FISHES)) {
                item.playerTouch(player);
            }
        }
    }

    private static int countInvFish() {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(ItemTags.FISHES)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 破坏者

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sabotageHurt(LivingHurtEvent event) {
        if (!sabotage || !ExecutionDamage.isExecution(event.getSource())) {
            return;
        }
        if (event.getEntity() == null || sabotageTarget == null
            || !sabotageTarget.equals(event.getEntity().getUUID())) {
            return;
        }
        if (sabotageCancel) {
            event.setCanceled(true);
        }
        else {
            event.setAmount(1.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sabotageDamage(LivingDamageEvent event) {
        if (!sabotage || !ExecutionDamage.isExecution(event.getSource())) {
            return;
        }
        if (event.getEntity() == null || sabotageTarget == null
            || !sabotageTarget.equals(event.getEntity().getUUID())) {
            return;
        }
        if (sabotageCancel) {
            event.setCanceled(true);
        }
        else {
            event.setAmount(1.0F);
        }
    }

    // ------------------------------------------------------------------ 工具

    private static void setBool(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value, boolean expected) {
        for (int i = 0; i < 10; i++) {
            try {
                value.set(expected);
                if (value.get() == expected) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 重试
            }
            hold(20L);
        }
    }

    private static void setBool(net.minecraftforge.common.ForgeConfigSpec.DoubleValue value, double expected) {
        for (int i = 0; i < 10; i++) {
            try {
                value.set(expected);
                if (Math.abs(value.get() - expected) < 1.0E-9D) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 重试
            }
            hold(20L);
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

    /** 反射写古旧书袋槽 0;顺带保证书袋物品在背包里(缺扩展时 false)。 */
    private static boolean bagSetBook(Item bag, Item book) {
        try {
            boolean have = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).getItem() == bag) {
                    have = true;
                    break;
                }
            }
            if (!have) {
                player.getInventory().add(new ItemStack(bag));
            }
            Class<?> capClass = Class.forName(
                "auviotre.enigmatic.addon.contents.objects.bookbag.AntiqueBagCapability");
            Object capability = capClass.getField("INVENTORY").get(null);
            @SuppressWarnings("rawtypes")
            Object lazy = player.getCapability((net.minecraftforge.common.capabilities.Capability) capability);
            if (!(lazy instanceof net.minecraftforge.common.util.LazyOptional<?> optional)) {
                return false;
            }
            Object handler = optional.resolve().orElse(null);
            if (handler == null) {
                return false;
            }
            Class<?> iface = Class.forName(
                "auviotre.enigmatic.addon.contents.objects.bookbag.IAntiqueBagHandler");
            iface.getMethod("setBook", int.class, ItemStack.class)
                .invoke(handler, 0, book == null ? ItemStack.EMPTY : new ItemStack(book));
            return book == null || !BookBag.findBook(player, new ResourceLocation(
                EnigmaticEnhance.MODID, ModIds.FISHING_HAUL_BOTTLE_PATH)).isEmpty();
        }
        catch (Throwable t) {
            log(" B 构造书袋内容异常: " + t);
            return false;
        }
    }

    private static void removeBagItem(Item bag) {
        try {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).getItem() == bag) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
        }
        catch (RuntimeException ignored) {
            // 清理失败不影响结论
        }
    }

    private static String f(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /**
     * "全额"判定:至少打满 20。
     *
     * <p>不要求恰好等于 20:整包里别的模组可以在事件层**加**伤害(实测偶尔出现 20 → 30 的暴击加成),
     * 我们只还原"被削减",从不去削别人加的那部分 —— 所以下限是 20,上不封顶。</p>
     */
    private static boolean full(float dealt) {
        return dealt >= HIT - 1.0E-3F;
    }

    private static boolean check(String name, String detail, boolean ok) {
        log(" " + name + " " + detail + (ok ? "" : "  [FAIL]"));
        return ok;
    }

    private static void pass(String name, String detail, boolean ok) {
        allPass &= ok;
        log(" " + name + " " + detail + (ok ? "" : "  [FAIL]"));
        log(" " + name + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void fail(String name, String detail) {
        allPass = false;
        log(" " + name + " " + detail + "  [FAIL]");
        log(" " + name + "-RESULT FAIL");
    }

    private static void log(String message) {
        EnigmaticEnhance.LOGGER.info(TAG + message);
    }
}
