package com.hengx.enigmaticenhance.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.hengx.enigmaticenhance.EarthAmuletState;
import com.hengx.enigmaticenhance.EarthAmuletTint;
import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.ModItems;

/**
 * 客户端:注册大地护符的模型谓词 {@code enigmaticenhance:amulet_state},让同一件物品按 NBT 换贴图。
 * <p>
 * 这是 1.20.1 的标准做法({@code ItemProperties.register} + 模型 {@code overrides});
 * 判定逻辑在服务端/客户端共用的 {@link EarthAmuletState} 里,客户端只做注册。
 * <p>
 * 只在 {@code Dist.CLIENT} 加载:专用服务端不会碰 {@code ItemProperties}(那是客户端类)。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EarthAmuletItemProperties {

    private EarthAmuletItemProperties() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        Item amulet = ModItems.earthAmuletOrNull();
        if (amulet == null) {
            return;
        }
        event.enqueueWork(() -> ItemProperties.register(amulet, EarthAmuletState.PROPERTY,
            (stack, level, entity, seed) -> EarthAmuletState.modelState(stack)));
        // 1.1.28k:边框按头颅类型换子模型(0 = 素金框,1..6 = 六种头颅)
        event.enqueueWork(() -> ItemProperties.register(amulet, EarthAmuletState.HEAD_PROPERTY,
            (stack, level, entity, seed) -> EarthAmuletState.headIndex(stack)));
    }

    /**
     * 1.1.28k:核心层染色 —— tintindex 1 返回"注入药水效果的颜色",没有药水返回白色
     * (白色 × 白色核心贴图 = 无色空芯);tintindex 0(金框)固定白色。
     * <p>
     * 只注册在**大地护符**这一件物品上:empowered / potion / heads 那几个旧模型只有 layer0,
     * 永远不会走到 tintindex 1,所以不受影响。
     */
    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        Item amulet = ModItems.earthAmuletOrNull();
        if (amulet == null) {
            return;
        }
        event.register((stack, tintIndex) -> EarthAmuletTint.colorFor(stack, tintIndex), amulet);
    }

}
