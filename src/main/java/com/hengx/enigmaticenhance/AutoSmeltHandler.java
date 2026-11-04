package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * 【自动熔炼】的全部核心判定与"熔什么"的唯一实现(对应奇妙附魔的 {@code AutoSmeltHandler#smeltOne})。
 * <p>
 * 只查<b>普通熔炉</b>配方 {@link RecipeType#SMELTING}:高炉/烟熏炉/营火以及任何"可熔炼"标记一律不看。
 * 查不到配方,或产物与输入是同一种物品 ⇒ 原样保留(绝不因为熔炼让物品消失)。
 * <p>
 * 经验:与奇妙附魔口径一致 —— <b>不额外发放熔炼经验</b>,也完全不碰原版挖掘经验
 * (挖掘经验由 {@code BlockState#spawnAfterBreak} 另外掉落,本模组只替换物品堆,不经过那个路径)。
 */
public final class AutoSmeltHandler {

    private AutoSmeltHandler() {}

    public static boolean enabled() {
        return RingConfig.autoSmeltEnabled();
    }

    /**
     * 工具上是否带本模组的自动熔炼(等级任何值都算有;行为一律按 1 级)。
     * <p>
     * 1.1.21:改用 {@link EnchantmentHelper#getEnchantments} 的映射来读 —— 它对
     * {@code minecraft:enchanted_book} 会改读 {@code StoredEnchantments}(与奇妙附魔的
     * {@code EnchantLevels} 完全一致),而 {@code getItemEnchantmentLevel} 只看 {@code Enchantments},
     * 对附魔书一律返回 0。这样"书/被别的模组塞进 StoredEnchantments 的物品"也能被认出来。
     */
    public static boolean hasEnchant(ItemStack tool) {
        if (tool == null || tool.isEmpty()) {
            return false;
        }
        Enchantment enchantment = ModEnchantments.autoSmeltOrNull();
        if (enchantment == null) {
            return false;
        }
        return levelOf(tool, enchantment) > 0;
    }

    /** 读某个附魔在这个堆上的等级(书籍走 StoredEnchantments;读不到返回 0)。 */
    public static int levelOf(ItemStack stack, Enchantment enchantment) {
        if (stack == null || stack.isEmpty() || enchantment == null) {
            return 0;
        }
        try {
            Integer level = EnchantmentHelper.getEnchantments(stack).get(enchantment);
            return level == null ? 0 : Math.max(0, level);
        }
        catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 是否被"按住潜行键"临时关掉。
     * <p>
     * 与奇妙附魔一致:用 {@link Player#isShiftKeyDown()}(潜行键的服务端状态),
     * 不用 {@code isCrouching()}(那是 Pose 推导的,会被强制姿态/爬行/其它模组改 Pose 影响)。
     */
    public static boolean suppressedBySneaking(@Nullable Player player) {
        return RingConfig.autoSmeltDisabledWhileSneaking() && player != null && player.isShiftKeyDown();
    }

    /**
     * 这一次挖掘本模组要做什么(1.1.22 起的唯一判定入口,{@link AutoSmeltPipeline} 直接用它)。
     * <ul>
     * <li>{@link Action#SKIP}:不是我们的场景 —— 总开关关 / 工具上没有本模组附魔 / 按住潜行键;</li>
     * <li>{@link Action#YIELD}:工具上确实也有对方的 {@code wonderenchant:auto_smelt} 且对方开着,
     *     并且用户显式把 {@code autoSmelt.yieldToWonderenchant} 打开(默认 <b>false</b>);</li>
     * <li>{@link Action#SMELT}:替换这次的原版掉落表(默认行为,与对方模组在不在、对方写了什么无关)。</li>
     * </ul>
     * 1.1.22 把"让位"改成**默认关闭**:1.1.21 的教训是 —— 让位条件再窄,只要它是"别人的代码说了算",
     * 对方管线不工作(或被别的模组拦掉/关掉)时我们的附魔就整体静默失效。而本模组的注入点是
     * "替换原版即将生成的那一份掉落",对方拿到的是产物、再熔一次也不会变 ⇒ <b>根本不需要让位</b>。
     */
    public static Action decide(@Nullable Player player, ItemStack tool) {
        if (player == null || !enabled()) {
            return Action.SKIP;
        }
        ItemStack effective = effectiveTool(player, tool);
        if (!hasEnchant(effective)) {
            return Action.SKIP;
        }
        if (suppressedBySneaking(player)) {
            return Action.SKIP;
        }
        if (RingConfig.yieldToWonderenchant() && AutoSmeltCompat.otherModTakesOver(effective)) {
            return Action.YIELD;
        }
        return Action.SMELT;
    }

    /** {@link #decide} 的兼容包装:只有"该熔炼"才返回 true。 */
    public static boolean isActive(@Nullable Player player, ItemStack tool) {
        return decide(player, tool) == Action.SMELT;
    }

    /** 一次挖掘的处置决定。 */
    public enum Action {
        /** 替换掉落表。 */
        SMELT,
        /** 让位给对方模组的管线(仅当配置显式打开)。 */
        YIELD,
        /** 不参与(总开关关/工具没附魔/潜行)。 */
        SKIP
    }

    /** 实际生效的工具:传进来的能用就用,否则退回玩家主手(日志/自检用)。 */
    public static ItemStack effectiveTool(@Nullable Player player, ItemStack tool) {
        if (hasEnchant(tool)) {
            return tool;
        }
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    /**
     * 这一堆是不是"某条普通熔炉配方的产物"(如 {@code iron_ingot} / {@code smooth_stone} / {@code stone})。
     * <p>
     * 1.1.22 去重的第二层:如果另一个模组(或别的管线)已经把它熔过了,我们不该再熔一次。
     * 代价是"本身既是产物、又能继续熔"的东西(例如石头 → 平滑石头)不会再被二次熔炼 —— 这是有意的。
     */
    public static boolean isSmeltingProduct(Level level, ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (!result.isEmpty() && result.getItem() == stack.getItem()) {
                    return true;
                }
            }
        }
        catch (Throwable t) {
            return false;
        }
        return false;
    }

    /**
     * 熔炼一个堆。
     *
     * @return 熔炼结果(数量守恒;产物超过最大堆叠时会拆成多堆)。
     *         没命中配方 / 产物为空 / 产物与输入同类 ⇒ 返回<b>只含原堆的同一个列表实例</b>,
     *         调用方用 {@code result.get(0) == input} 就能判断"没变",从而完全不碰原版掉落。
     */
    public static List<ItemStack> smeltOne(Level level, ItemStack input) {
        List<ItemStack> unchanged = List.of(input);
        if (level == null || input == null || input.isEmpty()) {
            return unchanged;
        }
        Optional<SmeltingRecipe> recipe;
        try {
            recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(input), level);
        }
        catch (Throwable t) {
            // 配方管理器还没就绪(极端早期调用):原样保留,绝不吞物品
            return unchanged;
        }
        if (recipe.isEmpty()) {
            return unchanged;
        }
        ItemStack result = recipe.get().getResultItem(level.registryAccess());
        if (result.isEmpty() || result.is(input.getItem())) {
            // 无产物,或"熔炼自己"(同类物品的循环配方)⇒ 原样保留
            return unchanged;
        }
        List<ItemStack> out = split(result, input.getCount());
        if (out.isEmpty()) {
            return unchanged;
        }
        return out;
    }

    /** 按数量守恒把产物拆成若干堆(每堆不超过该物品的最大堆叠)。 */
    private static List<ItemStack> split(ItemStack result, int inputCount) {
        long total = (long) result.getCount() * (long) inputCount;
        if (total <= 0L) {
            return List.of();
        }
        int maxStack = Math.max(1, result.getMaxStackSize());
        List<ItemStack> out = new ArrayList<>();
        long remaining = total;
        while (remaining > 0L && out.size() < 64) {
            int count = (int) Math.min(remaining, maxStack);
            out.add(result.copyWithCount(count));
            remaining -= count;
        }
        return out;
    }
}
