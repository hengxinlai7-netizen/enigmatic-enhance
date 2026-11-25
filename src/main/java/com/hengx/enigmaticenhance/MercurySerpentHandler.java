package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 水银之蛇的运行时。
 *
 * <p>数据(都存在实体的 PersistentData 里,不占存档结构):</p>
 * <ul>
 *   <li>{@code mercury_charges} —— 剩余次数盾层数(<b>保护判定的唯一依据</b>);</li>
 *   <li>{@code mercury_triggers_left} —— 还能触发几次(4.11.0 新增,与 buff 等级解耦);</li>
 *   <li>{@code mercury_invuln_until} —— 无敌帧到期时间(gameTime)。</li>
 * </ul>
 *
 * <h2>4.11.0:次数与 buff 等级解耦</h2>
 * <p>旧版把"等级"当次数用:每次触发 {@code 等级-1} 并重写效果。但石英花
 * ({@code ArtificialFlower}) 会对<b>它自己存着的那个效果</b>设 {@code Result.DENY},
 * 一旦我们把效果移除(等级掉到 -1 那一次),石英花就再也补不回来 → buff 永久消失 →
 * {@code has()} 恒 false → 保护整条失效,玩家被一路打死。</p>
 *
 * <p>现在:<b>不再改 buff 等级</b>,次数记在 {@code mercury_triggers_left} 里;
 * 触发次数用尽时只 {@code forceRemove} 效果,<b>已获得的层数照旧可用</b>;
 * 只要还有层数而 buff 不在,就每 tick 自动补回(玩家额外同步 HUD 包)。</p>
 */
public final class MercurySerpentHandler {

    /** 1.6.15:内部写血旁路(回满/复位),避免被自己的回滚 mixin 拦掉。 */
    public static final ThreadLocal<Boolean> ALLOW_WRITE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final String TAG_CHARGES = "mercury_charges";

    /** 4.11.0:还能触发几次(与 buff 等级解耦)。 */
    public static final String TAG_TRIGGERS_LEFT = "mercury_triggers_left";

    private static final String TAG_INVULN = "mercury_invuln_until";

    /** 每次破盾给的无敌帧。 */
    public static final int INVULN_TICKS = 10;

    /** 触发阈值(生命百分比)。 */
    public static final float THRESHOLD = 0.5F;

    /** 补回 buff 时给一个足够长的时长(实际由层数决定何时消失)。 */
    private static final int REFILL_DURATION = 20 * 60 * 10;

    /** 内部放行(只有"层数耗尽"时才允许真的移除)。 */
    private static final ThreadLocal<Boolean> ALLOW_REMOVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MercurySerpentHandler() {
    }

    /** 1.3.8:强制写入效果(绕过"外部 buff 施加拦截")。 */
    public static void forceApply(LivingEntity entity, MobEffect effect, int duration, int amplifier) {
        if (entity == null || effect == null) {
            return;
        }
        MobEffectInstance instance = new MobEffectInstance(effect, duration, amplifier, false, false, true);
        entity.getActiveEffectsMap().put(effect, instance);
        syncToClient(entity, instance);
    }

