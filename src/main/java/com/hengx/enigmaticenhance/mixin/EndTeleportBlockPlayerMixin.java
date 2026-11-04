package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.EnigmaticDragonGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 1.5.14:服务端玩家传送(跨维度 / 指令 / 模组)同样封锁。 */
@Mixin(ServerPlayer.class)
public class EndTeleportBlockPlayerMixin {

    @Inject(method = "m_8999_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$block(ServerLevel level, double x, double y, double z,
                                        float yaw, float pitch, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (EnigmaticDragonGate.dragonAliveInEnd(self) || EnigmaticDragonGate.dragonAliveIn(level)) {
            ci.cancel();
        }
    }
}