package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.PearlUtil;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 1.2.22:携带「附魔师珍珠」时,神化**强化台不消耗强化符石**。
 *
 * <p>强化/重铸都走 {@code AugmentingMenu#clickMenuButton(m_6366_)};
 * 这里不改它的逻辑,只做"备份 → 执行 → 还原数量":
 * 强化结果、附加词条、失败概率全部照原样,唯一区别是符石数量被写回。</p>
 */
@Mixin(targets = "dev.shadowsoffire.apotheosis.adventure.affix.augmenting.AugmentingMenu")
public abstract class AugmentingSigilMixin {

    @Shadow
    public abstract ItemStack getSigils();

    private int enigmaticenhance$sigilsBefore = -1;

    @Inject(method = "m_6366_", at = @At("HEAD"), remap = false, require = 0)
    private void enigmaticenhance$beforeClick(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!PearlUtil.wearing(player)) {
                return;
            }
            ItemStack sigils = this.getSigils();
            if (sigils != null && !sigils.isEmpty()) {
                this.enigmaticenhance$sigilsBefore = sigils.getCount();
            }
        }
        catch (Throwable ignored) {
            // 失败就走原版消耗
        }
    }

    @Inject(method = "m_6366_", at = @At("RETURN"), remap = false, require = 0)
    private void enigmaticenhance$afterClick(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (this.enigmaticenhance$sigilsBefore < 0) {
                return;
            }
            ItemStack sigils = this.getSigils();
            if (sigils != null && !sigils.isEmpty() && sigils.getCount() < this.enigmaticenhance$sigilsBefore) {
                sigils.setCount(this.enigmaticenhance$sigilsBefore);   // 退还符石
                com.hengx.enigmaticenhance.DebugLog.once("augsigil",
                    "[Pearl] 强化台退还符石 {} 个", this.enigmaticenhance$sigilsBefore);
            }
            this.enigmaticenhance$sigilsBefore = -1;
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }
}