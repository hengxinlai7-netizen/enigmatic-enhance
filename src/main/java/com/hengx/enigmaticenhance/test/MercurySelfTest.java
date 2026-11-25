package com.hengx.enigmaticenhance.test;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.MercurySerpentHandler;
import com.hengx.enigmaticenhance.ModEffects;

/**
 * 4.11.0 水银之蛇自检({@code -Denigmaticenhance.selftest=mercury}).
 *
 * <p>覆盖本次改动的全部要点:</p>
 * <ol>
 *   <li>触发不再改 buff 等级;</li>
 *   <li>触发后 charges 增加、mercury_triggers_left 减 1;</li>
 *   <li>次数归零 → 效果被移除,层数保留;</li>
 *   <li>charges&gt;0 且 buff 不在 → protectedFromDeath 仍为 true;</li>
 *   <li>buff 不在但 charges&gt;0 时 onHurt 仍把伤害清零。</li>
 * </ol>
 */
public final class MercurySelfTest {

    private MercurySelfTest() {}

    public static void register() {
        String selftest = System.getProperty("enigmaticenhance.selftest");
        if (selftest == null || !selftest.contains("mercury")) {
            return;
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(MercurySelfTest.class);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        int pass = 0;
        int fail = 0;
        ServerLevel level = event.getServer().overworld();
        MobEffect effect = ModEffects.mercurySerpentOrNull();
        if (effect == null) {
            log("SKIP: 水银之蛇效果未注册");
            return;
        }

        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            log("SKIP: 无法创建测试僵尸");
            return;
        }
        zombie.setPos(0, 100, 0);
        MercurySerpentHandler.forceApply(zombie, effect, 20 * 60, 1);   // 2 级
        int ampBefore = zombie.getEffect(effect) == null ? -1 : zombie.getEffect(effect).getAmplifier();

        // ---- T1/T2: 首次触发 ----
        zombie.setHealth(zombie.getMaxHealth() * 0.4F);
        MercurySerpentHandler.tryTrigger(zombie);
        MobEffectInstance after = zombie.getEffect(effect);
        int ampAfter = after == null ? -1 : after.getAmplifier();
        int charges1 = MercurySerpentHandler.charges(zombie);
        int left1 = MercurySerpentHandler.triggersLeft(zombie);

        boolean t1 = ampBefore == 1 && ampAfter == 1;
        pass += t1 ? 1 : 0; fail += t1 ? 0 : 1;
        log("T1 触发不改 buff 等级(amp " + ampBefore + " -> " + ampAfter + "): " + (t1 ? "PASS" : "FAIL"));

        boolean t2 = charges1 == 2 && left1 == 1;
        pass += t2 ? 1 : 0; fail += t2 ? 0 : 1;
        log("T2 首次触发 charges=" + charges1 + "(期望2) triggers_left=" + left1 + "(期望1): " + (t2 ? "PASS" : "FAIL"));

        // ---- T3: 第二次触发 → 次数归零,效果移除,层数保留 ----
        zombie.setHealth(zombie.getMaxHealth() * 0.4F);
        MercurySerpentHandler.tryTrigger(zombie);
        int charges2 = MercurySerpentHandler.charges(zombie);
        int left2 = MercurySerpentHandler.triggersLeft(zombie);
        boolean hasAfter = MercurySerpentHandler.has(zombie);
        boolean t3 = charges2 == 3 && left2 == 0 && !hasAfter;
        pass += t3 ? 1 : 0; fail += t3 ? 0 : 1;
        log("T3 次数耗尽: charges=" + charges2 + "(期望3) triggers_left=" + left2
            + "(期望0) has=" + hasAfter + "(期望false): " + (t3 ? "PASS" : "FAIL"));

        // ---- T4: buff 不在但层数 > 0 → 仍受死亡保护 ----
        boolean t4 = MercurySerpentHandler.protectedFromDeath(zombie);
        pass += t4 ? 1 : 0; fail += t4 ? 0 : 1;
        log("T4 buff 不在 + charges>0 → protectedFromDeath=" + t4 + ": " + (t4 ? "PASS" : "FAIL"));

        // ---- T5: buff 不在但层数 > 0 → onHurt 清零 ----
        Player dummy = null;
        LivingHurtEvent hurt = new LivingHurtEvent(zombie,
            level.damageSources().magic(), 100.0F);
        MercurySerpentHandler.onHurt(hurt);
        boolean t5 = hurt.isCanceled() && hurt.getAmount() == 0.0F;
        pass += t5 ? 1 : 0; fail += t5 ? 0 : 1;
        log("T5 buff 不在但 charges>0 → onHurt 清零(canceled=" + hurt.isCanceled()
            + " amount=" + hurt.getAmount() + "): " + (t5 ? "PASS" : "FAIL"));

        // ---- T6: charges 归零后不再受保护 ----
        zombie.getPersistentData().putInt("mercury_charges", 0);
        boolean t6 = !MercurySerpentHandler.protectedFromDeath(zombie) && !MercurySerpentHandler.has(zombie);
        pass += t6 ? 1 : 0; fail += t6 ? 0 : 1;
        log("T6 层数归零 → 不再受保护: " + (t6 ? "PASS" : "FAIL"));

        zombie.discard();
        log("RESULT PASSED=" + pass + " FAILED=" + fail);
    }

    private static void log(String message) {
        EnigmaticEnhance.LOGGER.info("[水银之蛇自检] {}", message);
    }
}
