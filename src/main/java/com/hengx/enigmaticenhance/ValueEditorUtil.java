package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * 1.3.1:数值编辑器 —— 与 {@code /attribute} 等价的属性写入工具。
 *
 * <p>每个(实体,属性)组合用固定 UUID 的 {@link AttributeModifier}({@code ADDITION})写入,
 * 因此可以反复叠加、也能一键清除(死亡/重生时按 UUID 前缀清理)。</p>
 */
public final class ValueEditorUtil {

    /** 修饰符名前缀 —— 死亡清理时按这个识别。 */
    public static final String MODIFIER_NAME = "enigmaticenhance:value_editor";

    /** 每个属性的固定 UUID(由属性 id 派生,稳定不变)。 */
    public static UUID modifierId(ResourceLocation attributeId) {
        long most = 0x56414C5545000000L ^ attributeId.toString().hashCode();
        long least = 0x454449544F520000L ^ (attributeId.toString().hashCode() * 31L);
        return new UUID(most, least);
    }

    /** 实体身上所有可编辑属性(已排序,id 稳定)。 */
    public static List<Attribute> attributesOf(LivingEntity entity) {
        List<Attribute> list = new ArrayList<>();
        for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
            if (entity.getAttributes().hasAttribute(attribute)) {
                list.add(attribute);
            }
        }
        list.sort((a, b) -> idOf(a).toString().compareTo(idOf(b).toString()));
        return list;
    }

    public static ResourceLocation idOf(Attribute attribute) {
        return BuiltInRegistries.ATTRIBUTE.getKey(attribute);
    }

    /** 中文名(没有翻译时退回属性 id)。 */
    public static Component displayName(Attribute attribute) {
        return Component.translatable(attribute.getDescriptionId());
    }

    /** 当前总值。 */
    public static double currentValue(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    /** 本编辑器加了多少(只算我们的修饰符)。 */
    public static double editorBonus(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return 0.0D;
        }
        AttributeModifier modifier = instance.getModifier(modifierId(idOf(attribute)));
        return modifier == null ? 0.0D : modifier.getAmount();
    }

    /** 把本编辑器的加成设为 {@code bonus}(0 = 移除)。 */
    public static void setBonus(LivingEntity entity, Attribute attribute, double bonus) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        UUID id = modifierId(idOf(attribute));
        instance.removeModifier(id);
        if (Math.abs(bonus) > 1.0E-6D) {
            instance.addPermanentModifier(new AttributeModifier(id, MODIFIER_NAME, bonus,
                AttributeModifier.Operation.ADDITION));
        }
    }

    /** 清除实体身上所有本编辑器的修饰符(死亡/重生时调用)。 */
    public static int clearAll(LivingEntity entity) {
        int removed = 0;
        for (Attribute attribute : attributesOf(entity)) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null && instance.getModifier(modifierId(idOf(attribute))) != null) {
                instance.removeModifier(modifierId(idOf(attribute)));
                ++removed;
            }
        }
        return removed;
    }

    private ValueEditorUtil() {
    }
}