package com.hengx.enigmaticenhance;

import java.util.Collections;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * 「恶魔血杯」(1.1.28a,{@code enigmaticenhance:demon_blood_chalice},Curios {@code curios:charm})的全部运行时行为。
 *
 * <h2>核心效果:所有药水效果「照常存在、不产生任何作用」</h2>
 * 玩家佩戴时:
 * <ul>
 * <li>效果**仍然在** {@code getActiveEffects()} 里 —— 数量、类型、等级、时长、{@code isBeneficial()} 全都不变,
 *     HUD 照常显示,{@code hasEffect(...)} 照常为 true,别的模组"检测 buff"的逻辑照常命中;</li>
 * <li>但**不会真正生效**:{@code MobEffect#applyEffectTick} / {@code #applyInstantenousEffect} 被短路
 *     (再生不回血、中毒不掉血、力量不增伤),{@code #addAttributeModifiers} / {@code #removeAttributeModifiers}
 *     被短路(速度/抗性/生命提升这类属性型效果拿不到修饰符);</li>
 * <li><b>不引入任何"无效/封印"状态</b>:不新增 {@code MobEffect}、不新增 {@code MobEffectCategory}、
 *     不给玩家挂任何自定义属性、不写任何 NBT 标记。对外它就是一件普通饰品 + 一堆普通 buff,
 *     全部状态只在本类的**内存**里({@link State}),服务器重启/玩家登出就没了(也不需要留着)。</li>
 * </ul>
 * 实现见 {@code com.hengx.enigmaticenhance.mixin.MobEffectSealMixin}(4 个 HEAD 注入,全部
 * {@code required=false} + {@code require=0},目标类改名也只是"不生效"而不是崩)。
 *
 * <h2>为什么还要"清理"一次效果属性</h2>
 * 属性修饰符是在<b>效果被加上/更新那一刻</b>一次性挂到玩家属性表上的,不是每 tick 重算。
 * 所以"先喝药、后戴杯子"时,旧的修饰符已经在了,必须主动摘掉 —— 这就是 {@link #purify(Player)};
 * 反过来"摘下杯子"时,被摘掉的修饰符不会被游戏自动补回来,所以要在 {@link #restore(Player)} 里
 * 逐个效果重新调一次 {@code addAttributeModifiers}(只补缺失的那些,幂等)。
 * 两个动作都只碰**效果自己声明的那几个 UUID**,不会动别的模组/别的来源的修饰符。
 *
 * <h2>联动一:每 1 个正面效果 ⇒「血战沙场之证」的已损失生命判定 +1%</h2>
 * 接入点是本体自己的 {@code SuperpositionHandler#getMissingHealthPool(Player)}
 * (见 {@code SuperpositionBloodbathMixin})。这个方法就是"已损失生命判定"
 * ({@code (最大生命 - 当前生命) / 最大生命}),血战沙场之证的四项加成与它的受击额外伤害
 * **全部**只读这一个值,所以只改这一处即可,不需要碰本体任何代码;加上后夹到 1.0(=100%)。
 *
 * <h2>联动二:每 1 个负面效果 ⇒ 视为 1 个诅咒附魔</h2>
 * 接入点是 {@code SuperpositionHandler#getCurseAmount(Player)}(见 {@code SuperpositionCurseMixin})。
 * 「千咒卷轴」({@code enigmaticlegacy:cursed_scroll})的伤害/挖掘/再生三条加成、以及它自己 tooltip 里
 * 显示的诅咒数,读的都是这个方法,所以负数效果数量直接以整数加进去即可。
 *
 * <h2>日志</h2>
 * 全部走 {@link DebugLog}(默认 {@code debugLog=false} ⇒ 零输出)。
 */
public final class DemonBloodChaliceHandler {

    public static final String LOG_TAG = "[DemonBloodChalice]";

    /** 1.2.7b:两条联动 mixin 是否真的生效(<clinit> 注入探针,启动时用来报警)。 */
    public static volatile boolean BLOODBATH_HOOK_OK = false;
    public static volatile boolean CURSE_HOOK_OK = false;

    /** 联动一:每个正面效果给「血战沙场之证」的已损失生命判定加的百分比(0.01 = 1%)。 */
    public static final double BLOODBATH_PER_POSITIVE = 0.01D;

    /** 联动一:判定值上限(= 100% 已损失;本身公式的理论最大值也是 1.0)。 */
    public static final float MAX_MISSING_HEALTH = 1.0F;

    /** 每个玩家一份的内存状态(不落 NBT、不挂属性、不进效果列表)。 */
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    /**
     * 1.1.28d:**读取型**原版效果的名单。
     * <p>
     * 1.1.28f:这组效果**不再被封印**(hasEffect 保持 true,它们会正常生效 —— 用户明确接受的取舍);
     * 这个集合现在只用于自检 T29 的"读取型"分类与报告。
     * <p>
     * 它们不是靠 {@code applyEffectTick}/属性修饰符实现的,而是原版在别处<b>直接读
     * {@code hasEffect}/{@code getEffect} 才生效</b>(防火=伤害免疫判定、水下呼吸/潮涌=溺水判定、
     * 隐身/发光=共享标记、跳跃=跳跃力度、飘浮/缓降/海豚=移动、夜视/失明/黑暗/反胃=客户端画面、
     * 不祥之兆/村庄英雄=袭击与交易)。对它们只在
     * {@code LivingEntityHasEffectSealMixin} 里把这两次读取对**佩戴者**改成"没有"。
     * <p>
     * 代价(1.1.28d 报告里写明):这几个效果 {@code hasEffect(...)} 返回 false、
     * {@code getEffect(...)} 返回 null;但效果**仍然在** {@code getActiveEffects()} 列表里、
     * 状态栏照常显示、数量/时长/等级/正面负面判定都不变 —— 所以"数 buff"的检测方式不受影响。
     */
    private static final Set<MobEffect> MECHANICAL = Collections.newSetFromMap(new IdentityHashMap<>());

    static {
        MECHANICAL.add(MobEffects.FIRE_RESISTANCE);
        MECHANICAL.add(MobEffects.WATER_BREATHING);
        MECHANICAL.add(MobEffects.CONDUIT_POWER);
        MECHANICAL.add(MobEffects.INVISIBILITY);
        MECHANICAL.add(MobEffects.GLOWING);
        MECHANICAL.add(MobEffects.NIGHT_VISION);
        MECHANICAL.add(MobEffects.BLINDNESS);
        MECHANICAL.add(MobEffects.DARKNESS);
        MECHANICAL.add(MobEffects.CONFUSION);
        MECHANICAL.add(MobEffects.LEVITATION);
        MECHANICAL.add(MobEffects.SLOW_FALLING);
        MECHANICAL.add(MobEffects.DOLPHINS_GRACE);
        MECHANICAL.add(MobEffects.JUMP);
        MECHANICAL.add(MobEffects.BAD_OMEN);
        MECHANICAL.add(MobEffects.HERO_OF_THE_VILLAGE);
        // 1.1.28d:抗性提升在 1.20.1 里没有独立属性,是 LivingEntity#getDamageAfterMagicAbsorb
        // 直接读 hasEffect(RESISTANCE)+等级算的,所以也归到"读取型"名单里一起封。
        MECHANICAL.add(MobEffects.DAMAGE_RESISTANCE);
    }

    /** 我们自己的"补回属性修饰符"调用需要绕过封印判定(仅本线程、仅这一小段)。 */
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DemonBloodChaliceHandler() {}

    /** 内存状态:{@code tick/valid/sealed} 是每 tick 一次的佩戴缓存(避免每 tick 每个效果都查 Curios)。 */
    private static final class State {
        long tick = Long.MIN_VALUE;
        boolean valid;
        boolean sealed;
        /** 是否曾经主动摘除过效果属性修饰符(摘下杯子时要补回来)。 */
        boolean purged;
    }

    private static State state(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    // ------------------------------------------------------------------ 佩戴查询

    /** 佩戴中的恶魔血杯(没有/异常返回空堆)。 */
    public static ItemStack equippedChalice(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        var chalice = ModItems.demonBloodChaliceOrNull();
        if (chalice == null) {
            return ItemStack.EMPTY;
        }
        try {
            // 4.6.2(内存):.map(...).orElse(...) ⇒ orElse(null) + 判空,不再每调一次新建
            // 一个 LazyOptional / lambda / 内层 Optional(理由见 EarthAmuletData#equippedAmulet)。
            var handler = CuriosApi.getCuriosInventory(player).orElse(null);
            if (handler == null) {
                return ItemStack.EMPTY;
            }
            SlotResult found = handler.findFirstCurio(stack -> !stack.isEmpty()
                && stack.getItem() == chalice).orElse(null);
            return found == null ? ItemStack.EMPTY : found.stack();
        }
        catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 是否佩戴着(不做缓存;需要高频判定时用 {@link #isSealed(LivingEntity)})。 */
    public static boolean wearing(Player player) {
        return !equippedChalice(player).isEmpty();
    }

    /** 丢掉该玩家的佩戴缓存(装备变化后必须调,否则本 tick 内还会用旧值)。 */
    public static void invalidate(Player player) {
        if (player != null) {
            state(player).valid = false;
        }
    }

    /**
     * 该实体此刻是否处于"效果被封印"状态 —— mixin 的唯一判定入口。
     * <p>只认真人玩家(饰品只能戴在玩家身上)、跳过我们自己的补回调用,并按 tick 缓存一次 Curios 查询。
     */
    public static boolean isSealed(LivingEntity entity) {
        if (Boolean.TRUE.equals(BYPASS.get())) {
            return false;
        }
        if (!(entity instanceof Player player)) {
            return false;
        }
        State state = state(player);
        if (state.valid && state.tick == player.tickCount) {
            return state.sealed;
        }
        state.sealed = wearing(player);
        state.tick = player.tickCount;
        state.valid = true;
        return state.sealed;
    }

    // ------------------------------------------------------------------ 读取型效果的封印判定(给 mixin 用)

    /** 1.1.28e:三件套(契约+血杯+手骨)时只封 I 级(amplifier == 0)的效果。 */
    public static boolean onlyLevelOne(LivingEntity entity) {
        return entity instanceof Player player && DemonBoneHandler.fullSet(player);
    }

    /** 这个等级的效果是否要被封(普通情况全封;三件套时只封 I 级)。 */
    public static boolean shouldSeal(LivingEntity entity, int amplifier) {
        return shouldSeal(entity, null, amplifier);
    }

    /**
     * 1.2.13c:**三件套**时的限级规则(用户指定):
     * <ul>
     *   <li>正面效果:<b>不封印</b>,只在下面 {@link #enforceSet} 里把等级压到 I 级;</li>
     *   <li>负面效果:只"无效一级" —— I 级(amplifier 0)整条无效,II 级及以上降一级后照常生效。</li>
     * </ul>
     * 非三件套仍然是"全部封印"(血杯单个佩戴的老行为)。
     */
    public static boolean shouldSeal(LivingEntity entity, MobEffect effect, int amplifier) {
        if (!isSealed(entity)) {
            return false;
        }
        if (!onlyLevelOne(entity)) {
            return true;          // 非三件套:全封
        }
        if (effect != null && effect.isBeneficial()) {
            return false;         // 三件套:正面效果保留(等级由 enforceSet 压成 I)
        }
        return amplifier == 0;    // 三件套:负面只无效 I 级
    }

    /** 1.2.13c:三件套时"正面效果只按 I 级生效" —— 瞬时型需要在执行点降级时用。 */
    public static boolean shouldLimitBeneficial(LivingEntity entity, MobEffect effect, int amplifier) {
        return effect != null && effect.isBeneficial() && amplifier > 0 && onlyLevelOne(entity) && isSealed(entity);
    }

    /** 给 mixin 用的"绕过封印"开关(我们自己降级重放时不要被自己的判定拦住)。 */
    public static boolean beginBypass() {
        boolean previous = Boolean.TRUE.equals(BYPASS.get());
        BYPASS.set(Boolean.TRUE);
        return previous;
    }

    public static void endBypass(boolean previous) {
        BYPASS.set(previous);
    }

    /**
     * 1.2.13c:三件套的"限级"执行体。
     * <p>正面效果压到 I 级(amplifier 0);负面效果降一级(II→I、III→II…;I 级留给
     * {@link #shouldSeal} 整条挡掉)。做法是"摘掉再按新等级加回去",这样效果自己声明的
     * 属性修饰符也会跟着按新等级重算。</p>
     */
    public static void enforceSet(Player player) {
        if (player == null || player.level().isClientSide || !onlyLevelOne(player)) {
            return;
        }
        for (MobEffectInstance instance : new ArrayList<>(player.getActiveEffects())) {
            MobEffect effect = instance.getEffect();
            int amplifier = instance.getAmplifier();
            int want = effect.isBeneficial() ? 0 : amplifier - 1;
            if (want < 0 || want == amplifier) {
                continue;
            }
            try {
                int duration = instance.getDuration();
                boolean ambient = instance.isAmbient();
                boolean visible = instance.isVisible();
                boolean icon = instance.showIcon();
                player.removeEffect(effect);
                player.addEffect(new MobEffectInstance(effect, duration, want, ambient, visible, icon));
                DebugLog.once("chalice:set:" + player.getUUID(),
                    "[DemonBloodChalice] 三件套限级: {} {} -> {}", effect.getDescriptionId(), amplifier, want);
            }
            catch (Throwable t) {
                DebugLog.error(LOG_TAG + " 限级失败", t);
            }
        }
    }

    /** 该效果是否属于"读取型"(靠原版 hasEffect/getEffect 生效,1.1.28f 起保留真值不封印)。 */
    public static boolean isMechanical(MobEffect effect) {
        return effect != null && MECHANICAL.contains(effect);
    }

    // ------------------------------------------------------------------ 效果计数

    /** 正面效果数量(= {@code MobEffect#isBeneficial()})。 */
    public static int positiveEffects(Player player) {
        int count = 0;
        if (player == null) {
            return 0;
        }
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().isBeneficial()) {
                ++count;
            }
        }
        return count;
    }

    /** 负面效果数量(= 非正面,含中性;用户口径是"每有一个负面 buff")。 */
    public static int negativeEffects(Player player) {
        int count = 0;
        if (player == null) {
            return 0;
        }
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (!instance.getEffect().isBeneficial()) {
                ++count;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ 联动数值(给两个 mixin 用)

    /** 联动一:已损失生命判定要额外加的那一部分(未佩戴 ⇒ 0)。 */
    public static float extraMissingHealth(LivingEntity entity) {
        if (!(entity instanceof Player player) || !isSealed(player)) {
            return 0.0F;
        }
        return (float) (BLOODBATH_PER_POSITIVE * positiveEffects(player));
    }

    /** 联动二:诅咒计数要额外加的那一部分(未佩戴 ⇒ 0)。 */
    public static int extraCurses(LivingEntity entity) {
        if (!(entity instanceof Player player) || !isSealed(player)) {
            return 0;
        }
        return negativeEffects(player);
    }

    // ------------------------------------------------------------------ 效果属性修饰符的摘除 / 补回

    /**
     * 摘掉**当前所有效果**已挂在玩家属性上的修饰符(只认效果自己声明的 UUID)。
     * 幂等:已经不在的跳过;一个都没摘到 ⇒ 不写任何日志。
     */
    public static void purify(Player player) {
        if (player == null) {
            return;
        }
        int removed = 0;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            MobEffect effect = instance.getEffect();
            for (Map.Entry<Attribute, AttributeModifier> entry : effect.getAttributeModifiers().entrySet()) {
                AttributeInstance attribute = player.getAttribute(entry.getKey());
                if (attribute != null && attribute.getModifier(entry.getValue().getId()) != null) {
                    attribute.removeModifier(entry.getValue().getId());
                    ++removed;
                }
            }
        }
        if (removed > 0) {
            DebugLog.info("{} {} 暂时摘除 {} 个效果属性修饰符", LOG_TAG, player.getName().getString(), removed);
        }
    }

    /**
     * 把**当前所有效果**缺失的属性修饰符补回来(摘下杯子 / 关掉封印时调)。
     * 幂等:只补缺的那几个;补的动作要绕过封印判定({@link #BYPASS}),否则会被自己的 mixin 拦掉。
     */
    public static void restore(Player player) {
        if (player == null) {
            return;
        }
        boolean previous = Boolean.TRUE.equals(BYPASS.get());
        BYPASS.set(Boolean.TRUE);
        try {
            for (MobEffectInstance instance : player.getActiveEffects()) {
                MobEffect effect = instance.getEffect();
                boolean missing = false;
                for (Map.Entry<Attribute, AttributeModifier> entry : effect.getAttributeModifiers().entrySet()) {
                    AttributeInstance attribute = player.getAttribute(entry.getKey());
                    if (attribute != null && attribute.getModifier(entry.getValue().getId()) == null) {
                        missing = true;
                        break;
                    }
                }
                if (missing) {
                    effect.addAttributeModifiers(player, player.getAttributes(), instance.getAmplifier());
                    DebugLog.info("{} {} 补回效果属性修饰符:{}", LOG_TAG, player.getName().getString(),
                        effect.getDescriptionId());
                }
            }
        }
        finally {
            BYPASS.set(previous);
        }
    }

    // ------------------------------------------------------------------ 佩戴 / 取下

    /** 物品的 {@code onEquip}:立刻把"先喝药、后戴杯子"留下的属性修饰符摘干净。 */
    public static void onEquipped(Player player) {
        if (player == null) {
            return;
        }
        try {
            invalidate(player);
            State state = state(player);
            state.sealed = wearing(player);
            state.tick = player.tickCount;
            state.valid = true;
            if (state.sealed) {
                state.purged = true;
                purify(player);
                DebugLog.info("{} {} 佩戴:效果保留但不生效(正面 {} / 负面 {})", LOG_TAG,
                    player.getName().getString(), positiveEffects(player), negativeEffects(player));
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 佩戴结算异常", t);
        }
    }

    /** 物品的 {@code onUnequip}:立刻把效果属性修饰符补回来(不需要重新喝药)。 */
    public static void onUnequipped(Player player) {
        if (player == null) {
            return;
        }
        try {
            invalidate(player);
            State state = state(player);
            if (state.purged && !isSealed(player)) {
                restore(player);
                state.purged = false;
                DebugLog.info("{} {} 取下:效果立即恢复", LOG_TAG, player.getName().getString());
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 取下结算异常", t);
        }
    }

    // ------------------------------------------------------------------ 每 tick 兜底

    /**
     * 服务端每 tick 兜底:佩戴时保证效果属性一直是"被摘掉"的状态(登录/换维度/重生等
     * 没走 {@code onEquip} 的路径也能覆盖);没佩戴且我们摘过 ⇒ 补回来。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        try {
            State state = state(player);
            if (isSealed(player) && onlyLevelOne(player)) {
                // 1.2.13c:三件套=限级模式,不要摘属性修饰符(purify),改成把等级压下来
                if (state.purged) {
                    restore(player);
                    state.purged = false;
                }
                enforceSet(player);
            }
            else if (isSealed(player)) {
                state.purged = true;
                purify(player);
            }
            else if (state.purged) {
                restore(player);
                state.purged = false;
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " tick 结算异常", t);
        }
    }
    /**
     * 1.2.7b:封印的**事件兜底** —— 不依赖 mixin 也能挡掉最显眼的两类:
     * 再生/瞬间治疗的加血、中毒/凋零/瞬间伤害的扣血。
     * <p>1.2.13c:三件套是"限级"模式,正面效果要能生效,所以三件套时这两个兜底直接放行。</p>
     */
    @SubscribeEvent
    public static void onSealedHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        try {
            if (event.getEntity() instanceof Player player && isSealed(player) && !onlyLevelOne(player)) {
                event.setAmount(0.0F);
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 治疗兜底异常", t);
        }
    }

    /** 封印期间:魔法类型伤害(中毒/凋零/瞬间伤害)一并归零;三件套放行。 */
    @SubscribeEvent
    public static void onSealedMagicHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player) || !isSealed(player) || onlyLevelOne(player)) {
                return;
            }
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.WITHER)
                || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) {
                event.setAmount(0.0F);
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 魔法伤害兜底异常", t);
        }
    }


    /** 玩家退出:内存状态跟着清掉(不留任何痕迹)。 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            STATES.remove(event.getEntity().getUUID());
        }
    }
}
