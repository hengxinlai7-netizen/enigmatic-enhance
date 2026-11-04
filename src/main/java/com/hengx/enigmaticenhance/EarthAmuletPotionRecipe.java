package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 药水注入配方:**大地护符 + 任意药水 → 护符获得该药水的全部效果**。
 * <p>
 * 语义(用户 1.1.24 需求):
 * <ul>
 * <li>药水可以是普通/喷溅/滞留型({@code minecraft:potion} 系的三种物品),任意变种;</li>
 * <li>只记录 **id + 等级**,**不记录时长**;</li>
 * <li>只保留**最新一次合成**的结果 —— 覆盖旧的,不累积;</li>
 * <li>水瓶这种没有任何效果的药水不匹配(没东西可记)。</li>
 * </ul>
 * 由于产物依赖输入堆的 NBT,在 {@link #assemble} 里从合成格读取药水效果,
 * 而不是固定写在 JSON 里;{@link #getResultItem} 只给 JEI/配方书一个"干净护符"做展示。
 */
public class EarthAmuletPotionRecipe implements CraftingRecipe {

    private final ResourceLocation id;

    public EarthAmuletPotionRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        EarthAmuletRecipeHelper.TwoStacks pair = EarthAmuletRecipeHelper.split(container);
        boolean ok = pair != null && isPotion(pair.other());
        if (RingConfig.debugLog() && !ok) {
            DebugLog.info("[灌注] 不匹配: pair={} 其它物品={} 效果数={}", pair != null,
                pair == null ? "-" : String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(pair.other().getItem())),
                pair == null ? -1 : EarthAmuletData.allEffects(pair.other()).size());
        }
        return ok;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        EarthAmuletRecipeHelper.TwoStacks pair = EarthAmuletRecipeHelper.split(container);
        if (pair == null || !isPotion(pair.other())) {
            return ItemStack.EMPTY;
        }
        List<EarthAmuletData.StoredEffect> effects = EarthAmuletData.storedEffects(pair.other());
        if (effects.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = pair.amulet().copy();
        result.setCount(1);
        // 覆盖:旧的药水效果整条丢掉,只留这一次的
        EarthAmuletData.setEffects(result, effects);
        return result;
    }

    /** 1.9.3:放宽容纳 —— 任何携带 buff 的物品都能灌注(原版药水 NBT + 神秘遗物 AdvancedPotion)。 */
    public static boolean isPotion(ItemStack stack) {
        return !EarthAmuletData.allEffects(stack).isEmpty();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        var item = ModItems.earthAmuletOrNull();
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
        list.add(Ingredient.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION));
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
        return ModItems.EARTH_AMULET_POTION.get();
    }

    /** 序列化器(JSON 只需要 {"type":"enigmaticenhance:earth_amulet_potion"})。 */
    public static class Serializer implements RecipeSerializer<EarthAmuletPotionRecipe> {

        @Override
        public EarthAmuletPotionRecipe fromJson(ResourceLocation recipeId, com.google.gson.JsonObject json) {
            return new EarthAmuletPotionRecipe(recipeId);
        }

        @Override
        public EarthAmuletPotionRecipe fromNetwork(ResourceLocation recipeId, net.minecraft.network.FriendlyByteBuf buffer) {
            return new EarthAmuletPotionRecipe(recipeId);
        }

        @Override
        public void toNetwork(net.minecraft.network.FriendlyByteBuf buffer, EarthAmuletPotionRecipe recipe) {
            // 没有额外字段:产物完全由合成格里的输入决定
        }
    }
}
