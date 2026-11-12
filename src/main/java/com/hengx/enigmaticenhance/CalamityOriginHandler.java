package com.hengx.enigmaticenhance;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.5.1:根源之祸。
 *
 * <ul>
 *   <li>末影龙**口吐的龙息**(damageSources().dragonBreath())会给目标挂上该效果;</li>
 *   <li>带该效果的实体**下次受到任何伤害**时,额外承受自身最大生命 **40% 的绝对真伤**
 *       (直接 setHealth,绕过护甲/抗性/吸收/无敌帧),随后效果清除;</li>
 *   <li>该效果无法被牛奶/净化/命令清除(见 {@code CalamityOriginRemovalMixin})。</li>
 * </ul>
 */
public final class CalamityOriginHandler {

    private static final ThreadLocal<Boolean> IN_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 1.5.15:内部移除的旁路开关(我们的"不可清除" mixin 会拦掉所有移除)。 */
    public static final ThreadLocal<Boolean> ALLOW_REMOVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private CalamityOriginHandler() {
    }

    /** 佩戴「恶魔血杯」的玩家:根源之祸无效。 */
    private static boolean wearingChalice(LivingEntity entity) {
        try {
            return entity instanceof net.minecraft.world.entity.player.Player player
                && DemonBloodChaliceHandler.wearing(player);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 最底层写血:直接改同步数据 DATA_HEALTH_ID,绕过对方对 setHealth 的拦截。 */
    private static void forceWriteHealth(LivingEntity entity, float value) {
        try {
            java.lang.reflect.Field f = findField(net.minecraft.world.entity.LivingEntity.class,
                "DATA_HEALTH_ID", "f_20968_");
            if (f == null) {
                entity.setHealth(value);
                return;
            }
            f.setAccessible(true);
            Object accessor = f.get(null);
            if (accessor instanceof net.minecraft.network.syncher.EntityDataAccessor<?> acc) {
                @SuppressWarnings("unchecked")
                net.minecraft.network.syncher.EntityDataAccessor<Float> typed =
                    (net.minecraft.network.syncher.EntityDataAccessor<Float>) acc;
                entity.getEntityData().set(typed, value);
            }
        }
        catch (Throwable ignored) {
            try {
                entity.setHealth(value);
            }
            catch (Throwable ignored2) {
                // 放弃
            }
        }
    }

    private static java.lang.reflect.Field findField(Class<?> cls, String... names) {
        while (cls != null && cls != Object.class) {
            for (String name : names) {
                try {
                    return cls.getDeclaredField(name);
                }
                catch (NoSuchFieldException ignored) {
                    // 下一个
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static MobEffect effect() {
        return ModEffects.CALAMITY_ORIGIN.isPresent() ? ModEffects.CALAMITY_ORIGIN.get() : null;
    }

    /** 龙息挂 buff:只认"末影龙口吐的龙息"伤害类型。 */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.calamityOriginEnabled()) {
            return;
        }
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            MobEffect effect = effect();
            if (effect == null) {
                return;
            }
            var source = event.getSource();
            // 龙息(末影龙口吐)判定:伤害类型为 dragon_breath
            boolean isBreath = source.is(net.minecraft.world.damagesource.DamageTypes.DRAGON_BREATH)
                || (source.getMsgId() != null && source.getMsgId().contains("dragon_breath"));
            DebugLog.info("[Calamity] 受伤:{} 类型={} 是龙息={}", entity.getName().getString(), source.getMsgId(), isBreath);
            if (isBreath
                && !wearingChalice(entity)) {   // 佩戴恶魔血杯 → 该效果无效
                // 1.5.8:直接写效果表,绕过"免疫负面效果"类护甲/图腾
                entity.getActiveEffectsMap().put(effect,
                    new MobEffectInstance(effect, Integer.MAX_VALUE, 0, false, false, true));
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 触发真伤:LivingDamageEvent(在此结算后写死扣血)。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.calamityOriginEnabled()) {
            return;
        }
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            MobEffect effect = effect();
            if (effect == null || !entity.hasEffect(effect)) {
                return;
            }
            if (!IN_DAMAGE.get().booleanValue()) {
                IN_DAMAGE.set(Boolean.TRUE);
                try {
                    // 1.5.15:走"允许移除"旁路,否则会被我们自己的不可清除 mixin 拦掉
                    ModEffectGuard.removeEffect(entity, effect);
                    DebugLog.info("[Calamity] 移除效果 仍有={}", entity.hasEffect(effect));
                    // 绝对真伤:不调用 hurt(在 LivingDamageEvent 里会递归到 StackOverflow),
                    // 直接写同步血量 + 底层兜底,任何"无敌/免伤"都无效
                    float trueDamage = entity.getMaxHealth() * CalamityOriginEffect.TRUE_DAMAGE_RATIO;
                    float before = entity.getHealth();
                    float target = Math.max(0.0F, entity.getHealth() - trueDamage);
                    DebugLog.info("[Calamity] 触发真伤:{} 血 {} -> 目标 {}", entity.getName().getString(), before, target);
                    try {
                        entity.setHealth(target);
                    }
                    catch (Throwable th) {
                        EnigmaticEnhance.LOGGER.warn("[Calamity] setHealth 失败: {}", th.toString());
                    }
                    if (entity.getHealth() > target + 0.01F) {
                        forceWriteHealth(entity, target);
                    }
                    if (entity.getHealth() <= 0.0F) {
                        entity.die(entity.damageSources().magic());
                    }
                }
                finally {
                    IN_DAMAGE.set(Boolean.FALSE);
                }
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}
