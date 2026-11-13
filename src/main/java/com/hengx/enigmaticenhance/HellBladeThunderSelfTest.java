package com.hengx.enigmaticenhance;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 4.8.2 的两个修复的自动化模拟自检(在真实 Forge 服务端里跑,-Denigmaticenhance.selftest=hellblade)。
 *
 * <h2>A. 地狱刃片唯一性(直接掷真实的 {@code minecraft:chests/nether_bridge})</h2>
 * <ul>
 *   <li>A0 表里确实有我们注入的 {@code enigmaticenhance_hell_blade} 池;</li>
 *   <li>A1 全新玩家连掷 20 次 → 刃片最多 1 枚;</li>
 *   <li>A2 已有 {@code LootedHellCharm} 标记的玩家连掷 20 次 → 0 枚;</li>
 *   <li>A3 身上已有刃片但没有标记(模拟 4.8.1 老存档)→ 不产出,并且补上标记;</li>
 *   <li>A4 标记位置正确: {@code getPersistentData().PlayerPersisted.LootedHellCharm == true},
 *       并且扩展自己的 {@code SuperpositionHandler.hasPersistentTag} 也认可(两边共用同一条唯一性)。</li>
 * </ul>
 *
 * <h2>B. 万钧之护雷击开关</h2>
 * <ul>
 *   <li>B1 开关开 → 真实调扩展的 {@code onEntityHurt} 后 Electric 增长;再调 {@code onTick} →
 *       真的落雷(EntityJoinLevelEvent 里数到 LightningBolt);</li>
 *   <li>B2 开关关 → 先把目标 Electric 设成 100000(残留),再调 {@code onTick} ×5 → 落雷 0 次;
 *       再跑一次 {@link ThunderChargeGuard} → 残留电荷被清 0;再命中一次 → Electric 不增长;</li>
 *   <li>B3 开关开回来 → 再落雷。</li>
 * </ul>
 *
 * <p>说明:FakePlayer 不会进实体表,而扩展的落雷需要"目标 2.2 格内有戴卷轴的玩家",所以这里把
 * FakePlayer 真正 {@code addFreshEntity} 进世界(拿不到就把这条判成失败并打印原因,不编结果)。</p>
 */
final class HellBladeThunderSelfTest {

    private static final String TAG = "[HellBladeThunderSelfTest]";

    private static final ResourceLocation NETHER_BRIDGE = new ResourceLocation("minecraft", "chests/nether_bridge");
    private static final ResourceLocation BLADE_ID = new ResourceLocation("enigmaticaddons", "hell_blade_charm");
    private static final ResourceLocation SCROLL_ID = new ResourceLocation("enigmaticaddons", "thunder_scroll");

    /** 我们注入的池名。 */
    private static final String POOL = "enigmaticenhance_hell_blade";
    /** 神秘遗物/扩展的玩家持久标记位置。 */
    private static final String PERSISTED_ROOT = "PlayerPersisted";
    private static final String ADDON_MARK = "LootedHellCharm";

    private static boolean allPass = true;

    /** 落雷计数(只在 captureBolts 打开时统计)。 */
    private static boolean captureBolts = false;
    private static int boltSpawns = 0;

    private HellBladeThunderSelfTest() {}

    static void register() {
        MinecraftForge.EVENT_BUS.register(HellBladeThunderSelfTest.class);
        DebugLog.info(TAG + " registered");
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!captureBolts || event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        if (event.getEntity() instanceof LightningBolt) {
            ++boltSpawns;
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
            restoreSwitches();
            System.out.println(TAG + " ALL-RESULT " + (allPass ? "PASS" : "FAIL"));
            server.execute(() -> server.getCommands()
                .performPrefixedCommand(server.createCommandSourceStack(), "stop"));
        }
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getChunk(0, 0);
        setBool(RingConfig.DEBUG_LOG, true);   // 让模组自己的判定日志也打出来
        try {
            boolean a = runHellBlade(server, level);
            boolean b = runThunder(level);
            allPass = allPass && a && b;
            log(" 小结 A(刃片唯一性)=" + a + " B(雷击开关)=" + b + " => " + (a && b ? "PASS" : "FAIL"));
        }
        finally {
            setBool(RingConfig.DEBUG_LOG, false);
        }
    }

