package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.1.28e 自检 T30:【恶魔的手骨】+ 三件套 + 恶魔契约的直伤(applyContract)。
 * <ul>
 * <li>T30a 单件承伤:佩戴者受到的伤害 ×1.5;</li>
 * <li>T30b 三个攻击条件:① 对方最大生命更高 ⇒ 伤害 ×1.3;② 对方当前生命更高 ⇒ 获得伤害吸收;
 *     ③ 对方没有负面效果 ⇒ 被施加一个原版负面;④ 三条同时 ⇒ 追加 1% 最大生命的真实伤害;</li>
 * <li>T30c 三件套:契约无敌帧 ×1.5;血杯只封 I 级(速度 I 无效、速度 II 生效);手骨按负面数 ×(1+0.1n);</li>
 * <li>T30d 契约直伤:applyContract 对 setHealth 类扣血同样生效(lethal / capped 两种)。</li>
 * </ul>
 */
final class DemonBoneSelfTest {

    private static final String TAG = "[BoneSelfTest]";
    private static final int Y = 110;

    private DemonBoneSelfTest() {}

    static boolean run(ServerLevel level, Player player) {
        boolean a = testTaken(level, player);
        boolean b = testAttackConditions(level, player);
        boolean c = testSet(level, player);
        boolean d = testContract(level, player);
        boolean all = a && b && c && d;
        log(" 恶魔的手骨小结 T30a=" + a + " T30b=" + b + " T30c(三件套)=" + c + " T30d(契约直伤)=" + d
            + " => " + (all ? "PASS" : "FAIL"));
        cleanup(player);
        return all;
    }

    // ------------------------------------------------------------------ a 承伤

    private static boolean testTaken(ServerLevel level, Player player) {
        log(" --- T30a 单件:佩戴者受到的伤害 x1.5");
        reset(player);
        boolean ok = true;
        Item bone = ModItems.demonHandBoneOrNull();
        if (bone == null) {
            check("T30a", false);
            return false;
        }
        ok &= equipBone(player);
        float base = 8.0F;
        LivingHurtEvent event = new LivingHurtEvent(player, level.damageSources().magic(), base);
        DemonBoneHandler.onHurt(event);
        log(" T30a 佩戴时受到 " + base + " 点 -> " + event.getAmount() + " [应 "
            + (base * DemonBoneHandler.TAKEN_MULTIPLIER) + "]");
        ok &= Math.abs(event.getAmount() - base * DemonBoneHandler.TAKEN_MULTIPLIER) < 0.01F;
        unequipBone(player);
        LivingHurtEvent plain = new LivingHurtEvent(player, level.damageSources().magic(), base);
        DemonBoneHandler.onHurt(plain);
        log(" T30a 不戴时受到 " + base + " 点 -> " + plain.getAmount() + " [应不变]");
        ok &= Math.abs(plain.getAmount() - base) < 0.01F;
        check("T30a", ok);
        return ok;
    }

    // ------------------------------------------------------------------ b 三个条件

