package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.PearlHandler;
import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 「附魔师的珍珠」在神化附魔台上的修复(神化那一侧,不动神化本体逻辑)。
 * <p>
 * 目标 {@code ApothEnchantmentMenu#clickMenuButton(Player,int)}(SRG {@code m_6366_},remap=false 因为神化是模组类):
 * <ul>
 * <li>HEAD:珍珠在场时,把青金石槽临时补成 64,绕过神化自己的"青金石数量"检查
 *     —— 等价于神秘遗物给原版 {@code getGoldCount()} 返回 64 的效果(那半个注入其实仍然作用在
 *     {@code ApothEnchantmentMenu} 上,因为它没有覆写 {@code getGoldCount}),同时记下输入栈快照与威力;</li>
 * <li>RETURN:先把青金石槽恢复原样(所以神化 lambda 里的 {@code lapis.shrink(j)} 实际扣的是临时栈,
 *     净效果等于"附魔不再消耗青金石"),再按神秘遗物原版公式补一次 {@code min(cost+7,40)} 的附魔并合并。</li>
 * </ul>
 * 神化自己的附魔选择(eterna/quanta/arcana、infusion 等)完全不变 —— 只在其结果上追加珍珠那部分。
 */
@Mixin(targets = "dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu")
public abstract class PearlApothEnchantMixin {

    @Unique
    private ItemStack enigmaticenhance$pearlSavedLapis;

    @Unique
    private ItemStack enigmaticenhance$pearlInputSnapshot;

    @Unique
    private int enigmaticenhance$pearlCost;

    @Inject(method = "m_6366_(Lnet/minecraft/world/entity/player/Player;I)Z",
        at = @At("HEAD"), remap = false)
    private void enigmaticenhance$pearlBeforeClick(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        this.enigmaticenhance$pearlSavedLapis = null;
        this.enigmaticenhance$pearlInputSnapshot = null;
        if (!RingConfig.pearlFixEnchantingTable()) {
            return;
        }
        if (id < 0 || id > 2) {
            return;
        }
        if (!PearlHandler.pearlPresent(player)) {
            return;
        }
        EnchantmentMenu self = (EnchantmentMenu) (Object) this;
        this.enigmaticenhance$pearlCost = self.costs[id];
        this.enigmaticenhance$pearlInputSnapshot = self.getSlot(0).getItem().copy();
        this.enigmaticenhance$pearlSavedLapis = self.getSlot(1).getItem().copy();
        self.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 64));
    }

    @Inject(method = "m_6366_(Lnet/minecraft/world/entity/player/Player;I)Z",
        at = @At("RETURN"), remap = false)
    private void enigmaticenhance$pearlAfterClick(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        ItemStack saved = this.enigmaticenhance$pearlSavedLapis;
        ItemStack before = this.enigmaticenhance$pearlInputSnapshot;
        int cost = this.enigmaticenhance$pearlCost;
        this.enigmaticenhance$pearlSavedLapis = null;
        this.enigmaticenhance$pearlInputSnapshot = null;
        if (saved == null) {
            return;
        }
        EnchantmentMenu self = (EnchantmentMenu) (Object) this;
        // 复原青金石槽:神化 lambda 扣掉的是我们的临时栈,所以真实青金石一点没少。
        self.getSlot(1).set(saved);
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || before == null || before.isEmpty()) {
            return;
        }
        ItemStack now = self.getSlot(0).getItem();
        if (now.isEmpty() || ItemStack.matches(before, now)) {
            // 这次点击没能真的附魔(例如经验不足),不补掷。
            return;
        }
        self.getSlot(0).set(PearlHandler.applyExtraEnchant(player, now, before, cost));
    }
}
