package com.hengx.enigmaticenhance.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.hengx.enigmaticenhance.EnigmaticEnhance;

/**
 * 客户端:接收服务端的 {@code NoClipPacket},把本地玩家的 {@code noPhysics} 打开/关掉。
 *
 * <h2>为什么必须"每 tick 重新置位"(1.2.12g,真凶)</h2>
 * 1.20.1 的 {@code Player#tick()} <b>第一句</b>就是:
 * <pre>
 *     this.noPhysics = this.isSpectator();
 * </pre>
 * 也就是**每个 tick 都会把 noPhysics 重置成"是否旁观"**。
 * 1.1.25b 的做法是"收到包时置一次" —— 下一 tick 就被原版抹掉,
 * 所以"龙首飞行穿方块"一直表现成完全无效(用户反复反馈的就是这个)。
 *
 * <p>现在分成两层:包只改本类里的一个开关;真正每 tick 在
 * {@link TickEvent.ClientTickEvent} 的 END(此时本地玩家 tick 已跑完,不会再被重置)
 * 把 {@code noPhysics} 同步成开关值。服务端同理,在 {@code PlayerTickEvent} 里每 tick 置位。</p>
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NoClipClientHandler {

    /** 服务端最近一次下发的状态。 */
    private static volatile boolean active;

    /**
     * 2.1.1:是否"曾经"收到过开启指令。
     *
     * <p>原来客户端完全依赖 {@link #active} 这一个布尔：服务端包一旦在维度切换/重登/卡顿时丢失，
     * 客户端就永远不穿方块（服务端允许、客户端自己撞墙 → 表现成"横向过不去"）。
     * 现在加一个自恢复：只要曾经开过、并且玩家此刻确实在飞，就继续保持穿墙。</p>
     */
    private static volatile boolean everActivated;

    private NoClipClientHandler() {
    }

    /** 服务端包到达时调用(只改开关,并立即应用一次避免第一帧不生效)。 */
    public static void apply(boolean noClip) {
        com.hengx.enigmaticenhance.NoClipState.clientActive = noClip;
        active = noClip;
        if (noClip) {
            everActivated = true;
        }
        applyNow(noClip);
    }

    /** 每客户端 tick 结束后重新置位:原版会在 Player#tick 开头把 noPhysics 重置成 isSpectator()。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        boolean flying = minecraft.player.getAbilities().flying;
        // 2.1.1 自恢复:包丢了但玩家还在飞 → 继续穿(服务端那边本来就是允许的)。
        boolean selfHeal = everActivated && flying;
        boolean want = (active || selfHeal) && !minecraft.player.isSpectator();
        com.hengx.enigmaticenhance.NoClipState.clientActive = want;
        if (minecraft.player.noPhysics != want) {
            minecraft.player.noPhysics = want;
        }
    }

    private static void applyNow(boolean noClip) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.noPhysics = noClip && !minecraft.player.isSpectator();
        }
    }

    /** 客户端当前是否处于"穿方块"状态(调试用)。 */
    public static boolean isActive() {
        return active;
    }
}
