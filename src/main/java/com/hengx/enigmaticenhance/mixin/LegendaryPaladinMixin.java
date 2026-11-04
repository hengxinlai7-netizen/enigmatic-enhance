package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.DebugLog;
import com.hengx.enigmaticenhance.HandOfExecutionHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 2.11.1:执行之手对 LegendaryPaladin 破防,并跳过它的死亡动画免疫。 */
@Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.PossessedPaladin.PossessedPaladinEntity", remap = false)
public class LegendaryPaladinMixin {

    @Inject(method = "m_6469_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$executionHurt(DamageSource source, float amount,
        CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!HandOfExecutionHandler.shouldBypass(source)) {
                return;
            }
            LivingEntity self = (LivingEntity) (Object) this;
            DebugLog.warn("[BossBypass] {} 命中 Mixin", self.getClass().getSimpleName());
            float next = Math.max(0.0F, self.getHealth() - amount);
            if (next > 0.0F) {
                self.setHealth(next);
                cir.setReturnValue(Boolean.TRUE);
                return;
            }
            // 致死:完全跳过 die()(它有死亡动画免疫/复活),直接补掉落 + 移除
            if (source.getEntity() instanceof Player p) {
                self.setLastHurtByPlayer(p);
            }
            self.setHealth(0.0F);
            self.die(source);   // 先走它的 die() —— 掉落/经验都在里面
            if (self.isAlive()) {
                // 被死亡动画保护复活了 → 稍后强制移除(掉落已经在上面结算过)
                final LivingEntity victim = self;
                if (!self.level().isClientSide) {
                    ((net.minecraft.server.level.ServerLevel) self.level()).getServer().execute(() -> {
                        try {
                            if (victim.isAlive()) {
                                victim.setHealth(0.0F);
                                victim.die(source);
                                // 2.11.9:它的 die() 只播动画不结算掉落 —— 这里强制补一次掉落结算
                                try {
                                    com.hengx.enigmaticenhance.DropHelper.dropAllDeathLoot(victim, source);
                                }
                                catch (RuntimeException | LinkageError ignored) {
                                    // 忽略
                                }
                                victim.remove(Entity.RemovalReason.KILLED);
                                DebugLog.warn("[BossBypass] {} 复活后被强制移除", victim.getName().getString());
                            }
                        }
                        catch (RuntimeException | LinkageError ignored) {
                            // 忽略
                        }
                    });
                }
            }
            cir.setReturnValue(Boolean.TRUE);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 放行
        }
    }
}