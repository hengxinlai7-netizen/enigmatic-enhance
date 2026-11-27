package com.hengx.enigmaticenhance;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * 4.17.1：斩杀时的掉落物兜底。
 *
 * <p>正常死亡流程里 {@code die()} 内部会调用 {@code dropAllDeathLoot}，掉落物照常。
 * 但如果别的模组把死亡拦下（复活 / 免死 / 死亡事件取消），我们又直接把血写成 0，
 * 目标就会"死了但什么都没掉"。</p>
 *
 * <p>补齐时通过 {@link LootEventGuard} 把 {@code LivingDropsEvent} 先抢跑：
 * HIGHEST 拷走列表、LOWEST 强制还原，外部模组取消或清空都不影响这次补发。</p>
 */
public final class DeathDrops {

    private static final String TAG = "enigmaticenhance:drops_forced";

    private DeathDrops() {
    }

    /** 补一次掉落；已经补过或目标不存在时直接返回。 */
    public static void force(LivingEntity target, DamageSource source) {
        if (target == null || source == null || target.level().isClientSide) {
            return;
        }
        try {
            if (target.getPersistentData().getBoolean(TAG)) {
                return;
            }
            target.getPersistentData().putBoolean(TAG, true);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 拿不到数据也继续尝试, 最多重复一次
        }

        LootEventGuard.ensureRegistered();
        LootEventGuard.begin();
        try {
            for (String name : new String[]{"m_6668_", "dropAllDeathLoot"}) {
                try {
                    Method m = LivingEntity.class.getDeclaredMethod(name, DamageSource.class);
                    m.setAccessible(true);
                    m.invoke(target, source);
                    DebugLog.warn("[掉落兜底] 死亡被拦截, 已补 dropAllDeathLoot({}): {}",
                            name, target.getName().getString());
                    return;
                }
                catch (NoSuchMethodException ignored) {
                    // 换下一个名字
                }
            }
            DebugLog.warn("[掉落兜底] 找不到 dropAllDeathLoot, 放弃");
        }
        catch (Throwable t) {
            DebugLog.warn("[掉落兜底] 补发失败: {}", t.toString());
        }
        finally {
            LootEventGuard.end();
        }
    }
}
