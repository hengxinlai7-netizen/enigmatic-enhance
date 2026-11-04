package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 把"古旧书袋里会提供加成的物品 + 加成"同步给客户端(**只在内容变化时发**)。
 *
 * <h2>为什么必须同步</h2>
 * 书袋内容在**服务端 capability**({@code AntiqueBagCapability.INVENTORY})里,客户端拿不到;
 * 石英花当前随机到的属性更只在服务端({@code ArtificialFlower.PLAYER_ATTRIBUTE_MAP}),客户端同样拿不到。
 * 所以由服务端算好**每条加成**再发给客户端,客户端只负责显示。
 *
 * <h2>只在变化时发</h2>
 * 每 tick 只做:算一份条目列表 → {@link BagHudEffects#signature}(物品 + 效果 + 数值 + 生效状态)→
 * 与上一条已发送的签名比较;**相同就什么都不做**(不发包、不刷新缓存)。内容进出/替换、书袋换手、
 * 登录(缓存为空)都会让签名变化 → 恰好发一次。{@code bagHudEnabled=false} 时连算都不算、也不发包。
 * <p>
 * 1.1.19:石英花属性的"剩余 tick"不进签名(见 {@link BagHudEffects#signature(List)}),所以
 * 属性不变、时长 100s→99s 的 20 tick 内签名恒定 → 一个包都不发;属性本身变化 → 恰好发一次。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BagHudSync {

    /** 每个玩家上一次已同步的签名(只在内存里,重登自然会重发一次)。 */
    private static final Map<UUID, String> LAST_SIGNATURE = new HashMap<>();

    /** 客户端缓存(服务端只在内容变化时更新它);tooltip 与 HUD 共用同一份。 */
    private static volatile java.util.List<BagHudEffects.Entry> cache = java.util.List.of();
    private static volatile boolean synced;

    public static void accept(java.util.List<BagHudEffects.Entry> entries) {
        cache = java.util.List.copyOf(entries);
        synced = true;
    }

    public static java.util.List<BagHudEffects.Entry> cache() {
        return cache;
    }

    /** 从没同步过(例如刚进服/服务端没发过)时 tooltip 要显示"(未同步)"。 */
    public static boolean synced() {
        return synced;
    }

    public static void resetClientCache() {
        cache = java.util.List.of();
        synced = false;
    }

    /** 自检用:真正"因为变化而同步"的次数(= 需要发包的次数)。 */
    private static int pushes;

    /** 自检用:真正调用 {@code CHANNEL.send} 的次数(真实玩家才 > 0)。 */
    private static int sent;

    private BagHudSync() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!RingConfig.bagTooltipEnabled()) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            tickUpdate(player);
        }
    }

    /**
     * 单个玩家的一次"检查并同步"。
     *
     * @return true = 这次内容变了并同步了一次
     */
    public static boolean tickUpdate(ServerPlayer player) {
        if (player == null || !RingConfig.bagTooltipEnabled()) {
            return false;
        }
        return sync(player, BagHudEffects.collect(player));
    }

    /**
     * 用给定的条目做一次"签名比较 → 需要时发包"。生产路径({@link #tickUpdate})与自检走的是
     * **同一条路径**,自检只是把"从服务端真实数据算条目"这一步换成构造好的条目。
     *
     * @return true = 这次内容变了并同步了一次
     */
    public static boolean sync(ServerPlayer player, List<BagHudEffects.Entry> entries) {
        if (player == null || !RingConfig.bagTooltipEnabled()) {
            return false;
        }
        String signature = BagHudEffects.signature(entries);
        String previous = LAST_SIGNATURE.get(player.getUUID());
        if (signature.equals(previous)) {
            return false; // 内容没变:不发包
        }
        LAST_SIGNATURE.put(player.getUUID(), signature);
        pushes++;
        // FakePlayer / 尚未连接到客户端的玩家没有 connection:只走"该不该发"的判定,不真发。
        if (player.connection != null) {
            sent++;
            EnigmaticNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new EnigmaticNetwork.BagHudPacket(entries));
        }
        if (RingConfig.debugLog()) {
            DebugLog.info("[书袋HUD] 同步 {} 条:{}", entries.size(), signature);
        }
        return true;
    }

    public static int pushes() {
        return pushes;
    }

    /** 自检用:真正发出的包数(FakePlayer 为 0)。 */
    public static int sent() {
        return sent;
    }

    /** 自检用:清空签名缓存与计数。 */
    public static void resetCache() {
        LAST_SIGNATURE.clear();
        pushes = 0;
        sent = 0;
    }

    public static List<BagHudEffects.Entry> emptyList() {
        return new ArrayList<>();
    }
}
