package com.hengx.enigmaticenhance;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 二次强化配方:**已注入凋灵骷髅头的护符 + 下界之星 → Empowered:1b**(免疫亡灵生物造成的伤害)。
 * <p>
 * 用户 1.1.24 明确修正的门槛:
 * <ul>
 * <li><b>必须</b>已经带 {@code minecraft:wither_skeleton_skull}(不带任何头颅 / 带其它头颅都不行);</li>
 * <li>已经 Empowered 的护符不再匹配 —— 不会重复消耗下界之星;</li>
 * <li>拒绝发生在**配方匹配层**({@code matches} 返回 false),不是"合出来但无效"。</li>
 * </ul>
 */
public class EarthAmuletEmpowerRecipe implements CraftingRecipe {

    private final ResourceLocation id;

    public EarthAmuletEmpowerRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        EarthAmuletRecipeHelper.TwoStacks pair = EarthAmuletRecipeHelper.split(container);
        if (pair == null || !pair.other().is(Items.NETHER_STAR)) {
            return false;
        }
        return EarthAmuletData.canBeEmpowered(pair.amulet());
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        EarthAmuletRecipeHelper.TwoStacks pair = EarthAmuletRecipeHelper.split(container);
        if (pair == null || !pair.other().is(Items.NETHER_STAR) || !EarthAmuletData.canBeEmpowered(pair.amulet())) {
            return ItemStack.EMPTY;
        }
        return EarthAmuletData.empowered(pair.amulet());
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        Item item = ModItems.earthAmuletOrNull();
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(EarthAmuletRecipeHelper.amuletIngredient());
        list.add(Ingredient.of(Items.NETHER_STAR));
        return list;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModItems.EARTH_AMULET_EMPOWER.get();
    }

    /** 序列化器。 */
    public static class Serializer implements RecipeSerializer<EarthAmuletEmpowerRecipe> {

        @Override
        public EarthAmuletEmpowerRecipe fromJson(ResourceLocation recipeId, com.google.gson.JsonObject json) {
            return new EarthAmuletEmpowerRecipe(recipeId);
        }

        @Override
        public EarthAmuletEmpowerRecipe fromNetwork(ResourceLocation recipeId, net.minecraft.network.FriendlyByteBuf buffer) {
            return new EarthAmuletEmpowerRecipe(recipeId);
        }

        @Override
        public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer, EarthAmuletEmpowerRecipe recipe) {
            // 没有额外字段
        }
    }
}
