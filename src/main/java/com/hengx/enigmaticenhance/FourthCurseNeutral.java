package com.hengx.enigmaticenhance;

import com.aizistral.omniconfig.wrappers.Omniconfig;

/**
 * 构造"修正后的第四重咒惩罚参数"。
 * <p>
 * 本类**只在神秘遗物本体存在时才会被加载**(引用了它的 {@code Omniconfig.PerhapsParameter} /
 * {@code Perhaps}),所以由 mixin(目标类就是神秘遗物的类,缺模组时整份 mixin 配置会被跳过)独占引用,
 * 事件/工具提示那条路径只用 {@link FourthCurseFix},不会因此缺少类。
 * <p>
 * 本体实现:{@code Perhaps.asModifierInverted() = 1 - probability/100}(bytecode 已核对),
 * 因此 {@code new PerhapsParameter(0)} 的系数就是 1.0F=完全不削减,{@code 25} 就是 ×0.75(= 惩罚减半)。
 */
public final class FourthCurseNeutral {

    private static Omniconfig.PerhapsParameter neutral;
    private static int cachedResidual = Integer.MIN_VALUE;

    private FourthCurseNeutral() {}

    /** 修正后的惩罚参数(按配置值缓存;配置改动后自动重建)。 */
    public static Omniconfig.PerhapsParameter parameter() {
        int residual = FourthCurseFix.residualPercent();
        if (neutral == null || cachedResidual != residual) {
            neutral = new Omniconfig.PerhapsParameter(residual);
            cachedResidual = residual;
        }
        return neutral;
    }
}
