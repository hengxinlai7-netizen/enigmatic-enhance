package com.hengx.enigmaticenhance;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 1.3.1:菜单类型注册。 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, EnigmaticEnhance.MODID);

    public static final RegistryObject<MenuType<ValueEditorMenu>> VALUE_EDITOR =
        MENUS.register("value_editor", () -> IForgeMenuType.create(
            (id, inv, data) -> new ValueEditorMenu(id, data == null ? -1 : data.readVarInt())));

    private ModMenus() {
    }
}