package com.hengx.enigmaticenhance.client;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.EnigmaticNetwork;
import com.hengx.enigmaticenhance.ItemToggleHandler;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端:在容器/物品栏界面里,Ctrl+左键 点在**任意支持开关的物品**上
 * (万钧之护卷轴 → 雷击开关;血战沙场之证 → 锁血开关)时,取消这次点击(不搬运物品),
 * 并发一条 C2S 消息让服务端校验并切换。
 * <p>
 * 与 {@link BookToggleScreenHandler} 同一套设施、同一条 {@link EnigmaticNetwork} 通道;
 * 判定与切换逻辑在通用类 {@link ItemToggleHandler} 里(1.1.15 从"只写给卷轴"的版本泛化而来,两处共用)。
 * 只注册在客户端({@code value = Dist.CLIENT});服务端不会加载本类。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ItemToggleScreenHandler {

    private ItemToggleScreenHandler() {}

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !Screen.hasControlDown()) {
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem() || !ItemToggleHandler.canToggle(slot.getItem())) {
            return;
        }
        // 不执行原本的搬运/交换;改为请求切换该物品的开关。
        event.setCanceled(true);
        EnigmaticNetwork.CHANNEL.sendToServer(
            new EnigmaticNetwork.ItemTogglePacket(screen.getMenu().containerId, slot.index));
    }
}
