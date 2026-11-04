package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.GluttonyHandler;

import net.minecraft.world.entity.player.Player;

/**
 * 修复「佩戴无止暴食之证时无法疾跑」。
 * <p>
 * 真实原因(1.20.1 字节码实测):客户端 {@code LocalPlayer} 的起跑/续跑门槛都是同一个私有谓词
 * {@code hasEnoughFoodToStartSprinting()}(官方名 / SRG {@code m_255269_()Z}),其中
 * {@code isPassenger() || getFoodData().getFoodLevel() > 6.0f || getAbilities().mayfly},
 * 而暴食效果把 foodLevel 钉死为 0 → 起跑被拒({@code canStartSprinting}→该谓词),
 * 且已在冲刺时被 {@code aiStep} 里的 {@code flag9 = !hasForwardImpulse() || !该谓词} 判定为"该停下"。
 * 因此只放宽这一个谓词即可同时覆盖"起跑"和"持续冲刺",不影响未佩戴时的原版行为。
 * <p>
 * 本 mixin 是客户端专属(注册在 mixin 配置的 "client" 段),专用服务器不会加载它;
 * 类里不引用任何客户端类型,只通过 {@code targets} 字符串定位。
 */
@Mixin(targets = "net.minecraft.client.player.LocalPlayer")
public abstract class GluttonySprintMixin {

    @Inject(method = "m_255269_()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$sprintWithCharm(CallbackInfoReturnable<Boolean> cir) {
        if (GluttonyHandler.sprintAllowed((Player) (Object) this)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
