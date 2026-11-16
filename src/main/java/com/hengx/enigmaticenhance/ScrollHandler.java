package com.hengx.enigmaticenhance;

import java.util.Random;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「万钧之护卷轴」({@code enigmaticaddons:thunder_scroll},类 {@code auviotre.enigmatic.addon.contents.items.ThunderScroll})的两项改动。
 *
 * <h2>① 雷击开关(逐件,物品 NBT + Ctrl+左键)</h2>
 * 扩展原本的做法(反编译 {@code AddonEventHandler#onEntityHurt}):攻击者戴着卷轴、且主手物品
 * {@code canPerformAction(ToolActions.SWORD_SWEEP)}(剑)时,给**目标**的持久数据
 * {@code Electric += 60 + rand(80) + (int)(伤害*10)};{@code AddonEventHandler#onTick} 里
 * {@code Electric} 涨到阈值就给目标劈一道带 {@code HarmlessThunder} 标签的雷。
 * 我们把"累积电荷"这一步做成可关:关掉后目标永远累积不到阈值,自然就不会落雷。
 * 状态写在**卷轴自己的 NBT**({@value #NBT_LIGHTNING_DISABLED})上,所以跟着物品走(存档/交易都保留);
 * 键不存在 = 开启(沿用扩展原行为)。
 *
 * <h2>② 造成的伤害 +100%(取代原来的"+100% 横扫伤害")</h2>
 * 扩展原本用 {@code MixinEnchantmentHelper.getSweeping} 把
 * {@code EnchantmentHelper#getSweepingDamageRatio}(SRG {@code m_44821_})抬到 {@code max(1.0, 原值)},
 * 于是横扫攻击按 100% 伤害结算;横扫那一段现在由 {@code EnchantmentHelperSweepMixin} 还原成原版公式。
 *
 * <p><b>4.8.4 起不再挂属性修饰符</b>:以前这里给佩戴者挂一条 {@code generic.attack_damage} 的
 * {@code MULTIPLY_TOTAL} 修饰符,于是"攻击力面板 / 武器 tooltip 显示的伤害 / 其它模组读
 * {@code ATTACK_DAMAGE} 的地方(觉醒、器灵、剑气面板…)"全都被翻倍,战斗外也能看到。
 * 现在改成和「恶魔的手骨」同一套做法:在 {@link LivingHurtEvent} 里把<b>这一次伤害</b>
 * 直接 {@code amount * (1 + percent/100)}(见 {@link #onHurtDirectBoost});属性表一个字节都不动,
 * 所以面板/ tooltip/ 其它模组的读取结果都与不戴卷轴时完全一致。代价是增伤只对"真的造成伤害"
 * 生效 —— 不再影响横扫、弹射物、以及任何靠 {@code ATTACK_DAMAGE} 二次计算的东西。</p>
 */
public final class ScrollHandler {

    /** 物品自身 NBT 键:为 true 表示"雷击已关闭"。键不存在 = 开启(扩展原行为)。 */
    public static final String NBT_LIGHTNING_DISABLED = "enigmaticenhance:LightningDisabled";

    /** 概率判定用,自带随机源(不动世界 RNG,避免影响别人的随机序列)。 */
    private static final Random RNG = new Random();

    private ScrollHandler() {}

    // ------------------------------------------------------------------ 目标判定 / NBT

    /** 1.1.15:卷轴的 Ctrl+左键 开关交给通用的 {@link ItemToggleHandler}(与血战沙场之证共用一套)。 */
    public static final ItemToggleHandler.Target TOGGLE_TARGET = new ItemToggleHandler.Target() {

        @Override
        public boolean matches(ItemStack stack) {
            return isTargetScroll(stack);
        }

        @Override
        public String nbtKey() {
            return NBT_LIGHTNING_DISABLED;
        }

        @Override
        public String displayName() {
            return "万钧之护卷轴·雷击";
        }

        @Override
        public boolean toggleAllowed() {
            return RingConfig.scaleToggleEnabled();
        }
    };

    public static boolean isTargetScroll(ItemStack stack) {
        return stack != null && !stack.isEmpty() && RingConfig.isThunderScroll(stack.getItem());
    }

    /** 该卷轴是否被玩家关掉了雷击(键不存在 = 开)。 */
    public static boolean isLightningDisabled(ItemStack stack) {
        return ItemToggleHandler.isDisabled(stack, NBT_LIGHTNING_DISABLED);
    }

    /** 写入/清除"雷击已关闭"标记(清除时若 NBT 空了就一并清掉,和书籍开关同一风格)。 */
    public static void setLightningDisabled(ItemStack stack, boolean disabled) {
        ItemToggleHandler.setDisabled(stack, NBT_LIGHTNING_DISABLED, disabled);
    }

    // ------------------------------------------------------------------ 服务端:处理 Ctrl+左键 请求

    /**
     * 服务端校验并执行一次"切换雷击开关"。1.1.15 起改为走通用的
     * {@link ItemToggleHandler#handleToggleRequest}(校验菜单 id / 槽位范围 / 槽里确实是本物品 / 配置允许),
     * 本方法保留只是为了兼容旧调用点。
     */
    public static void handleToggleRequest(ServerPlayer sender, int containerId, int slotIndex) {
        ItemToggleHandler.handleToggleRequest(sender, containerId, slotIndex);
    }

    // ------------------------------------------------------------------ 造成的伤害(直接增伤)

    /**
     * 4.8.4:卷轴的增伤改成<b>直接乘这一次伤害</b>(与 {@code DemonBoneHandler} 同一套做法)。
     *
     * <p>为什么不再挂 {@code generic.attack_damage} 修饰符:属性修饰符是"改角色面板",副作用是
     * 武器 tooltip 的伤害数字、其它模组读 {@code ATTACK_DAMAGE} 的结果、以及所有拿攻击力二次计算的
     * 东西(横扫、剑气、部分挖掘速度实现)都会跟着翻倍 —— 战斗外也看得见。直接增伤只改
     * {@link LivingHurtEvent} 里这一次的数值,属性表不动。</p>
     *
     * <p>优先级 {@code HIGH}:排在多数"按攻击力再算"的处理器之前,但仍在护甲/抗性/附魔减免之前
     * (那些在 {@code LivingDamageEvent} / {@code ActuallyHurt} 里),所以增伤<b>照旧会被护甲衰减</b>,
     * 不是穿甲。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurtDirectBoost(LivingHurtEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof Player attacker)) {
                return;
            }
            if (event.getEntity() == attacker) {
                return;
            }
            if (!attackDamageActiveFor(attacker)) {
                return;
            }
            double percent = RingConfig.scaleAttackDamagePercent();
            if (percent == 0.0D) {
                return;
            }
            float before = event.getAmount();
            event.setAmount((float) (before * (1.0D + percent / 100.0D)));
            if (RingConfig.debugLog()) {
                DebugLog.info("[卷轴增伤] {} 命中 {}:{} -> {}(+{}%)", attacker.getGameProfile().getName(),
                    event.getEntity().getName().getString(), before, event.getAmount(), percent);
            }
        }
        catch (RuntimeException | LinkageError e) {
            // 增伤失败绝不能影响这一击的正常结算
        }
    }

    /** 增伤是否生效(总开关 + 数值不为 0)。 */
    public static boolean attackDamageActive() {
        return RingConfig.scaleEnabled() && RingConfig.scaleAttackDamagePercent() != 0.0D;
    }

    /** 这张卷轴是否带着生效中的增伤(逐件判定:确实是本卷轴 + 总开关开着)。 */
    public static boolean attackDamageActiveFor(ItemStack scroll) {
        return attackDamageActive() && isTargetScroll(scroll);
    }

    /** 该玩家此刻是否"戴着卷轴且增伤生效"(取饰品栏里那件,再走 {@link #attackDamageActiveFor(ItemStack)})。 */
    public static boolean attackDamageActiveFor(Player player) {
        if (player == null) {
            return false;
        }
        if (!attackDamageActive()) {
            return false;
        }
        return isTargetScroll(Superposition.getCurioStack(player, RingConfig.thunderScrollItem()));
    }

    // ------------------------------------------------------------------ 雷击累积判定

    /**
     * 4.8.2:两个全局开关(总开关 + 雷击子开关)是否都开着。
     *
     * <p>这是<b>所有</b>「加电荷 / 落雷」路径的第一道闸 —— 只要有一处没走它,
     * 就会出现「关掉雷击还能打出来」。读配置失败按"关"处理,绝不放行。</p>
     */
    public static boolean lightningSwitchesOn() {
        try {
            return RingConfig.scaleEnabled() && RingConfig.scaleLightningEnabled();
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * 4.8.2:统一闸门 —— 总开关 + 雷击子开关 + 该实体身上那枚卷轴的逐件开关(Ctrl+左键)。
     *
     * <p>没戴卷轴时只受两个全局开关约束(扩展那边本来也只在"攻击者戴着卷轴"时才累积电荷)。
     * 任何要写 {@code Electric} 或要落雷的地方都必须先过这一关。</p>
     */
    public static boolean lightningAllowed(LivingEntity entity) {
        if (!lightningSwitchesOn()) {
            return false;
        }
        if (entity == null) {
            return false;
        }
        ItemStack scroll = Superposition.getCurioStack(entity, RingConfig.thunderScrollItem());
        return scroll.isEmpty() || !isLightningDisabled(scroll);
    }

    /**
     * 一次"攻击命中"是否允许给目标累积雷击电荷。
     * true = 按原版累积(总开关关掉 / 雷击关掉时一律 false)。
     */
    public static boolean allowLightningCharge(LivingEntity attacker) {
        // 4.8.2:先过统一闸门(总开关 + 雷击子开关 + 逐件开关),再看概率。
        // 以前这里在"取不到饰品栏里的卷轴"时会返回 true(放行),那等于开关全失效,已修掉。
        if (!lightningAllowed(attacker)) {
            return false;
        }
        return rollLightningChance();
    }

    /** 概率判定(百分比,100 = 必定)。电荷累积的两条路径共用同一套判定。 */
    public static boolean rollLightningChance() {
        double chance = RingConfig.scaleLightningChance();
        if (chance >= 100.0D) {
            return true;
        }
        if (chance <= 0.0D) {
            return false;
        }
        return RNG.nextDouble() * 100.0D < chance;
    }

    /**
     * 1.1.14:一枚"雷击电荷"的数值,与扩展自己在 {@code AddonEventHandler#onEntityHurt} 里的公式一致
     * ({@code 60 + rand(80) + (int)(伤害 * 10)})。
     */
    public static int chargeIncrement(RandomSource random, float amount) {
        return 60 + random.nextInt(80) + (int) (amount * 10.0F);
    }

    /**
     * 1.1.14:该玩家此刻是否处于"雷击电荷应当扩散"的状态 —— 电荷扩散开关 + 统一闸门
     * (总开关 + 雷击子开关 + 该卷轴的逐件开关)+ 佩戴卷轴(概率判定由调用处按目标逐个执行)。
     *
     * <p>4.8.2:统一闸门改成调用 {@link #lightningAllowed} —— 扩散这条路径以前只靠自己拼
     * {@code scaleChargeSpread()},和"累积电荷"那条路径口径不一致,是最容易漏开关的地方。</p>
     */
    public static ItemStack lightningActiveScroll(Player player) {
        if (player == null || !RingConfig.scaleChargeSpread()) {
            return ItemStack.EMPTY;
        }
        if (!lightningAllowed(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack scroll = Superposition.getCurioStack(player, RingConfig.thunderScrollItem());
        if (scroll.isEmpty() || isLightningDisabled(scroll)) {
            return ItemStack.EMPTY;
        }
        return scroll;
    }

    // ------------------------------------------------------------------ 配置热重载

    // 4.8.4:这里原来有 reapplyAll()/reapply(),用来在配置改动后给在线玩家重挂/摘掉
    // generic.attack_damage 的瞬时修饰符。改成"受击时直接乘"以后没有需要重挂的状态了
    // (每次受击都现读 RingConfig.scaleAttackDamagePercent()),整段已删除。
}
