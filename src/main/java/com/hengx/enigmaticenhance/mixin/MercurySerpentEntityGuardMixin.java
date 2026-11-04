package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.MercurySerpentHandler;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.4.1:「水银之蛇」的死亡保护**写死到移除与写血入口**。
 *
 * <p>之前的保护只拦 {@code LivingDeathEvent} 与 {@code hurt},所以"直接 discard/remove"
 * 或"setHealth(0) 后不跑死亡流程"都能绕过。这里补上:</p>
 * <ul>
 *   <li>{@code Entity#remove(RemovalReason)} —— 受保护时拒绝 KILLED 移除;</li>
 *   <li>{@code LivingEntity#setHealth(float)} —— 受保护时把 ≤0 的写入改成 1.0。</li>
 * </ul>
 *
 * <p>唯一的"逃生口"是引擎级的区块/维度卸载(那属于存档结构,不该拦)。</p>
 */
@Mixin(Entity.class)
public class MercurySerpentEntityGuardMixin {

    @Inject(method = "m_142467_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockKilledRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        try {
            if (reason == Entity.RemovalReason.CHANGED_DIMENSION || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
                return;   // 区块/维度相关的移除不拦
            }
            Entity self = (Entity) (Object) this;
            if (!(self instanceof LivingEntity living)) {
                return;
            }
            if (MercurySerpentHandler.protectedFromDeath(living)) {
                MercurySerpentHandler.consumeShieldOnBlock(living);
                MercurySerpentHandler.resetDyingState(living);
                ci.cancel();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就允许移除
        }
    }
}