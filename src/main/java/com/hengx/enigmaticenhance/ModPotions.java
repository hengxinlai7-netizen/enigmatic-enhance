package com.hengx.enigmaticenhance;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.2.26:把自定义效果注册成**药水**,这样 JEI 的药水/效果页能查到。
 *
 * <p>JEI 的效果页是从 {@code Potion} 注册表反查的:只有挂了效果的 Potion 才会显示,
 * 单纯注册 MobEffect 不会出现在那里。</p>
 */
public final class ModPotions {

    public static final DeferredRegister<Potion> POTIONS =
        DeferredRegister.create(ForgeRegistries.POTIONS, EnigmaticEnhance.MODID);

    /** 镜碎药水:受到伤害 +100%。 */
    public static final RegistryObject<Potion> MIRROR_SHATTER = POTIONS.register("mirror_shatter",
        () -> new Potion("mirror_shatter",
            new MobEffectInstance(ModEffects.MIRROR_SHATTER.get(), 3600, 0)));

    /** 血色之灾药水:护甲清零、禁疗、每层负面 +10% 受伤。 */
    public static final RegistryObject<Potion> BLOOD_CALAMITY = POTIONS.register("blood_calamity",
        () -> new Potion("blood_calamity",
            new MobEffectInstance(ModEffects.BLOOD_CALAMITY.get(), 3600, 0)));

    /** 水银之蛇药水(回满血/次数盾/死亡抗性)。 */
    public static final RegistryObject<Potion> MERCURY_SERPENT = POTIONS.register("mercury_serpent",
        () -> new Potion("mercury_serpent",
            new MobEffectInstance(ModEffects.MERCURY_SERPENT.get(), 3600, 2)));

    private ModPotions() {
    }
}