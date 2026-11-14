package com.hengx.enigmaticenhance;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 4.8.3:「执行之手」专属伤害类型 {@code enigmaticenhance:execution} 的引擎。
 *
 * <h2>为什么不再走"通用真实伤害"那条老路</h2>
 * <p>2.9.0~4.8.2 的做法是造一个 {@code enigmaticenhance:true_damage} 伤害源,再靠
 * {@code TrueDamageBypassMixin} 把 {@code ForgeHooks.onLivingAttack/Hurt/Damage} 整段跳过。
 * 那套办法有两个硬伤:</p>
 * <ol>
 *   <li><b>它并没有绕过原版减伤。</b>1.20.1 的 {@code DamageType} 只有
 *       {@code message_id / exhaustion / scaling / effects / death_message_type} 五个字段;
 *       旧的 {@code true_damage.json} 里写的 {@code "bypasses_armor": true} 之类<b>根本不是
 *       1.20.1 的字段</b>(DFU 的反序列化会静默忽略未知键),所以护甲、保护附魔、抗性、
 *       盾牌其实一直在照常生效 —— 这就是"强度不够"的根因。1.20.1 的免伤判定全部读
 *       {@code minecraft:tags/damage_type/bypasses_*} <b>标签</b>,见
 *       {@code data/minecraft/tags/damage_type/} 下 7 个文件(护甲 / 盾 / 无敌帧 / 冷却 /
 *       抗性 / 状态效果 / 保护附魔)。</li>
 *   <li><b>跳过整段事件派发是"全局副作用"。</b>别的模组在事件里做的不只是限伤
 *       (受击登记、音效、反伤、连击……),整段跳过会让它们一起失灵。现在只在
 *       <b>兜底补发</b>那一次上临时置位 {@link #forced()},作用域是"这一脚",不是全局。</li>
 * </ol>
 *
 * <h2>这一脚怎么打出去</h2>
 * <ol>
 *   <li>用 {@code execution} 伤害类型造 {@link DamageSource}(带攻击者,所以经验/掉落/
 *       仇恨/死亡信息都是正常归属);</li>
 *   <li>{@code target.invulnerableTime = 0} 后调原版 {@code hurt()} —— 死亡、掉落、音效、
 *       受击登记全部照常走原版;</li>
 *   <li>事件层双保险在 {@link HandOfExecutionHandler}:HIGHEST 记账、LOWEST 还原被削减的数值、
 *       被 cancel 时只驳回**这一脚**;</li>
 *   <li>万一 LOWEST 之后还有模组把它吃掉({@code hurt()} 返回 false 或进
 *       {@code actuallyHurt} 的数值不足),这里再**跳过事件派发补发差额**一次 —— 保证"全额"。</li>
 * </ol>
 *
 * <p><b>图腾警告</b>:{@code bypasses_invulnerability} 同时会让图腾救助
 * ({@code LivingEntity#checkTotemDeathProtection} 一开头就查这个标签)失效。
 * 这是刻意保留的强度,已在 tooltip 与 debugLog 里写明。</p>
 */
public final class ExecutionDamage {

    /** 执行之手专属伤害类型 id。 */
    public static final ResourceKey<DamageType> EXECUTION =
        ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(EnigmaticEnhance.MODID, "execution"));

    /**
     * "这一脚跳过事件派发"的作用域标记(线程内,只在 {@link #forceHurt} 的调用期间为 true)。
     *
     * <p>与 valettein 的全局 {@code setCanceled} no-op 完全不同:它只影响我们自己正在发的
     * <b>补发伤害</b>,别的模组、别的伤害源、同一 tick 的其它事件全都照常。</p>
     */
    private static final ThreadLocal<Boolean> FORCED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ExecutionDamage() {
    }

    /** 这个伤害源是不是执行之手。 */
    public static boolean isExecution(DamageSource source) {
        if (source == null) {
            return false;
        }
        try {
            return source.is(EXECUTION);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 当前线程是否处在"补发"阶段(给 {@code ExecutionBypassMixin} 读)。 */
    public static boolean forced() {
        return Boolean.TRUE.equals(FORCED.get());
    }

    /**
     * 造一个执行之手伤害源。
     *
     * @return {@code null} 表示服务器上根本没有 {@code enigmaticenhance:execution} 这个类型
     *         (数据包被删/写坏),调用方必须退回普通伤害,绝不能吞掉这一击
     */
    public static DamageSource source(LivingEntity attacker) {
        if (attacker == null || attacker.level() == null) {
            return null;
        }
        try {
            return new DamageSource(attacker.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(EXECUTION), attacker);
        }
        catch (RuntimeException | LinkageError e) {
            if (DebugLog.oncePending()) {
                DebugLog.once("exec:nodatapack",
                    "[执行之手] 缺少 damage_type {}:{},本次退回普通伤害", EXECUTION.location(), e);
            }
            return null;
        }
    }

    /**
     * 打出一脚执行之手伤害。
     *
     * @return 记账(含最终掉血量与削减/取消次数);<b>null = 没能接管</b>,调用方必须让普通伤害照常生效
     */
    public static ExecutionGuard.Hit apply(LivingEntity target, LivingEntity attacker, float amount,
                                           DamageSource fallback) {
        if (target == null || amount <= 0.0F || !target.isAlive() || target.level().isClientSide) {
            return null;
        }
        DamageSource src = source(attacker);
        if (src == null) {
            // 数据包缺失:只有调用方本来就拿着 execution 源时才勉强继续,否则交还普通伤害
            if (!isExecution(fallback)) {
                return null;
            }
            src = fallback;
        }
        ExecutionGuard.Hit hit = ExecutionGuard.remember(target, amount);
        hit.absorptionBefore(target.getAbsorptionAmount());
        float healthBefore = target.getHealth();
        try {
            target.invulnerableTime = 0;
            target.hurt(src, amount);
            hit.noteDealt(healthBefore - target.getHealth());
            // ---- 兜底:事件层(含排在我们后面的 LOWEST)吃掉的部分,跳过事件派发补回来 ----
            // 用"真实掉血"作判据,只补差额,不会重复伤害。
            float missing = hit.missing();
            if (hit.blockedByEvents() && missing > 1.0E-4F && target.isAlive() && hit.forcedTimes() == 0) {
                DebugLog.warn("[执行之手] 事件层拦掉了 {} 点伤害,跳过事件派发补发一次", missing);
                hit.markForced();
                forceHurt(target, src, missing);
            }
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[执行之手] 结算失败,退回普通伤害: {}", e.toString());
        }
        finally {
            hit.noteDealt(healthBefore - target.getHealth());
            ExecutionGuard.forget(target);
        }
        return hit;
    }

    /**
     * 补发:置位 {@link #FORCED()} 后再打一次 —— 此时
     * {@code ExecutionBypassMixin} 让 Forge 的三道伤害钩子直接返回原值,任何模组都插不上手。
     */
    private static boolean forceHurt(LivingEntity target, DamageSource src, float amount) {
        FORCED.set(Boolean.TRUE);
        try {
            target.invulnerableTime = 0;
            return target.hurt(src, amount);
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[执行之手] 补发失败: {}", e.toString());
            return false;
        }
        finally {
            FORCED.set(Boolean.FALSE);
        }
    }
}
