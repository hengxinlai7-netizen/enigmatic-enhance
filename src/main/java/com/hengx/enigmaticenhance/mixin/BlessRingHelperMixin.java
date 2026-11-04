package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 让救赎之戒「自带无法破坏」—— 拦的是**背叛值 BlessBetrayal**,不是 BlessDuration。
 * <p>
 * 字节码事实(见 research/ea-report.md):
 * <ul>
 * <li>玩家看到的那条"耐久条"是 {@code BlessBetrayal} 伪装出来的
 *     ({@code BlessRing#m_142522_/m_142158_/m_142159_}),与 {@code BlessDuration} 无关;</li>
 * <li>{@code BlessDuration} 只是"已经破损了 N 次"的计数器,破损时 +1,并让
 *     {@code getMaxBetrayal = 1250 - 100*BlessDuration} 递减 —— 它本身就是节流阀;</li>
 * <li>唯一写入口是 {@code Helper.setBlessAttribute(stack, "BlessBetrayal", v)};
 *     上游的 {@code Helper.addBetrayal(Player, int)} 被 {@code AddonEventHandler} 等 4 处直接调用。</li>
 * </ul>
 * 所以在<b>唯一写入口</b>上拦住 {@code BlessBetrayal}(而不是 {@code BlessDuration}):
 * 背叛值永远停在原值(通常是 0),{@code > getMaxBetrayal} 永不成立,破损分支不可能被触发,
 * 同时 {@code BlessDuration} 的原版节流行为(5 次上限)完整保留。
 * <p>
 * 另外在 {@code addBetrayal} 的 HEAD 再拦一道,覆盖 {@code AddonEventHandler} 的直接调用
 * (即使将来有人绕过 {@code setBlessAttribute} 也不会漏)。
 * <p>
 * {@code required=false}(见 mixins.json),没装神秘遗物扩展时只是一条警告,不影响启动。
 */
@Mixin(targets = "auviotre.enigmatic.addon.contents.items.BlessRing$Helper")
public class BlessRingHelperMixin {

    /** {@code BlessRing.BETRAYAL} 的字面量。 */
    private static final String BETRAYAL = "BlessBetrayal";

    /** 唯一写入口:key 为 BlessBetrayal 时整体取消。 */
    @Inject(method = "setBlessAttribute(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void enigmaticenhance$freezeBetrayal(ItemStack stack, String key, int value, CallbackInfo ci) {
        if (RingConfig.freezeBetrayal() && BETRAYAL.equals(key)) {
            ci.cancel();
        }
    }

    /** 上游唯一入口:AddonEventHandler 的 4 处直接调用走这里。 */
    @Inject(method = "addBetrayal(Lnet/minecraft/world/entity/player/Player;I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void enigmaticenhance$freezeBetrayalAdd(Player player, int amount, CallbackInfo ci) {
        if (RingConfig.freezeBetrayal()) {
            ci.cancel();
        }
    }
}
