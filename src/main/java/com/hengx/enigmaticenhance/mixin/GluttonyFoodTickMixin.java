package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.GluttonyHandler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * 1.2.5a:暴食之证的饱食度改成<b>直接锁定</b>。
 *
 * <p>以前(到 1.2.4 为止)的做法是每 tick 在 {@code PlayerTickEvent.START} 里把
 * {@code foodLevel/saturation} 写回锁定量 —— 那其实是"时刻调整":原版 {@code FoodData#tick}
 * 在同一 tick 里照样跑,只是下一 tick 又被覆盖回去,数值一直在被写。</p>
 *
 * <p>1.2.6g:改用 {@link GluttonyHandler#lockActive} —— **两端都取消**(客户端也要取消,\n * 否则客户端自己的 FoodData 会继续扣饱和度,而原版又只在饱食度变化时同步,显示就永远不对)。\n *\n * <p>现在直接在源头掐掉:{@code FoodData#tick(Player)}(SRG {@code m_38710_})开头就判断
 * 佩戴者是否戴着暴食之证,是就整个取消。于是原版再也不会改动饱食度/饱和度 ——
 * 值只在"刚戴上"那一刻写一次,之后恒定不动。</p>
 *
 * <p>副作用(有意为之):原版 {@code FoodData#tick} 也负责"消耗疲劳值"和"自然回血",
 * 一起被跳过;疲劳消耗与回血由 {@link GluttonyHandler} 自己结算(它本来就是这么设计的:
 * 回血代价记到饱和度上)。</p>
 */
@Mixin(FoodData.class)
public abstract class GluttonyFoodTickMixin {

    @Inject(method = "m_38710_", at = @At("HEAD"), cancellable = true, remap = false)
    private void enigmaticenhance$lockFood(Player player, CallbackInfo ci) {
        if (player != null && GluttonyHandler.lockActive(player)) {
            ci.cancel();
        }
    }
}
