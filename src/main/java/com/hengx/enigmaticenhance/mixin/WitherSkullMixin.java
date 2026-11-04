package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1.3.6:凋零之首强化。
 *
 * <ul>
 *   <li>爆炸半径 → 1.5 格(原版 1.0);</li>
 *   <li>命中实体额外造成目标 **5% 最大生命** 的伤害(判空);</li>
 *   <li>**蓝色(危险的)凋零之首**自动追踪最近的玩家;目标为空/已死则不做任何事。</li>
 * </ul>
 */
@Mixin(WitherSkull.class)
public class WitherSkullMixin {

    /** 发射后无视实体/方块的时长(2 秒 = 40 tick)。 */
    private static final int PHASE_TICKS = 40;

    /** 蓝色凋零之首:每 tick 微调速度朝向目标。 */
    @Inject(method = "m_8119_", at = @At("TAIL"), remap = false, require = 0)
    private void enigmaticenhance$homing(CallbackInfo ci) {
        try {
            WitherSkull self = (WitherSkull) (Object) this;
            // 1.3.7:发射后前 2 秒(40 tick)无视实体与方块
            boolean phasing = self.tickCount <= PHASE_TICKS;
            if (self.noPhysics != phasing) {
                self.noPhysics = phasing;
            }
            if (self.level().isClientSide || !self.isDangerous()) {
                return;
            }
            Player target = self.level().getNearestPlayer(self.getX(), self.getY(), self.getZ(), 32.0D,
                player -> player != null && player.isAlive() && !player.isSpectator()
                    && player != self.getOwner());
            if (target == null || !target.isAlive()) {
                return;   // 空处理:追踪不到玩家就不改速度,绝不崩
            }
            Vec3 aim = new Vec3(target.getX() - self.getX(),
                target.getY(0.5D) - self.getY(),
                target.getZ() - self.getZ()).normalize();
            Vec3 motion = self.getDeltaMovement();
            double speed = Math.max(0.05D, motion.length());
            self.setDeltaMovement(motion.scale(0.75D).add(aim.scale(speed * 0.45D)).normalize().scale(speed));
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就不追踪
        }
    }

    /** 命中实体:额外 5% 最大生命伤害。 */
    @Inject(method = "m_5790_", at = @At("TAIL"), remap = false, require = 0)
    private void enigmaticenhance$bonusDamage(EntityHitResult hit, CallbackInfo ci) {
        try {
            if (hit == null || hit.getEntity() == null) {
                return;   // 空处理
            }
            if (!(hit.getEntity() instanceof LivingEntity target) || !target.isAlive()) {
                return;
            }
            WitherSkull self = (WitherSkull) (Object) this;
            if (self.level().isClientSide) {
                return;
            }
            float bonus = target.getMaxHealth() * 0.05F;
            if (bonus > 0.0F) {
                target.hurt(self.damageSources().magic(), bonus);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 爆炸半径 → 1.5 格。 */
    @ModifyArg(method = "m_6532_", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;m_254845_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"),
        index = 6, remap = false, require = 0)
    private float enigmaticenhance$radius(float radius) {
        return 1.5F;
    }
}