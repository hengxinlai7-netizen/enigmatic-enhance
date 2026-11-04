package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.DebugLog;
import com.hengx.enigmaticenhance.EarthAmuletData;
import com.hengx.enigmaticenhance.EarthAmuletHandler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 骷髅头:**射箭不消耗箭矢**(1.1.24 起;1.2.12a 修好"有箭时被扣箭"的问题)。
 * <p>
 * 目标方法 {@code m_6298_}({@code Player#getProjectile(ItemStack)},1.20.1 SRG,已核实):
 * 它的 vanilla 逻辑是"先看手上/背包有没有箭;没有且不是创造模式 ⇒ 返回 {@code ItemStack.EMPTY}"。
 * 注意:Forge 的 {@code LivingGetProjectileEvent} 只在**已经找到箭**时才发出
 * ({@code ForgeHooks.getProjectile} 只包住"找到的那一支"),所以"完全没箭"这种情形
 * 事件根本不会触发 —— 必须在 RETURN 处补一支**游离的**箭。
 * <p>
 * 为什么安全:
 * <ul>
 * <li>只对**弓**({@link BowItem})生效,弩/三叉戟/其它投掷武器行为不变;</li>
 * <li>只在 vanilla 本来会返回空(即真的没箭、且不是创造模式)时补;
 *     创造模式({@code instabuild})与"无限"附魔走的都是**非空**返回值 ⇒ 原样返回,行为不变;</li>
 * <li>补的是一支**不挂在背包里**的 {@code new ItemStack(ARROW)},下游 {@code shrink(1)} 只作用在这个
 *     临时堆上 ⇒ 背包里的箭数一个都不会少(有箭时根本不会走到这里)。</li>
 * </ul>
 * {@code remap = false} 与本项目其它 mixin 一致(运行期是 SRG 名)。
 */
@Mixin(Player.class)
public class PlayerProjectileMixin {

    @Inject(method = "m_6298_", at = @At("RETURN"), cancellable = true, remap = false)
    private void enigmaticenhance$infiniteArrow(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        Player self = (Player) (Object) this;
        try {
            ItemStack result = cir.getReturnValue();
            if (result != null && !result.isEmpty()) {
                // 1.2.12a:背包里**有箭**时,原版 getProjectile 返回的是背包里那一摞本身,
                // BowItem/CrossbowItem 随后会 stack.shrink(1) ⇒ 箭真的被扣掉。
                // 骷髅头的描述是"射箭不消耗箭矢",所以这里改成返回一份**只有 1 个的副本**:
                // 下游 shrink(1) 只作用在副本上,背包一个不少;箭的种类(普通/药箭)保持不变。
                if (weapon != null && !weapon.isEmpty()
                        && weapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem
                        && !self.getAbilities().instabuild && !self.isCreative()
                        && EarthAmuletData.equippedWithHead(self, EarthAmuletData.HEAD_SKELETON)) {
                    cir.setReturnValue(result.copyWithCount(1));
                    DebugLog.info("{} {} 骷髅头:箭矢不消耗(返回副本)", EarthAmuletHandler.LOG_TAG,
                        self.getName().getString());
                }
                return;
            }
            if (weapon == null || weapon.isEmpty()
                    || !(weapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem)) {
                return;
            }
            if (self.getAbilities().instabuild || self.isCreative()) {
                return;
            }
            if (!EarthAmuletData.equippedWithHead(self, EarthAmuletData.HEAD_SKELETON)) {
                return;
            }
            cir.setReturnValue(new ItemStack(Items.ARROW));
            DebugLog.info("{} {} 骷髅头:背包无箭,补一支虚拟箭(不消耗)", EarthAmuletHandler.LOG_TAG,
                self.getName().getString());
        }
        catch (Throwable ignored) {
            // 任何异常都不许影响原版射箭:cir 里本来就是 vanilla 的返回值
        }
    }
}
