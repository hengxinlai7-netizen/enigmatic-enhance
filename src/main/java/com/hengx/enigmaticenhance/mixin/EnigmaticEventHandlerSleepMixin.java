package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hengx.enigmaticenhance.BedtimeReadingFix;

import net.minecraft.world.entity.player.Player;

/**
 * 「睡前读物」修正七咒之戒**第七重咒(失眠)**的注入点(1.1.28b)。
 *
 * <p>反编译依据({@code com.aizistral.enigmaticlegacy.handlers.EnigmaticEventHandler},
 * Enigmaticlegacy 2.30.1 字节码):{@code onPlayerTick(LivingEvent$LivingTickEvent)} 里那段</p>
 * <pre>
 * 421: getstatic     CursedRing.disableInsomnia
 * 427: ifne          496
 * 431: Player.isSleeping() / hasCurio(CURSED_RING)
 * 448: invokevirtual Player.m_36318_()I        &lt;- 第一次读 sleepTimer
 * 451: iconst_5 / if_icmpne 481               &lt;- == 5 时发"无法入睡"提示
 * 482: invokevirtual Player.m_36318_()I        &lt;- 第二次读 sleepTimer
 * 485: bipush 90 / if_icmple 496
 * 493: putfield      Player.f_36110_ (sleepCounter) = 90   &lt;- 真正的失眠惩罚
 * </pre>
 * <p>这两处调用读到的值<b>只用于上面两个比较</b>,所以把 {@code Player#getSleepTimer()} 在这一个方法里的
 * 返回值换掉,就能同时关掉"提示"和"夹 sleepCounter"两件事,而且完全不影响方法里其它任何逻辑
 * (第 1~6 重咒、火焰诅咒、以及其他所有判定都在别处,一个字节都不碰)。判定与替换值由
 * {@link BedtimeReadingFix#effectiveSleepTimer} 给出(每次即时算,不缓存状态)。</p>
 * <p>{@code remap = false} 与本项目其它 mixin 一致(运行期是 SRG 名 {@code m_36318_});
 * {@code require = 0, expect = 0}:万一将来本体改了这里,注入静默不生效而不是让游戏崩;
 * 整个类登记在 {@code enigmaticenhance.optional.mixins.json}({@code required:false} +
 * {@code injectors.defaultRequire:0})。</p>
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.handlers.EnigmaticEventHandler")
public class EnigmaticEventHandlerSleepMixin {

    @Redirect(
        method = "onPlayerTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;m_36318_()I"),
        remap = false, require = 0, expect = 0)
    private int enigmaticenhance$sleepTimer(Player player) {
        return BedtimeReadingFix.effectiveSleepTimer(player, player.getSleepTimer());
    }
}
