package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.3.3:水银之蛇的运行时。
 *
 * <p>数据(都存在实体的 PersistentData 里,不占存档结构):</p>
 * <ul>
 *   <li>{@code mercury_charges} —— 剩余次数盾层数;</li>
 *   <li>{@code mercury_invuln_until} —— 无敌帧到期时间(gameTime)。</li>
 * </ul>
 *
 * <p>触发:生命 ≤ 50% 时回满血、按当前等级获得次数盾,然后**等级 -1**;
 * 等级减到 0 时效果移除(剩余层数仍然可用)。</p>
 */
public final class MercurySerpentHandler {

    /** 1.6.15:内部写血旁路(回满/复位),避免被自己的回滚 mixin 拦掉。 */
    public static final ThreadLocal<Boolean> ALLOW_WRITE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final String TAG_CHARGES = "mercury_charges";

    private static final String TAG_INVULN = "mercury_invuln_until";

    /** 每次破盾给的无敌帧。 */
    public static final int INVULN_TICKS = 10;

    /** 触发阈值(生命百分比)。 */
    public static final float THRESHOLD = 0.5F;

    /** 内部放行(只有"层数耗尽"时才允许真的移除)。 */
    private static final ThreadLocal<Boolean> ALLOW_REMOVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MercurySerpentHandler() {
    }

    /** 1.3.8:强制写入效果(绕过"外部 buff 施加拦截")。 */
    public static void forceApply(LivingEntity entity, MobEffect effect, int duration, int amplifier) {
        if (entity == null || effect == null) {
            return;
        }
        entity.getActiveEffectsMap().put(effect,
            new MobEffectInstance(effect, duration, amplifier, false, false, true));
    }

    /** 1.4.1:是否受"死亡保护"(带 buff 且还有层数)—— remove/setHealth 的写死判定都用它。 */
    public static boolean protectedFromDeath(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide && has(entity) && charges(entity) > 0;
    }

    /** 当前是否允许外部移除(供 mixin 判定)。 */
    public static boolean removalAllowed() {
        return Boolean.TRUE.equals(ALLOW_REMOVE.get());
    }

    /** 内部强制移除(层数耗尽时用)。 */
    public static void forceRemove(LivingEntity entity, MobEffect effect) {
        ALLOW_REMOVE.set(Boolean.TRUE);
        ModEffectGuard.ALLOW_REMOVE.set(Boolean.TRUE);
        try {
            entity.removeEffect(effect);
            entity.getActiveEffectsMap().remove(effect);
        }
        finally {
            ALLOW_REMOVE.set(Boolean.FALSE);
            ModEffectGuard.ALLOW_REMOVE.set(Boolean.FALSE);
        }
    }

    private static MobEffect effect() {
        return ModEffects.mercurySerpentOrNull();
    }

    public static boolean has(LivingEntity entity) {
        // 4.5.0:配置开关(默认开启;关闭后整条水银之蛇机制不再生效)
        if (!RingConfig.mercurySerpentEnabled()) {
            return false;
        }
        MobEffect effect = effect();
        return effect != null && entity.hasEffect(effect);
    }

    /** 当前是否处于"破盾无敌"期间。 */
    public static boolean invulnerable(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData();
        if (!tag.contains(TAG_INVULN)) {
            return false;
        }
        long now = entity.level().getGameTime();
        long until = tag.getLong(TAG_INVULN);
        return now >= 0 && until > 0 && now < until && until - now <= INVULN_TICKS;
    }

    public static int charges(LivingEntity entity) {
        return entity.getPersistentData().getInt(TAG_CHARGES);
    }

