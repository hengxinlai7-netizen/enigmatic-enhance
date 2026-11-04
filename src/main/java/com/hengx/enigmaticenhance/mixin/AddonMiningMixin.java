package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.DebugLog;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.RingConfig;
import com.hengx.enigmaticenhance.Superposition;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * 取消冒险者之证 / 绝望者证章的「无法挖掘方块」负面。
 * <p>
 * 反编译依据(enigmaticaddons 1.2.6 <b>与实例版 1.2.5 都核对过</b>,两版的
 * {@code AddonEventHandler#miningStuff} 结构一致,只有 1.2.6 多了一段 SURVIVOR_SCROLL 加成):
 * <pre>
 * 60/44: hasCurio(player, ADVENTURE_CHARM)      // 1.2.6 / 1.2.5 偏移
 * ...
 * 88/72: PlayerEvent$BreakSpeed.setCanceled(true)     // 第 1 处(冒险者之证)
 * 91/75: hasCurio(player, DESPAIR_INSIGNIA)
 * ...
 * 103/87: PlayerEvent$BreakSpeed.setCanceled(true)    // 第 2 处(绝望者证章)
 * </pre>
 * 用 {@code @Redirect} + {@code ordinal=0/1} 只包住这两条 {@code setCanceled} 调用:
 * <ul>
 * <li>开关打开且对应饰品戴在身上 → 不调用 {@code setCanceled} = 允许挖掘;</li>
 * <li>开关关闭 / 两件都没戴 → 原样 {@code setCanceled(cancel)} 透传(保留"想留负面"的能力);</li>
 * <li>EARTH_PROMISE / SURVIVOR_SCROLL 的 {@code setNewSpeed} 加成在方法前段,完全没碰;</li>
 * <li>只包住扩展方法体内的这两条调用,<b>不会</b>解掉其它模组对同一事件的取消
 *     (第三方取消发生在方法外,我们的 handler 永远不会调用 {@code setCanceled(false)});</li>
 * <li>与第三方同类 mod(实例里的 {@code EnigmaticAddonsFix})共存:它是
 *     {@code @Redirect SuperpositionHandler.hasCurio} + {@code @Inject(TAIL)},
 *     TAIL 注入的调用排在原版两条之后,所以 ordinal=0/1 仍指向原版那两处。</li>
 * </ul>
 * {@code required=false}(见 mixins.json):没装神秘遗物扩展时只是一条警告。
 */
@Mixin(targets = "auviotre.enigmatic.addon.handlers.AddonEventHandler")
public class AddonMiningMixin {

    private static final String SET_CANCELED =
        "Lnet/minecraftforge/event/entity/player/PlayerEvent$BreakSpeed;setCanceled(Z)V";

    /** 第 1 处:冒险者之证(扩展里还带"按住 Shift 可豁免",那是它自己的判断,我们只在它决定取消时拦)。 */
    @Redirect(method = "miningStuff(Lnet/minecraftforge/event/entity/player/PlayerEvent$BreakSpeed;)V",
        at = @At(value = "INVOKE", target = SET_CANCELED, ordinal = 0), remap = false)
    private void enigmaticenhance$adventureCharmMining(PlayerEvent.BreakSpeed event, boolean cancel) {
        Player player = event.getEntity();
        if (cancel && RingConfig.adventureCharmAllowMining()
            && Superposition.hasCurio(player, RingConfig.adventureCharmItem())) {
            if (RingConfig.debugLog()) {
                DebugLog.info("[挖掘放行] {} 佩戴冒险者之证,未取消 BreakSpeed",
                    player.getGameProfile().getName());
            }
            return;
        }
        event.setCanceled(cancel);
    }

    /** 第 2 处:绝望者证章(扩展里没有任何豁免条件)。 */
    @Redirect(method = "miningStuff(Lnet/minecraftforge/event/entity/player/PlayerEvent$BreakSpeed;)V",
        at = @At(value = "INVOKE", target = SET_CANCELED, ordinal = 1), remap = false)
    private void enigmaticenhance$despairInsigniaMining(PlayerEvent.BreakSpeed event, boolean cancel) {
        Player player = event.getEntity();
        if (cancel && RingConfig.despairInsigniaAllowMining()
            && Superposition.hasCurio(player, RingConfig.despairInsigniaItem())) {
            if (RingConfig.debugLog()) {
                DebugLog.info("[挖掘放行] {} 佩戴绝望者证章,未取消 BreakSpeed",
                    player.getGameProfile().getName());
            }
            return;
        }
        event.setCanceled(cancel);
    }
}
