package com.hengx.enigmaticenhance.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.RevivalLeafStoneHandler;

import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;

/**
 * 4.5.0:「复苏之叶」飞行开关的真正落点。开关关闭时<b>不让扩展授予飞行</b>,
 * 而不是事后把 {@code mayfly} 撤回。
 *
 * <h2>目标字节码(实测,{@code javap -p -c libs/enigmaticaddons-1.2.6.jar})</h2>
 * <pre>
 * protected void handleFlight(net.minecraft.world.entity.player.Player);
 *    0: aload_0
 *    1: aload_1
 *    2: invokevirtual  hasPlantBy:(Lnet/minecraft/world/entity/player/Player;)Z
 *    5: ifeq           48                 // 没植物 → 走撤销分支
 *    8: aload_1
 *    9: invokevirtual  Player.m_150110_:()Lnet/minecraft/world/entity/player/Abilities;   // getAbilities()
 *   12: getfield       Abilities.f_35936_:Z                     // mayfly(读)
 *   15: ifne           30                 // 已经能飞 → 跳过授予(不动别人的值)
 *   18: aload_1
 *   19: invokevirtual  Player.m_150110_:()Lnet/minecraft/world/entity/player/Abilities;
 *   22: iconst_1
 *   23: putfield       Abilities.f_35936_:Z                     // ★ 唯一的"授予"写点:mayfly = true
 *   26: aload_1
 *   27: invokevirtual  Player.m_6885_:()V                       // onUpdateAbilities()
 *   30: flyMap.put(player, 5)
 *   48: ...(递减计数)
 *  103: (计数器 == 1 时)撤销:
 *  123: invokevirtual  Player.m_7500_:()Z                       // isCreative() → 创造模式直接跳过
 *  130: invokevirtual  Player.m_5833_:()Z                       // isSpectator()
 *  137: getfield / 142: putfield Abilities.f_35936_:Z           // mayfly = false
 *  145: getfield / 150: putfield Abilities.f_35935_:Z           // flying = false  ← 踢下飞行
 *  153: invokevirtual  Player.m_6885_:()V                       // onUpdateAbilities()
 * </pre>
 *
 * <p>结论(按字节码,不猜):</p>
 * <ol>
 *   <li>授予飞行<b>不是</b>调用 helper,就是一条 {@code PUT_FIELD Abilities.mayfly(Z)};
 *       所以 {@code @At(value = "FIELD", opcode = PUT_FIELD)} 是唯一精确的注入点;</li>
 *   <li>{@code handleFlight} 里对 {@code mayfly} 的写入一共<b>两处</b>(授予 true、扩展自己撤销 false),
 *       对 {@code flying} 一处、{@code onUpdateAbilities()} 两处。一个 {@code @Redirect}
 *       会同时接管同类写入的全部出现位置,正好把"授予"和"扩展自己的撤销"一起管住;</li>
 *   <li>授予那一段外面还有 {@code if (mayfly) skip} 的守卫 —— 玩家本来就能飞(创造/旁观/其它模组)时
 *       扩展本来就不写,我们的拦截只可能作用在"本来就没有飞行"的玩家身上。</li>
 * </ol>
 *
 * <h2>为什么用 SRG 名 + {@code remap = false}</h2>
 * <p>扩展是第三方模组,发布 jar 里对 MC 成员的引用已经是 SRG 名
 * ({@code f_35936_} = mayfly、{@code f_35935_} = flying、{@code m_6885_} = onUpdateAbilities),
 * 所以这里直接写 SRG 名并关掉 remap,和工程里 {@code EnchantmentHelperSweepMixin} 的做法一致。
 * 写成官方名反而在运行期对不上。</p>
 *
 * <h2>4.5.0:为什么不可能误伤别的飞行来源(修的正是老 bug)</h2>
 * <p>开关关闭 且 玩家带着复苏之叶时,本 mixin 让 {@code handleFlight} 对玩家能力的<b>所有</b>写入全部跳过;
 * 开关打开时三条 {@code @Redirect} 原样转调,扩展行为与未装本模组时逐指令一致。
 * 我们<b>没有"撤销"这个动作</b>了 —— 别人的 {@code mayfly} 我们从来没写过,自然踩不掉,
 * 也不会再有人在关闭开关时被 {@code flying = false} / {@code onUpdateAbilities()} 踢下飞行。</p>
 *
 * <p>{@code require = 0}:扩展缺席或将来改了这个方法时,注入静默不生效(退回扩展原行为),不会让游戏崩。
 * 用 {@code targets} 字符串而不是类型引用:没装扩展时类不存在,类型引用会在类加载阶段就抛异常。</p>
 */
@Mixin(targets = "auviotre.enigmatic.addon.contents.items.RevivalLeaf", remap = false)
public class RevivalLeafFlightMixin {

    /** HEAD:把"本次调用是否被开关压制"算出来并记住(供下面三个 {@code @Redirect} 读)。 */
    @Inject(method = "handleFlight", at = @At("HEAD"), remap = false, require = 0)
    private void enigmaticenhance$enter(Player player, CallbackInfo ci) {
        RevivalLeafStoneHandler.enterHandleFlight(player);
    }

    /** RETURN:清掉本次记录(不撤销任何东西)。 */
    @Inject(method = "handleFlight", at = @At("RETURN"), remap = false, require = 0)
    private void enigmaticenhance$exit(Player player, CallbackInfo ci) {
        RevivalLeafStoneHandler.exitHandleFlight();
    }

    /**
     * 拦住 {@code mayfly} 的写入(两处:授予 {@code true}、扩展自己撤销 {@code false})。
     * <p>开关关闭 → 一条都不写,玩家的 {@code mayfly} 保持原样(谁给的就是谁的)。</p>
     */
    @Redirect(method = "handleFlight",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/world/entity/player/Abilities;f_35936_:Z"),
        remap = false, require = 0)
    private void enigmaticenhance$mayfly(Abilities abilities, boolean value) {
        if (RevivalLeafStoneHandler.suppressed()) {
            return;
        }
        abilities.mayfly = value;
    }

    /**
     * 拦住 {@code flying} 的写入(扩展自己撤销时会顺手把玩家踢下飞行)。
     * <p>开关关闭 → 不写,正在飞的人不会因为这条指令掉下去。</p>
     */
    @Redirect(method = "handleFlight",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/world/entity/player/Abilities;f_35935_:Z"),
        remap = false, require = 0)
    private void enigmaticenhance$flying(Abilities abilities, boolean value) {
        if (RevivalLeafStoneHandler.suppressed()) {
            return;
        }
        abilities.flying = value;
    }

    /**
     * 拦住能力同步包({@code onUpdateAbilities()})。
     * <p>开关关闭 → 不发包,免得把别处刚设好的飞行状态用一包旧能力顶掉。</p>
     */
    @Redirect(method = "handleFlight",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_6885_()V"),
        remap = false, require = 0)
    private void enigmaticenhance$onUpdateAbilities(Player player) {
        if (RevivalLeafStoneHandler.suppressed()) {
            return;
        }
        player.onUpdateAbilities();
    }
}
