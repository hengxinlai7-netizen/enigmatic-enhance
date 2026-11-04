package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.DemonBoneHandler;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 1.1.28e 三件套(恶魔契约 + 恶魔的血杯 + 恶魔的手骨)的第二条:**无敌帧时间 +50%**。
 *
 * <h2>注入点</h2>
 * 原版 {@code LivingEntity#hurt(DamageSource, float)}(SRG {@code m_6469_})在真正吃下伤害之后
 * 会把 {@code invulnerableTime} 设成 20 tick(玩家 10 tick,受难度/其它模组影响)。这里在方法
 * RETURN 处(且只有这次 hurt 真的造成了伤害,返回值 {@code true} 时)把它乘以 1.5:
 * <ul>
 * <li>只对**穿着三件套的玩家本人**生效,其它实体/其它玩家一个字节都不碰;</li>
 * <li>只放大那一次 hit 之后的无敌帧,**不会**让它变成"永久无敌"—— 无敌帧照样会被时间消耗掉;</li>
 * <li>不改变伤害数值、不改变击退、不改变任何其它状态;</li>
 * <li>{@code require = 0} + 登记在 {@code enigmaticenhance.optional.mixins.json}:目标改名也只是不生效。</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class LivingEntityDemonSetMixin {

    @Inject(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("RETURN"), remap = false, require = 0)
    private void enigmaticenhance$longerInvulnerable(DamageSource source, float amount,
        CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!Boolean.TRUE.equals(cir.getReturnValue())) {
                return;
            }
            if (!((Object) this instanceof Player player) || player.level().isClientSide) {
                return;
            }
            if (!DemonBoneHandler.fullSet(player)) {
                return;
            }
            player.invulnerableTime = (int) Math.ceil(player.invulnerableTime * 1.5D);
        }
        catch (Throwable ignored) {
            // 任何意外都不影响这次伤害
        }
    }
}
