package com.hengx.enigmaticenhance;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.2.11a:「镜碎」的伤害结算 —— 带着这个状态的生物受到的伤害 ×2(即 +100%)。
 */
public final class MirrorShatterHandler {

    /** 受伤倍率(2.0 = +100%)。 */
    public static final float DAMAGE_MULTIPLIER = 2.0F;

    private MirrorShatterHandler() {
    }

    /** 给目标挂镜碎(秒)。 */
    public static void apply(LivingEntity entity, int seconds) {
        if (entity == null || seconds <= 0) {
            return;
        }
        var effect = ModEffects.mirrorShatterOrNull();
        if (effect == null) {
            return;
        }
        entity.addEffect(new MobEffectInstance(effect, seconds * 20, 0, false, true, true));
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.mirrorShatterEnabled()) {
            return;
        }
        try {
            LivingEntity entity = event.getEntity();
            var effect = ModEffects.mirrorShatterOrNull();
            if (entity == null || effect == null || !entity.hasEffect(effect)) {
                return;
            }
            event.setAmount(event.getAmount() * DAMAGE_MULTIPLIER);
            DebugLog.info("[MirrorShatter] {} 处于镜碎,受到伤害 x{}", entity.getName().getString(),
                DAMAGE_MULTIPLIER);
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[MirrorShatter] 结算异常", ex);
        }
    }
}
