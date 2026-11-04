package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 「纯净符文」落点那道**白光**的视觉体(1.1.28c):{@code enigmaticenhance:pure_rune_flash}。
 * <p>
 * 纯视觉:没有碰撞、不可选中、不可攻击、没有任何存档数据(不进 NBT、不读 NBT),
 * 服务端只负责在落点 spawn 一个、活满 {@value #LIFETIME} tick 就删掉;
 * 真正的画面由客户端 {@code PureRuneFlashRenderer} 画(贴地的白色发光圆环 + 一层很淡的内盘,
 * 见该类注释里"不遮挡实体"的实现细节)。
 * <p>
 * 半径常量 {@link PureRuneDamage#RADIUS} 与伤害判定共用同一个值,不会出现"看着 3 格、打 4 格"。
 */
public class PureRuneFlash extends Entity {

    /** 存在时长:20 tick = 1 秒。 */
    public static final int LIFETIME = 20;

    public PureRuneFlash(EntityType<? extends PureRuneFlash> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public PureRuneFlash(Level level, Vec3 pos) {
        this(ModEntities.PURE_RUNE_FLASH.get(), level);
        setPos(pos);
    }

    @Override
    protected void defineSynchedData() {
        // 纯视觉,不需要同步任何字段
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 纯视觉,不读存档
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 纯视觉,不写存档
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFETIME) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 只在 64 格内画(1 秒的东西,远到看不清也没意义)。 */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 64.0D * 64.0D;
    }
}
