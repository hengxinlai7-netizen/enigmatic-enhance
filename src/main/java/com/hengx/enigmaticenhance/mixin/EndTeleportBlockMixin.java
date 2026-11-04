package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.EnigmaticDragonGate;
import com.hengx.enigmaticenhance.EnigmaticEnhance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;

/**
 * 1.5.14:末影龙存活时,**末地维度内所有底层传送**全部失效。
 *
 * <p>覆盖:</p>
 * <ul>
 *   <li>{@code Entity#teleportTo(double,double,double)}({@code m_6021_});</li>
 *   <li>{@code Entity#teleportTo(double,double,double)} 的旧入口({@code m_20334_});</li>
 *   <li>{@code ServerPlayer#teleportTo(ServerLevel,double,double,double,float,float)}({@code m_8999_})。</li>
 * </ul>
 */
@Mixin(Entity.class)
public class EndTeleportBlockMixin {

    @Inject(method = "m_6021_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockA(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (EnigmaticDragonGate.dragonAliveInEnd(self)) {
            ci.cancel();
        }
    }

    @Inject(method = "m_20334_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$blockB(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (EnigmaticDragonGate.dragonAliveInEnd(self)) {
            ci.cancel();
        }
    }
}