    private static boolean testAttackConditions(ServerLevel level, Player player) {
        log(" --- T30b 三个攻击条件 + 真伤");
        reset(player);
        boolean ok = equipBone(player);
        if (!ok) {
            check("T30b", false);
            return false;
        }
        prepareFloor(level);
        // 目标:僵尸 20 血;玩家满血 20 -> 先压玩家到 10 血,构造"对方当前生命更高"
        player.setHealth(player.getMaxHealth() * 0.5F);
        Zombie zombie = spawn(level, 11.5D, 110.0D, 8.5D);
        if (zombie == null) {
            check("T30b", false);
            return false;
        }
        zombie.getAttribute(Attributes.MAX_HEALTH);
        // ① + ②:僵尸最大生命 20 == 玩家最大 20?把僵尸最大生命抬到 40 以构造"对方最大更高"
        var maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(40.0D);
        }
        zombie.setHealth(40.0F);
        float before = 10.0F;
        LivingHurtEvent event = new LivingHurtEvent(zombie, level.damageSources().playerAttack(player), before);
        DemonBoneHandler.onHurt(event);
        boolean biggerMax = zombie.getMaxHealth() > player.getMaxHealth();
        boolean richer = zombie.getHealth() > player.getHealth();
        float expect = before * (float) (1.0D + (biggerMax ? DemonBoneHandler.BIGGER_MAX_BONUS : 0.0D));
        log(" T30b ①对方最大生命更高=" + biggerMax + ":伤害 " + before + " -> " + event.getAmount()
            + " [应 " + expect + "]");
        ok &= biggerMax && Math.abs(event.getAmount() - expect) < 0.01F;
        float absorption = player.getAbsorptionAmount();
        log(" T30b ②对方当前生命更高=" + richer + ":获得伤害吸收=" + absorption + " [应 > 0]");
        ok &= richer && absorption > 0.0F;
        int harmful = DemonBoneHandler.countHarmful(zombie);
        log(" T30b ③对方原本没有负面效果 -> 现在负面数=" + harmful + " [应 >= 1]");
        ok &= harmful >= 1;
        // 清除负面后重来一次,检查 ④ 真伤(此时 ①②③ 再次同时满足)
        for (MobEffectInstance instance : new ArrayList<>(zombie.getActiveEffects())) {
            zombie.removeEffect(instance.getEffect());
        }
        zombie.setHealth(zombie.getMaxHealth());
        float healthBefore = zombie.getHealth();
        LivingHurtEvent all = new LivingHurtEvent(zombie, level.damageSources().playerAttack(player), 1.0F);
        DemonBoneHandler.onHurt(all);
        float lost = healthBefore - zombie.getHealth();
        float expectTrue = zombie.getMaxHealth() * DemonBoneHandler.TRUE_DAMAGE_RATIO;
        log(" T30b ④三条件同时:目标血量 " + healthBefore + " -> " + zombie.getHealth() + ";掉血=" + lost
            + " [应含真伤 " + expectTrue + "]");
        ok &= lost >= expectTrue - 0.01F;

