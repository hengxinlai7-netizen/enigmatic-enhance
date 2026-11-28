package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.OmniAttributeApplier;
import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 阻止「全属性」互叠：别的模组往属性上挂全属性修饰符时直接拒绝，只保留本模组这一份。
 *
 * <p>例：月之石系(Boon Tablet)每 tick 会给自己以外的所有属性挂一条
 * {@code "All Attribute Bonus"} 的 {@code MULTIPLY_TOTAL} 修饰符。它和我们的
 * {@code "EnigmaticEnhance Omni Attribute"} 会相乘，导致属性异常增殖。</p>
 *
 * <p>只拦名字里带 all attribute / omni attribute 的修饰符；单属性加成一律放行。</p>
 */
@Mixin(AttributeInstance.class)
public abstract class OmniStackGuardMixin {

    @Inject(method = "m_22133_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ee$guardAddModifier(AttributeModifier modifier, CallbackInfo ci) {
        if (ee$shouldBlock(modifier)) {
            ci.cancel();
        }
    }

    @Inject(method = "m_22118_", at = @At("HEAD"), cancellable = true, remap = false)
    private void ee$guardAddTransient(AttributeModifier modifier, CallbackInfo ci) {
        if (ee$shouldBlock(modifier)) {
            ci.cancel();
        }
    }

    private static boolean ee$shouldBlock(AttributeModifier modifier) {
        try {
            if (modifier == null || !RingConfig.omniBlockForeign()) {
                return false;
            }
            if (OmniAttributeApplier.isOwnModifier(modifier)) {
                return false;
            }
            String name = modifier.getName();
            if (name == null) {
                return false;
            }
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("all attribute") || lower.contains("omni attribute")
                    || lower.contains("all_attribute") || lower.contains("allattributes");
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
