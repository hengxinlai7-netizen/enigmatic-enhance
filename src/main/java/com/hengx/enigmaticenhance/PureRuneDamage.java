package com.hengx.enigmaticenhance;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 「纯净符文」的落点结算(1.1.28c):半径 3 格、5 点魔法伤害(亡灵 15 点)、会被方块阻挡。
 *
 * <h2>伤害来源</h2>
 * {@link #source(Level, Entity, Entity)} 显式构造:{@code DamageTypes.MAGIC} + (直接实体 = 投掷物,
 * 施加者 = 玩家)。所以目标挨的这一下是**玩家的魔法伤害**
 * ({@code source.getEntity() == 玩家}、{@code source.is(DamageTypes.MAGIC)}),走原版伤害管线
 * (抗性/保护附魔/无敌帧照常),不是"直接减血"。
 *
 * <h2>范围与遮挡</h2>
 * <ul>
 * <li>候选:{@code AABB(落点 ± 3)} 内的 {@link LivingEntity},再按"实体位置到落点的距离 ≤ 3"过滤;</li>
 * <li><b>会被方块阻挡</b>:{@link #blocked} 从落点向目标的**眼睛**做一次
 *     {@code level.clip(ClipContext.Block.COLLIDER, Fluid.NONE)},命中方块就跳过这个目标
 *     (所以躲在墙后/坑里的人不吃伤害,和爆炸的"看得见才炸到"同一口径);</li>
 * <li>排除施加者本人与投掷物实体。</li>
 * </ul>
 *
 * <h2>白光</h2>
 * 每次结算都在落点 spawn 一个 {@link PureRuneFlash}(1 秒后自己消失);
 * 画面完全在客户端,服务端不改任何方块/光照。
 */
public final class PureRuneDamage {

    /** 半径(格)。 */
    public static final double RADIUS = 3.0D;

    /** 普通目标伤害。 */
    public static final float DAMAGE = 5.0F;

    /** 亡灵({@code MobType.UNDEAD})伤害。 */
    public static final float UNDEAD_DAMAGE = 15.0F;

    private PureRuneDamage() {}

    /** 构造"玩家的魔法伤害"来源:类型 MAGIC,直接实体 = 投掷物,施加者 = 玩家。 */
    public static DamageSource source(Level level, Entity direct, Entity owner) {
        Entity attacker = owner == null ? direct : owner;
        return new DamageSource(
            level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.MAGIC),
            direct, attacker);
    }

    /** 落点结算:先放白光,再对范围内的目标逐个结算伤害。只在服务端调用。 */
    public static void detonate(Level level, Vec3 center, Entity direct, Entity owner) {
        if (level == null || center == null || level.isClientSide) {
            return;
        }
        level.addFreshEntity(new PureRuneFlash(level, center.add(0.0D, 0.05D, 0.0D)));
        DamageSource source = source(level, direct, owner);
        AABB area = new AABB(center, center).inflate(RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == owner || target == direct || !target.isAlive()) {
                continue;
            }
            if (target.position().distanceToSqr(center) > RADIUS * RADIUS) {
                continue;
            }
            if (blocked(level, center, target)) {
                DebugLog.info("{} 目标 {} 被方块挡住,跳过", "[PureRune]", target.getName().getString());
                continue;
            }
            boolean undead = target.getMobType() == MobType.UNDEAD;
            float amount = undead ? UNDEAD_DAMAGE : DAMAGE;
            target.hurt(source, amount);
            DebugLog.info("{} 命中 {}:{} 点({} )", "[PureRune]", target.getName().getString(), amount,
                undead ? "亡灵" : "普通");
        }
    }

    /** 落点到目标眼睛的射线是否被方块挡住。 */
    public static boolean blocked(Level level, Vec3 center, LivingEntity target) {
        Vec3 eye = target.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(center, eye, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.BLOCK;
    }
}
