package com.hengx.enigmaticenhance;

import java.lang.reflect.Method;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 3.0.1:绕过映射差异调用 {@link LivingEntity#dropAllDeathLoot(DamageSource)}。
 *
 * <h2>为什么不用 Mixin 的 {@code @Invoker}</h2>
 * <p>原实现是 {@code @Invoker("dropAllDeathLoot")}，但如果项目的 refmap 没把这个名字
 * 重映射成 SRG 名，运行时会直接失败：</p>
 * <pre>
 *   InvalidAccessorException: No candidates were found matching
 *   dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V in LivingEntity
 * </pre>
 * <p>而 1.20.1 的 SRG 名是 <b>{@code m_6668_}</b>（已从 {@code srg_to_official_1.20.1.tsrg}
 * 第 121461 行确认：{@code m_6668_ (Lnet/minecraft/world/damagesource/DamageSource;)V dropAllDeathLoot}）。</p>
 *
 * <p>所以这里改成运行时反射，<b>两个名字都试</b>：开发环境命中 {@code dropAllDeathLoot}，
 * 生产环境命中 {@code m_6668_}，任何一种映射都不会落空。</p>
 *
 * <h2>用途</h2>
 * <p>执行之手对 Boss 破防时，Boss 的 {@code die()} 只播死亡动画、不调 {@code super.die()}，
 * 所以不会结算掉落。这个方法让那些 Mixin 能在强制移除 Boss 之前手动结算一次掉落。</p>
 */
public final class DropHelper {

    /** 开发名。 */
    private static final String DEV_NAME = "dropAllDeathLoot";
    /** 生产名(SRG)。 */
    private static final String SRG_NAME = "m_6668_";

    private static Method cached;
    private static boolean resolved;

    private DropHelper() {
    }

    /**
     * 让 {@code entity} 结算一次死亡掉落(掉落物 + 装备拆解)。
     *
     * @param entity 即将被强制移除的实体
     * @param source 致死伤害来源
     */
    public static void dropAllDeathLoot(LivingEntity entity, DamageSource source) {
        if (entity == null || entity.level() == null || entity.level().isClientSide) {
            return;
        }
        Method method = resolve();
        if (method == null) {
            DebugLog.warn("[DropHelper] 未能定位 dropAllDeathLoot,跳过掉落结算");
            return;
        }
        try {
            method.invoke(entity, source);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            DebugLog.warn("[DropHelper] 结算掉落失败: {}", e.toString());
        }
    }

    private static synchronized Method resolve() {
        if (resolved) {
            return cached;
        }
        resolved = true;
        for (String name : new String[]{DEV_NAME, SRG_NAME}) {
            try {
                Method m = LivingEntity.class.getDeclaredMethod(name, DamageSource.class);
                m.setAccessible(true);
                cached = m;
                DebugLog.info("[DropHelper] 已定位 dropAllDeathLoot -> {}", name);
                return cached;
            }
            catch (NoSuchMethodException ignored) {
                // 试下一个名字
            }
            catch (RuntimeException ignored) {
                // 继续
            }
        }
        DebugLog.warn("[DropHelper] dropAllDeathLoot 的两个名字都找不到");
        return null;
    }
}
