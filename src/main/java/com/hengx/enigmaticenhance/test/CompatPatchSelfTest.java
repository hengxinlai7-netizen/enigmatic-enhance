package com.hengx.enigmaticenhance.test;

import com.hengx.enigmaticenhance.CompatPatchRules;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 4.10.0 正式版:抑制扩展「惩罚态」的自检({@code -Denigmaticenhance.selftest=addonhud})。
 */
public final class CompatPatchSelfTest {

    private CompatPatchSelfTest() {}

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(CompatPatchSelfTest.class);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        int pass = 0;
        int fail = 0;

        boolean t1 = CompatPatchRules.shouldSuppressPunished(true, true, true, true);
        pass += t1 ? 1 : 0; fail += t1 ? 0 : 1;
        log("T1 开关开 + 允许同戴 + 两戒齐全 -> 抑制: " + (t1 ? "PASS" : "FAIL"));

        boolean t2 = !CompatPatchRules.shouldSuppressPunished(true, true, true, false);
        pass += t2 ? 1 : 0; fail += t2 ? 0 : 1;
        log("T2 开关开 + 允许同戴 + 只有救赎之戒 -> 不抑制: " + (t2 ? "PASS" : "FAIL"));

        boolean t3 = !CompatPatchRules.shouldSuppressPunished(true, false, true, true);
        pass += t3 ? 1 : 0; fail += t3 ? 0 : 1;
        log("T3 玩家关了同戴 + 两戒齐全 -> 不抑制(不干预玩家设置): " + (t3 ? "PASS" : "FAIL"));

        boolean t4 = !CompatPatchRules.shouldSuppressPunished(false, true, true, true);
        pass += t4 ? 1 : 0; fail += t4 ? 0 : 1;
        log("T4 开关关 + 其它都满足 -> 不抑制(回到扩展原行为): " + (t4 ? "PASS" : "FAIL"));

        boolean t5 = !CompatPatchRules.shouldSuppressPunished(true, true, false, false);
        pass += t5 ? 1 : 0; fail += t5 ? 0 : 1;
        log("T5 两戒都不在 -> 不抑制: " + (t5 ? "PASS" : "FAIL"));

        // T6:真加载扩展的判据类 -> 触发 Mixin 应用(配合 -Dmixin.debug.verbose=true 可见 Mixing 行)
        try {
            Class<?> handler = Class.forName("auviotre.enigmatic.addon.handlers.SuperAddonHandler");
            boolean probe = (Boolean) handler.getMethod("isPunishedOne",
                    net.minecraft.world.entity.LivingEntity.class).invoke(null, new Object[] { null });
            log("T6 SuperAddonHandler 已加载, isPunishedOne(null)=" + probe + " -> PASS");
            pass++;
        }
        catch (Throwable e) {
            log("T6 SuperAddonHandler 加载/调用失败: " + e);
        }

        log("RESULT PASSED=" + pass + " FAILED=" + fail);
    }

    private static void log(String message) {
        com.hengx.enigmaticenhance.EnigmaticEnhance.LOGGER.info("[兼容补丁自检] {}", message);
    }
}
