package com.hengx.enigmaticenhance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.LivingEntity;

/**
 * 4.8.3:「执行之手」一次伤害结算的**记账本**(双保险的共用状态)。
 *
 * <p>为什么需要它:执行之手的伤害要经过三层:</p>
 * <pre>
 *   LivingAttackEvent(HIGHEST, 我方)  →  真正发一脚 enigmaticenhance:execution 伤害
 *        ├─ LivingHurtEvent   HIGHEST  记录原始值 / 看见事件派发
 *        ├─ LivingHurtEvent   LOWEST   被削减就还原、被 cancel 就只驳回这一脚
 *        ├─ LivingDamageEvent LOWEST   再兜一层 + 记下真正进入 actuallyHurt 的数值
 *        └─ hurt() 返回后由 {@link ExecutionDamage} 决定要不要"跳过事件派发"补发
 * </pre>
 *
 * <p>这些处理器执行在**同一个线程、同一次 {@code hurt()} 调用**里,所以用"目标 UUID → 这一次的
 * {@link Hit}"的小表就够了:一个实体在同一瞬间只可能有一次执行之手结算。表只在执行之手命中时
 * 写一次(普通攻击完全不碰),命中结束立刻删除,不会长大。</p>
 */
final class ExecutionGuard {

    /** 一次执行之手伤害的记账单元。可变字段都在同一线程里写。 */
    static final class Hit {

        /** 进 hurt() 之前的原始伤害(本次要达到的目标值)。 */
        private final float original;

        /** 进 hurt() 之前的黄血(吸收)值 —— LivingDamageEvent 里的数值本来就会被它吃掉一部分。 */
        private float absorptionBefore;

        /**
         * 真实掉血(healthBefore - healthAfter),**这是判断"到底打进去多少"的唯一权威值**。
         *
         * <p>为什么不读事件里的 amount:1.20.1 的 LivingDamageEvent 派发在黄血吸收之后,
         * 而且排在我们后面的 LOWEST 处理器仍然可以改它 —— 事件里的数值不等于真掉的血。
         * 用血量差既能自动算上黄血,又能抓住"我们之后还有模组削减"的情况。</p>
         */
        private float dealt;

        /** 事件派发是否真的发生过(用来区分"被事件拦了"和"根本没派发事件")。 */
        private boolean sawEvents;

        /** 被削减后又被还原的次数。 */
        private int reduced;

        /** 被 cancel 的次数(含已经被我们驳回的)。 */
        private int cancelled;

        /** 已经补发过几次。 */
        private int forced;

        Hit(float original) {
            this.original = original;
        }

        float original() {
            return original;
        }

        float dealt() {
            return dealt;
        }

        boolean sawEvents() {
            return sawEvents;
        }

        int reducedTimes() {
            return reduced;
        }

        int cancelledTimes() {
            return cancelled;
        }

        int forcedTimes() {
            return forced;
        }

        /** 这一次确实派发了事件(执行之手是一脚独立的伤害,事件通常都会派发)。 */
        void sawEventDispatch() {
            sawEvents = true;
        }

        void noteReduced() {
            reduced++;
        }

        void noteCancelled() {
            cancelled++;
        }

        void absorptionBefore(float amount) {
            absorptionBefore = Math.max(0.0F, amount);
        }

        void noteDealt(float amount) {
            if (amount > dealt) {
                dealt = amount;
            }
        }

        void markForced() {
            forced++;
        }

        /**
         * 这一脚**在血量层**应有的数值。
         *
         * <p>1.20.1 的 LivingDamageEvent 派发在"黄血吸收"之后(反编译
         * {@code LivingEntity#actuallyHurt}:先 {@code amount -= absorption},再
         * {@code ForgeHooks.onLivingDamage}),所以这里必须减掉进 hurt() 之前的黄血,
         * 否则带伤害吸收的目标会被我们多打一截。</p>
         */
        float expectedHealthDamage() {
            float absorbed = Math.min(absorptionBefore, original);
            float expected = original - absorbed;
            return expected > 0.0F ? expected : 0.0F;
        }

        /**
         * 这一次伤害**被事件层拦掉了**(而不是"根本没走事件,例如 Boss 自己在 hurt 里直写血量")。
         *
         * <p>只有派发过事件、并且真正掉的血不足目标值时才算"被拦",这样才不会对
         * "自己写血量的 Boss mixin" 或"黄血已经吃满"的情况重复补刀。</p>
         */
        boolean blockedByEvents() {
            return sawEvents && dealt < targetDealt() - 1.0E-4F;
        }

        /** 距离目标值还差多少(补发用)。 */
        float missing() {
            float m = targetDealt() - dealt;
            return m > 0.0F ? m : 0.0F;
        }

        /**
         * 这一次"应该掉的血"。
         *
         * <p>如果被取消且一点都没落地,目标就是原始值(整脚重打,黄血也会照常吃掉自己那份);
         * 否则是 {@link #expectedHealthDamage()}(已经扣掉黄血,避免多打)。</p>
         */
        private float targetDealt() {
            if (cancelled > 0 && dealt <= 1.0E-4F) {
                return original;
            }
            return expectedHealthDamage();
        }
    }

    /** 目标 → 正在结算的这一脚。只在执行之手命中期间有条目。 */
    private static final Map<UUID, Hit> ACTIVE = new ConcurrentHashMap<>();

    private ExecutionGuard() {
    }

    /** 开始记录这一次(覆盖同目标上一条未清的残留)。 */
    static Hit remember(LivingEntity target, float original) {
        Hit hit = new Hit(original);
        if (target != null) {
            UUID id = target.getUUID();
            if (ACTIVE.size() > 256) {
                ACTIVE.clear();   // 保险:绝不让它无限增长(正常永远到不了)
            }
            ACTIVE.put(id, hit);
        }
        return hit;
    }

    /** 读这一次;不是执行之手的伤害返回 null。 */
    static Hit recall(LivingEntity target) {
        if (target == null) {
            return null;
        }
        return ACTIVE.get(target.getUUID());
    }

    /** 结算结束,清掉。 */
    static void forget(LivingEntity target) {
        if (target != null) {
            ACTIVE.remove(target.getUUID());
        }
    }
}
