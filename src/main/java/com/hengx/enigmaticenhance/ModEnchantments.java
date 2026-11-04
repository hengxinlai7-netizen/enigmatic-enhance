package com.hengx.enigmaticenhance;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 附魔注册表。 */
public final class ModEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
        DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, EnigmaticEnhance.MODID);

    /** 自动熔炼。 */
    public static final RegistryObject<Enchantment> AUTO_SMELT =
        ENCHANTMENTS.register("auto_smelt", AutoSmeltEnchantment::new);

    /** 魔法保护。 */
    public static final RegistryObject<Enchantment> MAGIC_PROTECTION =
        ENCHANTMENTS.register("magic_protection", MagicProtectionEnchantment::new);

    /** 魔法剑气。 */
    public static final RegistryObject<Enchantment> MAGIC_SWORD_AURA =
        ENCHANTMENTS.register("magic_sword_aura", MagicSwordAuraEnchantment::new);

    /** 元素魔法。 */
    public static final RegistryObject<Enchantment> ELEMENTAL_MAGIC =
        ENCHANTMENTS.register("elemental_magic", ElementalMagicEnchantment::new);

    /** 不毁。 */
    public static final RegistryObject<Enchantment> UNBREAKABLE =
        ENCHANTMENTS.register("unbreakable", UnbreakableEnchantment::new);

    /** 虚空光环。 */
    public static final RegistryObject<Enchantment> VOID_AURA =
        ENCHANTMENTS.register("void_aura", VoidAuraEnchantment::new);

    /** 2.9.0:执行之手 —— 伤害转真实伤害,限 1 级,只能创造模式获取。 */
    public static final RegistryObject<Enchantment> HAND_OF_EXECUTION =
        ENCHANTMENTS.register("hand_of_execution", HandOfExecutionEnchantment::new);

    private ModEnchantments() {
    }

    public static AutoSmeltEnchantment autoSmeltOrNull() {
        return AUTO_SMELT.isPresent() ? (AutoSmeltEnchantment) AUTO_SMELT.get() : null;
    }

    public static MagicProtectionEnchantment magicProtectionOrNull() {
        return MAGIC_PROTECTION.isPresent() ? (MagicProtectionEnchantment) MAGIC_PROTECTION.get() : null;
    }

    public static MagicSwordAuraEnchantment magicSwordAuraOrNull() {
        return MAGIC_SWORD_AURA.isPresent() ? (MagicSwordAuraEnchantment) MAGIC_SWORD_AURA.get() : null;
    }

    public static ElementalMagicEnchantment elementalMagicOrNull() {
        return ELEMENTAL_MAGIC.isPresent() ? (ElementalMagicEnchantment) ELEMENTAL_MAGIC.get() : null;
    }

    public static UnbreakableEnchantment unbreakableOrNull() {
        return UNBREAKABLE.isPresent() ? (UnbreakableEnchantment) UNBREAKABLE.get() : null;
    }

    public static VoidAuraEnchantment voidAuraOrNull() {
        return VOID_AURA.isPresent() ? (VoidAuraEnchantment) VOID_AURA.get() : null;
    }

    public static HandOfExecutionEnchantment handOfExecutionOrNull() {
        return HAND_OF_EXECUTION.isPresent() ? (HandOfExecutionEnchantment) HAND_OF_EXECUTION.get() : null;
    }
}
