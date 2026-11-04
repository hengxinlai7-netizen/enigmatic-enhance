package com.hengx.enigmaticenhance.mixin;

import java.util.Iterator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 修「重进游戏后救赎之戒从饰品槽被挪进背包」。
 * <p>
 * 根因(Curios 5.14.1 字节码,见 research/ea-1.1.9-report.md):
 * <pre>
 * CurioStacksHandler.update()   -> resize(baseSize + 修正符)
 * CurioStacksHandler.resize(n)  -> 若 n &lt; getSlots(): loseStacks(..., n) + stackHandler.shrink(n)
 * CurioStacksHandler.loseStacks -> 把 index &gt;= n 的堆叠交给 itemHandler.loseInvalidStack(stack)
 * CurioInventoryWrapper.loseInvalidStack -> 排进 invalidStacks
 * CurioInventoryWrapper.handleInvalidStacks -> 对 Player: ItemHandlerHelper.giveItemToPlayer(player, stack)  // 进背包
 * </pre>
 * 即:只要「ring」槽的数量在登录前后发生过**收缩**(例如槽位管理类模组先
 * {@code clearSlotModifiers()} 把临时/永久格位修正符清掉、再按配置 {@code growSlotType} 加回来),
 * 槽位号最高的那枚戒指就会被 Curios 判定为"多余"并塞进背包 —— 用户那枚救赎之戒正好在 index 3
 * (ring 槽 Size=5:base 2 + legacy +3),而七咒之戒(0)、铁戒(1)在 2 以内所以安然无恙。
 * <p>
 * 本 mixin 不改 Curios 的收缩逻辑,只改"多余堆叠的去处":在把队列发给玩家**之前**,
 * 如果队列里是救赎之戒,就把它放回 ring 饰品槽(没有空位就先补一格),而不是塞进背包。
 * 物品不会丢、不会复制(已经在 curios 里时直接丢弃这一份队列副本)。
 */
@Mixin(targets = "top.theillusivec4.curios.common.capability.CurioInventoryCapability$CurioInventoryWrapper")
public abstract class BlessRingCurioKeepMixin {

    @Shadow private NonNullList<ItemStack> invalidStacks;

    @Shadow public abstract LivingEntity getWearer();

    @Inject(method = "handleInvalidStacks()V", at = @At("HEAD"), remap = false)
    private void enigmaticenhance$keepBlessRingInCurio(CallbackInfo ci) {
        if (!RingConfig.keepRingOnRelogin() || this.invalidStacks.isEmpty()) {
            return;
        }
        if (!(this.getWearer() instanceof Player)) {
            return;
        }
        ICuriosItemHandler handler = (ICuriosItemHandler) (Object) this;
        for (Iterator<ItemStack> it = this.invalidStacks.iterator(); it.hasNext(); ) {
            ItemStack stack = it.next();
            if (stack.isEmpty() || !RingConfig.isRing(stack.getItem())) {
                continue;
            }
            if (enigmaticenhance$placeBack(handler, stack)) {
                it.remove();
            }
        }
    }

    /** 把戒指放回 ring 槽;没空位就先扩一格。放不下则返回 false(交回原版处理,物品不会丢)。 */
    private static boolean enigmaticenhance$placeBack(ICuriosItemHandler handler, ItemStack stack) {
        if (handler.isEquipped(stack.getItem())) {
            return true; // 槽里已经有同款了,这一份只是收缩时多出来的副本 -> 丢弃队列副本,避免复制
        }
        if (enigmaticenhance$putInEmptySlot(handler, stack)) {
            return true;
        }
        handler.growSlotType("ring", 1);
        return enigmaticenhance$putInEmptySlot(handler, stack);
    }

    private static boolean enigmaticenhance$putInEmptySlot(ICuriosItemHandler handler, ItemStack stack) {
        ICurioStacksHandler stacksHandler = handler.getStacksHandler("ring").orElse(null);
        if (stacksHandler == null) {
            return false;
        }
        IDynamicStackHandler stacks = stacksHandler.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (stacks.getStackInSlot(i).isEmpty()) {
                stacks.setStackInSlot(i, stack);
                return true;
            }
        }
        return false;
    }
}
