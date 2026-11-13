package com.hengx.enigmaticenhance;

import java.util.Random;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

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
 * <h2>② +100% 攻击伤害(取代原来的"+100% 横扫伤害")</h2>
 * 扩展原本用 {@code MixinEnchantmentHelper.getSweeping} 把
 * {@code EnchantmentHelper#getSweepingDamageRatio}(SRG {@code m_44821_})抬到 {@code max(1.0, 原值)},
 * 于是横扫攻击按 100% 伤害结算。现在改成给佩戴者挂
 * {@code generic.attack_damage} 上的百分比修饰符(见 {@link #attackDamageModifier()});
 * 横扫那一段由 {@code EnchantmentHelperSweepMixin} 还原成原版公式。
 */
public final class ScrollHandler {

    /** 物品自身 NBT 键:为 true 表示"雷击已关闭"。键不存在 = 开启(扩展原行为)。 */
    public static final String NBT_LIGHTNING_DISABLED = "enigmaticenhance:LightningDisabled";

    /** 我们的攻击伤害修饰符 UUID(本模组专用)。 */
    public static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("3c9f2f6e-1d4b-4a7e-9c2f-7b5a1e0d6f21");

    private static final String MODIFIER_NAME = "enigmaticenhance:scale_of_protection_attack_damage";

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

    // ------------------------------------------------------------------ 攻击伤害(百分比,乘区)

    /** 攻击伤害修饰符:generic.attack_damage + MULTIPLY_TOTAL,数值 = 配置百分比 / 100。 */
    public static AttributeModifier attackDamageModifier() {
        return new AttributeModifier(ATTACK_DAMAGE_UUID, MODIFIER_NAME,
            RingConfig.scaleAttackDamagePercent() / 100.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** 攻击伤害加成是否生效(总开关 + 数值不为 0)。 */
    public static boolean attackDamageActive() {
        return RingConfig.scaleEnabled() && RingConfig.scaleAttackDamagePercent() != 0.0D;
    }

    /** 该实体是否戴着卷轴且加成生效(给 Curios 属性事件用)。 */
    public static boolean attackDamageActiveFor(ItemStack scroll) {
        return attackDamageActive() && isTargetScroll(scroll);
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

    /** 配置改动后:给所有在线玩家重挂/摘掉我们的攻击伤害修饰符(不重启即生效)。 */
    public static void reapplyAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            reapply(player);
        }
    }

    /** 单个玩家:先摘掉旧的,再按当前配置重新挂上(没戴卷轴/关掉就不挂)。 */
    public static void reapply(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        instance.removeModifier(ATTACK_DAMAGE_UUID);
        if (attackDamageActive() && !Superposition.getCurioStack(player, RingConfig.thunderScrollItem()).isEmpty()) {
            instance.addTransientModifier(attackDamageModifier());
            if (RingConfig.debugLog()) {
                DebugLog.info("[卷轴加成] 玩家 {} 攻击伤害修饰符重挂为 {}", player.getGameProfile().getName(),
                    RingConfig.scaleAttackDamagePercent());
            }
        }
    }
}
