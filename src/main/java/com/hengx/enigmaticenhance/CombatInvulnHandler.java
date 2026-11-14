package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.8.4:战斗无敌帧(下放到正常游戏,配置可调,支持伤害白名单)。
 *
 * <h2>时序</h2>
 * <pre>
 *   命中(now) → 伤害正常生效
 *   now+1 .. now+output        ← 输出窗口:这 output tick 内伤害仍然生效
 *   now+output+1 .. now+output+ticks  ← 无敌:免疫伤害
 *   之后 → 下一轮
 * </pre>
 *
 * <h2>白名单( config/enigmaticenhance-common.toml 的 [combatInvuln] 段)</h2>
 * <pre>
 *   combatInvulnWhitelist = [
 *       "tacz:bullet", "tacz:bullet_ignore_armor", ...   # 伤害类型 id
 *       "terra_entity:summon", "terra_entity:summoner",
 *       "com.tacz.guns", "org.confluence.terraentity"    # 实体类名片段
 *   ]
 * </pre>
 * <p>白名单里的伤害<b>完全绕过</b>无敌帧(泰拉召唤物伤害 / TACZ 枪械伤害)。</p>
 */
public final class CombatInvulnHandler {

    private static final String TAG_START = "combat_invuln_start";
    private static final String TAG_END = "combat_invuln_end";

    private CombatInvulnHandler() {
    }

    /** 该伤害是否在白名单里(伤害类型 id 或攻击者/直接实体的类名片段)。 */
    private static boolean whitelisted(DamageSource source) {
        if (source == null) {
            return false;
        }
        List<String> list = RingConfig.combatInvulnWhitelist();
        if (list.isEmpty()) {
            return false;
        }
        String attacker = source.getEntity() == null ? "" : source.getEntity().getClass().getName();
        String direct = source.getDirectEntity() == null ? "" : source.getDirectEntity().getClass().getName();
        for (String entry : list) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String e = entry.trim();
            // 伤害类型 id(如 tacz:bullet)
            if (e.indexOf(':') > 0) {
                try {
                    net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(e);
                    if (source.is(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DAMAGE_TYPE, rl))) {
                        return true;
                    }
                }
                catch (RuntimeException | LinkageError ignored) {
                    // 非法 id:忽略
                }
            }
            // 实体类名片段(如 org.confluence.terraentity)
            if (!attacker.isEmpty() && attacker.contains(e)) {
                return true;
            }
            if (!direct.isEmpty() && direct.contains(e)) {
                return true;
            }
        }
        return false;
    }

    /** @return true 表示这次伤害被无敌帧拦下 */
    private static boolean blockIfInvulnerable(LivingEntity entity, DamageSource source) {
        if (entity == null || entity.level().isClientSide) {
            return false;
        }
        // 4.8.3:执行之手自带 bypasses_cooldown / bypasses_invulnerability,
        // 本模组自己的战斗无敌帧也不能吃它(否则"全额"会被我们自己拦掉)
        if (ExecutionDamage.isExecution(source)) {
            return false;
        }
        if (!RingConfig.combatInvulnEnabled()) {
            return false;
        }
        // 白名单伤害:完全绕过无敌帧
        if (whitelisted(source)) {
            return false;
        }
        int ticks = RingConfig.combatInvulnTicks();
        if (ticks <= 0) {
            return false;
        }
        int output = Math.max(0, Math.min(200, RingConfig.combatInvulnOutput()));
        long now = entity.level().getGameTime();
        CompoundTag tag = entity.getPersistentData();
        long start = tag.getLong(TAG_START);
        long end = tag.getLong(TAG_END);
        if (now >= start && now < end) {
            entity.invulnerableTime = Math.max(entity.invulnerableTime, 2);
            if (RingConfig.debugLog()) {
                DebugLog.info("[CombatInvuln] 拦截:{} now={} window=[{}, {})",
                    entity.getName().getString(), now, start, end);
            }
            return true;
        }
        long newStart = now + output;
        tag.putLong(TAG_START, newStart);
        tag.putLong(TAG_END, newStart + ticks);
        if (RingConfig.debugLog()) {
            DebugLog.info("[CombatInvuln] 放行:{} now={} 下次无敌=[{}, {})",
                entity.getName().getString(), now, newStart, newStart + ticks);
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        try {
            if (blockIfInvulnerable(event.getEntity(), event.getSource())) {
                event.setAmount(0.0F);
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 护甲后的最终伤害再兜一层(有些模组只改这一层)。 */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamage(LivingDamageEvent event) {
        try {
            if (blockIfInvulnerable(event.getEntity(), event.getSource())) {
                event.setAmount(0.0F);
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}
