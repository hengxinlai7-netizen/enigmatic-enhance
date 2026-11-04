package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.1.29a:魔法剑气实体。
 * <ul>
 * <li>无重力、直线飞行,速度 1.6 格/tick;</li>
 * <li>每 tick 先 clip 方块(命中即消失),再扫 AABB 找实体;</li>
 * <li>命中数 = 附魔等级(1~3),达到即消失;</li>
 * <li>不设距离上限:只有 tickCount &gt; 200 或区块未加载才消失;</li>
 * <li>伤害走 {@code indirectMagic(this, owner)} ⇒ 直接来源是剑气本身,不会被"近战 -80%"误伤。</li>
 * </ul>
 */
public class MagicSwordAuraEntity extends Projectile {

    private static final int MAX_LIFE = 200;
    private static final double SPEED = 1.6D;
    /** 1.2.6g:剑气周围 1 格内的生物都算命中(球形半径,不分前后左右)。 */
    private static final double HIT_RADIUS = 1.0D;

    private int enchantLevel = 1;
    private int hits = 0;
    private float auraDamage = 1.0F;
    /** 1.2.3a:出手瞬间是否判定为原版暴击(下落/跳跃攻击),命中时伤害 ×1.5。 */
    private boolean crit = false;

    /** 1.2.9a:虚空剑气 —— 范围 +50%、无视实体阻挡、无视无敌/无敌帧/限伤。 */
    private boolean voidAura = false;

    /** 1.2.6f:已经打过的实体(命中盒前后各扩 0.75 格后,避免同一只在相邻 tick 被重复命中)。 */
    private final java.util.Set<java.util.UUID> hitEntities = new java.util.HashSet<>();