    // ================================================================ A:刃片唯一性

    private static boolean runHellBlade(MinecraftServer server, ServerLevel level) {
        Item blade = ForgeRegistries.ITEMS.getValue(BLADE_ID);
        if (blade == null) {
            log(" A 无法执行:物品 " + BLADE_ID + " 不存在(没装神秘遗物扩展?)");
            return false;
        }
        LootTable table = server.getLootData().getLootTable(NETHER_BRIDGE);
        boolean hasTable = table != LootTable.EMPTY;
        boolean hasPool = hasTable && table.getPool(POOL) != null;
        log(" A0 表存在=" + hasTable + " 我们的刃片池存在=" + hasPool + " [都应 true]");

        // A1 全新玩家 ×20
        FakePlayer p1 = newPlayer(level, "hb_a1");
        int c1 = roll(table, level, p1, blade, 20);
        boolean a1 = c1 <= 1;
        log(" A1 全新玩家连掷 20 次 → 刃片 " + c1 + " 枚 [应 ≤1];标记=" + hasFlag(p1)
            + " => " + (a1 ? "PASS" : "FAIL"));

        // A2 已有标记 ×20
        FakePlayer p2 = newPlayer(level, "hb_a2");
        writeFlag(p2, true);
        int c2 = roll(table, level, p2, blade, 20);
        boolean a2 = c2 == 0;
        log(" A2 已有 LootedHellCharm 标记的玩家连掷 20 次 → 刃片 " + c2 + " 枚 [应 0] => "
            + (a2 ? "PASS" : "FAIL"));

        // A3 身上有刃片但没有标记
        FakePlayer p3 = newPlayer(level, "hb_a3");
        p3.getInventory().add(new ItemStack(blade));
        boolean owns = Superposition.hasItem(p3, blade);
        int c3 = roll(table, level, p3, blade, 1);
        // 注:池子还带着 15% 的概率条件,单次掷很可能根本没走到唯一性闸门,
        // 所以"补打标记"这一步用真正的兜底机制验证:每 32 tick 的对账扫描。
        boolean scanFlag = false;
        try {
            p3.tickCount = 32;
            HellBladeLootHandler.onPlayerTick(
                new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                    net.minecraftforge.event.TickEvent.Phase.END, p3));
            scanFlag = hasFlag(p3);
        }
        catch (Throwable t) {
            log(" A3 驱动对账扫描异常: " + t);
        }
        boolean a3 = c3 == 0 && scanFlag;
        log(" A3 身上已有刃片但无标记(ownsBlade=" + owns + "),掷 1 次 → 产出 " + c3
            + " 枚 [应 0];对账扫描后标记=" + scanFlag + " [应 true] => "
            + (a3 ? "PASS" : "FAIL"));

        // A4 标记位置 + 扩展侧认可
        int extra = 0;
        int guard = 0;
        while (!hasFlag(p1) && c1 == 0 && guard++ < 200) {
            extra += roll(table, level, p1, blade, 1);
        }
        boolean flagNow = hasFlag(p1);
        boolean addonAgrees = addonHasTag(p1);
        boolean a4 = flagNow && addonAgrees;
        log(" A4 A1 玩家的标记: PlayerPersisted.LootedHellCharm=" + flagNow
            + ";扩展 hasPersistentTag=" + addonAgrees
            + (extra > 0 || guard > 0 ? "(前 20 次没出,补掷 " + guard + " 次/产出 " + extra + " 枚才打上标记)" : "")
            + " [都应 true] => " + (a4 ? "PASS" : "FAIL"));

