package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.PearlUtil;

import dev.shadowsoffire.placebo.cap.InternalItemHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 1.2.27:携带附魔师珍珠时,**神化重铸台不消耗素材**。
 *
 * <p>不再反射内部类:直接混入菜单本身,{@code @Shadow} 拿素材容器
 * {@code choicesInv}(类型 {@code InternalItemHandler})。</p>
 *
 * <p>注入点用菜单关闭({@code removed})之前的时机不可靠,所以改为在
 * 结果槽取走时由 {@code Slot#onTake} 触发 —— 见 {@link ReforgingRefundMixin}。
 * 本类提供"备份/还原素材"的静态入口。</p>
 */
@Mixin(targets = "dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu")
public class ReforgingMenuMixin {

    @Shadow
    protected InternalItemHandler choicesInv;

    @Shadow
    protected InternalItemHandler itemInv;

    @Inject(method = "m_6877_", at = @At("HEAD"), remap = false, require = 0)
    private void enigmaticenhance$restoreOnClose(Player player, CallbackInfo ci) {
        // 关闭界面时兜底:素材若被扣光而珍珠还在,补回 1 个
        try {
            if (!PearlUtil.wearing(player) || this.choicesInv == null) {
                return;
            }
            for (int i = 0; i < this.choicesInv.getSlots(); i++) {
                ItemStack stack = this.choicesInv.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                stack.setCount(Math.max(1, stack.getCount()));
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }
}