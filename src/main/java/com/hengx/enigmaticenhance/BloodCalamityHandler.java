package com.hengx.enigmaticenhance;

import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.2.23:血色之灾的运行时。
 *
 * <p>三层效果:</p>
 * <ol>
 *   <li>护甲:每 tick 校正属性修饰符 —— 未戴「血战沙场之证」→ 护甲 ×0;
 *       佩戴 → 护甲 ×0.4(保留 40%)。用固定 UUID,不会重复叠加;</li>
 *   <li>受到的伤害:每有 1 个负面效果 ×(1 + 10%)(恒定生效);</li>
 *   <li>造成的伤害(仅佩戴沙场之证时):每有 1 个负面效果 ×(1 + 10%)。</li>
 * </ol>
 */
public final class BloodCalamityHandler {

    /** 每层负面效果的伤害加成。 */
    public static final float PER_DEBUFF = 0.10F;

    /** 护甲修饰符 UUID(固定,保证幂等)。 */
    private static final UUID ARMOR_UUID = UUID.fromString("5f1c8f7e-9a41-4b1c-9d5e-7b0c3a5e2d41");

    private static final String ARMOR_NAME = "enigmaticenhance:blood_calamity_armor";

    /** 「血战沙场之证」。 */
    private static final String BERSERK_PATH = "berserk_charm";

    /** 允许移除血色之灾的内部开关(击杀清除时用)。 */
    private static final ThreadLocal<Boolean> ALLOW_REMOVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private BloodCalamityHandler() {
    }

    /** 内部强制移除(绕过"不能被清除"的保护)。 */
    public static void forceRemove(LivingEntity entity) {
        MobEffect effect = ModEffects.bloodCalamityOrNull();
        if (effect == null || entity == null) {
            return;
        }
        ALLOW_REMOVE.set(Boolean.TRUE);
        try {
            entity.removeEffect(effect);
        }
        finally {
            ALLOW_REMOVE.set(Boolean.FALSE);
        }
    }

    /** 当前是否允许外部移除(供 mixin 判定)。 */
    public static boolean removalAllowed() {
        return Boolean.TRUE.equals(ALLOW_REMOVE.get());
    }

    /** 1.2.23:击杀生物后自动清除自己的血色之灾。 */
    @SubscribeEvent
    public static void onKill(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.bloodCalamityEnabled()) {
            return;
        }
        try {
            if (event.getSource().getEntity() instanceof Player killer && killer.level() instanceof net.minecraft.server.level.ServerLevel) {
                forceRemove(killer);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    // ------------------------------------------------------------------ 工具

    public static boolean wearingBerserkCharm(Player player) {
        if (player == null) {
            return false;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isBerserk(player.getInventory().getItem(i))) {
                return true;
            }
        }
        if (isBerserk(player.getOffhandItem())) {
            return true;
        }
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(BloodCalamityHandler::isBerserk).isPresent())
                .orElse(false);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isBerserk(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && BERSERK_PATH.equals(id.getPath());
    }

    /** 身上有害效果数量。 */
    public static int debuffCount(LivingEntity entity) {
        int count = 0;
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                ++count;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ 护甲

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.bloodCalamityEnabled()) {
            return;
        }
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        MobEffect effect = ModEffects.bloodCalamityOrNull();
        if (effect == null) {
            return;
        }
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor == null) {
            return;
        }
        try {
            if (!player.hasEffect(effect)) {
                armor.removeModifier(ARMOR_UUID);
                return;
            }
            double factor = wearingBerserkCharm(player) ? -0.6D : -1.0D;
            AttributeModifier existing = armor.getModifier(ARMOR_UUID);
            if (existing != null && Math.abs(existing.getAmount() - factor) < 1.0E-6D) {
                return;
            }
            armor.removeModifier(ARMOR_UUID);
            armor.addTransientModifier(new AttributeModifier(ARMOR_UUID, ARMOR_NAME, factor,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        catch (RuntimeException | LinkageError ignored) {
            // 失败就保持当前护甲
        }
    }

    // ------------------------------------------------------------------ 伤害

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.bloodCalamityEnabled()) {
            return;
        }
        try {
            LivingEntity target = event.getEntity();
            if (target == null || target.level().isClientSide) {
                return;
            }
            MobEffect effect = ModEffects.bloodCalamityOrNull();
            if (effect == null) {
                return;
            }
            // ① 受到伤害:只要中血色之灾,每层负面 +10%
            if (target.hasEffect(effect)) {
                int debuffs = debuffCount(target);
                if (debuffs > 0) {
                    float taken = 1.0F + PER_DEBUFF * debuffs;
                    event.setAmount(event.getAmount() * taken);
                    DebugLog.once("bcTaken:" + debuffs,
                        "[BloodCalamity] 受伤 x{} ({} 个负面)", taken, debuffs);
                }
            }
            // ② 造成伤害:中血色之灾 + 佩戴沙场之证,每层负面 +10%
            if (event.getSource().getEntity() instanceof Player attacker
                && attacker.hasEffect(effect)
                && wearingBerserkCharm(attacker)) {
                int debuffs = debuffCount(attacker);
                if (debuffs > 0) {
                    float dealt = 1.0F + PER_DEBUFF * debuffs;
                    event.setAmount(event.getAmount() * dealt);
                    DebugLog.once("bcDealt:" + debuffs,
                        "[BloodCalamity] 造成伤害 x{} ({} 个负面)", dealt, debuffs);
                }
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯附加效果
        }
    }
}