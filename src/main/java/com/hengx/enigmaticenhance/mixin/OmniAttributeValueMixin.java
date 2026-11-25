package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.OmniAttribute;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 4.11.0:让【全属性】({@code enigmaticenhance:omni}) 的读值按「百分比加成」语义返回。
 *
 * <h2>为什么</h2>
 * omni 的基础值是 0,而魔法石英花给的是 {@code MULTIPLY_BASE}(乘以基础值)——
 * {@code 0 × 1.12 = 0},面板/工具提示永远显示 0,玩家看到的就是"花给的加成没生效"。
 * 这里只在<b>属性就是 omni</b> 时把 {@code getValue} 的返回值换成
 * {@link OmniAttribute#effectiveBonus(AttributeInstance)}(基数视为 1.0 的展平值),
 * 其它属性、其它模组完全不受影响。
 *
 * <p>{@code m_22135_} 是生产环境里 {@code AttributeInstance#getValue} 的 SRG 名;
 * {@code remap = false} + {@code require = 0}:解析不到就静默跳过(不影响游戏)。
 * handler 是<b>实例方法</b>(宿主 {@code getValue} 是实例方法)。</p>
 */
@Mixin(AttributeInstance.class)
public final class OmniAttributeValueMixin {

    @Inject(method = "m_22135_", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void enigmaticenhance$effectiveOmniValue(CallbackInfoReturnable<Double> cir) {
        try {
            AttributeInstance self = (AttributeInstance) (Object) this;
            Attribute omni = OmniAttribute.getOrNull();
            if (omni == null || self.getAttribute() != omni) {
                return;
            }
            cir.setReturnValue(OmniAttribute.effectiveBonus(self));
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败保持原值
        }
    }
}
