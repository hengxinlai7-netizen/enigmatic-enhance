package com.hengx.enigmaticenhance;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.1.25 自检:【恶魔契约】(-D 开关,由 {@link EarthAmuletSelfTest} 串起来跑)。
 * <p>
 * 关键手法:{@link FakePlayer#isInvulnerableTo} 恒返回 true(Forge 的实现),所以真实 {@code hurt}
 * 对它永远不生效。这里用 <b>子类 {@link TestPlayer} 只覆写这一个方法</b>,于是伤害走的是**真正的**
 * vanilla 管线({@code hurt → actuallyHurt → LivingDamageEvent → setHealth}),
 * 断言的"实际扣血"就是真扣血,而不是只改事件里的数字。
 * <p>
 * 覆盖:
 * <ul>
 * <li>T7 定义:物品 id / 不可堆叠 / 无耐久 / curios:charm 槽 / **没有任何配方产出它**;</li>
 * <li>T8 致命模式(默认):20 血受 5 ⇒ 扣 20 当场死;3 血受 100 ⇒ 扣 3;</li>
 * <li>T9 温和模式(lethal=false):20 血受 5 ⇒ 扣 5(不变);20 血受 100 ⇒ 扣 20(封顶);</li>
 * <li>T10 作用域:只对佩戴者生效(旁边的玩家同样伤害不受影响);脱下后立即恢复正常;</li>
 * <li>T11 debugLog 门控:false 零输出 / true 有输出。</li>
 * </ul>
 */
final class DemonPactSelfTest {

    private static final String TAG = "[DemonPactSelfTest]";
    private static final String PROBE = DemonPactHandler.LOG_TAG;
    private static final int Y = 110;

    private DemonPactSelfTest() {}

    static boolean run(ServerLevel level) {
        boolean t7 = testDefinition(level);
        boolean t8 = testLethalMode(level);
        boolean t9 = testCappedMode(level);
        boolean t10 = testScopeAndUnequip(level);
        boolean t11 = testDebugGate(level);
        boolean all = t7 && t8 && t9 && t10 && t11;
        log(" 恶魔契约小结 T7=" + t7 + " T8=" + t8 + " T9=" + t9 + " T10=" + t10 + " T11=" + t11
            + " => " + (all ? "PASS" : "FAIL"));
        return all;
    }

    // ------------------------------------------------------------------ T7

    private static boolean testDefinition(ServerLevel level) {
        log(" --- T7 恶魔契约定义 / 槽位 / 无配方");
        Item item = ModItems.demonPactOrNull();
        ResourceLocation id = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
        log(" T7 物品 id=" + id + " [应为 enigmaticenhance:demon_pact]");
        boolean ok = "enigmaticenhance:demon_pact".equals(String.valueOf(id));
        if (item == null) {
            check("T7", false);
            return false;
        }
        ItemStack stack = new ItemStack(item);
        boolean charm = false;
        try {
            charm = CuriosApi.getItemStackSlots(stack, level).containsKey(ModIds.CURIO_SLOT_CHARM);
        }
        catch (Throwable t) {
            log(" T7 Curios 槽位查询异常: " + t);
        }
        log(" T7 最大堆叠=" + stack.getMaxStackSize() + " [应 1];最大耐久=" + stack.getMaxDamage()
            + " [应 0];curios:charm=" + charm + " [应 true]");
        ok &= stack.getMaxStackSize() == 1 && stack.getMaxDamage() == 0 && charm;

        // 1.2.2c 起有配方了(注册配方 enigmaticenhance:heart_shaped),断言改成"只有我们自己那一条"
        List<String> sources = new java.util.ArrayList<>();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            try {
                if (recipe.getResultItem(level.registryAccess()).is(item)) {
                    sources.add(recipe.getId().toString());
                }
            }
            catch (Throwable ignored) {
                // 某个配方取产物失败与我们无关
            }
        }
        boolean onlyOurs = sources.size() == 1 && "enigmaticenhance:demon_pact".equals(sources.get(0));
        log(" T7 产出恶魔契约的配方=" + sources + " [应只有 enigmaticenhance:demon_pact]");
        ok &= onlyOurs;
        check("T7", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T8 致命模式

    private static boolean testLethalMode(ServerLevel level) {
        log(" --- T8 致命模式(默认 lethal=true):伤害 = 当前生命值");
        boolean ok = true;
        setLethal(true);

        TestPlayer full = newPlayer(level, "ea_pact_full");
        equip(full);
        ok &= DemonPactData.equippedPact(full).getItem() == ModItems.DEMON_PACT.get();
        boolean hurt = full.hurt(testSource(level), 5.0F);
        log(" T8 20 血受 5 点:扣血后=" + full.getHealth() + " [应 0];hurt 返回=" + hurt + " [应 true]");
        ok &= hurt && full.getHealth() <= 0.0F;

        TestPlayer low = newPlayer(level, "ea_pact_low");
        low.setHealth(3.0F);
        equip(low);
        boolean hurt2 = low.hurt(testSource(level), 100.0F);
        log(" T8 3 血受 100 点:扣血后=" + low.getHealth() + " [应 0];hurt 返回=" + hurt2 + " [应 true]");
        ok &= hurt2 && low.getHealth() <= 0.0F;

        TestPlayer none = newPlayer(level, "ea_pact_none");
        boolean hurt3 = none.hurt(testSource(level), 5.0F);
        log(" T8 对照(未佩戴)20 血受 5 点:扣血后=" + none.getHealth() + " [应 15]");
        ok &= hurt3 && Math.abs(none.getHealth() - 15.0F) < 0.001F;

        full.discard();
        low.discard();
        none.discard();
        check("T8", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T9 温和模式

    private static boolean testCappedMode(ServerLevel level) {
        log(" --- T9 温和模式(lethal=false):只把上限压到当前生命值");
        boolean ok = true;
        setLethal(false);
        try {
            TestPlayer small = newPlayer(level, "ea_pact_cap_small");
            equip(small);
            small.hurt(testSource(level), 5.0F);
            log(" T9 20 血受 5 点(小于当前生命):扣血后=" + small.getHealth() + " [应 15]");
            ok &= Math.abs(small.getHealth() - 15.0F) < 0.001F;

            TestPlayer big = newPlayer(level, "ea_pact_cap_big");
            equip(big);
            big.hurt(testSource(level), 100.0F);
            log(" T9 20 血受 100 点(大于当前生命):扣血后=" + big.getHealth() + " [应 0]");
            ok &= big.getHealth() <= 0.0F;

            small.discard();
            big.discard();
        }
        finally {
            setLethal(true);
        }
        log(" T9 已把 lethal 恢复为 " + RingConfig.demonPactLethal() + " [应 true]");
        ok &= RingConfig.demonPactLethal();
        check("T9", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T10 作用域 / 脱下

    private static boolean testScopeAndUnequip(ServerLevel level) {
        log(" --- T10 只对佩戴者生效 + 脱下后立即恢复");
        boolean ok = true;
        setLethal(true);

        TestPlayer wearer = newPlayer(level, "ea_pact_wearer");
        TestPlayer bystander = newPlayer(level, "ea_pact_bystander");
        equip(wearer);
        wearer.hurt(testSource(level), 5.0F);
        bystander.hurt(testSource(level), 5.0F);
        log(" T10 同一伤害:佩戴者扣血后=" + wearer.getHealth() + " [应 0];旁边玩家扣血后="
            + bystander.getHealth() + " [应 15,不受影响]");
        ok &= wearer.getHealth() <= 0.0F && Math.abs(bystander.getHealth() - 15.0F) < 0.001F;

        TestPlayer removed = newPlayer(level, "ea_pact_removed");
        equip(removed);
        unequip(removed);
        boolean gone = DemonPactData.equippedPact(removed).isEmpty();
        removed.hurt(testSource(level), 5.0F);
        log(" T10 脱下后 equippedPact 为空=" + gone + " [应 true];受 5 点扣血后="
            + removed.getHealth() + " [应 15]");
        ok &= gone && Math.abs(removed.getHealth() - 15.0F) < 0.001F;

        wearer.discard();
        bystander.discard();
        removed.discard();
        check("T10", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T11 日志门控

    private static boolean testDebugGate(ServerLevel level) {
        if (Boolean.getBoolean("enigmaticenhance.selftest.quiet")) {
            log(" T11 SKIP (quiet run)");
            return true;
        }
        log(" --- T11 debugLog 门控(默认 false 零输出)");
        setLethal(true);
        EarthAmuletSelfTest.setDebugLog(false);
        EarthAmuletSelfTest.hold(300L);
        int quietBefore = EarthAmuletSelfTest.logHits(PROBE);
        TestPlayer a = newPlayer(level, "ea_pact_gate_q");
        equip(a);
        a.hurt(testSource(level), 5.0F);
        a.discard();
        EarthAmuletSelfTest.hold(450L);
        int quietAfter = EarthAmuletSelfTest.logHits(PROBE);
        log(" T11 debugLog=false 同一动作新增日志行=" + (quietAfter - quietBefore) + " [应为 0]");
        boolean ok = quietBefore >= 0 && quietAfter >= 0 && quietAfter - quietBefore == 0;

        EarthAmuletSelfTest.setDebugLog(true);
        EarthAmuletSelfTest.hold(300L);
        int loudBefore = EarthAmuletSelfTest.logHits(PROBE);
        TestPlayer b = newPlayer(level, "ea_pact_gate_l");
        equip(b);
        b.hurt(testSource(level), 5.0F);
        b.discard();
        EarthAmuletSelfTest.hold(450L);
        int loudAfter = EarthAmuletSelfTest.logHits(PROBE);
        log(" T11 debugLog=true 同一动作新增日志行=" + (loudAfter - loudBefore) + " [应 >=1]");
        ok &= loudBefore >= 0 && loudAfter >= 0 && loudAfter - loudBefore >= 1;
        check("T11", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 工具

    /** 只把"无敌"这一个方法改掉:其余行为与 Forge 的 FakePlayer 完全一致(连接、能力、属性都在)。 */
    private static final class TestPlayer extends FakePlayer {

        TestPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }
    }

    private static TestPlayer newPlayer(ServerLevel level, String name) {
        TestPlayer player = new TestPlayer(level, new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(8.5D, Y, 8.5D, 0.0F, 0.0F);
        player.getInventory().clearContent();
        player.setHealth(20.0F);
        player.invulnerableTime = 0;
        return player;
    }

    private static boolean equip(Player player) {
        ItemStack pact = new ItemStack(ModItems.DEMON_PACT.get());
        try {
            return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    var charm = handler.getCurios().get(ModIds.CURIO_SLOT_CHARM);
                    if (charm == null) {
                        return false;
                    }
                    charm.getStacks().setStackInSlot(0, pact);
                    return !DemonPactData.equippedPact(player).isEmpty();
                })
                .orElse(false);
        }
        catch (Throwable t) {
            log(" 装备恶魔契约失败: " + t);
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
            // 清理用,忽略
        }
    }

    /** 测试用伤害源:generic_kill(带 bypasses_invulnerability)。
     *  为什么不用普通伤害:FakePlayer 的 spawnInvulnerableTime 在构造器里被写成 60,而它的 tick() 是空实现
     *  永远不递减 ⇒ 60 tick 内 hurt 直接返回 false。用带 bypass 标签的伤害才能真的走到
     *  LivingDamageEvent + setHealth 这条真实扣血管线(顺便证明契约不特判 /kill 类伤害)。 */
    private static DamageSource testSource(ServerLevel level) {
        return level.damageSources().genericKill();
    }

    private static void setLethal(boolean value) {
        for (int i = 0; i < 10; i++) {
            try {
                RingConfig.DEMON_PACT_LETHAL.set(value);
                if (RingConfig.DEMON_PACT_LETHAL.get() == value) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 配置还没就绪
            }
            EarthAmuletSelfTest.hold(150L);
        }
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