    public MagicSwordAuraEntity(EntityType<? extends MagicSwordAuraEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public MagicSwordAuraEntity(Level world, Player owner, int enchantLv, float damage) {
        this(world, owner, enchantLv, damage, false);
    }

    public MagicSwordAuraEntity(Level world, Player owner, int enchantLv, float damage, boolean crit) {
        this(world, owner, enchantLv, damage, crit, false);
    }

    public MagicSwordAuraEntity(Level world, Player owner, int enchantLv, float damage, boolean crit, boolean voidAura) {
        this(ModEntities.magicSwordAuraOrNull(), world);
        this.setOwner(owner);
        this.enchantLevel = Math.max(1, Math.min(3, enchantLv));
        this.auraDamage = damage <= 0.0F ? 1.0F : damage;
        this.crit = crit;
        this.voidAura = voidAura;
        Vec3 look = owner.getLookAngle();
        this.setPos(owner.getX() + look.x * 0.6D, owner.getEyeY() - 0.1D, owner.getZ() + look.z * 0.6D);
        this.setDeltaMovement(look.scale(SPEED));
        this.setYRot((float) (Math.atan2(look.x, look.z) * 180.0D / Math.PI));
        this.setXRot((float) (-Math.asin(look.y) * 180.0D / Math.PI));
    }

    public boolean isCrit() {
        return crit;
    }

    public int getEnchantLevel() {
        return enchantLevel;
    }

    public void setEnchantLevel(int level) {
        this.enchantLevel = Math.max(1, Math.min(3, level));
    }

    /** 1.2.9a:虚空剑气把飞行距离拉长 50%(范围 +50%)。 */
    private int maxLife() {
        return this.voidAura ? (int) (MAX_LIFE * VoidAuraEnchantment.RANGE_MULTIPLIER) : MAX_LIFE;
    }

    private int maxHits() {
        return this.voidAura ? Integer.MAX_VALUE : Math.max(1, Math.min(3, enchantLevel));
    }

    @Override
    protected void defineSynchedData() {
        // 无额外同步数据
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (tickCount > maxLife() || !level().isLoaded(blockPosition())) {
            discard();
            return;
        }
        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());
        // 1) 方块碰撞
        BlockHitResult block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, this));
        if (block.getType() == HitResult.Type.BLOCK) {
            setPos(block.getLocation());
            discard();
            return;
        }
        // 2) 先移动,再按新位置刷 AABB(避免高速穿过实体/方块)
        setPos(to);
        // 1.1.29o:白色碎光粒子(参考效果里"白碎刃"其实是粒子,黑刀才是实体)
        if (level().isClientSide()) {
            var rnd = level().getRandom();
            for (int i = 0; i < 3; i++) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.CRIT,
                    getX() + (rnd.nextDouble() - 0.5D) * 0.6D,
                    getY() + (rnd.nextDouble() - 0.5D) * 0.6D,
                    getZ() + (rnd.nextDouble() - 0.5D) * 0.6D,
                    (rnd.nextDouble() - 0.5D) * 0.05D,
                    (rnd.nextDouble() - 0.5D) * 0.05D,
                    (rnd.nextDouble() - 0.5D) * 0.05D);
            }
        }
        // 1.2.6f:命中盒同时往前、往后各扩 0.75 格 —— 敌方碰撞箱和剑气重合(贴脸/身后)时也能打到
        AABB box = hitBox(getBoundingBox(), getDeltaMovement());
        for (Entity e : level().getEntities(this, box)) {
            if (e instanceof LivingEntity already && !hitOnce(already)) {
                continue;
            }
            if (!(e instanceof LivingEntity target) || e == getOwner() || !target.isAlive()) {
                continue;
            }
            // 1.2.3a:暴击只乘数值,伤害类型仍是 indirectMagic(不改伤害来源、不加近战效果)
            // 1.2.9a:元素魔法再乘一层学派强度;虚空剑气走特殊结算(无视无敌/限伤)
            float damage = MagicCombatHandler.critDamage(auraDamage, crit);
            hitTarget(target, damage);
            if (crit) {
                critFeedback(target);
            }
            DebugLog.info("[Aura] 命中 {} 造成 {} 点魔法伤害(等级 {} 暴击 {} 虚空 {})",
                target.getName().getString(), damage, enchantLevel, crit, voidAura);
            ++hits;
            if (hits >= maxHits()) {
                discard();
                return;
            }
    }
    }

    /**
     * 1.2.6g:剑气命中盒 = 当前碰撞箱向外扩 {@value #HIT_RADIUS} 格(周围一圈都算),
     * 再沿本 tick 位移前后各扫一段(速度 1.6 格/刻,不这样可能从怪身上跳过去)。
     */
    public static AABB hitBox(AABB own, Vec3 delta) {
        // 1.2.6g:以剑气为中心,周围 1 格(所有方向)内的活体都能吃到;
        // 再沿本 tick 位移方向各扫一段,避免 1.6 格/刻的速度从怪身上"跳"过去。
        AABB box = own.inflate(HIT_RADIUS);
        if (delta != null && delta.lengthSqr() > 1.0E-6D) {
            box = box.expandTowards(delta).expandTowards(delta.scale(-1.0D));
        }
        return box;
    }

    /** 同一只生物只打一次:返回 true 表示还没打过(并记为已打)。 */
    public boolean hitOnce(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return this.hitEntities.add(entity.getUUID());
    }



    /**
    /** 1.2.9a:元素魔法 —— 主人手上那把剑带附魔时,剑气也吃"学派强度 ×1%"加成。 */
    private float elementalMultiplier() {
        try {
            Entity owner = getOwner();
            if (owner instanceof net.minecraft.world.entity.player.Player player
                && ElementalMagicHandler.hasEnchantment(player, player.getMainHandItem())) {
                return ElementalMagicHandler.damageMultiplier(player);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 缺铁魔法等情况:不加成
        }
        return 1.0F;
    }

    /**
     * 1.2.9a:命中结算。普通剑气走原版 hurt;虚空剑气额外做到:
     * 无视无敌帧(invulnerableTime=0)、无视无敌(invulnerable 临时清零)、无视限伤
     * (hurt 之后如果实际掉血少于应受伤害,直接把差额从血量里扣掉),
     * 但伤害类型仍是 indirectMagic —— 即"非虚空伤害"。
     */
    void hitTarget(LivingEntity target, float damage) {
        var source = damageSources().indirectMagic(this, getOwner());
        // 1.2.25:已经是"死亡中"的目标不再补刀 —— 重复 hurt 会把 deathTime 反复清零,
        // 传奇怪物会出现"掉了掉落物但一直不死"(假死)的状态。
        // 1.2.30:血量为 0 但还活着(传奇 Boss 的阶段保护,如云魔 DiedOnce)→ 直接强制死亡
        if (target.getHealth() <= 0.0F && target.isAlive()) {
            forceDie(target, source);
            return;
        }
        if (target.isDeadOrDying() || target.deathTime > 0) {
            return;
        }
        if (!this.voidAura) {
            target.invulnerableTime = 0;
            target.hurt(source, damage);
            applySchoolDamage(target, damage);
            return;
        }
        // 1.2.9g:虚空剑气改成**直接结算血量** —— 不走 hurt() 的伤害管线,
        // 于是任何"限伤/减伤/无敌窗口/无敌标志/伤害上限"都影响不到它(诡厄巫法的使徒、灾变各 BOSS 的
        // 限伤都是在 hurt()/LivingHurtEvent 里改这次伤害的数值或直接 return false,这里根本不经过那一层)。
        // 只有"致死"这一步仍然走 hurt(),为的是让原版死亡流程(die → dropAllDeathLoot)跑起来,掉落物正常。
        float before = target.getHealth();
        boolean wasInvulnerable = target.isInvulnerable();
        target.setInvulnerable(false);
        try {
            // 1.2.9h:照搬「矢量突破」的做法 —— 先清掉命中冷却(invulnerableTime/hurtTime/lastHurt),
            // 直接写血量,再把客户端那份 DATA_HEALTH_ID 同步过去,并补上击杀归属。
            clearHitCooldown(target);
            // 先吃吸收(和原版一致:伤害先扣金色血条)
            float absorption = target.getAbsorptionAmount();
            float absorbed = Math.min(absorption, damage);
            if (absorbed > 0.0F) {
                target.setAbsorptionAmount(absorption - absorbed);
            }
            float remaining = damage - absorbed;
            float left = before - remaining;
            if (left > 0.0F) {
                target.setHealth(left);
                // 1.2.13a:试验假人(dummmmmmy)是"无限血":TargetDummyEntity#setHealth 里
                // hasInfiniteHealth()=true 时不会真的改血量,伤害数字也只由它的 LivingHurtEvent
                // 监听(ModEvents#onEntityDamage)广播。所以血量写不进去时必须回退走 hurt(),
                // 否则虚空剑气在假人身上"完全不造成伤害/不出数字"。
                boolean healthChanged = target.getHealth() < before - 1.0E-4F;
                if (!healthChanged) {
                    clearHitCooldown(target);
                    target.hurt(source, damage);
                    applyKillCredit(target);
                    target.hurtTime = 10;
                    target.hurtDuration = 10;
                    logHit(target, damage, remaining, absorbed, false);
                    return;
                }
                forceSyncHealth(target, left);
                applyKillCredit(target);
                target.hurtTime = 10;
                target.hurtDuration = 10;
                logHit(target, damage, remaining, absorbed, false);
                return;
            }
            // 致死:压到 0.05 血再走一次 hurt,让 die()/掉落/经验/击杀统计都正常
            target.setHealth(Math.min(before, 0.05F));
            applyKillCredit(target);
            clearHitCooldown(target);
            target.hurt(source, 1000.0F);
            if (isLegendaryBoss(target) && !target.isRemoved()) {
                // 1.2.32:B 方案兜底 —— 限伤/阶段 Boss(云魔、湮灭体)即使 hurt 没打死,
                // 也直接走 die():清掉它自己的无敌/阶段标记后强制死亡,避免假死。
                prepareBossDeath(target);
                target.setHealth(0.0F);
                forceDie(target, source);
            }
            logHit(target, damage, remaining, absorbed, true);
        }
        finally {
            target.setInvulnerable(wasInvulnerable);
            applySchoolDamage(target, damage);
        }
    }
    /** 1.2.9h:清命中冷却(等价于矢量突破的 clearHitCooldown)。 */
    private static void clearHitCooldown(LivingEntity target) {
        try {
            target.invulnerableTime = 0;
            target.hurtTime = 0;
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 1.2.9h:把服务端血量写回同步字段(DATA_HEALTH_ID),客户端血条/血量才会跟着掉。 */
    private static void forceSyncHealth(LivingEntity target, float health) {
        try {
            java.lang.reflect.Field field = null;
            for (String name : new String[] { "DATA_HEALTH_ID", "f_20968_" }) {
                try {
                    field = LivingEntity.class.getDeclaredField(name);
                    break;
                }
                catch (NoSuchFieldException ignored) {
                    // 试下一个名字
                }
            }
            if (field == null) {
                return;
            }
            field.setAccessible(true);
            Object accessor = field.get(null);
            if (accessor instanceof net.minecraft.network.syncher.EntityDataAccessor<?> raw) {
                @SuppressWarnings("unchecked")
                net.minecraft.network.syncher.EntityDataAccessor<Float> typed =
                    (net.minecraft.network.syncher.EntityDataAccessor<Float>) raw;
                target.getEntityData().set(typed, health);
            }
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // 同步失败也不影响服务端数值
        }
    }

    /** 1.2.9h:补击杀归属(击杀统计/掉落归属/经验)。 */
    private void applyKillCredit(LivingEntity target) {
        try {
            if (getOwner() instanceof net.minecraft.world.entity.player.Player player) {
                target.setLastHurtByPlayer(player);
                target.setLastHurtByMob(player);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 1.2.9h:前几次命中无条件打日志(不用开 debugLog),方便确认到底打没打进去。 */
    private static final java.util.concurrent.atomic.AtomicInteger HIT_LOGS = new java.util.concurrent.atomic.AtomicInteger();

    private static void logHit(LivingEntity target, float damage, float remaining, float absorbed, boolean lethal) {
        if (HIT_LOGS.get() >= 8) {
            return;
        }
        HIT_LOGS.incrementAndGet();
        DebugLog.info("[Aura] 虚空命中 {}:期望 {} / 实扣 {} / 吸收 {} / 剩余 {} / 致死 {}",
            target.getName().getString(), damage, remaining, absorbed,
            String.format("%.1f", target.getHealth()), lethal);
    }



    /** 1.2.9c:血量清零 + 调用原版 die(),保证"强行击杀"也有掉落物。 */
    /** 1.2.28:把传奇 Boss 自己的"无敌计时/阶段标记"清掉,让 die() 能真的走完。 */
    /** 1.2.34:只有传奇 Boss(legendary_monsters)才有"假死/阶段"设计,其余实体一律不干预。 */
    private static boolean isLegendaryBoss(LivingEntity target) {
        return target.getClass().getName().startsWith("net.miauczel.legendary_monsters.entity.");
    }

    private static void prepareBossDeath(LivingEntity target) {
        try {
            String name = target.getClass().getName();
            if (name.endsWith("Cloud_GolemEntity")) {
                setBooleanField(target, "DiedOnce", true);
                setIntField(target, "GolemInvulnerabilityTime", 0);
            }
            setIntField(target, "InvulnerabilityTime", 0);   // 传奇 Boss 父类字段
            com.hengx.enigmaticenhance.DebugLog.info("[Boss] {} 特判执行(类={})", target.getName().getString(), name);
        }
        catch (Throwable ignored) {
            // 非传奇 Boss 或字段不存在 ⇒ 不处理
        }
    }

    /** 反射把一个整型字段写成指定值(继承链上找)。 */
    private static void setIntField(Object target, String field, int value) {
        try {
            Class<?> cls = target.getClass();
            while (cls != null && cls != Object.class) {
                try {
                    java.lang.reflect.Field f = cls.getDeclaredField(field);
                    f.setAccessible(true);
                    f.setInt(target, value);
                    return;
                }
                catch (NoSuchFieldException ignored) {
                    cls = cls.getSuperclass();
                }
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }

    /** 反射把一个布尔字段写成指定值(继承链上找)。 */
    private static void setBooleanField(Object target, String field, boolean value) {
        try {
            Class<?> cls = target.getClass();
            while (cls != null && cls != Object.class) {
                try {
                    java.lang.reflect.Field f = cls.getDeclaredField(field);
                    f.setAccessible(true);
                    f.setBoolean(target, value);
                    return;
                }
                catch (NoSuchFieldException ignored) {
                    cls = cls.getSuperclass();
                }
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }

    private static void forceDie(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        // 1.2.33:先用**独立的无视无敌伤害源**(genericKill)走一次 —— 它带
        // BYPASSES_INVULNERABILITY 标签,多数"限伤/阶段"Boss 会认可它,而且不经过普通 hurt 的减伤分支。
        try {
            target.setHealth(Math.min(target.getHealth(), 0.01F));
            target.kill();
        }
        catch (Throwable ignored) {
            // 失败继续走下面的 die() 兜底
        }
        if (target.isRemoved()) {
            return;
        }
        // 1.2.28:传奇 Boss 的"无敌/阶段"不走原版 invulnerableTime,需要按类名特判。
        //   · 云魔(Cloud_GolemEntity):字段 DiedOnce 为 false 时 die() 会走"二阶段"而不真正死亡 → 强制置 true;
        //   · 湮灭构筑体(TheObliteratorEntity):字段 InvulnerabilityTime 是它自己的无敌计时 → 清零。
        prepareBossDeath(target);
        try {
            if (target.getHealth() > 0.0F) {
                target.setHealth(0.0F);
            }
            // 1.2.9c:反射调原版 die(DamageSource) —— 运行期是 SRG 名(m_6667_),开发环境是 die,两个都试。
            // 走 die() 才会执行 dropAllDeathLoot/经验/击杀统计,掉落物不会丢。
            java.lang.reflect.Method die = null;
            for (String name : new String[] { "die", "m_6667_" }) {
                try {
                    die = LivingEntity.class.getDeclaredMethod(name, net.minecraft.world.damagesource.DamageSource.class);
                    break;
                }
                catch (NoSuchMethodException ignored) {
                    // 试下一个名字
                }
            }
            if (die != null) {
                com.hengx.enigmaticenhance.DebugLog.info("[Boss] {} 调用 die(),血量={}", target.getName().getString(), target.getHealth());
                die.setAccessible(true);
                die.invoke(target, source);
                // 1.2.27:血量 0 却没死(传奇怪物的死亡保护)—— 直接移除实体,
                // 掉落与经验已经在 die()/hurt() 里结算过,这里只是让它彻底消失。
            if (isLegendaryBoss(target) && !target.isRemoved()) {
                    com.hengx.enigmaticenhance.DebugLog.info("[Boss] {} die() 后仍存活 → remove(KILLED)", target.getName().getString());
                target.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                target.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                }
            }
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // 极端情况下(名字都对不上)保持原样:至少血量已经清零
        }
    }

    /** 1.2.9b:命中后按学派分别追加对应学派的伤害(元素魔法)。 */
    private void applySchoolDamage(LivingEntity target, float baseDamage) {
        try {
            Entity owner = getOwner();
            if (owner instanceof net.minecraft.world.entity.player.Player player
                && ElementalMagicHandler.hasEnchantment(player, player.getMainHandItem())) {
                ElementalMagicHandler.applySchoolDamage(target, baseDamage, player);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 缺铁魔法 / 学派异常:只丢加成,不影响剑气本体伤害
        }
    }

    /**
     * 1.2.3a:暴击反馈 —— 和原版跳跃暴击一样出暴击粒子 + 暴击音效(玩家造成的那种)。
     */
    private void critFeedback(LivingEntity target) {
        try {
            if (level() instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    12, 0.25D, 0.3D, 0.25D, 0.15D);
            }
            level().playSound(null, target.getX(), target.getY(), target.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯表现,失败不影响伤害
        }
    }

    /** 法术强度加成:反射读取 irons_spellbooks 的法强属性,取不到就 1.0。 */
    private float spellPowerMultiplier() {
        try {
            var key = net.minecraft.resources.ResourceLocation.tryParse("irons_spellbooks:spell_power");
            if (key == null) {
                return 1.0F;
            }
            var attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(key);
            if (attr == null) {
                return 1.0F;
            }
            Entity owner = getOwner();
            if (owner instanceof LivingEntity living && living.getAttributes().hasAttribute(attr)) {
                double value = living.getAttributeValue(attr);
                return (float) Math.max(0.1D, 1.0D + value);
            }
        }
        catch (Throwable ignored) {
            // 没装 irons_spellbooks 就只吃基础伤害
        }
        return 1.0F;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.enchantLevel = Math.max(1, tag.getInt("AuraLevel"));
        this.hits = tag.getInt("AuraHits");
        this.auraDamage = tag.contains("AuraDamage") ? tag.getFloat("AuraDamage") : 1.0F;
        this.crit = tag.getBoolean("AuraCrit");
        this.voidAura = tag.getBoolean("AuraVoid");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AuraLevel", enchantLevel);
        tag.putInt("AuraHits", hits);
        tag.putFloat("AuraDamage", auraDamage);
        tag.putBoolean("AuraCrit", crit);
        tag.putBoolean("AuraVoid", voidAura);
    }

    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
