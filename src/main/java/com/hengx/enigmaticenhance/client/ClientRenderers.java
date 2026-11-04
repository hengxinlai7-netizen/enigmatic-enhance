package com.hengx.enigmaticenhance.client;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.ModEntities;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端渲染器注册(1.1.28c,只存在于物理客户端)。
 * <p>
 * {@code value = Dist.CLIENT} 让 Forge 在**专用服务器**上直接跳过这个类,所以
 * {@link PureRuneProjectileRenderer}/{@link PureRuneFlashRenderer} 引用的
 * {@code net.minecraft.client.*} 在服务端永远不会被加载 —— 服务端启动验证时不会看到任何
 * "客户端类缺失"的报错。
 * <p>
 * {@link EntityRenderersEvent.RegisterRenderers} 是模组总线上的客户端事件,只在客户端触发。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRenderers {

    private ClientRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PURE_RUNE.get(), PureRuneProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.PURE_RUNE_FLASH.get(), PureRuneFlashRenderer::new);
    }


    /** 1.1.29a:魔法剑气渲染器注册。 */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onRegisterAuraRenderers(
        net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.hengx.enigmaticenhance.ModEntities.MAGIC_SWORD_AURA.get(),
            MagicSwordAuraRenderer::new);
    }
}
