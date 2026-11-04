package com.hengx.enigmaticenhance;

import java.util.UUID;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 【全属性】的实际作用点。
 * <p>
 * 不再在读取时相乘(那样只有走 AttributeMap 的代码能吃到,L2/神化的面板直接读 AttributeInstance 就看不到),
 * 而是给玩家身上**每一个**属性挂一条 {@code MULTIPLY_TOTAL} 修饰符:
 * <pre>
 * 最终值 = (基础值 + ADD 类) × (1 + MULTIPLY_BASE 类) × (1 + Σ MULTIPLY_TOTAL)
 * </pre>
 * {@code MULTIPLY_TOTAL} 就是"独立乘区":它在其它加成算完之后单独相乘,而且是一条**真实修饰符**,
 * 因此面板(神化属性页、L2 面板)、战斗结算、任何读取路径都能看到这个加成。
 * <p>
 * 每隔 {@value #INTERVAL} tick 同步一次:数值变了就换掉那条修饰符,全属性为 0 就摘掉。
 * 只作用于玩家(属性本身也只在玩家身上注册)。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OmniAttributeApplier {

    private static final UUID UUID_OMNI = UUID.fromString("1f0c2a44-8a3d-4b1e-9f57-3c6d9a2b7e10");
    private static final String NAME_OMNI = "EnigmaticEnhance Omni Attribute";
    private static final int INTERVAL = 10;

    private static int counter;

    private OmniAttributeApplier() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!RingConfig.omniEnabled()) {
            return;
        }
        if (++counter % INTERVAL != 0) {
            return;
        }
        apply(event.player);
    }

    private static void apply(Player player) {
        Attribute omni = OmniAttribute.getOrNull();
        if (omni == null) {
            return;
        }
        double value = player.getAttributeValue(omni);

        for (Attribute attribute : ForgeRegistries.ATTRIBUTES) {
            if (attribute == omni || !player.getAttributes().hasAttribute(attribute)) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            AttributeModifier existing = instance.getModifier(UUID_OMNI);
            if (value == 0.0D) {
                if (existing != null) {
                    instance.removeModifier(UUID_OMNI);
                }
                continue;
            }
            if (existing != null) {
                if (existing.getAmount() == value) {
                    continue;
                }
                instance.removeModifier(UUID_OMNI);
            }
            instance.addTransientModifier(new AttributeModifier(UUID_OMNI, NAME_OMNI, value,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
}