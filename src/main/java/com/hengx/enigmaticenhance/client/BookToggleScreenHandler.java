package com.hengx.enigmaticenhance.client;

import com.hengx.enigmaticenhance.BooksHandler;
import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.EnigmaticNetwork;
import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端:在容器/物品栏界面里,Ctrl+左键 点在目标书籍上 → 取消这次点击(不搬运物品),
 * 并发一条 C2S 消息让服务端切换禁用状态。
 * <p>
 * 只注册在客户端({@code value = Dist.CLIENT});服务端不会加载本类。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BookToggleScreenHandler {

    private BookToggleScreenHandler() {}

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!RingConfig.booksEnabled() || !RingConfig.booksToggleEnabled()) {
            return;
        }
        if (event.getButton() != 0 || !Screen.hasControlDown()) {
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem() || !BooksHandler.isTargetBook(slot.getItem())) {
            return;
        }
        // 不执行原本的搬运/交换;改为切换禁用状态。
        event.setCanceled(true);
        EnigmaticNetwork.CHANNEL.sendToServer(
            new EnigmaticNetwork.BookTogglePacket(screen.getMenu().containerId, slot.index));
    }
}
