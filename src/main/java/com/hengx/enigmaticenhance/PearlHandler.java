package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 「附魔师的珍珠」在神化(Apotheosis)路径上的等价实现。
 * <p>
 * <b>珍珠在神秘遗物里的原本实现</b>(证据见报告):
 * {@code com.aizistral.enigmaticlegacy.mixin.MixinEnchantmentContainer} 注入
 * {@code EnchantmentMenu#clickMenuButton(Player,int)} 与 {@code EnchantmentMenu#getGoldCount()}。
 * 前者的语义是:当 {@code EnchanterPearl.isPresent(player)} 时,自己接管这次附魔 ——
 * <ol>
 * <li>主附魔列表仍然来自附魔台自己的 {@code getEnchantmentList}(虚调用,所以神化的覆写会被用上);</li>
 * <li>额外再掷一次 {@code EnchantmentHelper.enchantItem(random, 输入.copy(), min(costs[slot] + 7, 40), false)},
 *     并用 {@code SuperpositionHandler.mergeEnchantments} 合并进结果,再用
 *     {@code maybeApplyEternalBinding} 判定永恒绑定诅咒;</li>
 * <li>全程不 shrink 青金石(配上 {@code getGoldCount() = 64},等于"附魔不再需要青金石")。</li>
 * </ol>
 * 神化的 {@code ApothEnchantmentMenu} 覆写了 {@code clickMenuButton},神秘遗物的注入被遮蔽,所以珍珠在神化附魔台上失效。
 * 这里在神化那一侧补回等价的第 2、3 步(主列表仍由神化自己算,不碰它的行为)。
 */
public final class PearlHandler {

    /** 神化路径上"补掷一次附魔"实际执行的次数(自检用)。 */
    public static volatile int extraRollCount;

    private PearlHandler() {}

    /**
     * 珍珠的触发条件与神秘遗物完全一致(直接问它自己的 {@code EnchanterPearl#isPresent}:
     * 身为七咒之人 且 佩戴珍珠)。
     */
    public static boolean pearlPresent(Player player) {
        return PearlCompat.pearlPresent(player);
    }

    /**
     * 神化附魔台上的"珍珠补掷":公式与神秘遗物原版一致
     * {@code min(cost + 7, 40)},然后合并(含永恒绑定诅咒判定)。
     *
     * @param player   附魔的玩家
     * @param result   神化已经附魔完成的结果栈(会被替换)
     * @param original 附魔前的原始输入栈(用于按原版规则掷取额外附魔)
     * @param cost     该选项的附魔威力(EnchantmentMenu.costs[slot])
     * @return 合并后的栈(即使没变化也返回原栈)
     */
    public static ItemStack applyExtraEnchant(Player player, ItemStack result, ItemStack original, int cost) {
        int power = Math.min(cost + 7, 40);
        ItemStack extra = EnchantmentHelper.enchantItem(player.getRandom(), original.copy(), power, false);
        ItemStack merged = PearlCompat.mergeEnchantments(result, extra);
        merged = PearlCompat.maybeApplyEternalBinding(merged);
        extraRollCount++;
        if (RingConfig.debugLog()) {
            DebugLog.info("[附魔珍珠] 神化附魔台补掷: power=" + power + " (+7,cap40) cost=" + cost
                + " extra=" + extra.getAllEnchantments() + " -> " + merged.getAllEnchantments());
        }
        return merged;
    }
}
