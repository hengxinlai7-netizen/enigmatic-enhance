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
 * 头颅注入配方:**大地护符 + 生物头颅 → 解锁该头颅的能力**(头颅被消耗)。
 * <p>
 * 支持 6 种头颅(见 {@link EarthAmuletData#HEADS}),**可叠加**:护符已经带了别的头颅时仍可继续注入,
 * 产物复制原有 NBT 再把新头颅追加到 {@code Heads} 列表;已解锁过的同一颗头颅不匹配(避免白吃)。
 */
public class EarthAmuletHeadRecipe implements CraftingRecipe {

    private final ResourceLocation id;

    public EarthAmuletHeadRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        EarthAmuletRecipeHelper.TwoStacks pair = EarthAmuletRecipeHelper.split(container);
        if (pair == null) {
            return false;
        }
        String headId = EarthAmuletData.headIdOf(pair.other());
        return !headId.isEmpty() && !EarthAmuletData.hasHead(pair.amulet(), headId);
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        EarthAmuletRecipeHelper.TwoStacks pair = EarthAmuletRecipeHelper.split(container);
        if (pair == null) {
            return ItemStack.EMPTY;
        }
        String headId = EarthAmuletData.headIdOf(pair.other());
        if (headId.isEmpty() || EarthAmuletData.hasHead(pair.amulet(), headId)) {
            return ItemStack.EMPTY;
        }
        return EarthAmuletData.withHead(pair.amulet(), headId);
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
        Ingredient heads = Ingredient.EMPTY;
        for (String headId : EarthAmuletData.HEADS) {
            Item head = EarthAmuletData.headItem(headId);
            if (head != null) {
                heads = heads.isEmpty() ? Ingredient.of(head) : Ingredient.merge(java.util.List.of(heads, Ingredient.of(head)));
            }
        }
        list.add(heads);
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
        return ModItems.EARTH_AMULET_HEAD.get();
    }

    /** 序列化器。 */
    public static class Serializer implements RecipeSerializer<EarthAmuletHeadRecipe> {

        @Override
        public EarthAmuletHeadRecipe fromJson(ResourceLocation recipeId, com.google.gson.JsonObject json) {
            return new EarthAmuletHeadRecipe(recipeId);
        }

        @Override
        public EarthAmuletHeadRecipe fromNetwork(ResourceLocation recipeId, net.minecraft.network.FriendlyByteBuf buffer) {
            return new EarthAmuletHeadRecipe(recipeId);
        }

        @Override
        public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer, EarthAmuletHeadRecipe recipe) {
            // 没有额外字段
        }
    }
}