        // 收尾:把 A3 的刃片拿走,免得影响 B
        p3.getInventory().clearContent();
        return hasTable && hasPool && a1 && a2 && a3 && a4;
    }

    /** 掷 {@code times} 次真实的下界要塞表,数掉出来的刃片。 */
    private static int roll(LootTable table, ServerLevel level, FakePlayer player, Item blade, int times) {
        int found = 0;
        for (int i = 0; i < times; i++) {
            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(new BlockPos(0, 64, 0)))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(0.0F)
                .create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params, level.random.nextLong())) {
                if (stack != null && !stack.isEmpty() && stack.is(blade)) {
                    ++found;
                }
            }
        }
        return found;
    }

    // ================================================================ B:雷击开关

    private static boolean runThunder(ServerLevel level) {
        Item scroll = ForgeRegistries.ITEMS.getValue(SCROLL_ID);
        if (scroll == null) {
            log(" B 无法执行:物品 " + SCROLL_ID + " 不存在");
            return false;
        }
        FakePlayer player = newPlayer(level, "thunder_b");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        String slot = equipScrollAnywhere(player, new ItemStack(scroll));
        boolean equipped = slot != null;
        boolean inWorld = false;
        try {
            inWorld = level.addFreshEntity(player);
        }
        catch (Throwable t) {
            log(" B0 把测试玩家放进世界失败: " + t);
        }
        log(" B0 佩戴卷轴=" + equipped + " [应 true];测试玩家进入世界=" + inWorld
            + "(扩展的落雷要求目标 2.2 格内有戴卷轴的玩家)");

        Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
        zombie.moveTo(player.getX() + 1.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(zombie);

        Object addon = null;
        Method onTick = null;
        Method onHurt = null;
        try {
            Class<?> type = Class.forName("auviotre.enigmatic.addon.handlers.AddonEventHandler");
            addon = type.getDeclaredConstructor().newInstance();
            onTick = type.getMethod("onTick", LivingEvent.LivingTickEvent.class);
            onHurt = type.getMethod("onEntityHurt", LivingHurtEvent.class);
            log(" B0 反射到扩展处理器: onTick=" + (onTick != null) + " onEntityHurt=" + (onHurt != null));
        }
        catch (Throwable t) {
            log(" B0 无法反射扩展处理器: " + t);
        }

        // ---- B1 开关开
        setSwitches(true);
        zombie.getPersistentData().putInt("Electric", 0);
        // 扩展的落雷阈值很高(实测 100000 电荷一次 onTick 只掉约 19569),所以这里真的连打,
        // 让电荷自然涨过阈值 —— 这才是"开关开着会落雷"的真实路径。
        int hits = 0;
        int e1 = 0;
        while (hits < 400 && e1 <= 30000) {
            e1 = hurtOnce(zombie, player, level, addon, onHurt);
            ++hits;
        }
        // 扩展的落雷条件正好相反:目标 2.2 格内**不能**有戴卷轴的玩家(贴身输出时不落雷)。
        // 所以攒够电荷后把玩家挪远,再走 onTick 看是否真的落雷。
        player.moveTo(zombie.getX() + 12.0D, zombie.getY(), zombie.getZ(), 0.0F, 0.0F);
        int bolts1 = tickAndCount(zombie, addon, onTick);
        boolean b1 = e1 > 0 && bolts1 > 0;
        log(" B1 开关=开 → 连打 " + hits + " 次后 Electric=" + e1 + " [应 >0];玩家退到 12 格外后 onTick → 落雷="
            + bolts1 + " 次 [应 >0] => " + (b1 ? "PASS" : "FAIL"));

        // ---- B2 开关关
        setSwitches(false);
        zombie.getPersistentData().putInt("Electric", 100000);
        int bolts2 = tickAndCount(zombie, addon, onTick);
        int chargeLeft = zombie.getPersistentData().getInt("Electric");
        zombie.tickCount = 20; // 让 ThunderChargeGuard 的 %20 命中
        try {
            ThunderChargeGuard.onLivingTick(new LivingEvent.LivingTickEvent(zombie));
        }
        catch (Throwable t) {
            log(" B2 跑 ThunderChargeGuard 异常: " + t);
        }
        int chargeCleared = zombie.getPersistentData().getInt("Electric");
        int e2 = hurtAndRead(zombie, player, level, addon, onHurt);
        boolean b2 = bolts2 == 0 && chargeCleared == 0 && e2 == 0;
        log(" B2 开关=关 → 残留 Electricity=100000 时落雷=" + bolts2 + " 次 [应 0];"
            + "清理前电荷=" + chargeLeft + ";清理后电荷=" + chargeCleared + " [应 0];"
            + "再命中一次 Electric=" + e2 + " [应 0] => " + (b2 ? "PASS" : "FAIL"));

        // ---- B3 开关开回来
        setSwitches(true);
        zombie.getPersistentData().putInt("Electric", 100000);
        int bolts3 = tickAndCount(zombie, addon, onTick);
        boolean b3 = bolts3 > 0;
        log(" B3 开关=开(恢复) → 落雷=" + bolts3 + " 次 [应 >0] => " + (b3 ? "PASS" : "FAIL"));

        // 收尾
        zombie.getPersistentData().remove("Electric");
        zombie.discard();
        if (inWorld) {
            player.discard();
        }
        return b1 && b2 && b3;
    }

    /** 真正调一次扩展的 onEntityHurt(我们的 mixin 就挂在这上面),返回目标身上累积后的 Electric。 */
    private static int hurtOnce(Zombie zombie, FakePlayer player, ServerLevel level,
        Object addon, Method onHurt) {
        if (onHurt == null || addon == null) {
            return -1;
        }
        try {
            onHurt.invoke(addon, new LivingHurtEvent(zombie,
                level.damageSources().playerAttack(player), 10.0F));
        }
        catch (Throwable t) {
            log(" B 调 onEntityHurt 异常: " + t);
        }
        return zombie.getPersistentData().getInt("Electric");
    }

    /** 清零后再打一次,返回这一击之后的 Electric(用于验证"关掉开关时不再累积")。 */
    private static int hurtAndRead(Zombie zombie, FakePlayer player, ServerLevel level,
        Object addon, Method onHurt) {
        zombie.getPersistentData().putInt("Electric", 0);
        return hurtOnce(zombie, player, level, addon, onHurt);
    }

    /** 调 5 次扩展的 onTick,返回这段时间里真正生成的雷电数量。 */
    private static int tickAndCount(Zombie zombie, Object addon, Method onTick) {
        boltSpawns = 0;
        captureBolts = true;
        try {
            if (onTick != null && addon != null) {
                for (int i = 0; i < 5; i++) {
                    onTick.invoke(addon, new LivingEvent.LivingTickEvent(zombie));
                }
            }
        }
        catch (Throwable t) {
            log(" B 调 onTick 异常: " + t);
        }
        finally {
            captureBolts = false;
        }
        return boltSpawns;
    }

    // ================================================================ 工具

    private static FakePlayer newPlayer(ServerLevel level, String name) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(8.5D, 100.0D, 8.5D, 0.0F, 0.0F);
        player.getInventory().clearContent();
        return player;
    }

    /** 读 {@code PlayerPersisted.LootedHellCharm}(与扩展同一个位置)。 */
    private static boolean hasFlag(FakePlayer player) {
        try {
            CompoundTag root = player.getPersistentData();
            return root.contains(PERSISTED_ROOT, Tag.TAG_COMPOUND)
                && root.getCompound(PERSISTED_ROOT).getBoolean(ADDON_MARK);
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static void writeFlag(FakePlayer player, boolean on) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.contains(PERSISTED_ROOT, Tag.TAG_COMPOUND)
            ? root.getCompound(PERSISTED_ROOT)
            : new CompoundTag();
        if (on) {
            persisted.putBoolean(ADDON_MARK, true);
        }
        else {
            persisted.remove(ADDON_MARK);
        }
        root.put(PERSISTED_ROOT, persisted);
    }

    /** 问扩展自己认不认这个标记(两边共用同一条唯一性的关键证据)。 */
    private static boolean addonHasTag(FakePlayer player) {
        try {
            Class<?> type = Class.forName("com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler");
            Method method = type.getMethod("hasPersistentTag",
                net.minecraft.world.entity.player.Player.class, String.class);
            return Boolean.TRUE.equals(method.invoke(null, player, ADDON_MARK));
        }
        catch (Throwable t) {
            log(" A4 调扩展 hasPersistentTag 失败: " + t);
            return false;
        }
    }

    /**
     * 把卷轴塞进<b>任意一个有格子的</b>饰品槽并确认"饰品栏里真的有它"。
     *
     * <p>说明:本隔离环境里玩家没有解锁 {@code scroll} 槽(格数 0 —— 要靠千咒卷轴/契约卷轴那类东西
     * 才开放),而扩展累积电荷只看 {@code SuperpositionHandler.hasCurio(player, THUNDER_SCROLL)}
     * (遍历所有槽位),所以放进 charm/hands 等任意槽位对被测逻辑是完全等价的。</p>
     */
    private static String equipScrollAnywhere(FakePlayer player, ItemStack stack) {
        var lazy = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
        if (lazy == null || !lazy.isPresent()) {
            log(" Curios 拿不到玩家饰品栏(FakePlayer?)");
            return null;
        }
        var handler = lazy.orElse(null);
        if (handler == null) {
            log(" Curios 饰品栏为空");
            return null;
        }
        StringBuilder sizes = new StringBuilder();
        for (String slot : new String[] { "charm", "hands", "ring", "head", "back", "spellstone", "scroll" }) {
            var handlerStacks = handler.getCurios().get(slot);
            int slots = handlerStacks == null ? -1 : handlerStacks.getStacks().getSlots();
            sizes.append(slot).append('=').append(slots).append(' ');
            if (slots < 1) {
                continue;
            }
            try {
                handlerStacks.getStacks().setStackInSlot(0, stack.copy());
                if (Superposition.hasCurio(player, stack.getItem())) {
                    log(" 饰品槽容量: " + sizes);
                    log(" 卷轴已放进 " + slot + " 槽第 0 格(hasCurio=true)");
                    return slot;
                }
            }
            catch (Throwable t) {
                log(" 放 " + slot + " 槽异常: " + t);
            }
        }
        log(" 饰品槽容量: " + sizes + " → 没有任何槽位能放下卷轴");
        return null;
    }

    private static void setSwitches(boolean on) {
        setBool(RingConfig.SCALE_ENABLED, on);
        setBool(RingConfig.SCALE_LIGHTNING_ENABLED, on);
        try {
            RingConfig.SPEC.save();
        }
        catch (Throwable ignored) {
            // 忽略
        }
        log(" 开关设置: scaleOfProtectionEnabled=" + RingConfig.scaleEnabled()
            + " scaleOfProtectionLightningEnabled=" + RingConfig.scaleLightningEnabled());
    }

    private static void restoreSwitches() {
        setBool(RingConfig.SCALE_ENABLED, true);
        setBool(RingConfig.SCALE_LIGHTNING_ENABLED, true);
        try {
            RingConfig.SPEC.save();
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }

    private static void setBool(ForgeConfigSpec.BooleanValue value, boolean v) {
        try {
            value.set(v);
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }

    private static void log(String message) {
        // 直接 println:自检的每一行都必须出现在日志里,不能受 debugLog 配置影响
        System.out.println(TAG + message);
    }
}
