package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.DebugLog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.RingConfig;
import com.hengx.enigmaticenhance.ScrollHandler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 「万钧之护卷轴」的雷击开关(逐件,物品 NBT)。
 * <p>
 * 反编译依据(enigmaticaddons 1.2.6 与实例 1.2.5 的 {@code AddonEventHandler#onEntityHurt} 都核对过):
 * <pre>
 * if (hasCurio(攻击者, THUNDER_SCROLL)) {                       // 1002
 *     if (攻击者.getMainHandItem().canPerformAction(SWORD_SWEEP)) {   // 1008-1019 主手是剑
 *         int electric = 目标.getPersistentData().getInt("Electric");
 *         目标.getPersistentData().putInt("Electric",
 *             electric + 60 + rand(80) + (int)(event.getAmount() * 10.0F));   // &lt;= 就是这一处 putInt
 *     }
 *     event.setAmount(ThunderScroll.modify(目标, event.getAmount()));   // 护甲穿透,没碰
 * }
 * </pre>
 * 于是:
 * <ul>
 * <li>HEAD 处先算好"这次命中是否允许给目标累积电荷"({@link ScrollHandler#allowLightningCharge});

 *     结果放在 {@link #CHARGE_ALLOWED} 里(同一次方法调用内读,{@code ThreadLocal} 保证线程安全);</li>
 * <li>再用 {@code @Redirect} 包住方法体内**所有** {@code CompoundTag.putInt(String,int)} 调用,
 *     只对 key = "Electric" 且本次不允许时跳过写入(等价于"不累积"),其余 key 原样透传,
 *     所以不依赖 ordinal、也不受其它模组注入影响的顺序变化影响;</li>
 * <li>护甲穿透的 {@code setAmount} 在 if 之外,依旧生效 —— 关掉雷击不会顺带关掉别的东西。</li>
 * </ul>
 * 关掉雷击后目标永远累积不到 {@code onTick} 里的阈值,自然不会再落雷。
 */
@Mixin(targets = "auviotre.enigmatic.addon.handlers.AddonEventHandler")
public class AddonThunderLightningMixin {

    private static final String ELECTRIC = "Electric";

    /** onEntityHurt 期间"本次命中是否允许累积电荷"。HEAD 写、putInt 处读,同一次调用内完成。 */
    private static final ThreadLocal<Boolean> CHARGE_ALLOWED = ThreadLocal.withInitial(() -> Boolean.TRUE);

    @Inject(method = "onEntityHurt(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V",
        at = @At("HEAD"), remap = false)
    private void enigmaticenhance$decideLightningCharge(LivingHurtEvent event, CallbackInfo ci) {
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        boolean allowed = ScrollHandler.allowLightningCharge(attacker);
        CHARGE_ALLOWED.set(allowed);
        if (!allowed && RingConfig.debugLog()) {
            DebugLog.info("[卷轴雷击] 本次命中不累积电荷(雷击已关或概率未中)");
        }
    }

    @Redirect(method = "onEntityHurt(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;m_128405_(Ljava/lang/String;I)V"),
        remap = false)
    private void enigmaticenhance$gateElectric(CompoundTag tag, String key, int value) {
        if (ELECTRIC.equals(key) && !CHARGE_ALLOWED.get()) {
            return; // 不写 = 电荷保持原值,即"这一击不累积"
        }
        tag.putInt(key, value);
    }
}
