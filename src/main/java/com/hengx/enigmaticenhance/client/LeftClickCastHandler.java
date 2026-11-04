package com.hengx.enigmaticenhance.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.hengx.enigmaticenhance.ElementalMagicHandler;
import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.EnigmaticNetwork;

/**
 * 1.2.25:客户端"左键空挥 / 对空开火"时请求服务端施法。
 *
 * <p>{@code LeftClickEmpty} 是纯客户端事件(对着空气左键才触发),服务端收不到,
 * 所以这里只在客户端发一个 {@code CastSpellPacket},由服务端走同一条施法路径。
 * 这样 TACZ 枪对空开火、剑空挥也能连携施法。</p>
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LeftClickCastHandler {

    private LeftClickCastHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        try {
            Player player = event.getEntity();
            if (player == null) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            if (!ElementalMagicHandler.hasEnchantment(player, stack)) {
                return;
            }
            EnigmaticNetwork.CHANNEL.sendToServer(new EnigmaticNetwork.CastSpellPacket());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯客户端提示性发包,失败不影响游戏
        }
    }
}