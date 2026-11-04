package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.PearlHandler;
import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.world.entity.player.Player;

/**
 * 「附魔师的珍珠」→ <b>远古重铸台</b>({@code ancientreforging},第三方独立模组)同样不消耗新生符石。
 * <p>
 * 与神化重铸台({@code PearlReforgeMixin})完全一致的拦截方式:两边是同一个作者/同一套结构,
 * {@code AncientReforgingMenu} 里唯一的新生符石消耗来源就是
 * <pre>
 * getSigilCost(int slot) == costs[0] * (slot + 1)
 * </pre>
 * 证据(ancientreforging-1.3.jar 字节码):
 * <ul>
 * <li>{@code AncientReforgingMenu#getSigilCost(I)I}:{@code costs[0]} 乘 {@code slot+1},无其它分支;</li>
 * <li>{@code AncientReforgingMenu$ReforgingResultSlot#onTake(Player, ItemStack)}(SRG {@code m_142406_}):
 *     偏移 85–97 为 {@code this$0.getSlot(2).getItem().shrink(this$0.getSigilCost(slotIndex))}
 *     —— 唯一扣新生符石的地方;珍宝材料({@code getMatCost},偏移 69–82)与经验
 *     ({@code getLevelCost} + {@code ApothMiscUtil.getExpCostForSlot},偏移 100–113)是另外两段,互不影响;</li>
 * <li>{@code AncientReforgingMenu$ReforgingResultSlot#mayPickup(Player)}(SRG {@code m_8010_}):
 *     用同一个 {@code getSigilCost} 当"能不能取走产物"的门槛 —— 返回 0 同时做到"不扣"与"没有符石也能重铸";</li>
 * <li>{@code AncientReforgingScreen}:界面上的数字也走 {@code getSigilCost},所以客户端显示同步变成 0。</li>
 * </ul>
 * 因此只在珍珠在场时把该方法返回 0:符石不减、门槛消失,材料/经验/词缀生成(在
 * {@code slotsChanged}/{@code LootController} 里)完全不动。
 * <p>
 * 目标类是第三方模组类,可能整个缺席,所以本 mixin 单独登记在
 * {@code enigmaticenhance.optional.mixins.json}({@code required:false} + {@code injectors.defaultRequire:0}):
 * 缺模组、或对方改了方法名时只会静默跳过,不影响主配置里那些必需注入。
 */
@Mixin(targets = "com.ianm1647.ancientreforging.screen.AncientReforgingMenu")
public abstract class AncientReforgeMixin {

    /** {@code AncientReforgingMenu#player}(模组自己的字段名,不在混淆表里,所以 remap = false)。 */
    @Shadow(remap = false)
    protected Player player;

    /** 1.6.44:素材同样免消耗。 */
    @Inject(method = "getMatCost(I)I", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$freeAncientMatReforge(int slot, CallbackInfoReturnable<Integer> cir) {
        try {
            if (!RingConfig.pearlFreeReforge()) {
                return;
            }
            if (this.player == null || !PearlHandler.pearlPresent(this.player)) {
                return;
            }
            cir.setReturnValue(0);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 1.6.44:素材数量(无参 getMatCount)同样归零。 */
    @Inject(method = "getMatCount()I", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$freeAncientMatCount(CallbackInfoReturnable<Integer> cir) {
        try {
            if (!RingConfig.pearlFreeReforge()) {
                return;
            }
            if (this.player == null || !PearlHandler.pearlPresent(this.player)) {
                return;
            }
            cir.setReturnValue(0);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @Inject(method = "getSigilCost(I)I", at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$freeAncientSigilReforge(int slot, CallbackInfoReturnable<Integer> cir) {
        if (!RingConfig.pearlFreeReforge()) {
            return;
        }
        if (this.player == null || !PearlHandler.pearlPresent(this.player)) {
            return;
        }
        cir.setReturnValue(0);
    }
}
