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

    /**
     * 同一 tick 内已经处理过的目标,避免 attack/hurt 双入口重复扣血。
     *
     * <p>4.6.2(装箱):值从 {@code Integer} 改成复用同一个 {@code int[]}(长度 1)。
     * 原来<b>每次伤害结算</b>都要 {@code Integer.valueOf(target.tickCount)} —— 实体 tickCount
     * 基本都在 Integer 缓存区(-128..127)之外 ⇒ 每一击一个短命 Integer,tick 与 put 都白花。
     * 现在用 {@code computeIfAbsent} 拿到格子后只写下标 0,热路径零分配。
     * 仍然用 {@link java.util.concurrent.ConcurrentHashMap}(与原来一致,不引入新的线程风险);
     * 语义不变:比较的仍是"这一次的 tickCount 与前一次是否相同"。</p>
     */
    private static final java.util.Map<java.util.UUID, int[]> LAST_TICK = new java.util.concurrent.ConcurrentHashMap<>();

    /** "还没记录过"的哨兵(实体 tickCount 不会是它)。 */
    private static final int NO_TICK = Integer.MIN_VALUE;

    /** 2.10.6:这次伤害的攻击者是否带着「执行之手」(给针对性 Mixin 用)。 */
    public static boolean shouldBypass(net.minecraft.world.damagesource.DamageSource source) {
        // 3.8.2:总开关关闭 ⇒ 所有 Boss 破防 Mixin 直接放行,绝不干预击杀流程
        if (!RingConfig.handOfExecutionEnabled()) {
            return false;
        }
        try {
            if (!(source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)) {
                return false;
            }
            HandOfExecutionEnchantment enchant = ModEnchantments.handOfExecutionOrNull();
            if (enchant == null) {
                return false;
            }
            // 3.8.1:改为与 AutoSmeltHandler.levelOf 相同的读法(getEnchantments 映射),
            // 这样附魔书 / 被别的模组塞进 StoredEnchantments 的物品也能认出来,
            // 并且与 handle() 里打印的等级保持一致(之前两处读法不同,出现过
            // "日志说没有附魔、破防却触发"的矛盾)。
            java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> main =
                net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(attacker.getMainHandItem());
            java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> off =
                net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(attacker.getOffhandItem());
            Integer mainLevel = main.get(enchant);
            Integer offLevel = off.get(enchant);
            if ((mainLevel != null && mainLevel > 0) || (offLevel != null && offLevel > 0)) {
                // 4.6.2(内存):先问 oncePending() 再拼 key。once() 在记录表满了之后本来就不输出,
                // 但 "exec:bypass:" + UUID 这个 String 在调用前就已经建好了(每击一次白花)。
                if (DebugLog.oncePending()) {
                    DebugLog.once("exec:bypass:" + attacker.getUUID(),
                        "[Execution] 破防已启用:主手等级={} 副手等级={}",
                        mainLevel == null ? 0 : mainLevel, offLevel == null ? 0 : offLevel);
                }
                return true;
            }
            return false;
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
            if (!RingConfig.handOfExecutionEnabled()) {
                return;   // 3.8.2:总开关关闭 ⇒ 执行之手完全失效(不改真实伤害)
            }
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
            // 4.6.2(内存):每击都要拼 key + 反查物品注册表,先问 oncePending() 再拼。
            if (attacker instanceof Player p && DebugLog.oncePending()) {
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
                // 4.6.2(内存):同上,key 是现拼的 + 两次注册表反查,先问 oncePending()。
                if (DebugLog.oncePending()) {
                    DebugLog.once("exec:noench:" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                        attacker.getMainHandItem().getItem()) + ":" + (attacker instanceof Player ? "p" : "m"),
                        "[Execution] 主/副手 {} 上没有 执行之手,跳过", attacker.getMainHandItem().getItem());
                }
                return;
            }
            int tick = target.tickCount;
            int[] prev = LAST_TICK.computeIfAbsent(target.getUUID(), k -> new int[] { NO_TICK });
            boolean sameTick = prev[0] == tick;
            prev[0] = tick;
            if (sameTick) {
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
        int[] prev = LAST_TICK.get(target.getUUID());
        return prev != null && prev[0] == target.tickCount;
    }
}