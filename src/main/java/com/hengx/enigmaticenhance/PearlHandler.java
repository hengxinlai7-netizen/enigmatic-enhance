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
        // 3.8.5:过滤书架兼容。
        // EnchantmentHelper#enchantItem 是一次【独立的原版掷取】,它不知道附魔台上挂了什么书架,
        // 所以会把过滤书架(例如海洋过滤书架)本该排掉的附魔重新塞进结果 —— 表现就是"过滤失效"。
        // 这里把补掷的结果裁剪成"只提升输入栈本来就有的附魔",不再新增任何新附魔;
        // 想恢复旧行为(允许新增)把配置 pearl.extraEnchantOnlyExisting 改成 false。
        if (RingConfig.pearlExtraEnchantOnlyExisting()) {
            extra = keepOnlyExisting(extra, original);
        }
        ItemStack merged = PearlCompat.mergeEnchantments(result, extra);
        merged = PearlCompat.maybeApplyEternalBinding(merged);
        extraRollCount++;
        if (RingConfig.debugLog()) {
            DebugLog.info("[附魔珍珠] 神化附魔台补掷: power=" + power + " (+7,cap40) cost=" + cost
                + " extra=" + extra.getAllEnchantments() + " -> " + merged.getAllEnchantments());
        }
        return merged;
    }

    /** 只保留 {@code base} 上已经存在的附魔(数量取栈本数量,不制造新附魔)。 */
    private static ItemStack keepOnlyExisting(ItemStack rolled, ItemStack base) {
        try {
            var baseEnch = base.getAllEnchantments();
            var rolledEnch = rolled.getAllEnchantments();
            if (rolledEnch.isEmpty()) {
                return rolled;
            }
            ItemStack out = base.copy();
            boolean any = false;
            for (var e : rolledEnch.entrySet()) {
                if (baseEnch.containsKey(e.getKey())) {
                    out.enchant(e.getKey(), e.getValue());
                    any = true;
                }
            }
            return any ? out : base.copy();
        }
        catch (RuntimeException | LinkageError ignored) {
            return rolled;
        }
    }
}
