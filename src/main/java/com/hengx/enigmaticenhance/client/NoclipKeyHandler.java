package com.hengx.enigmaticenhance.client;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.EnigmaticNetwork;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 2.8.0:「龙首穿墙」开关按键。
 *
 * <p><b>默认不绑定任何键</b>({@link GLFW#GLFW_KEY_UNKNOWN}),玩家在"控制 → 按键绑定"里自己绑。
 * 按下后客户端发一个 {@code NoClipTogglePacket} 给服务端,由服务端翻转该玩家的穿墙开关。</p>
 */
public final class NoclipKeyHandler {

    public static final KeyMapping TOGGLE = new KeyMapping(
        "key.enigmaticenhance.noclip_toggle",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        "key.categories.enigmaticenhance");

    private NoclipKeyHandler() {
    }

    /** MOD 总线:注册按键。 */
    @Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {

        private ModEvents() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
        }
    }

    /** FORGE 总线:按键按下时发包。 */
    @Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {

        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
                return;
            }
            if (Minecraft.getInstance().player == null) {
                return;
            }
            // 2.9.5:consumeClick 必须在 tick 里轮询;放在 InputEvent.Key 里会漏掉。
            boolean fired = false;
            while (TOGGLE.consumeClick()) {
                fired = true;
            }
            if (fired) {
                EnigmaticNetwork.CHANNEL.sendToServer(new EnigmaticNetwork.NoClipTogglePacket());
            }
        }
    }
}
