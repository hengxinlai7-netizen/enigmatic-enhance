package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 2.9.0:「执行之手」效果 —— 造成伤害时把伤害转成真实伤害。
 *
 * <p>在 HIGHEST 拿未夹过的原始伤害,交给 {@link TrueDamage} 直写血量;
 * <b>真实伤害若失败(被拦截/反射失败),自动退回正常伤害</b>(不取消事件)。</p>
 */
public final class HandOfExecutionHandler {

    /** 同一 tick 内已经处理过的目标,避免 attack/hurt 双入口重复扣血。 */
    private static final java.util.Map<java.util.UUID, Integer> LAST_TICK = new java.util.concurrent.ConcurrentHashMap<>();

    /** 2.10.6:这次伤害的攻击者是否带着「执行之手」(给针对性 Mixin 用)。 */
    public static boolean shouldBypass(net.minecraft.world.damagesource.DamageSource source) {
        try {
            if (!(source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)) {
                return false;
            }
            HandOfExecutionEnchantment enchant = ModEnchantments.handOfExecutionOrNull();
            if (enchant == null) {
                return false;
            }
            return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                enchant, attacker.getMainHandItem()) > 0
                || net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                    enchant, attacker.getOffhandItem()) > 0;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private HandOfExecutionHandler() {
    }

    /** 2.9.3:更早的入口 —— 有些 boss 在 LivingAttackEvent 就取消,根本不会发 LivingHurtEvent。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        apply(event.getEntity(), event.getSource(), event.getAmount(), event);
    }

    private static void apply(LivingEntity target, net.minecraft.world.damagesource.DamageSource source,
        float amount, Object event) {
        try {
            if (target == null || amount <= 0.0F) {
                return;
            }
            if (!(source.getEntity() instanceof LivingEntity attacker)) {
                return;
            }
            HandOfExecutionEnchantment enchant = ModEnchantments.handOfExecutionOrNull();
            if (enchant == null) {
                return;
            }
            ItemStack weapon = attacker.getMainHandItem();
            int lvl = EnchantmentHelper.getItemEnchantmentLevel(enchant, weapon);
            // 2.9.8 诊断:只要玩家出手就记一次(每种物品一条),看清入口到底收到什么。
            if (attacker instanceof Player p) {
                DebugLog.once("exec:atk:" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(weapon.getItem()),
                    "[Execution] 入口命中: 主手={} 附魔等级={} 目标={}",
                    weapon.getItem(), lvl, target.getName().getString());
            }
            if (lvl <= 0) {
                weapon = attacker.getOffhandItem();   // 2.9.7:主手是空/其它物品时也认副手
                lvl = EnchantmentHelper.getItemEnchantmentLevel(enchant, weapon);
            }
            if (lvl <= 0) {
                // 2.9.5 诊断:主手没有附魔时,每个物品只提醒一次(方便确认是不是拿错手/拿错物品)
                DebugLog.once("exec:noench:" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                    attacker.getMainHandItem().getItem()) + ":" + (attacker instanceof Player ? "p" : "m"),
                    "[Execution] 主/副手 {} 上没有 执行之手,跳过", attacker.getMainHandItem().getItem());
                return;
            }
            int tick = target.tickCount;
            Integer prev = LAST_TICK.put(target.getUUID(), tick);
            if (prev != null && prev == tick) {
                // 这一击已经在 LivingAttackEvent 里算过真实伤害 → 只把普通伤害清零
                if (event instanceof LivingHurtEvent hurt) {
                    hurt.setAmount(0.0F);
                }
                return;
            }
            float dealt = TrueDamage.apply(target, attacker, amount, source);
            if (dealt >= 0.0F) {
                if (event instanceof LivingHurtEvent hurt) {
                    hurt.setAmount(0.0F);
                    // 同样不取消:让 hurt 流程跑完(登记受击、播动画),只是伤害为 0
                    hurt.setCanceled(false);
                }
                else if (event instanceof net.minecraftforge.event.entity.living.LivingAttackEvent atk) {
                    // 2.10.1:不要取消攻击 —— 传奇怪物靠 hurt() 里登记"被打了"(hasHurt/target)
                    // 来决定是否回血;取消掉它就会认为没人打它,一路回血,真实伤害全被抵消。
                    // 这里只写真实伤害,让 hurt() 继续走(它后续的普通伤害会被记入 same-tick,
                    // 再由 LivingHurtEvent 入口把 amount 清零)。
                    atk.setCanceled(false);
                }
                DebugLog.warn("[Execution] {} 对 {} 真实伤害 {} (入口={})", attacker.getName().getString(),
                    target.getName().getString(), amount,
                    event instanceof LivingHurtEvent ? "hurt" : "attack");
            }
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[Execution] 处理失败,退回正常伤害: {}", e.toString());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        apply(event.getEntity(), event.getSource(), event.getAmount(), event);
    }

    /** 同 tick 已经把伤害记在真实伤害里了 → 现在把普通伤害清零(不取消,保留受击登记)。 */
    private static boolean alreadyApplied(LivingEntity target) {
        Integer prev = LAST_TICK.get(target.getUUID());
        return prev != null && prev == target.tickCount;
    }
}