package com.hengx.enigmaticenhance;

import java.lang.reflect.Method;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.2.19:冥河之水 × 诡厄巫法(Goety)的灵魂联动。
 *
 * <ul>
 *   <li>携带冥河之水(背包/副手/书袋)时:把玩家的灵魂能量标记为"激活"
 *       ({@code ISoulEnergy#setSEActive(true)}),于是能像有灵魂方舟一样收集灵魂;</li>
 *   <li>每秒(20 tick)恢复 10 点灵魂({@code SEHelper#increaseSouls}),上限交给 Goety 自己算;</li>
 *   <li>取下后不再补。</li>
 * </ul>
 *
 * <p>全部反射调用 Goety,未安装时静默失效;不直接改存档结构,避免写坏灵魂数据。</p>
 */
public final class StyxSoulHandler {

    /** 每秒恢复的灵魂数。 */
    public static final int SOULS_PER_SECOND = 10;

    private static Method increaseSouls;
    private static Method getCapability;
    private static Method setActive;
    private static boolean resolved;

    private StyxSoulHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (!StyxWaterHandler.hasStyxWater(player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        try {
            resolve();
            if (setActive != null) {
                Object capability = getCapability.invoke(null, player);
                if (capability != null) {
                    setActive.invoke(capability, Boolean.TRUE);
                }
            }
            if (increaseSouls != null) {
                increaseSouls.invoke(null, player, SOULS_PER_SECOND);
            }
        }
        catch (Throwable ignored) {
            // Goety 缺失/版本差异 ⇒ 只是不吸魂
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> helper = Class.forName("com.Polarice3.Goety.utils.SEHelper");
            increaseSouls = helper.getMethod("increaseSouls", Player.class, int.class);
            getCapability = helper.getMethod("getCapability", Player.class);
            Class<?> soulEnergy = Class.forName("com.Polarice3.Goety.common.capabilities.soulenergy.ISoulEnergy");
            setActive = soulEnergy.getMethod("setSEActive", boolean.class);
        }
        catch (Throwable ignored) {
            increaseSouls = null;
            getCapability = null;
            setActive = null;
        }
    }

    /** 反射是否绑定成功(自检/调试用)。 */
    public static boolean ready() {
        resolve();
        return increaseSouls != null;
    }
}