    /** 1.4.4:所有生物每 tick 检查 50% 触发(不再依赖受击路径)。 */
    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            if (!has(entity)) {
                return;
            }
            // 1.4.8:受保护却处于"死亡中" → 每 10 tick 强制复位(消除死亡动画/抽搐)
            if (protectedFromDeath(entity) && (entity.isDeadOrDying() || entity.getHealth() <= 0.0F)) {
                resetDyingState(entity);
            }
            tryTrigger(entity);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 每 tick:检查 50% 触发。 */
    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
            || event.player.level().isClientSide) {
            return;
        }
        tryTrigger(event.player);
    }

    /** 供非玩家实体调用(由 LivingHurtEvent 驱动)。 */
    public static void tryTrigger(LivingEntity entity) {
        MobEffect effect = effect();
        if (effect == null || entity == null) {
            return;
        }
        MobEffectInstance instance = entity.getEffect(effect);
        if (instance == null) {
            return;
        }
        float max = entity.getMaxHealth();
        if (max <= 0.0F || entity.getHealth() > max * THRESHOLD) {
            return;
        }
        // 1) 回满血
        entity.setHealth(max);
        // 2) 按当前等级给次数盾
        int level = instance.getAmplifier() + 1;
        CompoundTag tag = entity.getPersistentData();
        tag.putInt(TAG_CHARGES, tag.getInt(TAG_CHARGES) + level);
        // 3) 等级 -1(到 0 就移除效果,但已获得的层数保留)
        int newAmplifier = instance.getAmplifier() - 1;
        forceRemove(entity, effect);
        if (newAmplifier >= 0) {
            forceApply(entity, effect, instance.getDuration(), newAmplifier);
        }
        DebugLog.once("mercury:trigger:" + entity.getUUID(),
            "[Mercury] {} 触发:回满血,获得 {} 层盾,等级 {}", entity.getName().getString(),
            level, newAmplifier + 1);
    }

    /** 伤害入口(最高优先级):无敌期间或还有层数 → 完全抵消。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            if (!has(entity)) {
                return;
            }
            // 无敌帧内:伤害无效
            if (invulnerable(entity)) {
                event.setCanceled(true);
                event.setAmount(0.0F);
                return;
            }
            int charges = charges(entity);
            if (charges <= 0) {
                // 1.6.35:没盾 → **伤害正常结算**(只做 50% 触发检查,由"锁 1 血"兜底)
                tryTrigger(entity);
                return;
            }
            // 有盾 → 消耗 1 层,抵消这一次伤害,并给 10 tick 无敌
            CompoundTag tag = entity.getPersistentData();
            tag.putInt(TAG_CHARGES, charges - 1);
            tag.putLong(TAG_INVULN, entity.level().getGameTime() + INVULN_TICKS);
            event.setCanceled(true);
            event.setAmount(0.0F);
            DebugLog.once("mercury:block:" + entity.getUUID(),
                "[Mercury] {} 次数盾抵消一次伤害,剩余 {} 层", entity.getName().getString(), charges - 1);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 1.6.19:受保护实体**不产生掉落物**(防止"死了但没死"的掉落)。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        try {
            // 1.6.48:只要还带着水银之蛇(无论层数),就说明"这次死亡会被拦下",清掉掉落。
            net.minecraft.world.entity.LivingEntity dead = event.getEntity();
            if (dead != null && has(dead)) {
                event.getDrops().clear();
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 1.4.13:致死手段被拦下时消耗 1 层盾(等级同时 -1),层数耗尽后不再免疫。 */
    public static void consumeShieldOnBlock(LivingEntity entity) {
        try {
            MobEffect effect = effect();
            if (effect == null || entity == null) {
                return;
            }
            int charges = charges(entity);
            if (charges <= 0) {
                return;
            }
            entity.getPersistentData().putInt(TAG_CHARGES, charges - 1);
            MobEffectInstance instance = entity.getEffect(effect);
            if (instance != null) {
                int amplifier = instance.getAmplifier();
                int duration = instance.getDuration();
                forceRemove(entity, effect);
                if (amplifier - 1 >= 0) {
                    forceApply(entity, effect, duration, amplifier - 1);
                }
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 1.4.7:拦下死亡后必须清掉"正在死亡"的标记,否则会一直播放死亡动画(表现为抽搐)。 */
    public static void resetDyingState(LivingEntity entity) {
        try {
            entity.setHealth(entity.getMaxHealth());
            setEntityField(entity, "dead", "f_20922_", Boolean.FALSE);
            setEntityField(entity, "deathTime", "f_20919_", Integer.valueOf(0));
            entity.hurtTime = 0;
            entity.hurtDuration = 0;
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    private static void setEntityField(Object entity, String mojName, String srgName, Object value) {
        try {
            Class<?> cls = entity.getClass();
            while (cls != null && cls != Object.class) {
                for (String name : new String[] { mojName, srgName }) {
                    try {
                        java.lang.reflect.Field f = cls.getDeclaredField(name);
                        f.setAccessible(true);
                        if (value instanceof Boolean b) {
                            f.setBoolean(entity, b);
                        }
                        else if (value instanceof Integer i) {
                            f.setInt(entity, i);
                        }
                        return;
                    }
                    catch (NoSuchFieldException ignored) {
                        // 试下一个名字
                    }
                }
                cls = cls.getSuperclass();
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }

    /** 1.4.5:第二道闸 —— 有些模组直接改 LivingDamageEvent 的数值,这里再拦一次。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            if (!has(entity)) {
                return;
            }
            // 1.4.12:锁 1 血 —— 任何单次伤害最多把血打到 1,不会进入死亡
            float cap = Math.max(0.0F, entity.getHealth() - 1.0F);
            if (event.getAmount() > cap) {
                event.setAmount(cap);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 死亡抗性:持有该效果/还有层数时不会死。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            MobEffect effect = effect();
            if (effect == null || !has(entity)) {
                return;
            }
            // 1.3.18:死亡抗性要求"还有层数",每触发一次消耗 1 层,并把等级 -1
            int charges = charges(entity);
            if (charges <= 0) {
                return;
            }
            MobEffectInstance instance = entity.getEffect(effect);
            int amplifier = instance == null ? 0 : instance.getAmplifier();
            int duration = instance == null ? 200 : instance.getDuration();
            entity.getPersistentData().putInt(TAG_CHARGES, charges - 1);
            forceRemove(entity, effect);
            if (amplifier - 1 >= 0) {
                forceApply(entity, effect, duration, amplifier - 1);
            }
            entity.setHealth(entity.getMaxHealth());
            event.setCanceled(true);
            DebugLog.once("mercury:death:" + entity.getUUID(),
                "[Mercury] {} 死亡抗性生效(消耗 1 层,剩余 {},等级 {})",
                entity.getName().getString(), charges - 1, amplifier);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}
