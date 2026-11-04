package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * 给饰品挂属性修饰符(纯事件,不注入)。
 * <ul>
 * <li>救赎之戒:{@code enigmaticenhance:omni} +20%。</li>
 * <li>万钧之护卷轴:{@code generic.attack_damage} 百分比加成(默认 +100%,
 *     {@code MULTIPLY_TOTAL}),取代它原本的"横扫攻击造成 100% 伤害"。</li>
 * </ul>
 * 走 Curios 的 {@link CurioAttributeModifierEvent},也就是 Curios 合并
 * {@code ICurioItem#getAttributeModifiers} 的**同一条管线**(反编译
 * {@code CuriosImplMixinHooks#getAttributeModifiers} → {@code MinecraftForge.EVENT_BUS.post(new CurioAttributeModifierEvent(...))});
 * 神秘遗物本体/扩展的饰品(谜之护身符、冒险者之证、绝望者证章)也是把
 * {@code generic.attack_damage} 的 {@code AttributeModifier} 挂在这条管线上,所以是"同一个属性修饰符体系"。
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
            return;
        }
        if (ScrollHandler.attackDamageActiveFor(event.getItemStack())) {
            event.addModifier(Attributes.ATTACK_DAMAGE, ScrollHandler.attackDamageModifier());
        }
    }
}
