package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 「纯净符文」的投掷物(1.1.28c):{@code enigmaticenhance:pure_rune}。
 * <p>
 * 行为与雪球/鸡蛋同一套({@link ThrowableItemProjectile}),只在两处不同:
 * <ul>
 * <li>{@link #onHitEntity}/{@link #onHitBlock}:在落点结算一次 {@link PureRuneDamage#detonate}
 *     然后立刻消失(不反弹、不落地继续存在);</li>
 * <li>{@link #tick}:飞满 {@link #MAX_FLIGHT_TICKS}(60 tick = 3 秒)还没命中就自行在当前位置结算,
 *     防止它一直飞下去(也顺手把"扔到虚空"这类情况处理掉)。</li>
 * </ul>
 * 防重复结算用 {@link #detonated} 一个布尔量(同一个 tick 里 onHit 与超时都可能触发)。
 */
public class PureRuneProjectile extends ThrowableItemProjectile {

    /** 最长飞行时间(3 秒),到点自动落地结算。 */
    public static final int MAX_FLIGHT_TICKS = 60;

    private boolean detonated;

    public PureRuneProjectile(EntityType<? extends PureRuneProjectile> type, Level level) {
        super(type, level);
    }

    public PureRuneProjectile(Level level, LivingEntity owner) {
        super(ModEntities.PURE_RUNE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        Item rune = ModItems.pureRuneOrNull();
        return rune == null ? Items.AIR : rune;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        detonate(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        detonate(Vec3.atCenterOf(result.getBlockPos()).add(0.0D, 0.5D, 0.0D));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !detonated && tickCount > MAX_FLIGHT_TICKS) {
            detonate(position());
        }
    }

    /** 落点结算(视觉 + 伤害),然后丢弃自己。只在服务端执行。 */
    private void detonate(Vec3 at) {
        if (detonated || level().isClientSide) {
            return;
        }
        detonated = true;
        PureRuneDamage.detonate(level(), at, this, getOwner());
        discard();
    }
}
