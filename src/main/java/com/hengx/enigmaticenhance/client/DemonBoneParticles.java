package com.hengx.enigmaticenhance.client;

import com.hengx.enigmaticenhance.DemonBoneHandler;
import com.hengx.enigmaticenhance.EnigmaticEnhance;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「恶魔的手骨」的黑烟粒子(1.1.28e,只存在于物理客户端)。
 * <p>
 * 每 10 tick 在佩戴者周围 1~2 个低频粒子:{@code SMOKE}(黑烟)为主,间或一个 {@code SOUL}(紫白魂火),
 * 从手部高度缓慢上飘、带一点随机偏移 —— 频率低,不会糊住画面。
 * <p>
 * {@code value = Dist.CLIENT} 让 Forge 在专用服务器上跳过这个类(不加载任何客户端类)。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = Dist.CLIENT)
public final class DemonBoneParticles {

    private static int counter;

    private DemonBoneParticles() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        // 4.6.3(CPU):把"每 10 tick 一次"的计数器门控提到 wearing() 之前。
        // 原来每 tick 都先问一次 wearing()(→ Curios capability 查询 + 遍历饰品槽),然后 10 次里有
        // 9 次被计数器挡掉 —— 那 9 次查询是纯白做。现在只做一次 int 取模,10 tick 才查一次佩戴。
        // 唯一可察觉的差别:计数器不再"只在佩戴时前进",所以戴上后第一缕烟可能早 0~9 tick 出现
        // (之后仍是稳定的每 10 tick 一次),视觉上无感。
        if (++counter % 10 != 0) {
            return;
        }
        if (!DemonBoneHandler.wearing(minecraft.player)) {
            return;
        }
        int count = 1 + minecraft.player.getRandom().nextInt(2);
        for (int i = 0; i < count; i++) {
            double x = minecraft.player.getX() + (minecraft.player.getRandom().nextDouble() - 0.5D) * 0.7D;
            double y = minecraft.player.getY() + 0.9D + minecraft.player.getRandom().nextDouble() * 0.5D;
            double z = minecraft.player.getZ() + (minecraft.player.getRandom().nextDouble() - 0.5D) * 0.7D;
            minecraft.level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.01D, 0.0D);
            if (minecraft.player.getRandom().nextInt(3) == 0) {
                minecraft.level.addParticle(ParticleTypes.SOUL, x, y + 0.1D, z, 0.0D, 0.005D, 0.0D);
            }
        }
    }
}
