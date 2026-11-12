package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 【自动熔炼】的获取途径之二:合成配方(1.1.21 新增,「一书八煤炭块」无序配方)。
 * <p>
 * 配方本体是**纯 JSON 数据文件** {@code data/enigmaticenhance/recipes/auto_smelt_book.json}
 * (1 书 + 8 煤炭块 → 带 {@code StoredEnchantments} 的附魔书)。这里不注册任何自定义配方类型:
 * <ul>
 * <li>1.20.1 Forge 把 {@code ShapedRecipe#itemStackFromJson} 换成了
 *     {@code CraftingHelper.getItemStack(json, readNBT=true, readCount=true)},
 *     所以 <b>原版 {@code crafting_shapeless} 的 result 里可以直接写 "nbt"</b> —— 无需自定义 serializer;</li>
 * <li>正因为类型是原版 {@code minecraft:crafting_shapeless},JEI / REI / 原版配方书都能直接识别并显示;</li>
 * <li>配方按 id 唯一({@code enigmaticenhance:auto_smelt_book}),数据包重载幂等,不会重复注册。</li>
 * </ul>
 * 本类只提供"期望产物"的构造与判定,给自检与文档用(生产逻辑全在 JSON 里)。
 */
public final class AutoSmeltRecipe {

    /** 配方 id 的 path(完整 id = enigmaticenhance:auto_smelt_book)。 */
    public static final String RECIPE_PATH = "auto_smelt_book";
    /** 配方文件位置。 */
    public static final ResourceLocation RECIPE_ID =
        new ResourceLocation(EnigmaticEnhance.MODID, RECIPE_PATH);
    /** 材料:1 本书。 */
    public static final int BOOK_COUNT = 1;
    /** 材料:8 个煤炭块(与那本书凑满 3×3)。 */
    public static final int COAL_BLOCK_COUNT = 8;

    private AutoSmeltRecipe() {}

    /** 附魔的完整 id(enigmaticenhance:auto_smelt),日志与断言用。 */
    public static String enchantmentId() {
        return EnigmaticEnhance.MODID + ":" + ModIds.AUTO_SMELT_PATH;
    }

    /** 这个堆是不是"我们的自动熔炼附魔书"(物品 + 附魔都对)。 */
    public static boolean isAutoSmeltBook(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.ENCHANTED_BOOK) {
            return false;
        }
        Enchantment enchantment = ModEnchantments.autoSmeltOrNull();
        if (enchantment == null) {
            return false;
        }
        // 附魔书把魔咒存在 StoredEnchantments 里,必须走 getEnchantments(它会改读那个标签);
        // 用 getItemEnchantmentLevel 读附魔书一律是 0(1.20.1 实测)。
        return AutoSmeltHandler.levelOf(stack, enchantment) > 0;
    }
}
