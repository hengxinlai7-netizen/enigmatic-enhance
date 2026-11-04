package com.hengx.enigmaticenhance;

import com.google.gson.JsonObject;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * 「需要特定形态的大地之心」的有序配方(1.2.2c 起)。
 *
 * <h2>为什么不用普通 JSON 配方</h2>
 * 用户要求:旅行家帽子用**任意**大地之心;恶魔三件(血杯 / 契约 / 手骨)必须用**深渊侵蚀**过的那颗。
 * 深渊侵蚀是物品上的一个标记({@link AbyssalEarthHeart#TAG}),普通 {@code crafting_shaped} 的材料判定
 * 不看标记,{@code forge:partial_nbt} 又只能"必须带某个标记"、没法表达"随便什么标记都行",
 * 而且一旦写死 NBT,以后标记语义一改就得再改配方。注册一个自己的配方类型最省事:
 * 形状/材料交给原版 {@link ShapedRecipe} 匹配,<b>只额外加一条"这颗心得是深渊侵蚀的"判定</b>。
 *
 * <h2>判定规则</h2>
 * <ul>
 * <li>{@code heart = "any"}:形状对上就行,任意大地之心都可以(干净 / 受污染 / 深渊侵蚀);</li>
 * <li>{@code heart = "abyssal"}（默认）:形状对上之后,合成格里**必须有一颗带 abyssalTaint 的地心**。
 *     只被七咒之戒污染过、或者干净的心都不行 —— 这就是"受污染的不能当深渊侵蚀的用"。</li>
 * </ul>
 */
public class HeartShapedRecipe extends ShapedRecipe {

    /** 是否要求"深渊侵蚀"的大地之心。 */
    private final boolean requireAbyssal;

    public HeartShapedRecipe(ResourceLocation id, String group, CraftingBookCategory category, int width,
        int height, NonNullList<Ingredient> ingredients, ItemStack result, boolean requireAbyssal) {
        super(id, group, category, width, height, ingredients, result);
        this.requireAbyssal = requireAbyssal;
    }

    public boolean requireAbyssal() {
        return this.requireAbyssal;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (!super.matches(container, level)) {
            return false;
        }
        if (!this.requireAbyssal) {
            return true;
        }
        Item heart = RingConfig.earthHeartItem();
        if (heart == null) {
            return false;
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(heart) && AbyssalEarthHeart.isAbyssalTainted(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModItems.HEART_SHAPED.get();
    }

    /**
     * 序列化器:形状/材料/产物全部委托原版 {@link ShapedRecipe.Serializer} 解析,
     * 本类只多读写一个布尔字段 {@code requireAbyssal}(JSON 里叫 {@code "heart": "any" | "abyssal"})。
     */
    public static class Serializer implements RecipeSerializer<HeartShapedRecipe> {

        private final ShapedRecipe.Serializer vanilla = new ShapedRecipe.Serializer();

        @Override
        public HeartShapedRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            ShapedRecipe base = this.vanilla.fromJson(recipeId, json);
            return rebuild(recipeId, base, readRequireAbyssal(json));
        }

        @Override
        public HeartShapedRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
            ShapedRecipe base = this.vanilla.fromNetwork(recipeId, buffer);
            return rebuild(recipeId, base, buffer.readBoolean());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, HeartShapedRecipe recipe) {
            this.vanilla.toNetwork(buffer, recipe);
            buffer.writeBoolean(recipe.requireAbyssal);
        }

        /** JSON 里没写就是 {@code abyssal}(恶魔三件用);写 {@code "any"} 才是任意大地之心(旅行帽用)。 */
        private static boolean readRequireAbyssal(JsonObject json) {
            if (json.has("heart")) {
                return !"any".equalsIgnoreCase(json.get("heart").getAsString());
            }
            return true;
        }

        private static HeartShapedRecipe rebuild(ResourceLocation id, ShapedRecipe base, boolean requireAbyssal) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.addAll(base.getIngredients());
            return new HeartShapedRecipe(id, base.getGroup(), base.category(), base.getWidth(), base.getHeight(),
                ingredients, base.getResultItem(RegistryAccess.EMPTY).copy(), requireAbyssal);
        }
    }
}
