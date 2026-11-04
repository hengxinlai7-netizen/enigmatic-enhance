package com.hengx.enigmaticenhance;

import java.util.Random;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
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

    /** 切换雷击开关,返回切换后"雷击是否开启"。 */
    public static boolean toggleLightning(ItemStack stack) {
        return ItemToggleHandler.toggle(TOGGLE_TARGET, stack);
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
     * 一次"攻击命中"是否允许给目标累积雷击电荷。
     * true = 按原版累积(总开关关掉 / 与本功能无关时一律 true,保证原行为不变)。
     */
    public static boolean allowLightningCharge(LivingEntity attacker) {
        if (!RingConfig.scaleEnabled()) {
            return false; // 总开关关:雷击不生效(用户口径)
        }
        if (!RingConfig.scaleLightningEnabled()) {
            return false; // 雷击子效果被关掉
        }
        if (attacker == null) {
            return false;
        }
        ItemStack scroll = Superposition.getCurioStack(attacker, RingConfig.thunderScrollItem());
        if (scroll.isEmpty()) {
            return true; // 攻击者没戴卷轴:不是本功能管的范围
        }
        return !isLightningDisabled(scroll) && rollLightningChance();
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
     * 1.1.14:该玩家此刻是否处于"雷击电荷应当扩散"的状态 —— 总开关 + 雷击子开关 + 电荷扩散开关 +
     * 佩戴卷轴 + 该卷轴的雷击没被 Ctrl+左键 关掉(概率判定由调用处按目标逐个执行)。
     */
    public static ItemStack lightningActiveScroll(Player player) {
        if (player == null || !RingConfig.scaleChargeSpread()) {
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
