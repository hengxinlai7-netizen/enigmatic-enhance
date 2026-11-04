package com.hengx.enigmaticenhance.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.ModMenus;

/** 1.3.1:注册数值编辑器的界面。 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ValueEditorClientSetup {

    private ValueEditorClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.VALUE_EDITOR.get(), ValueEditorScreen::new));
    }
}