package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * 给饰品挂属性修饰符(纯事件,不注入)。
 * <ul>
 * <li>救赎之戒:{@code enigmaticenhance:omni} +20%。</li>
 * </ul>
 * <p>
 * <b>4.8.4:万钧之护卷轴不再走这里。</b>它的 +100% 原来是
 * {@code generic.attack_damage} 上的 {@code MULTIPLY_TOTAL} 修饰符(经
 * {@link CurioAttributeModifierEvent} 挂上去,和 Curios 合并
 * {@code ICurioItem#getAttributeModifiers} 是同一条管线),副作用是武器 tooltip 显示的伤害、
 * 其它模组读 {@code ATTACK_DAMAGE} 的结果、以及横扫/剑气/部分挖掘速度全都跟着翻倍。
 * 现在改成 {@link ScrollHandler#onHurtDirectBoost} 里直接乘这一次伤害,属性表完全不碰,
 * 所以本类里那条 {@code ATTACK_DAMAGE} 的 {@code addModifier} 已删除。
 * <p>
 * 注意:本类引用了 Curios 的类,**只有 Curios 已加载时才会被注册**
 * (见 {@link EnigmaticEnhance}),缺 Curios 时本类根本不会被加载。
 */
public final class CurioCompat {

    @SubscribeEvent
    public void onCurioAttributeModifier(CurioAttributeModifierEvent event) {
        if (RingConfig.isRing(event.getItemStack().getItem())) {
            Attribute omni = OmniAttribute.getOrNull();
            if (omni != null) {
                event.addModifier(omni, RingConfig.ringOmniModifier(omni));
            }
        }
    }
}
