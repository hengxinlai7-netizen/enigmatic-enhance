package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.CursedXpCap;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 4.8.4:无知诅咒卷轴「到上限就停手 + 不把人吸到 0 级」,<b>不取消整个 tick</b>。
 *
 * <h2>根因(反编译 1.2.7 的 curioTick)</h2>
 * <pre>
 * int portion = getXPPortion(player);                 // 1/5 级; level&gt;100 再乘 (1+level/100)
 * if (absorptionMode) {
 *     if (getPlayerXP(player) &gt;= portion) { drain(portion); XPStored += portion; }   // ① 无上限
 *     else if (getPlayerXP(player) &gt; 0)   { drain(全部);    XPStored += 全部;    }   // ② 吸到 0 级
 * }
 * for (ExperienceOrb orb : 半径内经验球) orb.playerTouch(player);                       // ③ 吸球
 * </pre>
 *
 * <h2>做法:把 {@code getPlayerXP} 重定向成 0,而不是 cancel</h2>
 * ① {@link Redirect} 夹 {@code CursedXPScroll#getXPPortion} → 本 tick 份额不超过剩余额度;
 * ② {@link Redirect} 把 {@code ExperienceHelper.getPlayerXP(Player)} 改成 0(仅在吸收模式 + 该停的时候)——
 *    这样上面两个分支都判假({@code 0>=portion} 假、{@code 0>0} 假),<b>不 drain 也不改 XPStored</b>,
 *    但方法继续往下执行 ⇒ 末尾"扫半径内经验球"那段**照常运行**。
 * ③ HEAD 只做记录(stack 与真实 XP),<b><u>绝不 cancel</u></b>。
 *
 * <p>为什么不能 cancel:一旦 cancel,整个方法(含吸球)都不跑 —— 实测表现为"卷轴连地上的经验球都不吸了"。
 * 上一版就是这么回归的,这里改掉。</p>
 *
 * <h2>{@code getPlayerXP} 的真实语义(反编译确认)</h2>
 * <pre>
 * return (int)( getExperienceForLevel(player.experienceLevel)
 *             + player.experienceProgress * player.getXpNeededForNextLevel() );
 * </pre>
 * 不是 {@code totalExperience}。所以真实值用 {@link CursedXpCap#realPlayerXp(Player)} 等价实现,
 * 由 HEAD 取一次存 ThreadLocal(避免 Redirect 里再调一次被自己拦)。
 *
 * <p>取回模式({@code AbsorptionMode=false})一律原样:两个 Redirect 都直接返回原语义值。</p>
 */
@Mixin(value = auviotre.enigmatic.addon.contents.items.CursedXPScroll.class)
public abstract class CursedXpScrollCapMixin {

    /** Redirect 拿不到 {@code curioTick} 的 stack/player 形参,由 HEAD 记下(同 tick 同线程顺序执行)。 */
    private static final ThreadLocal<ItemStack> CURRENT_STACK = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CURRENT_XP = new ThreadLocal<>();

    /** HEAD:只记录,不 cancel。 */
    @Inject(method = "curioTick", at = @At("HEAD"), remap = false, require = 0)
    private void enigmaticenhance$capture(SlotContext context, ItemStack stack, CallbackInfo ci) {
        CURRENT_STACK.set(stack);
        if (context != null && context.entity() instanceof Player player) {
            CURRENT_XP.set(CursedXpCap.realPlayerXp(player));
        }
        else {
            CURRENT_XP.remove();
        }
    }

    /**
     * ① 本 tick 的份额不超过"离上限还差多少"。
     * <p>{@code getXPPortion} 在目标类里只有 1 个调用点(已 javap 核对),所以不需要 ordinal。</p>
     */
    @Redirect(
        method = "curioTick",
        at = @At(
            value = "INVOKE",
            target = "Lauviotre/enigmatic/addon/contents/items/CursedXPScroll;getXPPortion(Lnet/minecraft/world/entity/player/Player;)I"),
        remap = false,
        require = 0)
    private int enigmaticenhance$clampPortion(auviotre.enigmatic.addon.contents.items.CursedXPScroll self,
                                              Player player) {
        int raw = CursedXpCap.portionForLevel(player == null ? 0 : player.experienceLevel);
        ItemStack stack = CURRENT_STACK.get();
        if (stack == null || stack.isEmpty() || !CursedXpCap.isAbsorptionMode(stack)) {
            return raw;                     // 取回模式:原样
        }
        int clamped = CursedXpCap.clampPortion(raw, CursedXpCap.storedXp(stack));
        // clamped == 0 只有"已到上限"才会出现;这里保守回 raw,
        // 绝不把 0/负数交给扩展(否则它会走 portion<=0 → 5 的兜底,反而多吸)
        return clamped > 0 ? clamped : raw;
    }

    /**
     * ② 该停的时候把 {@code getPlayerXP} 的返回值改成 0:
     * 两个吸收分支都判假 → 不 drain、不改 XPStored,但方法继续跑 ⇒ 吸球照常。
     *
     * <p>注意:目标是<b>静态</b>方法({@code ExperienceHelper.getPlayerXP}),但 Mixin 校验的是
     * <b>宿主方法</b>({@code curioTick})的静态性 —— 宿主是实例方法,所以本 handler 也必须写成
     * 非静态的;写成 {@code static} 会报
     * "'static' modifier of handler method does not match target"。</p>
     */
    @Redirect(
        method = "curioTick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/aizistral/enigmaticlegacy/helpers/ExperienceHelper;getPlayerXP(Lnet/minecraft/world/entity/player/Player;)I"),
        remap = false,
        require = 0)
    private int enigmaticenhance$zeroXpWhenSkipping(Player player) {
        int real = CURRENT_XP.get() != null ? CURRENT_XP.get() : CursedXpCap.realPlayerXp(player);
        ItemStack stack = CURRENT_STACK.get();
        if (stack == null || stack.isEmpty()) {
            return real;
        }
        boolean skip = CursedXpCap.shouldSkip(stack, real, player == null ? 0 : player.experienceLevel);
        CursedXpCap.logSkipDecision(stack, real, player == null ? 0 : player.experienceLevel, skip);
        return skip ? 0 : real;
    }
}
