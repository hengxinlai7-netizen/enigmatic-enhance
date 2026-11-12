package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 1.2.27:TACZ(永恒枪械工坊：零)开枪时连携施法。
 *
 * <p>TACZ 用自己的输入处理开枪,并会拦掉左键的 Forge 交互事件,
 * 所以 {@code LeftClickEmpty/LeftClickBlock} 对枪不生效。这里监听它自己的
 * {@code GunFireEvent}(扣扳机)。</p>
 *
 * <p><b>零硬引用</b>:4.6.4 起本类不再 {@code import} 任何 TACZ 类型 ——
 * 枪事件类只按名字反射探测,拿到 {@link Class} 后用一个注册在 {@link Event}
 * 根类型上的通用监听器做 {@code ==} 过滤(见 {@link #register(IEventBus)})。
 * TACZ 不存在时 {@link #register} 直接返回,不注册任何东西、不写日志。</p>
 */
public final class TaczCompat {

    /** TACZ 开枪事件类;null = 未安装。 */
    private static volatile Class<?> gunFireEventClass;

    private TaczCompat() {
    }

    /** 装了 TACZ 才调用;本方法自己再判一次,缺失时彻底静默。 */
    public static void register(IEventBus bus) {
        try {
            gunFireEventClass = Class.forName("com.tacz.guns.api.event.common.GunFireEvent", false,
                TaczCompat.class.getClassLoader());
        }
        catch (Throwable t) {
            gunFireEventClass = null;
            return;
        }
        if (gunFireEventClass == null) {
            return;
        }
        // 用 Event 根类型注册通用监听器:不引用枪事件类型也能收到它。
        bus.addListener(TaczCompat::onAnyEvent);
    }

    /** 通用监听器:只有本体正好是枪事件时才处理,其余事件一次 instanceof 后就返回。 */
    private static void onAnyEvent(Event event) {
        Class<?> type = gunFireEventClass;
        if (type == null || event.getClass() != type) {
            return;
        }
        onGunFire(event);
    }

    private static void onGunFire(Event event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.taczSpellCastEnabled()) {
            return;
        }
        try {
            Object side = event.getClass().getMethod("getLogicalSide").invoke(event);
            if (side != null && "CLIENT".equals(String.valueOf(side))) {
                return;
            }
            Object shooter = event.getClass().getMethod("getShooter").invoke(event);
            if (!(shooter instanceof Player player) || player.level().isClientSide) {
                return;
            }
            ElementalSpellCastHandler.castFromClientSwing(
                player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
        }
        catch (Throwable ignored) {
            // 枪的连携失败不影响开火
        }
    }
}
