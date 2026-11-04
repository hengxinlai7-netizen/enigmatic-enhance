package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.EnigmaticDragonGate;
import com.hengx.enigmaticenhance.DebugLog;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 1.6.0:末影龙存活时,**末地维度内所有远距离位置跳变**(传送)全部失效。
 *
 * <p>探险者指南针、Xaero 世界地图、末影珍珠、指令 tp、模组传送最终都会把玩家坐标
 * 直接写到 {@code Entity#setPos}({@code m_6034_})。这里对**玩家**做拦截:</p>
 * <ul>
 *   <li>只在末地维度、且存在存活末影龙时生效;</li>
 *   <li>只有位移超过 8 格才拦(正常行走/击退不受影响)。</li>
 * </ul>
 */
@Mixin(Entity.class)
public class EndSetPosBlockMixin {

    @Inject(method = "m_6034_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockJump(double x, double y, double z, CallbackInfo ci) {
        try {
            Entity self = (Entity) (Object) this;
            if (!(self instanceof ServerPlayer)) {
                return;
            }
            if (!EnigmaticDragonGate.dragonAliveIn(self.level())) {
                return;
            }
            double dx = x - self.getX();
            double dy = y - self.getY();
            double dz = z - self.getZ();
            if (dx * dx + dy * dy + dz * dz > 64.0D) {
                DebugLog.info("[Teleport] 拦截玩家位移 {}", self.getName().getString());
                ci.cancel();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}