package com.hengx.enigmaticenhance;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 失落引擎的雷击惩罚:彻底掐掉。
 * <p>
 * 反编译事实(AddonEventHandler.onEntityDamage,off 641~900):玩家佩戴失落引擎且受到
 * {@code DamageTypeTags.IS_LIGHTNING} 伤害时,它会把伤害改写成
 * {@code amount*(4~7) + getMaxHealth()} ⇒ 必死,同时按 maxDamage 摧毁护甲与饰品。
 * <p>
 * 因此在**最早**的一层({@link LivingAttackEvent})就取消这次攻击:LivingHurt/LivingDamage
 * 全部不会触发,它那段改写也就没有机会跑,护甲/饰品同样不会被摧毁。
 * 另在 {@link LivingDamageEvent} 兜一层(LOWEST),防止有别的路径绕过 LivingAttackEvent。
 * 仅对"佩戴失落引擎的玩家 + 闪电伤害"生效,不影响任何其它伤害与雷电。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EngineDamageGuard {

    private EngineDamageGuard() {}

    private static boolean isEngineLightning(net.minecraft.world.damagesource.DamageSource source,
                                             net.minecraft.world.entity.Entity entity) {
        if (!RingConfig.disableEngineLightningItemClear()) {
            return false;
        }
        if (!(entity instanceof Player)) {
            return false;
        }
        if (!source.is(DamageTypeTags.IS_LIGHTNING)) {
            return false;
        }
        Player player = (Player) entity;
        Item engine = RingConfig.engineItem();
        return engine != null && Superposition.hasItem(player, engine);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (isEngineLightning(event.getSource(), event.getEntity())) {
            event.setCanceled(true);
            // 默认静默:debugLog 关闭时不查用户名/伤害 id,也不拼日志字符串。
            if (RingConfig.debugLog()) {
                DebugLog.info("[惩罚拦截] 佩戴失落引擎,已取消这次闪电攻击(伤害={}, 来源={})",
                    event.getAmount(), event.getSource().getMsgId());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (isEngineLightning(event.getSource(), event.getEntity())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            // 默认静默(同上)。
            if (RingConfig.debugLog()) {
                DebugLog.info("[惩罚拦截] 佩戴失落引擎,已把闪电伤害清零(来源={})", event.getSource().getMsgId());
            }
        }
    }
}