    /** 4.11.0:服务端补回效果后把 HUD 同步给玩家(否则客户端看不到那个 buff)。 */
    private static void syncToClient(LivingEntity entity, MobEffectInstance instance) {
        if (entity instanceof ServerPlayer player) {
            try {
                player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), instance));
            }
            catch (RuntimeException | LinkageError ignored) {
                // 同步失败不影响服务端逻辑
            }
        }
    }

    /**
     * 是否受"死亡保护"。
     *
     * <p>4.11.0:<b>只看层数</b> —— 石英花可能一时补不回 buff,但层数还在就仍然受保护。</p>
     */
    public static boolean protectedFromDeath(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide && charges(entity) > 0;
    }

    /** 当前是否允许外部移除(供 mixin 判定)。 */
    public static boolean removalAllowed() {
        return Boolean.TRUE.equals(ALLOW_REMOVE.get());
    }

    /** 内部强制移除(次数耗尽时用)。 */
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
        return entity != null && effect != null && entity.hasEffect(effect);
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

    /** 4.11.0:还能触发几次;键不存在时返回 -1(=未初始化)。 */
    public static int triggersLeft(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData();
        return tag.contains(TAG_TRIGGERS_LEFT) ? tag.getInt(TAG_TRIGGERS_LEFT) : -1;
    }

    /** 所有生物每 tick:补回 buff + 检查 50% 触发。 */
    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            if (!RingConfig.mercurySerpentEnabled()) {
                return;
            }
            // 4.11.0:层数还在但 buff 不在 → 每次补回(石英花的 DENY 挡不住 forceApply)
            if (charges(entity) > 0 && !has(entity)) {
                MobEffect effect = effect();
                if (effect != null) {
                    // 补回时用 0 级:次数记在 mercury_triggers_left,不再靠等级表达
                    forceApply(entity, effect, REFILL_DURATION, 0);
                }
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

    /**
     * 生命降到 50% 以下时:回满血 + 按剩余次数给层数。
     *
     * <p>4.11.0:<b>不动 buff 等级</b>(旧版降级会触发石英花的 DENY,导致再也补不上);
     * 次数用尽时只移除效果,已获得的层数保留。</p>
     */
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
        CompoundTag tag = entity.getPersistentData();
        // 次数:首次(键不存在)按 amplifier + 1 初始化
        int left = tag.contains(TAG_TRIGGERS_LEFT) ? tag.getInt(TAG_TRIGGERS_LEFT) : instance.getAmplifier() + 1;
        if (left <= 0) {
            return;   // 次数已用尽:不再触发(层数仍然可用)
        }
        // 1) 回满血
        entity.setHealth(max);
        // 2) 给层数:与旧版总量一致(2 级=2+1 次触发 → 2+1 层)
        tag.putInt(TAG_CHARGES, tag.getInt(TAG_CHARGES) + left);
        // 3) 次数 -1;用尽则移除效果(等级不变,层数保留)
        int remaining = left - 1;
        tag.putInt(TAG_TRIGGERS_LEFT, remaining);
        if (remaining <= 0) {
            forceRemove(entity, effect);
        }
        DebugLog.info("[Mercury] {} 触发:回满血,获得 {} 层盾,剩余 {} 次触发",
            entity.getName().getString(), left, remaining);
    }

    /** 伤害入口(最高优先级):无敌期间或还有层数 → 完全抵消。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            if (!RingConfig.mercurySerpentEnabled()) {
                return;
            }
            // 4.11.0:层数 > 0 就抵消 —— 不再要求 buff 还在
            int charges = charges(entity);
            if (!has(entity) && charges <= 0) {
                return;
            }
            // 无敌帧内:伤害无效
            if (invulnerable(entity)) {
                event.setCanceled(true);
                event.setAmount(0.0F);
                return;
            }
            if (charges <= 0) {
                // 没盾 → 伤害正常结算(只做 50% 触发检查,由"锁 1 血"兜底)
                tryTrigger(entity);
                return;
            }
            // 有盾 → 消耗 1 层,抵消这一次伤害,并给 10 tick 无敌
            CompoundTag tag = entity.getPersistentData();
            tag.putInt(TAG_CHARGES, charges - 1);
            tag.putLong(TAG_INVULN, entity.level().getGameTime() + INVULN_TICKS);
            event.setCanceled(true);
            event.setAmount(0.0F);
            DebugLog.info("[Mercury] {} 次数盾抵消一次伤害,剩余 {} 层",
                entity.getName().getString(), charges - 1);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 受保护实体**不产生掉落物**(防止"死了但没死"的掉落)。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        try {
            LivingEntity dead = event.getEntity();
            if (dead != null && (has(dead) || protectedFromDeath(dead))) {
                event.getDrops().clear();
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 致死手段被拦下时消耗 1 层盾(4.11.0:不再改 buff 等级)。 */
    public static void consumeShieldOnBlock(LivingEntity entity) {
        try {
            if (entity == null) {
                return;
            }
            int charges = charges(entity);
            if (charges <= 0) {
                return;
            }
            entity.getPersistentData().putInt(TAG_CHARGES, charges - 1);
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

    /** 第二道闸 —— 有些模组直接改 LivingDamageEvent 的数值,这里再拦一次。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            if (!RingConfig.mercurySerpentEnabled()) {
                return;
            }
            if (!has(entity) && charges(entity) <= 0) {
                return;
            }
            // 锁 1 血 —— 任何单次伤害最多把血打到 1,不会进入死亡
            float cap = Math.max(0.0F, entity.getHealth() - 1.0F);
            if (event.getAmount() > cap) {
                event.setAmount(cap);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 死亡抗性:还有层数时不会死(4.11.0:不再改 buff 等级)。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            if (!RingConfig.mercurySerpentEnabled()) {
                return;
            }
            int charges = charges(entity);
            if (charges <= 0) {
                return;
            }
            entity.getPersistentData().putInt(TAG_CHARGES, charges - 1);
            entity.setHealth(entity.getMaxHealth());
            event.setCanceled(true);
            DebugLog.info("[Mercury] {} 死亡抗性生效(消耗 1 层,剩余 {})",
                entity.getName().getString(), charges - 1);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
    /**
     * 4.11.1:拦截"直调致死"路径(涟漪包 CovenantCombat 这类直接 {@code setHealth(-N)} 再 {@code die()} 的模组)。
     *
     * <p>事件层({@code LivingHurtEvent})拦不到这种调用,所以这里在 {@code die()} / {@code setHealth(<=0)}
     * 的入口直接拦:只要还有层数,就消耗 1 层并拉回满血,返回 {@code true} 表示调用方应取消原操作。</p>
     *
     * @return 是否已拦截
     */
    public static boolean guardDirectDeath(LivingEntity entity) {
        try {
            if (entity == null || entity.level().isClientSide) {
                return false;
            }
            if (!RingConfig.mercuryGuardDirectDeath() || !RingConfig.mercurySerpentEnabled()) {
                return false;
            }
            if (charges(entity) <= 0) {
                return false;
            }
            consumeShieldOnBlock(entity);
            setHealthBypass(entity, entity.getMaxHealth());
            DebugLog.info("[Mercury] {} 拦截直调死亡(剩余 {} 层)", entity.getName().getString(), charges(entity));
            return true;
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** {@code setHealth} 守卫的判定:新血量 <= 0 且还有层数才拦。 */
    public static boolean shouldGuardSetHealth(float newHealth, int charges) {
        return newHealth <= 0.0F && charges > 0;
    }

    /** 内部写血旁路:避免自己的 {@code setHealth} mixin 把自己再拦一次。 */
    public static void setHealthBypass(LivingEntity entity, float health) {
        Boolean previous = ALLOW_WRITE.get();
        ALLOW_WRITE.set(Boolean.TRUE);
        try {
            entity.setHealth(health);
        }
        finally {
            ALLOW_WRITE.set(previous);
        }
    }

    /** 当前是否处于"内部写血"旁路(供 mixin 判定,避免递归)。 */
    public static boolean bypassingWrite() {
        return Boolean.TRUE.equals(ALLOW_WRITE.get());
    }
}
