package com.hengx.enigmaticenhance.test;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.OmniAttribute;

/**
 * 4.11.0【全属性】展平逻辑自检({@code -Denigmaticenhance.selftest=omni})。
 *
 * <p>覆盖三种修饰符操作在 base=0 属性上的正确语义:</p>
 * <ul>
 *   <li>无修饰符 → 0;</li>
 *   <li>ADDITION 0.2 → 0.2(救赎之戒,保持现状);</li>
 *   <li>MULTIPLY_BASE 0.12 → 0.12(石英花,本次修复);</li>
 *   <li>两者都有 → 1.2 × 1.12 - 1 = 0.344;</li>
 *   <li>负值 ADDITION -0.3 → -0.3;</li>
 *   <li>MULTIPLY_TOTAL 0.12 → 0.12。</li>
 * </ul>
 */
public final class OmniSelfTest {

    private OmniSelfTest() {}

    public static void register() {
        String selftest = System.getProperty("enigmaticenhance.selftest");
        if (selftest == null || !selftest.contains("omni")) {
            return;
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(OmniSelfTest.class);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        int pass = 0;
        int fail = 0;

        double none = OmniAttribute.effectiveBonus(List.of());
        boolean t1 = none == 0.0D;
        pass += t1 ? 1 : 0; fail += t1 ? 0 : 1;
        log("T1 无修饰符 -> " + none + "(期望 0.0): " + (t1 ? "PASS" : "FAIL"));

        double addition = OmniAttribute.effectiveBonus(List.of(mod(0.2D, AttributeModifier.Operation.ADDITION)));
        boolean t2 = close(addition, 0.2D);
        pass += t2 ? 1 : 0; fail += t2 ? 0 : 1;
        log("T2 ADDITION 0.2 -> " + addition + "(期望 0.2): " + (t2 ? "PASS" : "FAIL"));

        double multBase = OmniAttribute.effectiveBonus(
            List.of(mod(0.12D, AttributeModifier.Operation.MULTIPLY_BASE)));
        boolean t3 = close(multBase, 0.12D);
        pass += t3 ? 1 : 0; fail += t3 ? 0 : 1;
        log("T3 MULTIPLY_BASE 0.12 -> " + multBase + "(期望 0.12,此前恒为 0): " + (t3 ? "PASS" : "FAIL"));

        double both = OmniAttribute.effectiveBonus(List.of(
            mod(0.2D, AttributeModifier.Operation.ADDITION),
            mod(0.12D, AttributeModifier.Operation.MULTIPLY_BASE)));
        boolean t4 = close(both, 0.344D);
        pass += t4 ? 1 : 0; fail += t4 ? 0 : 1;
        log("T4 ADDITION+MULTIPLY_BASE -> " + both + "(期望 0.344): " + (t4 ? "PASS" : "FAIL"));

        double negative = OmniAttribute.effectiveBonus(
            List.of(mod(-0.3D, AttributeModifier.Operation.ADDITION)));
        boolean t5 = close(negative, -0.3D);
        pass += t5 ? 1 : 0; fail += t5 ? 0 : 1;
        log("T5 ADDITION -0.3 -> " + negative + "(期望 -0.3): " + (t5 ? "PASS" : "FAIL"));

        double multTotal = OmniAttribute.effectiveBonus(
            List.of(mod(0.12D, AttributeModifier.Operation.MULTIPLY_TOTAL)));
        boolean t6 = close(multTotal, 0.12D);
        pass += t6 ? 1 : 0; fail += t6 ? 0 : 1;
        log("T6 MULTIPLY_TOTAL 0.12 -> " + multTotal + "(期望 0.12): " + (t6 ? "PASS" : "FAIL"));

        log("RESULT PASSED=" + pass + " FAILED=" + fail);
    }

    private static AttributeModifier mod(double amount, AttributeModifier.Operation op) {
        return new AttributeModifier(UUID.randomUUID(), "selftest_omni", amount, op);
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < 1.0E-9D;
    }

    private static void log(String message) {
        EnigmaticEnhance.LOGGER.info("[全属性自检] {}", message);
    }
}
