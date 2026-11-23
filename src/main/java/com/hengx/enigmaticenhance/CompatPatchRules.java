package com.hengx.enigmaticenhance;

/**
 * 「抑制扩展惩罚态」的纯判定逻辑(无客户端依赖,可在服务端自检里断言)。
 *
 * <h2>背景</h2>
 * 神秘遗物扩展的 {@code SuperAddonHandler#isPunishedOne(LivingEntity)} 是「两戒同戴 = 处于惩罚态」
 * 的唯一判据;惩罚态下它每帧覆盖原版光照贴图(lightmap,见扩展的 {@code MixinLightTexture}),
 * 在 ModernUI 环境里会让字体图集与实体/方块纹理采样异常 —— 界面文字全消失、纹理变黑。
 *
 * <p>本模组本来就允许两戒同戴且拦截惩罚执行,所以在这个前提下惩罚态应当恒为 false:
 * 从判据源头抑制一次,渲染与惩罚两条链路就一起解决了。</p>
 */
public final class CompatPatchRules {

    private CompatPatchRules() {}

    /**
     * 是否要把「惩罚态」判成 false。
     *
     * <p>只有<b>开关打开</b>且<b>玩家自己允许同戴</b>且<b>两枚戒指都在身上</b>时才抑制;
     * 玩家没开同戴、或只戴了一枚,都保持扩展原判定。</p>
     */
    public static boolean shouldSuppressPunished(boolean patchEnabled, boolean allowBoth,
                                                 boolean hasBless, boolean hasCursed) {
        return patchEnabled && allowBoth && hasBless && hasCursed;
    }
}
