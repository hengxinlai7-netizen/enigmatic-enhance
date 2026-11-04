package com.hengx.enigmaticenhance;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 「大地护符」三条合成配方共用的判定:做 2 个物品的"无序配对"。
 * <p>
 * 规则(与用户 1.1.24 的需求一致):
 * <ul>
 * <li>合成格里**恰好 2 个**非空堆:一个大地护符 + 一个"另一件"(药水 / 头颅 / 下界之星);</li>
 * <li>两个护符、或两件"另一件"⇒ 不匹配(避免歧义);</li>
 * <li>护符的 NBT(已有的药水效果/头颅/强化)由 {@code assemble} 复制到产物,绝不丢失。</li>
 * </ul>
 */
final class EarthAmuletRecipeHelper {

    private EarthAmuletRecipeHelper() {}

    /** 配对结果(护符 + 另一件)。 */
    record TwoStacks(ItemStack amulet, ItemStack other) {}

    /** 把合成格拆成"护符 + 另一件";不满足形状时返回 null。 */
    static TwoStacks split(CraftingContainer container) {
        if (container == null) {
            return null;
        }
        ItemStack amulet = ItemStack.EMPTY;
        ItemStack other = ItemStack.EMPTY;
        int nonEmpty = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (++nonEmpty > 2) {
                return null;
            }
            if (EarthAmuletData.isAmulet(stack)) {
                if (!amulet.isEmpty()) {
                    return null;
                }
                amulet = stack;
            }
            else {
                if (!other.isEmpty()) {
                    return null;
                }
                other = stack;
            }
        }
        if (nonEmpty != 2 || amulet.isEmpty() || other.isEmpty()) {
            return null;
        }
        return new TwoStacks(amulet, other);
    }

    /** 护符的物品 ingredient(注册表未就绪时返回空 ingredient)。 */
    static Ingredient amuletIngredient() {
        var item = ModItems.earthAmuletOrNull();
        return item == null ? Ingredient.EMPTY : Ingredient.of(item);
    }
}
