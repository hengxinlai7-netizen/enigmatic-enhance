package com.hengx.enigmaticenhance;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 3.8.0:把「爆伤」从"原版近战跳劈暴击"解放出来 —— <b>玩家造成任何伤害都有 50% 概率触发暴击</b>,
 * 并且<b>沿用各模组自己往暴击上加的爆伤</b>。
 *
 * <h2>原来为什么只有近战能吃到</h2>
 * <p>神秘遗物扩展的 {@code AddonEventHandler#onCriticalHit} 与神秘佳肴的
 * {@code EnigmaticDelightEventHandler#onCriticalHit} 都是往
 * {@code CriticalHitEvent#setDamageModifier(...)} 上累加:</p>
 *
 * <ul>
 *   <li>扩展:失落引擎的 {@code critModifier}、冰霜碎裂每级 {@code +0.4};</li>
 *   <li>佳肴:武器护符(攻击力 &lt; 1.5 时 +0.5)、暴食护符(缺失饱食度 × critModifier)、
 *       地狱刃片护符;</li>
 *   <li>其它模组同理。</li>
 * </ul>
 *
 * <p>而 {@code CriticalHitEvent} 只在 {@code Player#attack} 的跳劈路径里由原版触发,
 * 所以这些爆伤<b>永远只有近战能吃</b>。</p>
 *
 * <h2>本类怎么做</h2>
 * <p>不做任何数值硬编码:判定命中后<b>伪造一次 {@link CriticalHitEvent}</b> 并 post 到事件总线,
 * 让所有模组的暴击处理器把自己的爆伤加进来,再读 {@code getDamageModifier()} 作为最终倍率,
 * 乘到<b>这一次的整段伤害</b>上。这样:</p>
 *
 * <ul>
 *   <li>爆伤来源与它们原本完全一致(扩展 / 佳肴 / 其它模组的加成都会算进去);</li>
 *   <li>法术、弓箭、召唤物、爆炸同样能触发;</li>
 *   <li>概率固定 50%,不再依赖"跳起、下落中、不冲刺"。</li>
 * </ul>
 */
public final class PlayerCritHandler {

    /** 基础暴击率 50%。 */
    public static final float BASE_CHANCE = 0.50F;

    /** 基础暴击倍率(与原版一致);各模组的爆伤在这个基数上叠加。 */
    public static final float BASE_MODIFIER = 1.5F;

    private PlayerCritHandler() {
    }

    /** 4.5.0:暴击概率(读配置,默认 50%)。 */
    public static float critChance() {
        try {
            double pct = RingConfig.allDamageCritChance();
            return (float) Math.max(0.0D, Math.min(100.0D, pct)) / 100.0F;
        }
        catch (Throwable t) {
            return BASE_CHANCE;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        // 4.5.0:以前写死开关在代码里,现在改成配置项(默认开启,行为不变)
        if (!RingConfig.allDamageCritEnabled()) {
            return;
        }
        try {
            LivingEntity target = event.getEntity();
            if (target == null || target.level().isClientSide || target.isDeadOrDying()) {
                return;
            }
            if (event.getAmount() <= 0.0F) {
                return;
            }
            // 真实伤害已绕过所有加成,这里不再叠
            if (TrueDamage.isTrueDamage(event.getSource())) {
                return;
            }
            Entity attacker = event.getSource().getEntity();
            if (!(attacker instanceof Player)) {
                attacker = event.getSource().getDirectEntity();
            }
            if (!(attacker instanceof Player player)) {
                return;
            }
            if (player == target) {
                return;
            }
            if (player.getRandom().nextFloat() >= critChance()) {
                return;
            }

            // 伪造一次暴击事件:让扩展 / 佳肴 / 其它模组把自己的爆伤加进来
            CriticalHitEvent crit = new CriticalHitEvent(player, target, event.getAmount(), true);
            crit.setDamageModifier(BASE_MODIFIER);
            MinecraftForge.EVENT_BUS.post(crit);
            if (crit.isCanceled()) {
                return;
            }
            float mult = crit.getDamageModifier();
            if (mult <= 1.0F) {
                mult = BASE_MODIFIER;
            }
            event.setAmount(event.getAmount() * mult);

            player.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, 0.7F, 1.0F);
            // 4.6.2(内存):先问 oncePending() 再拼 key(每次暴击都要拼 "crit:"+UUID,
            // 而 once() 在记录表满了之后本来就不输出)。
            if (DebugLog.oncePending()) {
                DebugLog.once("crit:" + player.getUUID(),
                    "[Crit] {} 全伤暴击 ×{}", player.getName().getString(), mult);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 暴击失败不影响原伤害
        }
    }
}
