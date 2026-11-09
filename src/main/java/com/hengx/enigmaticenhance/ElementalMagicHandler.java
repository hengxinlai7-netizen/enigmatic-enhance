package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.2.9b:「元素魔法」的运行时 —— 追加**对应学派的伤害类型**。
 *
 * <h2>学派强度 → 伤害类型</h2>
 * 铁魔法给每个学派同时注册了"强度属性"和"伤害类型",命名一一对应:
 * <pre>
 *   irons_spellbooks:fire_spell_power      ⇄  irons_spellbooks:fire_magic
 *   irons_spellbooks:ice_spell_power       ⇄  irons_spellbooks:ice_magic
 *   irons_spellbooks:lightning_spell_power ⇄  irons_spellbooks:lightning_magic
 *   … holy / ender / blood / evocation / nature / eldritch 同理
 * </pre>
 * 所以:每个学派的加成分别按<b>该学派自己的伤害类型</b>结算一次(1.2.9b 起不再统一走 indirectMagic)。
 * 只有"属性存在 + 对应伤害类型也注册了"时才打 —— 这就是需求里的"前提是有这个属性的伤害";
 * 其它扩展新加的学派只要沿用 {@code <school>_spell_power} 属性 + {@code <school>_magic}
 * 伤害类型,也自动被这里吃进来。
 *
 * <p>裸的 {@code irons_spellbooks:spell_power}(不隶属任何学派)按 {@code minecraft:magic} 结算。</p>
 *
 * <p>魔法剑气命中时走同一套:{@link #applySchoolDamage}。</p>
 */
public final class ElementalMagicHandler {

    /** 每次伤害最多额外加多少倍(防止学派叠到离谱)。 */
    public static final float MAX_BONUS = 10.0F;
    /** 所有学派加起来的**总上限**(+1000%),防止多学派叠加到没有上限。 */
    /** 1.2.20:所有学派加起来的**总上限**(+1000%),防止多学派叠加到没有上限。 */

    /** 重入保护:我们自己补的那段伤害会再次触发 LivingHurtEvent。 */
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ElementalMagicHandler() {
    }

    /** 一个学派的加成:伤害类型 + 强度(0.25 = +25%)。 */
    public record SchoolHit(ResourceLocation damageType, float power) {
    }

    public static boolean ironsLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("irons_spellbooks");
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 收集这个实体身上所有"有对应伤害类型"的学派加成。
     * 属性 {@code <ns>:<school>_spell_power} ↔ 伤害类型 {@code <ns>:<school>_magic};
     * 裸 {@code spell_power} ⇒ {@code minecraft:magic}。
     */
    public static List<SchoolHit> schoolHits(LivingEntity entity) {
        List<SchoolHit> hits = new ArrayList<>();
        if (entity == null) {
            return hits;
        }
        var damageTypes = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            if (id == null || !id.getPath().endsWith("spell_power")) {
                continue;
            }
            float power;
            try {
                if (!entity.getAttributes().hasAttribute(attribute)) {
                    continue;
                }
                // 1.2.13b:只取"超过基础值"的部分 —— 学派强度属性的默认值(铁魔法里是 1.0D)是"基础强度",
                // 不是加成。以前直接 getAttributeValue,裸的 1.0 会变成 +100% 额外伤害。
                double base = attribute.getDefaultValue();
                power = (float) Math.max(0.0D, entity.getAttributeValue(attribute) - base);
            }
            catch (RuntimeException | LinkageError ignored) {
                continue;
            }
            if (power <= 0.0F) {
                continue;
            }
            ResourceLocation typeKey = damageTypeFor(id);
            if (typeKey == null) {
                continue;
            }
            if (damageTypes.getHolder(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, typeKey)).isEmpty()) {
                continue;   // "前提是有这个属性的伤害":没有对应伤害类型就不打
            }
            hits.add(new SchoolHit(typeKey, Math.min(MAX_BONUS, power)));
        }
        return hits;
    }

    /** 属性 id → 伤害类型 id;裸 spell_power 用 minecraft:magic。 */
    public static ResourceLocation damageTypeFor(ResourceLocation attributeId) {
        if (attributeId == null) {
            return null;
        }
        String path = attributeId.getPath();
        if (!path.endsWith("spell_power")) {
            return null;
        }
        String school = path.substring(0, path.length() - "spell_power".length());
        if (school.isEmpty()) {
            return new ResourceLocation("minecraft", "magic");
        }
        if (school.endsWith("_")) {
            school = school.substring(0, school.length() - 1);
        }
        return new ResourceLocation(attributeId.getNamespace(), school + "_magic");
    }

    /** 学派伤害来源:直接来源与造成者都是玩家(这样能正常算击杀/掉落/仇恨)。 */
    public static DamageSource sourceFor(Level level, ResourceLocation typeKey, Entity attacker) {
        var holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolder(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, typeKey));
        Optional<? extends net.minecraft.core.Holder<DamageType>> optional = holder;
        if (optional.isEmpty()) {
            return level.damageSources().indirectMagic(attacker, attacker);
        }
        return new DamageSource(optional.get(), attacker, attacker);
    }

    /**
     * 按学派分别追加伤害:每个学派 = 基础伤害 × 该学派强度,各用自己的伤害类型结算一次。
     * 返回追加的总伤害(给日志用)。
     */
    public static float applySchoolDamage(LivingEntity target, float baseDamage, Player attacker) {
        if (target == null || attacker == null || baseDamage <= 0.0F || target.level().isClientSide()) {
            return 0.0F;
        }
        List<SchoolHit> hits = schoolHits(attacker);
        if (hits.isEmpty()) {
            return 0.0F;
        }
        float total = 0.0F;
        for (SchoolHit hit : hits) {
            float extra = baseDamage * hit.power();
            if (extra <= 0.0F) {
                continue;
            }
            try {
                target.invulnerableTime = 0;
                target.hurt(sourceFor(target.level(), hit.damageType(), attacker), extra);
                total += extra;
            }
            catch (RuntimeException | LinkageError ignored) {
                // 单个学派出错不影响其它学派
            }
        }
        return total;
    }

    /** 手上这件是不是带「元素魔法」。 */
    public static boolean hasEnchantment(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        ElementalMagicEnchantment enchantment = ModEnchantments.elementalMagicOrNull();
        if (enchantment == null) {
            return false;
        }
        try {
            return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 兼容旧引用:学派强度总和(1.0 = +100%)。 */
    public static float schoolPowerBonus(LivingEntity entity) {
        float sum = 0.0F;
        for (SchoolHit hit : schoolHits(entity)) {
            sum += hit.power();
        }
        return Math.min(MAX_BONUS * 4.0F, sum);
    }

    /** 兼容旧引用:伤害倍率。 */
    public static float damageMultiplier(LivingEntity entity) {
        return 1.0F + schoolPowerBonus(entity);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // 4.5.0:配置开关(默认开启;关闭后不再追加属性伤害)
        if (!RingConfig.elementalMagicEnabled()) {
            return;
        }
        if (!ironsLoaded() || Boolean.TRUE.equals(APPLYING.get())) {
            return;
        }
        try {
            if (!(event.getSource().getEntity() instanceof Player player)) {
                return;
            }
            LivingEntity target = event.getEntity();
            if (target == null || target == player || target.level().isClientSide()) {
                return;
            }
            if (!hasEnchantment(player, player.getMainHandItem())) {
                return;
            }
            APPLYING.set(Boolean.TRUE);
            float extra;
            try {
                extra = applySchoolDamage(target, event.getAmount(), player);
            }
            finally {
                APPLYING.set(Boolean.FALSE);
            }
            if (extra > 0.0F) {
                DebugLog.info("[ElementalMagic] {} 追加 {} 点学派伤害", player.getName().getString(), extra);
            }
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[ElementalMagic] 追加伤害异常", ex);
        }
    }
}
