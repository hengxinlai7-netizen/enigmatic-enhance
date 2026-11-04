package com.hengx.enigmaticenhance;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 1.2.6e:给"瓶子"系列补 JEI 信息页。
 *
 * <p>这一串瓶子除「大地之瓶」是合成出来的之外,其余都是转化产物,**没有配方** ——
 * JEI 的合成表里天然查不到。JEI 对这种情况的标准做法就是注册"信息页"
 * ({@link IRecipeRegistration#addIngredientInfo}),把获取方式写成文字显示在物品的用途页里。</p>
 *
 * <p>只依赖 JEI 的 API(编译期),运行期 JEI 不在也不会加载这个类 ——
 * {@code @JeiPlugin} 是给 JEI 自己扫描用的。</p>
 */
@JeiPlugin
public class JeiInfoPlugin implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation(EnigmaticEnhance.MODID, "jei_info");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        register(registration, ModItems.earthBottleOrNull(), "earth_bottle", 2);
        register(registration, ModItems.historyMistOrNull(), "history_mist", 3);
        register(registration, ModItems.spiritLightOrNull(), "spirit_light", 3);
        register(registration, ModItems.styxWaterOrNull(), "styx_water", 2);
        register(registration, ModItems.warFogOrNull(), "war_fog", 2);
        register(registration, ModItems.sunBottleOrNull(), "bottled_sun", 2);
        register(registration, ModItems.uncertainFogOrNull(), "uncertain_fog", 2);
        register(registration, ModItems.mirrorShadowOrNull(), "mirror_shadow", 3);
        // 2.4.1:瓶装意识之海(2.1.4 加的物品一直没接 JEI)
        register(registration, ModItems.seaOfConsciousnessOrNull(), "sea_of_consciousness", 5);
    }

    /** 一件物品的信息页:按 {@code line1..lineN} 逐行取语言键。 */
    private void register(IRecipeRegistration registration, Item item, String id, int lines) {
        if (item == null) {
            return;
        }
        Component[] text = new Component[lines];
        for (int i = 0; i < lines; i++) {
            text[i] = Component.translatable("jei.enigmaticenhance." + id + ".line" + (i + 1));
        }
        registration.addIngredientInfo(new ItemStack(item), mezz.jei.api.constants.VanillaTypes.ITEM_STACK, text);
    }
}
