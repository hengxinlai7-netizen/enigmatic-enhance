package com.hengx.enigmaticenhance;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.2.11a:「镜碎」状态 —— 受到的伤害 +100%。
 *
 * <p>实现方式:挂一条 {@code MAX_HEALTH} 之外的属性修饰符做不到"受伤翻倍",所以这里做两件事:</p>
 * <ul>
 *   <li>{@link MirrorShatterHandler} 在 {@code LivingHurtEvent} 里把伤害 ×2(唯一实际生效点);</li>
 *   <li>本类只负责注册状态本体(名字、颜色、图标位置),数值由上面的 handler 给。</li>
 * </ul>
 */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
        DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, EnigmaticEnhance.MODID);

    /** enigmaticenhance:mirror_shatter —— 镜碎(受伤 +100%)。 */
    public static final RegistryObject<MobEffect> MIRROR_SHATTER =
        EFFECTS.register("mirror_shatter", () -> new MirrorShatterEffect());


    /** 1.2.23:enigmaticenhance:blood_calamity —— 血色之灾(护甲清零/禁疗/每层负面 +10% 受伤)。 */
    public static final RegistryObject<MobEffect> BLOOD_CALAMITY =
        EFFECTS.register("blood_calamity", () -> new BloodCalamityEffect());
    /** 1.3.3:enigmaticenhance:mercury_serpent —— 水银之蛇(回满血/次数盾/死亡抗性)。 */
    public static final RegistryObject<MobEffect> ETERNAL_SILENCE =
        EFFECTS.register("eternal_silence", EternalSilenceEffect::new);

    public static final RegistryObject<MobEffect> CALAMITY_ORIGIN =
        EFFECTS.register("calamity_origin", CalamityOriginEffect::new);

    public static final RegistryObject<MobEffect> MERCURY_SERPENT =
        EFFECTS.register("mercury_serpent", () -> new MercurySerpentEffect());

    private ModEffects() {
    }

    /** 镜碎实例(未注册 ⇒ null)。 */
    public static MobEffect mirrorShatterOrNull() {
        try {
            return MIRROR_SHATTER.isPresent() ? MIRROR_SHATTER.get() : null;
        }

        catch (Throwable t) {
            return null;
        }
    }

    /** 1.2.23:血色之灾(没注册时返回 null)。 */
    public static MobEffect bloodCalamityOrNull() {
        return BLOOD_CALAMITY.isPresent() ? BLOOD_CALAMITY.get() : null;
    }

    /** 1.3.3:水银之蛇(没注册时返回 null)。 */
    public static MobEffect mercurySerpentOrNull() {
        return MERCURY_SERPENT.isPresent() ? MERCURY_SERPENT.get() : null;
    }

    /** 状态本体:有害、深紫色,没有属性修饰符(伤害翻倍在事件里做)。 */
    public static final class MirrorShatterEffect extends MobEffect {

        public MirrorShatterEffect() {
            super(MobEffectCategory.HARMFUL, 0x8A2BE2);
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            // 没有每 tick 效果
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return false;
        }
    }

    /** 兼容旧引用:本类不再使用属性修饰符,保留常量避免别处编译失败。 */
    public static final AttributeModifier UNUSED = new AttributeModifier(
        java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"),
        "enigmaticenhance:unused", 0.0D, AttributeModifier.Operation.ADDITION);
}
