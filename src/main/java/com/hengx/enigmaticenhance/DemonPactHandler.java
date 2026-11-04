package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「恶魔契约」的唯一运行时行为(1.1.25):**佩戴者受到伤害时,把这次伤害的数值调整为当前生命值。**
 * <p>
 * 为什么挂 {@link LivingDamageEvent} 而不是 {@link net.minecraftforge.event.entity.living.LivingHurtEvent}:
 * <ul>
 * <li>1.20.1 的伤害管线是 {@code LivingEntity#hurt → actuallyHurt}:
 *     {@code LivingHurtEvent} 在**护甲/附魔/抗性减免之前**发出,改它只会改"减免前的名义伤害",
 *     最终扣掉的血仍然会被护甲啃掉一截 ⇒ 达不到"伤害 = 当前生命值";</li>
 * <li>{@code LivingDamageEvent} 在**减免之后、写血之前**发出,它的 amount 就是马上要从生命值里减掉的那个数,
 *     所以在这里改成 {@code player.getHealth()} 才是字面意义上的"这次伤害的数值 = 当前生命值"。</li>
 * </ul>
 * 优先级用 {@code LOWEST}(最后执行),保证其它模组的伤害修正先算完、契约的规则最后覆盖,
 * 不会出现"契约被别的加成改回去"的情况。
 * <p>
 * 作用域:只对**佩戴者本人**生效(哪怕同一次 AOE 打中队友/怪物,它们的数值也不变);
 * 不改变 {@code DamageSource}(伤害类型/来源/击杀归属全部原样)。
 * 不特判 /kill、虚空、outOfWorld —— 它们照常走这条规则(契约本来就是要命的东西)。
 * <p>
 * 两种解释(配置 {@code demonPact.lethal}):
 * <ul>
 * <li>{@code true}(默认)= 字面实现:数值**直接等于**当前生命值 ⇒ 任何一次伤害都致死;</li>
 * <li>{@code false} = 温和解释:只把伤害**上限**压到当前生命值({@code min(伤害, 当前生命)}),
 *     小于当前生命的伤害保持原样。</li>
 * </ul>
 * 日志全部走 {@link DebugLog}(默认 debugLog=false ⇒ 零输出)。
 */
public final class DemonPactHandler {

    /** 日志/自检共用的稳定 ASCII 标记。 */
    public static final String LOG_TAG = "[DemonPact]";

    private DemonPactHandler() {}

    /**
     * 1.1.28e:**统一契约修正函数**。
     * <p>
     * 契约原来只挂 {@code LivingDamageEvent}(也就是只覆盖走 {@code hurt()} 的伤害)。
     * 本函数把同一套规则抽出来,给"直接 setHealth 扣血"这类路径用:
     * {@code lethal=true} ⇒ 扣血额度直接变成当前生命值(致死);{@code false} ⇒ 只把额度压到当前生命值。
     * <p>
     * 只处理"扣血"方向:{@code amount <= 0}、没戴契约、已经死亡时原样返回;
     * 治疗/设置血量为更高值不会经过这里(调用方只在扣血路径上调用),所以不会误伤治疗或血量上限。
     * 目前调用方是 {@link DemonBoneHandler#trueDamage} 与 {@link DemonBoneHandler#trueDamage} 的玩家目标分支;
     * 不拦截第三方模组自己的 {@code setHealth}(无法区分读档/治疗/换维度,风险大于收益,已在报告写明)。
     */
    public static float applyContract(Player player, float amount) {
        try {
            if (player == null || amount <= 0.0F) {
                return amount;
            }
            if (DemonPactData.equippedPact(player).isEmpty()) {
                return amount;
            }
            float health = player.getHealth();
            if (health <= 0.0F) {
                return amount;
            }
            float adjusted = RingConfig.demonPactLethal() ? health : Math.min(amount, health);
            if (RingConfig.debugLog() && adjusted != amount) {
                DebugLog.info("[DemonPact] 直伤契约修正 {} -> {} (当前生命 {})", amount, adjusted, health);
            }
            return Math.max(0.0F, adjusted);
        }
        catch (Throwable t) {
            return amount;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
                return;
            }
            if (DemonPactData.equippedPact(player).isEmpty()) {
                return;
            }
            float amount = event.getAmount();
            float health = player.getHealth();
            if (amount <= 0.0F || health <= 0.0F) {
                return;
            }
            float adjusted = RingConfig.demonPactLethal() ? health : Math.min(amount, health);
            if (adjusted <= 0.0F || adjusted == amount) {
                return;
            }
            event.setAmount(adjusted);
            DebugLog.info("{} {} 恶魔契约:伤害 {} -> {} (当前生命 {})", LOG_TAG, player.getName().getString(),
                amount, adjusted, health);
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 伤害结算异常", t);
        }
    }
}
