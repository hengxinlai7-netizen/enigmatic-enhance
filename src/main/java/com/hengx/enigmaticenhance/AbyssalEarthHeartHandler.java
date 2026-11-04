package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「深渊侵蚀的大地之心」的每 tick 驱动(1.2.2)。
 * <p>
 * 只做一件事:每隔 {@link AbyssalEarthHeart#SCAN_INTERVAL_TICKS} 刻对每名玩家调一次
 * {@link AbyssalEarthHeart#convertPlayer(Player)} —— 判定与改写全在那一边,这里不碰任何状态。
 *
 * <h2>为什么不用物品自己的 inventoryTick</h2>
 * 本体「受污染的大地之心」是靠 {@code EarthHeart#inventoryTick} 驱动的,但那条路径在 1.20.1 里
 * **只覆盖玩家背包的 36 格**({@code Player#tick} → {@code Inventory#tick} 只遍历 {@code items}),
 * 盔甲栏、副手、饰品栏都不在里面。用户明确要求「背包 / 饰品栏 / 物品栏 / 装备栏」四处都要算,
 * 所以这里用玩家 tick 统一扫一遍,与物品自身有没有 tick 无关。
 * <p>
 * 只跑服务端:{@link TickEvent.PlayerTickEvent} 两端都会触发,先用 {@code level().isClientSide()} 挡掉客户端。
 */
public final class AbyssalEarthHeartHandler {

    /** 日志/自检共用的稳定 ASCII 标记。 */
    public static final String LOG_TAG = "[AbyssalEarthHeart]";

    private AbyssalEarthHeartHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player == null || player.level() == null || player.level().isClientSide()) {
            return;
        }
        if (player.tickCount % AbyssalEarthHeart.SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        AbyssalEarthHeart.convertPlayer(player);
    }
}
