package com.hengx.enigmaticenhance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * 「恶魔的手骨」(1.1.28e,{@code enigmaticenhance:demon_hand_bone},{@code curios:hands})的全部运行时行为。
 *
 * <h2>单件(数值细节见 1.1.28e 说明.txt)</h2>
 * <ul>
 * <li>佩戴者<b>受到的伤害</b> ×1.5({@link LivingHurtEvent},受击者是佩戴者);</li>
 * <li>佩戴者<b>造成伤害</b>时,三个独立条件(可同时成立):
 *     ① 对方最大生命 &gt; 自身最大生命 ⇒ 本次伤害 ×(1+0.3);
 *     ② 对方当前生命 &gt; 自身当前生命 ⇒ 获得本次伤害 10% 的伤害吸收(200 tick,取最大值刷新,见
 *        {@link #absorbed} / {@link #ABSORB_EXPIRY});</li>
 * <li>③ 对方身上没有任何负面效果 ⇒ 随机施加一个原版负面效果(I 级、5~10 秒);
 *     三条件同时成立时,额外对目标造成其最大生命 1% 的**真实伤害**(直写血量,见
 *     {@link #trueDamage},并经过 {@link DemonPactHandler#applyContract} 的契约修正);</li>
 * <li><b>三件套</b>(契约 + 血杯 + 手骨同时在 curios 里)时:手骨的伤害再按目标身上的负面效果数量
 *     ×(1+0.1×数量),与上面的 ×1.3 **相加**成同一个乘区(公式见 {@link #attackMultiplier});</li>
 * <li>佩戴时客户端低频冒黑烟(见 {@code client.DemonBoneParticles})。</li>
 * </ul>
 * 判定每次即时计算,不缓存任何状态;日志全部走 {@link DebugLog}(默认零输出)。
 */
public final class DemonBoneHandler {

    public static final String LOG_TAG = "[DemonBone]";

    /** 受击额外承伤倍率。 */
    public static final float TAKEN_MULTIPLIER = 1.5F;
    /** 条件①的增伤。 */
    public static final double BIGGER_MAX_BONUS = 0.30D;
    /** 条件②的伤害吸收比例与持续时间。 */
    public static final float ABSORB_RATIO = 0.10F;
    public static final int ABSORB_DURATION = 200;
    /** 条件③的负面效果时长范围(tick)。 */
    public static final int DEBUFF_MIN = 100;
    public static final int DEBUFF_MAX = 200;
    /** 单件:目标身上每有一个已存在的负面效果,本次伤害 +10%。 */
    public static final double CURSE_BONUS = 0.10D;
    /** 三条件全满足时的真实伤害比例(目标最大生命的 1%)。 */
    public static final float TRUE_DAMAGE_RATIO = 0.01F;

    /** 条件③可随机施加的原版负面效果(都是"持续性"负面,瞬时型不在内)。 */
    private static final List<MobEffect> DEBUFFS = List.of(
        MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.POISON, MobEffects.WITHER,
        MobEffects.HUNGER, MobEffects.DIG_SLOWDOWN, MobEffects.BLINDNESS, MobEffects.CONFUSION,
        MobEffects.LEVITATION, MobEffects.DARKNESS);

    /** 我们自己给出的伤害吸收额度与到期 tick(每玩家一份,内存里)。 */
    private static final Map<UUID, float[]> ABSORB = new HashMap<>();

    /**
     * 三件套:被手骨命中过的目标 → 到期 tick(禁止回复生命的窗口)。
     * 只在{@code fullSet}时写入;不写 NBT、不挂效果、不加属性。
     *
     * <p><b>4.6.0(内存)</b>:这张表的 key 是<b>任意 LivingEntity</b>(包括怪),原来唯一的清理点是
     * {@link #onHeal} —— 目标在窗口内<b>没有触发过治疗事件</b>(被打死、被刷掉、区块卸载)就永远留在表里。
     * 一个三件套玩家每打一只怪就写一条(UUID + Long,≈ 60B 键值 + Node 32B),挂机刷怪场一小时能积
     * 上万条且永不回收。现在由 {@link #onTargetDeath}/{@link #onTargetLeaveLevel} 跟着实体生命周期删,
     * 表的大小 == "最近 N 秒内被手骨打过的、且还活着的实体数"(通常 &lt; 20)。</p>
     */
    private static final Map<UUID, Long> HEAL_BLOCK = new HashMap<>();

    private static final Random RNG = new Random();

    private DemonBoneHandler() {}

    // ------------------------------------------------------------------ 佩戴查询

    /** 佩戴中的恶魔的手骨(没有/异常返回空堆)。 */
    public static ItemStack equippedBone(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        Item bone = ModItems.demonHandBoneOrNull();
        if (bone == null) {
            return ItemStack.EMPTY;
        }
        try {
            return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(stack -> !stack.isEmpty()
                        && stack.getItem() == bone)
                    .map(SlotResult::stack)
                    .orElse(ItemStack.EMPTY))
                .orElse(ItemStack.EMPTY);
        }
        catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 是否戴着恶魔的手骨。 */
    public static boolean wearing(Player player) {
        return !equippedBone(player).isEmpty();
    }

    /** 三件套是否齐(契约 + 血杯 + 手骨),每次即时计算。 */
    public static boolean fullSet(Player player) {
        if (player == null) {
            return false;
        }
        return !DemonPactData.equippedPact(player).isEmpty()
            && !DemonBloodChaliceHandler.equippedChalice(player).isEmpty()
            && !equippedBone(player).isEmpty();
    }

    // ------------------------------------------------------------------ 伤害结算

    /** 攻击侧的总倍率:1 + 0.3(条件①) + 0.1×目标已有负面数(单件,每个 +10%)。 */
    public static double attackMultiplier(Player attacker, LivingEntity target, boolean biggerMax) {
        double multiplier = 1.0D;
        if (biggerMax) {
            multiplier += BIGGER_MAX_BONUS;
        }
        multiplier += CURSE_BONUS * countHarmful(target);
        return multiplier;
    }

    /** 目标身上负面效果的数量。 */
    public static int countHarmful(LivingEntity target) {
        int count = 0;
        for (MobEffectInstance instance : target.getActiveEffects()) {
            if (!instance.getEffect().isBeneficial()) {
                ++count;
            }
        }
        return count;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onHurt(LivingHurtEvent event) {
        // 4.6.0(内存):每命中一次原本有 3~5 条 DebugLog.info 调用。info 内部会先查 debugLog 再输出,
        // 但**参数是在调用前就求值的** —— 每条都要建一个 Object[](varargs),
        // 还要算 player.getName().getString() / target.getName().getString() / effect.getDescriptionId()。
        // 默认 debugLog=false 时这些全部是白花的分配(一次近战 ≈ 5 个 Object[] + 若干 String/Component),
        // 战斗一密集就是稳定的垃圾来源。这里先取一次开关,关掉时整段跳过。
        boolean log = DebugLog.debugEnabled();
        try {
            LivingEntity victim = event.getEntity();
            if (victim.level().isClientSide) {
                return;
            }
            // 1) 佩戴者受到的伤害 +50%
            if (victim instanceof Player player && wearing(player)) {
                event.setAmount(event.getAmount() * TAKEN_MULTIPLIER);
                if (log) {
                    DebugLog.info("{} {} 承伤 x{} -> {}", LOG_TAG, player.getName().getString(),
                        TAKEN_MULTIPLIER, event.getAmount());
                }
            }
            // 2) 佩戴者造成的伤害
            if (!(event.getSource().getEntity() instanceof Player attacker) || attacker == victim) {
                return;
            }
            if (!wearing(attacker)) {
                return;
            }
            LivingEntity target = event.getEntity();
            boolean biggerMax = target.getMaxHealth() > attacker.getMaxHealth();
            boolean richerHealth = target.getHealth() > attacker.getHealth();
            // 注意:这里的数量必须是"本次施加之前"的已有负面数,所以要在 applyRandomDebuff 之前算
            int harmfulBefore = countHarmful(target);
            boolean firstDebuff = harmfulBefore == 0;
            double multiplier = attackMultiplier(attacker, target, biggerMax);
            event.setAmount((float) (event.getAmount() * multiplier));
            if (biggerMax) {
                if (log) {
                    DebugLog.info("{} 目标最大生命更高:额外 +{}%", LOG_TAG, (int) (BIGGER_MAX_BONUS * 100));
                }
            }
            if (richerHealth) {
                float absorb = event.getAmount() * ABSORB_RATIO;
                grantAbsorption(attacker, absorb);
            }
            if (firstDebuff) {
                applyRandomDebuff(target);
            }
            else {
                if (log) {
                    DebugLog.info("{} 目标已有 {} 个负面:本次 +{}%", LOG_TAG, harmfulBefore,
                        (int) (CURSE_BONUS * harmfulBefore * 100.0D));
                }
            }
            // 三件套:被手骨命中的目标在 N 秒内不能回血
            if (fullSet(attacker)) {
                markNoHeal(target);
            }
            if (biggerMax && richerHealth && firstDebuff) {
                trueDamage(target, target.getMaxHealth() * TRUE_DAMAGE_RATIO, event.getSource());
            }
            if (log) {
                DebugLog.info("{} {} 命中 {}:x{} -> {}", LOG_TAG, attacker.getName().getString(),
                    target.getName().getString(), multiplier, event.getAmount());
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 伤害结算异常", t);
        }
    }

    /** 条件②:记一笔我们给出的伤害吸收(取最大值,不叠加)+ 到期 tick。 */
    private static void grantAbsorption(Player player, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        float current = player.getAbsorptionAmount();
        // 1.2.10:吸收量必须封顶 —— 伤害被倍率放大到几千时,不封顶会产生上千点吸收,
        // 原版/多排血条 HUD 画不出来,表现为"生命和额外生命都不渲染"。
        // 1.2.11a:单次吸收最多 10% 最大生命,吸收总量最多等于最大生命。
        float perGrant = Math.max(0.0F, player.getMaxHealth() * 0.1F);
        float totalCap = Math.max(0.0F, player.getMaxHealth());
        float cap = Math.min(perGrant, totalCap);
        float granted = Math.min(Math.max(current, amount), cap);
        player.setAbsorptionAmount(Math.min(granted, totalCap));
        ABSORB.put(player.getUUID(), new float[] { granted, player.tickCount + ABSORB_DURATION });
    }

    /** 三件套:记下"这个目标在 N 秒内不能回血"。 */
    private static void markNoHeal(LivingEntity target) {
        int seconds = RingConfig.demonBoneHealBlockSeconds();
        if (seconds <= 0) {
            return;
        }
        HEAL_BLOCK.put(target.getUUID(), target.tickCount + (long) seconds * 20L);
        if (DebugLog.debugEnabled()) {
            DebugLog.info("{} 目标 {} 进入禁疗窗口 {} 秒", LOG_TAG, target.getName().getString(), seconds);
        }
    }

    /** 三件套:被手骨命中过的目标在窗口内不能回血。 */
    @SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            Long expiry = HEAL_BLOCK.get(entity.getUUID());
            if (expiry == null) {
                return;
            }
            if (entity.tickCount > expiry) {
                HEAL_BLOCK.remove(entity.getUUID());
                return;
            }
            event.setAmount(0.0F);
            DebugLog.info("{} 禁疗:{} 的这次治疗被清零", LOG_TAG, entity.getName().getString());
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 禁疗结算异常", t);
        }
    }

    /** 条件③:随机一个原版负面效果(I 级,5~10 秒)。 */
    private static void applyRandomDebuff(LivingEntity target) {
        MobEffect effect = DEBUFFS.get(RNG.nextInt(DEBUFFS.size()));
        int duration = DEBUFF_MIN + RNG.nextInt(DEBUFF_MAX - DEBUFF_MIN + 1);
        target.addEffect(new MobEffectInstance(effect, duration, 0));
        if (DebugLog.debugEnabled()) {
            DebugLog.info("{} 给 {} 施加随机负面 {}", LOG_TAG, target.getName().getString(), effect.getDescriptionId());
        }
    }

    /**
     * 真实伤害:直写血量(并被 {@link DemonPactHandler#applyContract} 的契约修正放大/上限压到当前生命),
     * 写 0 时补一次死亡判定。
     */
    public static float trueDamage(LivingEntity target, float raw, net.minecraft.world.damagesource.DamageSource source) {
        if (target == null || raw <= 0.0F || !target.isAlive()) {
            return 0.0F;
        }
        float amount = raw;
        if (target instanceof Player player) {
            amount = DemonPactHandler.applyContract(player, amount);
        }
        float health = target.getHealth();
        float next = Math.max(0.0F, health - amount);
        target.setHealth(next);
        DebugLog.info("{} 真实伤害 {}:{} -> {}", LOG_TAG, target.getName().getString(), health, next);
        if (next <= 0.0F && target.isAlive()) {
            target.setHealth(0.0F);
            target.die(source == null ? target.damageSources().magic() : source);
        }
        return amount;
    }

    // ------------------------------------------------------------------ 吸收到期 + 退出清理

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.player;
        float[] data = ABSORB.get(player.getUUID());
        if (data == null || player.tickCount < (long) data[1]) {
            return;
        }
        ABSORB.remove(player.getUUID());
        if (player.getAbsorptionAmount() <= data[0] + 0.01F) {
            player.setAbsorptionAmount(0.0F);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            UUID id = event.getEntity().getUUID();
            ABSORB.remove(id);
            HEAL_BLOCK.remove(id);
        }
    }

    /**
     * 4.6.0(内存):目标死亡 ⇒ 它的禁疗窗口已经没有意义,立刻删掉。
     * <p>死掉的实体不可能再收到 {@code LivingHealEvent}({@code heal()} 会先判 {@code isAlive()}),
     * 所以删掉这条<b>不改变任何可观察行为</b>,只是不再把 UUID 永久留在表里。</p>
     */
    @SubscribeEvent
    public static void onTargetDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() != null) {
            HEAL_BLOCK.remove(event.getEntity().getUUID());
        }
    }

    /**
     * 4.6.0(内存):目标离开当前 level(被刷掉 / 区块卸载后 discard / 换维度)⇒ 同样删掉。
     * <p>同 {@link #onTargetDeath}:离开世界的实体收不到治疗事件,删掉是纯内存行为。</p>
     */
    @SubscribeEvent
    public static void onTargetLeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() != null) {
            HEAL_BLOCK.remove(event.getEntity().getUUID());
        }
    }

    // ------------------------------------------------------------------ 自检辅助

    /** 自检:目标是否处于我们记下的"禁疗窗口"里。 */
    public static boolean noHealWindow(LivingEntity entity) {
        Long expiry = entity.getUUID() == null ? null : HEAL_BLOCK.get(entity.getUUID());
        return expiry != null && entity.tickCount <= expiry;
    }

    /** 自检:把某个目标的禁疗窗口设成"已经过期"。 */
    public static void expireNoHeal(LivingEntity entity) {
        if (entity.getUUID() != null) {
            HEAL_BLOCK.remove(entity.getUUID());
        }
    }
}
