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
}
