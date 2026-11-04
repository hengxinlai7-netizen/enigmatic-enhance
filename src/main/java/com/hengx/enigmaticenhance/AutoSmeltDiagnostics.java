package com.hengx.enigmaticenhance;

import java.lang.reflect.Method;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.1.22:启动时打**一条** ASCII 日志,让"自动熔炼到底装没装上"一眼可查:
 * <pre>
 * [AutoSmelt] ready: enchantment=enigmaticenhance:auto_smelt enabled=true hook=Block#getDrops/PRESENT
 *            yieldToWonderenchant=false peerMod=false peerEnchant=absent peerConfig=false
 * </pre>
 * {@code hook=PRESENT} 是通过反射扫 {@link Block} 类的<b>已注入回调</b>得出的:
 * Mixin 会把 {@code @Inject} 的处理方法重命名成 {@code handler$<prefix>$<原名>} 挂到目标类上,
 * 所以扫到 {@code enigmaticenhance$autoSmeltDrops} 就说明 {@code BlockGetDropsMixin} 真的注入成功了
 * (不需要 {@code -Dmixin.debug=true})。自检也用它做断言。
 * <p>
 * 这条日志是**唯一**不受 {@code debugLog} 门控的输出(每次启动一行),其余判定日志仍然默认静默。
 */
public final class AutoSmeltDiagnostics {

    /** 注入回调的原始方法名(= mixin 里 @Inject 处理方法的名字)。 */
    private static final String HANDLER_NAME = "enigmaticenhance$autoSmeltDrops";

    private AutoSmeltDiagnostics() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            DebugLog.startup(
                "[AutoSmelt] ready: enchantment={} enabled={} hook=Block#getDrops/{} yieldToWonderenchant={} "
                    + "peerMod={} peerEnchant={} peerConfig={}",
                AutoSmeltRecipe.enchantmentId(), RingConfig.autoSmeltEnabled(),
                hookPresent() ? "PRESENT" : "MISSING", RingConfig.yieldToWonderenchant(),
                AutoSmeltCompat.wonderenchantLoaded(), AutoSmeltCompat.wonderEnchantmentId(),
                AutoSmeltCompat.wonderFeatureEnabled());
        }
        catch (Throwable t) {
            DebugLog.warn("[AutoSmelt] ready log failed: {}", String.valueOf(t));
        }
    }

    /**
     * {@code BlockGetDropsMixin} 的 {@code @Inject} 回调是否真的挂在 {@link Block} 上。
     * 自检用它断言"注入成功",用户也可以从启动日志一眼看到。
     */
    public static boolean hookPresent() {
        try {
            for (Method method : Block.class.getDeclaredMethods()) {
                if (method.getName().contains(HANDLER_NAME)) {
                    return true;
                }
            }
        }
        catch (Throwable t) {
            return false;
        }
        return false;
    }
}