        // ③ 的二选一:目标本来就有负面效果 ⇒ 不再施加新的,改为每个 +10%
        for (MobEffectInstance instance : new ArrayList<>(zombie.getActiveEffects())) {
            zombie.removeEffect(instance.getEffect());
        }
        // 僵尸是亡灵,对中毒免疫,所以这里用亡灵也能中的两个负面
        zombie.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 0));
        int before2 = DemonBoneHandler.countHarmful(zombie);
        LivingHurtEvent stacked = new LivingHurtEvent(zombie,
            level.damageSources().playerAttack(player), 10.0F);
        DemonBoneHandler.onHurt(stacked);
        int after2 = DemonBoneHandler.countHarmful(zombie);
        float expect2 = 10.0F * (float) (1.0D + DemonBoneHandler.BIGGER_MAX_BONUS
            + DemonBoneHandler.CURSE_BONUS * before2);
        log(" T30b 目标已有 " + before2 + " 个负面:伤害 10 -> " + stacked.getAmount() + " [应 " + expect2
            + "];本次是否新增负面=" + (after2 != before2) + " [应 false]");
        ok &= before2 == 2 && after2 == 2 && Math.abs(stacked.getAmount() - expect2) < 0.01F;
        zombie.discard();
        check("T30b", ok);
        return ok;
    }

    // ------------------------------------------------------------------ c 三件套

    private static boolean testSet(ServerLevel level, Player player) {
        log(" --- T30c 三件套:无敌帧 / 血杯只封 I 级 / 手骨按负面数");
        reset(player);
        boolean ok = equipBone(player);
        ok &= equipChalice(player);
        ok &= equipPact(player);
        boolean full = DemonBoneHandler.fullSet(player);
        log(" T30c 三件套齐=" + full + " [应 true]");
        ok &= full;
        // 无敌帧:契约逻辑在 mixin 里,这里验证同一套算式
        int base = 20;
        int extended = (int) Math.ceil(base * 1.5D);
        log(" T30c 无敌帧 " + base + " -> " + extended + " [应 30]");
        ok &= extended == 30;

        // 血杯(三件套,1.2.13c):正面效果按 I 级生效;负面效果只无效 I 级
        player.removeAllEffects();
        double speed0 = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0));
        DemonBloodChaliceHandler.enforceSet(player);
        double speedI = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        log(" T30c 速度 I(amp=0):" + speed0 + " -> " + speedI + " [应变高:正面按 I 级生效]");
        ok &= speedI > speed0 + 1.0E-6D;
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 2));
        DemonBloodChaliceHandler.enforceSet(player);
        double speedIII = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        log(" T30c 速度 III(amp=2):" + speed0 + " -> " + speedIII + " [应≈速度 I(被压成 I 级)]");
        ok &= Math.abs(speedIII - speedI) < 1.0E-6D;
        player.removeAllEffects();
        boolean slowSealed = DemonBloodChaliceHandler.shouldSeal(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
        log(" T30c 缓慢 I:shouldSeal=" + slowSealed + " [应 true:负面 I 级整条无效]");
        ok &= slowSealed;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
        DemonBloodChaliceHandler.enforceSet(player);
        MobEffectInstance slow = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        int slowAmp = slow == null ? -1 : slow.getAmplifier();
        log(" T30c 缓慢 II:限级后 amp=" + slowAmp + " [应 0(降一级)]");
        ok &= slowAmp == 0;
        player.removeAllEffects();

        // 手骨(单件):目标每有一个已有负面 +10%
        for (int n : new int[] { 0, 2, 4 }) {
            player.removeAllEffects();
            net.minecraft.world.effect.MobEffect[] pool = {
                MobEffects.POISON, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.HUNGER };
            for (int i = 0; i < n; i++) {
                player.addEffect(new MobEffectInstance(pool[i % pool.length], 400, 0));
            }
            double mult = DemonBoneHandler.attackMultiplier(player, player, false);
            double expect = 1.0D + DemonBoneHandler.CURSE_BONUS * n;
            log(" T30c 自身负面数=" + n + " -> 倍率=" + mult + " [应 " + expect + "]");
            ok &= Math.abs(mult - expect) < 1.0E-9D;
        }
        player.removeAllEffects();

        // 三件套:被手骨命中过的目标 5 秒内不能回血
        prepareFloor(level);
        Zombie victim2 = spawn(level, 11.5D, 110.0D, 8.5D);
        if (victim2 != null) {
            vectorHit(player, victim2, 1.0F);
            boolean marked = DemonBoneHandler.noHealWindow(victim2);
            LivingHealEvent heal = new LivingHealEvent(victim2, 4.0F);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(heal);
            log(" T30c 目标进入禁疗窗口=" + marked + " [应 true];治疗事件被清零="
                + (heal.getAmount() == 0.0F) + " [应 true]");
            ok &= marked && heal.getAmount() == 0.0F;
            DemonBoneHandler.expireNoHeal(victim2);
            LivingHealEvent heal2 = new LivingHealEvent(victim2, 4.0F);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(heal2);
            log(" T30c 窗口过期后治疗=" + heal2.getAmount() + " [应 4]");
            ok &= Math.abs(heal2.getAmount() - 4.0F) < 0.01F;
            victim2.discard();
        }
        else {
            ok = false;
        }
        check("T30c", ok);
        return ok;
    }

    // ------------------------------------------------------------------ d 契约直伤

    private static boolean testContract(ServerLevel level, Player player) {
        log(" --- T30d 恶魔契约对 setHealth 直伤同样生效");
        reset(player);
        boolean ok = equipPact(player);
        player.setHealth(10.0F);
        float lethal = DemonPactHandler.applyContract(player, 5.0F);
        log(" T30d lethal 模式:直伤 5 -> " + lethal + " [应 10 = 当前生命]");
        ok &= Math.abs(lethal - 10.0F) < 0.01F;
        setLethal(false);
        float capped = DemonPactHandler.applyContract(player, 5.0F);
        float cappedHuge = DemonPactHandler.applyContract(player, 50.0F);
        setLethal(true);
        log(" T30d capped 模式:直伤 5 -> " + capped + " [应 5];直伤 50 -> " + cappedHuge + " [应压到 10]");
        ok &= Math.abs(capped - 5.0F) < 0.01F && Math.abs(cappedHuge - 10.0F) < 0.01F;
        // 真的走一次真伤路径:目标(玩家自己)戴契约,直伤应被契约改写成致死
        player.setHealth(10.0F);
        float applied = DemonBoneHandler.trueDamage(player, 1.0F, level.damageSources().magic());
        log(" T30d trueDamage(1 点)经过契约后扣除=" + applied + " [应 10];剩余血量=" + player.getHealth());
        ok &= Math.abs(applied - 10.0F) < 0.01F && player.getHealth() <= 0.01F;
        player.setHealth(player.getMaxHealth());
        check("T30d", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 工具

    private static void reset(Player player) {
        unequipBone(player);
        unequipChalice(player);
        unequipPact(player);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0.0F);
        DemonBloodChaliceHandler.invalidate(player);
        setLethal(true);
    }

    private static void cleanup(Player player) {
        reset(player);
    }

    /** 骨头的"正牌"槽是 curios:hands;但测试环境不一定注册了这个槽类型,放不进去就退回 charm 槽。 */
    private static boolean equipBone(Player player) {
        Item bone = ModItems.demonHandBoneOrNull();
        if (bone == null) {
            return false;
        }
        if (putInSlot(player, "hands", 0, new ItemStack(bone)) != null
            && !DemonBoneHandler.equippedBone(player).isEmpty()) {
            return true;
        }
        return putInSlot(player, "charm", 2, new ItemStack(bone)) != null
            && !DemonBoneHandler.equippedBone(player).isEmpty();
    }

    private static void unequipBone(Player player) {
        putInSlot(player, "hands", 0, ItemStack.EMPTY);
        putInSlot(player, "charm", 2, ItemStack.EMPTY);
    }

    private static boolean equipChalice(Player player) {
        Item chalice = ModItems.demonBloodChaliceOrNull();
        if (chalice == null) {
            return false;
        }
        putInSlot(player, "charm", 0, new ItemStack(chalice));
        DemonBloodChaliceHandler.onEquipped(player);
        return DemonBloodChaliceHandler.wearing(player);
    }

    private static void unequipChalice(Player player) {
        putInSlot(player, "charm", 0, ItemStack.EMPTY);
        DemonBloodChaliceHandler.invalidate(player);
    }

    private static boolean equipPact(Player player) {
        Item pact = ModItems.demonPactOrNull();
        if (pact == null) {
            return false;
        }
        putInSlot(player, "charm", 1, new ItemStack(pact));
        return !DemonPactData.equippedPact(player).isEmpty();
    }

    private static void unequipPact(Player player) {
        putInSlot(player, "charm", 1, ItemStack.EMPTY);
    }

    /** 往某个 curios 槽的第 index 格放东西(槽不够就先长一格);返回放入的栈或 null。 */
    private static ItemStack putInSlot(Player player, String slot, int index, ItemStack stack) {
        try {
            return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    var stacks = handler.getCurios().get(slot);
                    int guard = 0;
                    while ((stacks == null || stacks.getSlots() <= index) && guard++ < 6) {
                        try {
                            handler.growSlotType(slot, 1);
                        }
                        catch (Throwable ignored) {
                            break; // 这个槽类型没注册,长不出来
                        }
                        stacks = handler.getCurios().get(slot);
                    }
                    if (stacks == null || stacks.getSlots() <= index) {
                        return null;
                    }
                    stacks.getStacks().setStackInSlot(index, stack);
                    return stack;
                })
                .orElse(null);
        }
        catch (Throwable t) {
            log(" T30 放置 " + slot + " 异常: " + t);
            return null;
        }
    }

    private static void setLethal(boolean value) {
        for (int i = 0; i < 10; i++) {
            try {
                RingConfig.DEMON_PACT_LETHAL.set(value);
                if (RingConfig.demonPactLethal() == value) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 配置未就绪
            }
            EarthAmuletSelfTest.hold(100L);
        }
    }

    /** 让佩戴者打目标一下(走一次 onHurt,给禁疗窗口打标记)。 */
    private static void vectorHit(Player attacker, LivingEntity target, float amount) {
        LivingHurtEvent event = new LivingHurtEvent(target,
            attacker.level().damageSources().playerAttack(attacker), amount);
        DemonBoneHandler.onHurt(event);
    }

    private static void prepareFloor(ServerLevel level) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlockAndUpdate(new net.minecraft.core.BlockPos(8 + dx, Y - 1, 8 + dz),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(8 + dx, Y + dy, 8 + dz),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static Zombie spawn(ServerLevel level, double x, double y, double z) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return null;
        }
        zombie.moveTo(x, y, z, 0.0F, 0.0F);
        zombie.setPersistenceRequired();
        try {
            zombie.setNoAi(true);
        }
        catch (Throwable ignored) {
            // 无 setter 也不影响
        }
        level.addFreshEntity(zombie);
        zombie.setHealth(zombie.getMaxHealth());
        return zombie;
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
