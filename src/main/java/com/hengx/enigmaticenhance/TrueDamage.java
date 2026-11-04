package com.hengx.enigmaticenhance;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 真实伤害:通用绕过所有"事件层限伤",同时保留正常掉落。
 *
 * <h2>原理</h2>
 * <ol>
 *   <li>用自定义伤害类型 {@code enigmaticenhance:true_damage}(JSON 里全部 bypass 标签);</li>
 *   <li>{@link com.hengx.enigmaticenhance.mixin.TrueDamageBypassMixin} 在
 *       {@code ForgeHooks.onLivingAttack/onLivingHurt/onLivingDamage} 里,遇到这个伤害源
 *       就<b>直接返回原值、不派发事件</b> —— 任何模组在事件里夹伤害的逻辑都不会执行;</li>
 *   <li>伤害本身仍走原版 {@code hurt()},所以护甲/免伤/无敌帧/死亡/掉落全部照常。</li>
 * </ol>
 *
 * <p><b>不针对任何具体模组类名</b>,对面改名/更新也不会失效。</p>
 */
public final class TrueDamage {

    /** 自定义伤害类型 id。 */
    public static final ResourceKey<DamageType> TRUE_DAMAGE =
        ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(EnigmaticEnhance.MODID, "true_damage"));

    private TrueDamage() {
    }

    /** 这个伤害源是不是我们的真实伤害。 */
    public static boolean isTrueDamage(DamageSource source) {
        return source != null && source.is(TRUE_DAMAGE);
    }

    /** 造一个真实伤害源。 */
    public static DamageSource source(LivingEntity attacker, DamageSource fallback) {
        try {
            if (attacker != null && attacker.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                return new DamageSource(sl.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(TRUE_DAMAGE), attacker);
            }
            if (attacker != null && attacker.level().registryAccess() != null) {
                return new DamageSource(attacker.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(TRUE_DAMAGE), attacker);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 退回原伤害源
        }
        return fallback;
    }

    /**
     * 造成真实伤害。
     *
     * @return 实际造成量;返回 &lt; 0 表示未能处理,调用方应退回普通伤害
     */
    public static float apply(LivingEntity target, LivingEntity attacker, float amount, DamageSource fallback) {
        if (target == null || amount <= 0.0F || !target.isAlive() || target.level().isClientSide) {
            return -1.0F;
        }
        try {
            DamageSource src = source(attacker, fallback);
            // 走原版 hurt:护甲/无敌帧/死亡/掉落都由原版处理,
            // 但事件派发已被 Mixin 跳过,所以限伤不生效。
            target.invulnerableTime = 0;
            boolean ok = target.hurt(src, amount);
            return ok ? amount : -1.0F;
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[TrueDamage] 真实伤害失败,退回正常伤害: {}", e.toString());
            return -1.0F;
        }
    }
}