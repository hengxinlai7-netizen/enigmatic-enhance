package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import com.tacz.guns.api.event.common.GunFireEvent;

/**
 * 1.2.27:TACZ(永恒枪械工坊：零)开枪时连携施法。
 *
 * <p>TACZ 用自己的输入处理开枪,并会拦掉左键的 Forge 交互事件
 * ({@code com.tacz.guns.event.PreventGunClick}),所以 {@code LeftClickEmpty/LeftClickBlock}
 * 对枪不生效。这里直接监听它自己的 {@link GunFireEvent}(扣扳机)。</p>
 *
 * <p>本类只在 TACZ 存在时才被注册(见 {@code EnigmaticEnhance}),没有 TACZ 时不会加载。</p>
 */
public final class TaczCompat {

    private TaczCompat() {
    }

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.taczSpellCastEnabled()) {
            return;
        }
        try {
            if (event.getLogicalSide() == LogicalSide.CLIENT) {
                return;
            }
            if (!(event.getShooter() instanceof Player player)) {
                return;
            }
            if (player.level().isClientSide) {
                return;
            }
            ElementalSpellCastHandler.castFromClientSwing(
                player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 枪的连携失败不影响开火
        }
    }
}