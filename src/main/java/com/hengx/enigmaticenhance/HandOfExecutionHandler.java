package com.hengx.enigmaticenhance;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 4.8.3:「执行之手」—— <b>不被任何减免</b>的伤害实现。
 *
 * <h2>四层,缺一层都能被绕过</h2>
 * <ol>
 *   <li><b>数据层(声明式)</b>:{@code enigmaticenhance:execution} 写进
 *       {@code minecraft:tags/damage_type/} 的 {@code bypasses_armor / bypasses_shield /
 *       bypasses_invulnerability / bypasses_cooldown / bypasses_resistance / bypasses_effects /
 *       bypasses_enchantments}。护甲、保护附魔、抗性、盾牌、无敌帧、图腾全部在原版代码里直接失效。
 *       <b>零 mixin、零反射、可被数据包检查</b>。</li>
 *   <li><b>接管层</b>:{@link #onAttack}({@code LivingAttackEvent} HIGHEST)拦下"带执行之手的
 *       普通攻击",记下原始伤害,改发一脚 {@link ExecutionDamage} 类型的伤害,并把原来那一脚的
 *       伤害清零(只清零,<b>不取消</b> —— 取消会让传奇生物认为"没人打我"而不掉血/回血)。</li>
 *   <li><b>事件层双保险</b>:{@code LivingHurtEvent}/{@code LivingDamageEvent} 的 HIGHEST 记账,
 *       LOWEST 把被削减的数值还原;被 cancel 时<b>只驳回这一脚</b>的取消(不是全局 no-op)。</li>
 *   <li><b>兜底层</b>:{@link ExecutionDamage#apply} 发现事件层之后还是没打满,就在
 *       <b>跳过事件派发</b>(线程内临时标记)的前提下补发差额 —— 见 {@link ExecutionDamage}。</li>
 * </ol>
 *
 * <h2>与 valettein 的差别</h2>
 * <p>它是"把 6 个 bypass tag 的 {@code is()} 全部返回 true + {@code setCanceled} 变 no-op +
 * {@code setAmount} 只许变大":全局改写了所有伤害、所有事件的语义。这里同等的强度靠
 * <b>专属伤害类型 + 官方标签</b>拿到,事件层只在"执行之手自己这一脚"上动手,
 * 别的伤害源、别的模组完全不受影响。</p>
 *
 * <h2>图腾</h2>
 * <p>{@code bypasses_invulnerability} 会让 {@code checkTotemDeathProtection} 直接返回 false ——
 * 也就是<b>图腾救不下被执行的生物</b>。这是要的强度,已写进 tooltip 与 debugLog。</p>
 */
public final class HandOfExecutionHandler {

    /** 已经在 {@link #onAttack} 接管过的那一脚的原始伤害源(等它自己的 LivingHurtEvent 来清零)。 */
    private static record Pending(DamageSource source, int tick) {}

    private static final Map<UUID, Pending> ZERO_OUT = new ConcurrentHashMap<>();

    /** tooltip 两行(常量,只建一次;ItemTooltipEvent 在悬停期间每帧触发)。 */
    private static final Component BYPASS_LINE =
        Component.translatable("tooltip.enigmaticenhance.hand_of_execution.bypass")
            .withStyle(ChatFormatting.GRAY);
    private static final Component TOTEM_LINE =
        Component.translatable("tooltip.enigmaticenhance.hand_of_execution.totem")
            .withStyle(ChatFormatting.GRAY);

    /** 附魔 id 的 NBT 文本形式(快速判定用,避免每帧反序列化附魔表)。 */
    private static final String ENCHANT_ID = EnigmaticEnhance.MODID + ":hand_of_execution";

    private HandOfExecutionHandler() {
    }

    // ------------------------------------------------------------------ 附魔判定

    /**
     * 这次伤害的攻击者是否带着「执行之手」(主手或副手)。
     *
     * @return 带附魔的攻击者;没有则 {@code null}
     */
    private static LivingEntity attackerWithExecution(DamageSource source) {
        if (!RingConfig.handOfExecutionEnabled() || source == null) {
            return null;
        }
        try {
            if (!(source.getEntity() instanceof LivingEntity attacker)) {
                return null;
            }
            HandOfExecutionEnchantment enchant = ModEnchantments.handOfExecutionOrNull();
            if (enchant == null) {
                return null;
            }
            if (levelOf(attacker.getMainHandItem(), enchant) > 0
                || levelOf(attacker.getOffhandItem(), enchant) > 0) {
                return attacker;
            }
            return null;
        }
        catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** 2.10.6:这次伤害的攻击者是否带着「执行之手」(给针对性 Boss Mixin 用)。 */
    public static boolean shouldBypass(DamageSource source) {
        // 3.8.2:总开关关闭 ⇒ 所有 Boss 破防 Mixin 直接放行,绝不干预击杀流程
        if (!RingConfig.handOfExecutionEnabled()) {
            return false;
        }
        try {
            LivingEntity attacker = attackerWithExecution(source);
            if (attacker == null) {
                return false;
            }
            // 4.6.2:先问 oncePending() 再拼 key(每击一次白花)
            if (DebugLog.oncePending()) {
                HandOfExecutionEnchantment enchant = ModEnchantments.handOfExecutionOrNull();
                int mainLevel = enchant == null ? 0 : levelOf(attacker.getMainHandItem(), enchant);
                int offLevel = enchant == null ? 0 : levelOf(attacker.getOffhandItem(), enchant);
                DebugLog.once("exec:bypass:" + attacker.getUUID(),
                    "[Execution] 破防已启用:主手等级={} 副手等级={}", mainLevel, offLevel);
            }
            return true;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 附魔等级(附魔书 / 被别的模组塞进 StoredEnchantments 的物品也认)。 */
    private static int levelOf(ItemStack stack, HandOfExecutionEnchantment enchant) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Integer level = EnchantmentHelper.getEnchantments(stack).get(enchant);
        return level == null ? 0 : level;
    }

    // ------------------------------------------------------------------ 第 2 层:接管

    /**
     * 接管入口:把"带执行之手的普通攻击"换成"执行之手伤害"。
     *
     * <p>为什么在 {@code LivingAttackEvent}(HIGHEST)而不是 hurt 里:这一层最早,伤害值还没被
     * 任何模组夹过,而且<b>还没被 {@code getDamageAfterArmorAbsorb} 打过折</b>;同时保留
     * {@code hurt()} 的完整流程(受击登记、音效、死亡、掉落)。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!RingConfig.handOfExecutionEnabled()) {
            return;
        }
        try {
            DamageSource source = event.getSource();
            if (ExecutionDamage.isExecution(source)) {
                return;   // 我们自己刚发出去的那一脚
            }
            LivingEntity target = event.getEntity();
            if (target == null || event.getAmount() <= 0.0F) {
                return;
            }
            LivingEntity attacker = attackerWithExecution(source);
            if (attacker == null) {
                noteNoEnchant(source);
                return;
            }
            ExecutionGuard.Hit hit = ExecutionDamage.apply(target, attacker, event.getAmount(), source);
            if (hit == null) {
                return;   // 数据包缺 execution 类型 ⇒ 普通伤害照常
            }
            // 原来那一脚的伤害清零:记下"这个 source 实例",等它自己的 LivingHurtEvent 来了清
            rememberZeroOut(target, source);
            logHit(attacker, target, hit);
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[执行之手] 接管失败,退回普通伤害: {}", e.toString());
        }
    }

    private static void rememberZeroOut(LivingEntity target, DamageSource source) {
        if (ZERO_OUT.size() > 256) {
            ZERO_OUT.clear();
        }
        ZERO_OUT.put(target.getUUID(), new Pending(source, target.tickCount));
    }

    // ------------------------------------------------------------------ 第 3 层:双保险

    /**
     * 双保险的<b>上半场</b>:执行之手那一脚在 HIGHEST 记下"事件确实派发了";
     * 而"普通攻击那一脚"在这里被清零(只清伤害,不取消)。
     *
     * <p>为什么只是清零而不是 {@code setCanceled(true)}:2.10.1 的教训 —— 传奇怪物靠
     * {@code hurt()} 里登记"被打过"决定是否回血/进入战斗,取消掉它就会认为没人打它。
     * 清零则让 {@code hurt()} 继续跑完(登记、动画、音效都在),实际伤害由执行之手那一脚承担。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onHurtHighest(LivingHurtEvent event) {
        try {
            LivingEntity target = event.getEntity();
            DamageSource source = event.getSource();
            // 4.6.2 同款思路:先做一次"几乎必然命中空表"的 map.get,再谈读配置/查附魔 ——
            // 普通伤害在本模组没开启时,这里只花一次 CHM.get 就返回。
            ExecutionGuard.Hit hit = ExecutionGuard.recall(target);
            if (hit != null && ExecutionDamage.isExecution(source)) {
                hit.sawEventDispatch();
                return;
            }
            if (!RingConfig.handOfExecutionEnabled() || event.getAmount() <= 0.0F) {
                return;
            }
            LivingEntity attacker = attackerWithExecution(source);
            if (attacker == null) {
                return;
            }
            Pending pending = ZERO_OUT.get(target.getUUID());
            if (pending != null && pending.source() == source && pending.tick() == target.tickCount) {
                // 这一脚已经由执行之手结算 → 原始伤害清零(保留受击流程)
                ZERO_OUT.remove(target.getUUID());
                event.setAmount(0.0F);
                return;
            }
            // 极少数模组不走 hurt() / 在 LivingAttackEvent 就被别家取消:这里补一次接管
            ExecutionGuard.Hit again = ExecutionDamage.apply(target, attacker, event.getAmount(), source);
            if (again != null) {
                event.setAmount(0.0F);
                logHit(attacker, target, again);
            }
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[执行之手] 双保险(HIGHEST)失败: {}", e.toString());
        }
    }

    /**
     * 双保险的<b>下半场</b>:被削减就还原;被取消就只驳回"这一脚"。
     *
     * <p>不学 valettein 把 {@code Event#setCanceled} 全局改成 no-op(那会让所有模组的取消语义全废);
     * 这里只在我们自己发出去、并带着 {@link ExecutionGuard} 记账的那一脚上把取消驳回去。
     * 取消撤销之后,伤害照常走完原版流程,不需要重发,也就没有重复伤害的风险。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurtLowest(LivingHurtEvent event) {
        try {
            ExecutionGuard.Hit hit = ExecutionGuard.recall(event.getEntity());
            if (hit == null || !ExecutionDamage.isExecution(event.getSource())) {
                return;
            }
            float original = hit.original();
            if (event.getAmount() < original - 1.0E-4F) {
                hit.noteReduced();
                event.setAmount(original);   // 被别的模组削减 → 还原成原始值
            }
            if (event.isCanceled()) {
                hit.noteCancelled();
                event.setCanceled(false);    // 只驳回这一脚
            }
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[执行之手] 双保险(LOWEST/hurt)失败: {}", e.toString());
        }
    }

    /**
     * 护甲/保护之后还有模组只改 {@code LivingDamageEvent}:同一套还原逻辑再兜一层。
     *
     * <p>注意 1.20.1 的 LivingDamageEvent 派发在"黄血吸收"<b>之后</b>(反编译
     * {@code LivingEntity#actuallyHurt}:先扣 absorption,再 {@code ForgeHooks.onLivingDamage}),
     * 所以这里的基准值不是 {@code original},而是
     * {@link ExecutionGuard.Hit#expectedHealthDamage()}(原始值 - 进 hurt 前已有的黄血)。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamageLowest(LivingDamageEvent event) {
        try {
            ExecutionGuard.Hit hit = ExecutionGuard.recall(event.getEntity());
            if (hit == null || !ExecutionDamage.isExecution(event.getSource())) {
                return;
            }
            float expected = hit.expectedHealthDamage();
            if (expected > 0.0F && event.getAmount() < expected - 1.0E-4F) {
                hit.noteReduced();
                event.setAmount(expected);
            }
            if (event.isCanceled()) {
                hit.noteCancelled();
                event.setCanceled(false);
            }
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[执行之手] 双保险(LOWEST/damage)失败: {}", e.toString());
        }
    }

    /** 执行之手那一脚在 LivingAttackEvent 层就被别家拦了:记一笔,兜底层会补发。 */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onExecutionAttack(LivingAttackEvent event) {
        try {
            ExecutionGuard.Hit hit = ExecutionGuard.recall(event.getEntity());
            if (hit == null || !ExecutionDamage.isExecution(event.getSource())) {
                return;
            }
            hit.sawEventDispatch();
            if (event.isCanceled()) {
                hit.noteCancelled();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    // ------------------------------------------------------------------ 日志

    /**
     * 一次命中的结算日志(受 {@code debugLog} 门控):
     * <pre>[执行之手] 伤害: 原始=20.0 最终=20.0 被削减=0次 被取消=0次</pre>
     */
    private static void logHit(LivingEntity attacker, LivingEntity target, ExecutionGuard.Hit hit) {
        if (!DebugLog.debugEnabled()) {
            return;
        }
        DebugLog.info("[执行之手] 伤害: 原始={} 最终={} 被削减={}次 被取消={}次 补发={}次 ({} -> {})",
            hit.original(), hit.dealt(), hit.reducedTimes(), hit.cancelledTimes(), hit.forcedTimes(),
            attacker == null ? "?" : attacker.getName().getString(),
            target == null ? "?" : target.getName().getString());
        // 图腾失效这件事只提醒一次,免得每个玩家都困惑"为什么图腾没救下来"
        if (DebugLog.oncePending()) {
            DebugLog.once("exec:totem",
                "[执行之手] 提示:execution 带 bypasses_invulnerability,图腾救助(checkTotemDeathProtection)对被执行的目标不生效");
        }
    }

    /** 2.9.5 诊断:主手没有附魔时,每种物品只提醒一次(方便确认是不是拿错手/拿错物品)。 */
    private static void noteNoEnchant(DamageSource source) {
        if (!DebugLog.oncePending() || source == null
            || !(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        // 4.6.2:先问 oncePending() 再拼 key,避免每击白拼字符串
        DebugLog.once("exec:noench:" + ForgeRegistries.ITEMS.getKey(attacker.getMainHandItem().getItem())
            + ":" + (attacker instanceof Player ? "p" : "m"),
            "[Execution] 主/副手 {} 上没有 执行之手,跳过", attacker.getMainHandItem().getItem());
    }

    // ------------------------------------------------------------------ tooltip

    /**
     * 给带「执行之手」的物品(含附魔书)补两行说明。
     *
     * <p>这一行必须写清"图腾不管用",否则玩家只会觉得图腾坏了。</p>
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        try {
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty() || !hasExecutionRaw(stack)) {
                return;
            }
            List<Component> lines = event.getToolTip();
            String bypass = BYPASS_LINE.getString();
            for (Component line : lines) {
                if (line != null && bypass.equals(line.getString())) {
                    return;   // 只加一次(悬停期间本事件每帧触发)
                }
            }
            lines.add(BYPASS_LINE);
            lines.add(TOTEM_LINE);
        }
        catch (RuntimeException | LinkageError ignored) {
            // tooltip 失败不影响物品
        }
    }

    /**
     * 零分配判定:这叠物品是不是带着执行之手(4.6.1 同款做法,直接读附魔列表的两个原版键)。
     *
     * <p>不用 {@code EnchantmentHelper.getEnchantments} —— 它会新建 HashMap 并反查注册表,
     * 而 {@code ItemTooltipEvent} 在悬停期间每帧都会触发。</p>
     */
    private static boolean hasExecutionRaw(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        if (tag.contains("Enchantments", Tag.TAG_LIST)) {
            return listHasExecution(tag, "Enchantments");
        }
        if (tag.contains("StoredEnchantments", Tag.TAG_LIST)) {
            return listHasExecution(tag, "StoredEnchantments");
        }
        return false;
    }

    private static boolean listHasExecution(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (ENCHANT_ID.equals(list.getCompound(i).getString("id"))) {
                return true;
            }
        }
        return false;
    }

}
