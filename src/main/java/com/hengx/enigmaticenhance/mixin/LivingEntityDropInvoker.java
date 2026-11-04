package com.hengx.enigmaticenhance.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 2.11.1:暴露 LivingEntity.dropAllDeathLoot,用于"跳过死亡动画直接结算掉落"。 */
@Mixin(LivingEntity.class)
public interface LivingEntityDropInvoker {

    @Invoker("dropAllDeathLoot")
    void enigmaticenhance$dropAllDeathLoot(DamageSource source);
}