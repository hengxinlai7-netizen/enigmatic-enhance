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
 * <h2>为什么必须"每 tick 重新置位"(1.2.12g)</h2>
 * 1.20.1 的 {@code Player#tick()} <b>第一句</b>就是:
 * <pre>
 *     this.noPhysics = this.isSpectator();
 * </pre>
 * 也就是**每个 tick 都会把 noPhysics 重置成"是否旁观"**。
 * 1.1.25b 的做法是"收到包时置一次" —— 下一 tick 就被原版抹掉,
 * 所以"龙首飞行穿方块"一直表现成完全无效。
 *
 * <p>现在分成两层:包只改本类里的一个开关;真正每 tick 在
 * {@link TickEvent.ClientTickEvent} 的 END(此时本地玩家 tick 已跑完,不会再被重置)
 * 把 {@code noPhysics} 同步成开关值。服务端同理,在 {@code PlayerTickEvent} 里每 tick 置位。</p>
 *
 * <h2>2.11.27:修复旁观者视野变黑(重要)</h2>
 * <p>旧代码是:</p>
 * <pre>
 *   boolean want = (active || selfHeal) &amp;&amp; !player.isSpectator();
 *   if (player.noPhysics != want) player.noPhysics = want;
 * </pre>
 * <p>对<b>旁观者</b>来说 {@code isSpectator()} 为真 ⇒ {@code want = false} ⇒
 * 原版刚在 {@code Player#tick()} 里设好的 {@code noPhysics = true} 被这一行改回 {@code false}。
 * 结果是旁观者每 tick 都在"可穿墙 → 不可穿墙"之间反复切换,客户端因此重新生成
 * "埋在实心方块里"的黑暗遮挡层 —— 表现为<b>进入地下后视野变黑</b>。</p>
 *
 * <p>而且这段代码<b>不佩戴护符也会执行</b>,所以任何人都可能中招。</p>
 *
 * <p>现在两条硬规则:</p>
 * <ol>
 *   <li><b>旁观者完全不碰</b>:直接 return,{@code noPhysics} 交给原版管理;</li>
 *   <li><b>只回收自己打开的那一份</b>:用一个 {@link #engaged} 标记,
 *       从没被本模组置过 true 的玩家,本模组永远不写 {@code false},
 *       避免把它们被别的模组打开的穿墙关掉。</li>
 * </ol>
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

    /**
     * 2.11.27:当前是否<b>由本模组</b>把 {@code noPhysics} 打开着。
     *
     * <p>只有这个标记为真时,才允许把 {@code noPhysics} 写回 {@code false};
     * 否则一律不碰,避免误关别的模组/旁观模式设下的穿墙。</p>
     */
    private static volatile boolean engaged;

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
        // 2.11.27:旁观模式一律不碰 —— 原版的 noPhysics = isSpectator() 必须原样保留
        if (minecraft.player.isSpectator()) {
            if (engaged) {
                engaged = false;
            }
            active = false;
            com.hengx.enigmaticenhance.NoClipState.clientActive = false;
            return;
        }
        boolean flying = minecraft.player.getAbilities().flying;
        // 2.1.1 自恢复:包丢了但玩家还在飞 → 继续穿(服务端那边本来就是允许的)。
        boolean selfHeal = everActivated && flying;
        boolean want = active || selfHeal;
        com.hengx.enigmaticenhance.NoClipState.clientActive = want;
        if (want) {
            if (!minecraft.player.noPhysics) {
                minecraft.player.noPhysics = true;
            }
            engaged = true;
        }
        else if (engaged) {
            // 只回收我们自己打开的那一份
            minecraft.player.noPhysics = false;
            engaged = false;
        }
    }

    private static void applyNow(boolean noClip) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null
            && !minecraft.player.isSpectator()) {
            minecraft.player.noPhysics = noClip;
            engaged = noClip;
        }
    }

    /** 客户端当前是否处于"穿方块"状态(调试用)。 */
    public static boolean isActive() {
        return active;
    }
}
