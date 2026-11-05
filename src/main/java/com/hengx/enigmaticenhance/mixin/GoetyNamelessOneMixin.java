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

/** 2.11.1:执行之手对 GoetyNamelessOne 破防,并跳过它的死亡动画免疫。 */
@Mixin(targets = "com.k1sak1.goetyawaken.common.entities.hostile.undead.necromancer.AbstractNamelessOne", remap = false)
public class GoetyNamelessOneMixin {

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
            if (!self.isRemoved()) {
                // 被死亡动画保护复活了 → 稍后强制移除(掉落已经在上面结算过)
                final LivingEntity victim = self;
                if (!self.level().isClientSide) {
                    ((net.minecraft.server.level.ServerLevel) self.level()).getServer().execute(() -> {
                        try {
                            if (!victim.isRemoved()) {
                                // 3.8.1:区分两种收尾情形,避免重复结算掉落
                                //   复活=true  :它靠死亡动画/复活机制活回来了,掉落大概率没结算 ⇒ 补一次
                                //   复活=false :血已经 0 但实体没进移除队列(卡死),掉落通常已由上面的 die() 结算
                                boolean revived = victim.getHealth() > 0.0F;
                                victim.setHealth(0.0F);
                                if (revived) {
                                    victim.die(source);
                                    try {
                                        com.hengx.enigmaticenhance.DropHelper.dropAllDeathLoot(victim, source);
                                    }
                                    catch (RuntimeException | LinkageError ignored) {
                                        // 忽略
                                    }
                                }
                                victim.remove(Entity.RemovalReason.KILLED);
                                DebugLog.warn("[BossBypass] {} 已强制移除(复活={})",
                                    victim.getName().getString(), revived);
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