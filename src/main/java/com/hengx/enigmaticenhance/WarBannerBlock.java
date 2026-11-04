package com.hengx.enigmaticenhance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.2.29:战争之旗。
 *
 * <p><b>为什么不用原版 BannerBlock</b>:原版 {@code BannerRenderer} 强制读取方块的
 * {@code facing}/{@code rotation} 属性,用 {@code AbstractBannerBlock} 会崩;
 * 换成 {@code WallBannerBlock} 又会在放置时与 {@code BlockItem} 互相递归
 * (StackOverflowError)。所以这里回到**普通方块 + 自定义模型**,外观靠模型贴近旗帜。</p>
 *
 * <p>功能:每 3 秒(60 tick)对半径 12 格球体内所有实体(含玩家)施加 5 秒血色之灾;
 * 无碰撞体积;硬度 1.0F、木质音效、斧类工具可采集。</p>
 */
public class WarBannerBlock extends Block {

    public static final double RADIUS = 12.0D;
    public static final int APPLY_INTERVAL = 60;
    public static final int DURATION_TICKS = 100;

    public WarBannerBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(1.0F)
            .sound(SoundType.WOOD)
            .noCollission()
            .noOcclusion());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, APPLY_INTERVAL);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        MobEffect effect = ModEffects.bloodCalamityOrNull();
        if (effect != null) {
            double cx = pos.getX() + 0.5D;
            double cy = pos.getY() + 0.5D;
            double cz = pos.getZ() + 0.5D;
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - RADIUS, cy - RADIUS, cz - RADIUS, cx + RADIUS, cy + RADIUS, cz + RADIUS))) {
                if (entity.distanceToSqr(cx, cy, cz) <= RADIUS * RADIUS) {
                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        effect, DURATION_TICKS, 0, false, true, true));
                }
            }
        }
        level.scheduleTick(pos, this, APPLY_INTERVAL);
    }
}