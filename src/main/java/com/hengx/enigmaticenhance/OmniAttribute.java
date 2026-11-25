package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 新属性【全属性】:{@code enigmaticenhance:omni}。
 * <p>
 * 含义:数值即"全局加成"(0.2 = 全部属性 +20%)。它通过 {@link com.hengx.enigmaticenhance.mixin.AttributeMapMixin}
 * 在**所有属性读取的最外层**乘上去,也就是**独立乘区**:
 * <pre>
 * 最终值 = 原版/其它模组算完并裁剪后的值 × (1 + 全属性)
 * </pre>
 * 因此它不参与其它属性的加成计算,也不会被它们的上限裁掉。
 * <p>
 * 目前只挂到玩家身上(挂到怪身上会连带放大怪物的所有属性,不是需求要的效果)。
 */
public final class OmniAttribute {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(ForgeRegistries.ATTRIBUTES, EnigmaticEnhance.MODID);

    /** 默认 0,允许负值(负全属性 = 全局减益)。 */
    public static final RegistryObject<Attribute> OMNI = ATTRIBUTES.register("omni",
        () -> new RangedAttribute("attribute." + EnigmaticEnhance.MODID + ".omni", 0.0D, -1024.0D, 1024.0D).setSyncable(true));

    private OmniAttribute() {}

    @SubscribeEvent
    public static void attachToPlayers(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, OMNI.get());
    }

    /** 取属性实例;注册完成前返回 null(调用方需要容错)。 */
    public static Attribute getOrNull() {
        try {
            return OMNI.get();
        }
        catch (Throwable t) {
            return null;
        }
    }

    /**
     * 把 omni 上的修饰符按「百分比加成」展平:基数视为 1.0,返回值 = 实际加成(0.12 = +12%)。
     *
     * <p>为什么需要:omni 的基础值是 <b>0</b>,而 MAGIC 类修饰符是"乘以基础值" ——
     * 石英花给的是 {@code MULTIPLY_BASE 0.12},{@code 0 × 1.12 = 0},永远吃不到。
     * 这里统一成"以 1.0 为基数"的语义,三种操作都能正确叠加:</p>
     * <ul>
     *   <li>{@code ADDITION} 0.2 → +20%(救赎之戒,保持现状);</li>
     *   <li>{@code MULTIPLY_BASE} / {@code MULTIPLY_TOTAL} 0.12 → +12%(石英花,本次修复);</li>
     *   <li>两者都有:1.2 × 1.12 - 1 = 0.344;</li>
     *   <li>负值 {@code ADDITION -0.3} → -30%。</li>
     * </ul>
     */
    public static double effectiveBonus(@javax.annotation.Nullable net.minecraft.world.entity.ai.attributes.AttributeInstance instance) {
        if (instance == null) {
            return 0.0D;
        }
        return effectiveBonus(instance.getModifiers());
    }

    /** 纯函数版本(自检直接断言,不需要真实实体)。 */
    public static double effectiveBonus(java.util.Collection<net.minecraft.world.entity.ai.attributes.AttributeModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return 0.0D;
        }
        double v = 1.0D;
        for (net.minecraft.world.entity.ai.attributes.AttributeModifier m : modifiers) {
            switch (m.getOperation()) {
                case ADDITION -> v += m.getAmount();
                case MULTIPLY_BASE, MULTIPLY_TOTAL -> v *= (1.0D + m.getAmount());
            }
        }
        return v - 1.0D;
    }
}
