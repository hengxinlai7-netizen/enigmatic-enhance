package com.hengx.enigmaticenhance;

import java.util.UUID;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「无止暴食之证」({@code enigmaticdelicacy:gluttony_charm},Curios 的 {@code charm} 槽)佩戴效果。
 * <p>
 * 需求:佩戴时饱食度锁 0、饱和度锁 20,任何消耗(疾跑/跳跃/挖掘/攻击的 addExhaustion)只从饱和度里扣,
 * 饱食度为 0 导致原版自然回血不可用,因此自己按固定节奏回血,并把等价代价(6 疲劳)也记到饱和度路线上。
 * <p>
 * 挂载方式:Forge 事件,不写任何 vanilla mixin。
 * <ul>
 * <li>{@link TickEvent.PlayerTickEvent} Phase.START —— Forge 在 {@code Player#tick()} 的**第一条**指令就发这个事件,
 *     而 {@code FoodData#tick} 在同一方法稍后(且只在服务端)执行,所以在这里写回锁定量,原版本 tick 的消耗结算
 *     就必然落在饱和度上(不会变成"完全免疫消耗"),下一 tick 再补满 —— 与"饱和度锁 20"并不矛盾;</li>
 * <li>{@link TickEvent.PlayerTickEvent} Phase.END —— 原版结算之后:补一次 {@code >= 4} 的疲劳结算(原版是
 *     {@code > 4},恰好 4.0 时它不动手,这里补上),再按节奏回血;</li>
 * <li>{@link LivingAttackEvent} —— 饱食度为 0 会让原版每 80 tick 造成一次饥饿伤害,佩戴时取消它。</li>
 * </ul>
 * 判定物品只用注册表 id({@link RingConfig#gluttonyCharmItem()}),模组缺失即 null → 整个功能静默不生效。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GluttonyHandler {

    /** 原版 {@code FoodData#tick} 的疲劳阈值:每满 4 点扣 1 点(原版先扣饱和度,饱和度为 0 才扣饱食度)。 */
    private static final float EXHAUSTION_PER_POINT = 4.0F;

    /** 原版自然回血的代价:heal(1) 配 addExhaustion(6)(foodLevel >= 18 的 80 tick 档)。 */
    private static final float HEAL_EXHAUSTION_COST = 6.0F;

    /** 原版饱食度上限。 */
    private static final int MAX_FOOD_LEVEL = 20;

    /** 已经被"锁定"过的玩家(只在服务端用一个 set 记录,摘下/下线时清除)。 */
    private static final java.util.Set<UUID> LOCKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static boolean loggedOnce;

    private GluttonyHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (player == null) {
            return;
        }
        boolean active = lockActive(player);
        boolean server = !player.level().isClientSide();
        if (event.phase == TickEvent.Phase.START) {
            if (active) {
                // 1.2.6g:锁定在**两端**都做。以前只在服务端写,客户端自己的 FoodData 仍在扣饱和度,
                // 而原版只在"饱食度变化"时把 health 包发下来 —— 我们把饱食度钉死成 0,
                // 客户端就永远收不到真实饱和度 ⇒ 看起来"饱和度没锁住"。
                if (server && LOCKED.add(player.getUUID())) {
                    lockValues(player);
                }
                enforceLock(player);
                if (server) {
                    syncFood(player);   // 每秒把饱食度/饱和度再发一次,客户端显示不可能跑偏
                }
                return;
            }
            if (server && LOCKED.remove(player.getUUID())) {
                unlockValues(player);
            }
            return;
        }
        // END 只做服务端结算(回血与疲劳);客户端不需要
        if (!server || !active) {
            return;
        }
        FoodData food = player.getFoodData();
        settleExhaustion(food);
        regenerate(player, food);
    }

    /**
     * "该不该锁"——**不区分客户端/服务端**,食物锁必须在两端同时生效,
     * 否则客户端预测出来的饱和度会盖住服务端同步值。
     */
    public static boolean lockActive(Player player) {
        if (player == null) {
            return false;
        }
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (!RingConfig.gluttonyEnabled()) {
            return false;
        }
        return wearsCharm(player);
    }

    /** 把当前生命/饱食度/饱和度发给客户端(每秒一次,值不变也发,保证显示是锁定量)。 */
    private static void syncFood(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server)
            || player.tickCount % 20 != 0) {
            return;
        }
        try {
            server.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                server.getHealth(), server.getFoodData().getFoodLevel(),
                server.getFoodData().getSaturationLevel()));
        }
        catch (RuntimeException ignored) {
            // 连接已断开之类:忽略
        }
    }

    /**
     * 漂移纠正:只在数值不等于锁定量时写一次(值正确时零写入)。
     * 饱食度与饱和度都管 —— 否则回血代价会把饱和度一路扣到 0。
     */
    public static void enforceLock(Player player) {
        try {
            FoodData food = player.getFoodData();
            int lockedFood = RingConfig.gluttonyLockedFoodLevel();
            if (food.getFoodLevel() != lockedFood) {
                food.setFoodLevel(lockedFood);
            }
            float lockedSaturation = RingConfig.gluttonyLockedSaturation();
            if (Math.abs(food.getSaturationLevel() - lockedSaturation) > 1.0E-4F) {
                food.setSaturation(lockedSaturation);
            }
            if (food.getExhaustionLevel() > 0.0F) {
                food.setExhaustion(0.0F);
            }
        }
        catch (RuntimeException ignored) {
            // 任何异常都不能影响玩家 tick
        }
    }


    /** 戴上时写一次锁定量(饱食度 + 饱和度 + 清空疲劳)。 */
    private static void lockValues(Player player) {
        try {
            FoodData food = player.getFoodData();
            food.setFoodLevel(RingConfig.gluttonyLockedFoodLevel());
            food.setSaturation(RingConfig.gluttonyLockedSaturation());
            food.setExhaustion(0.0F);
            DebugLog.info("[暴食之证] 锁定生效(%s):饱食度 %d / 饱和度 %s",
                player.getName().getString(), RingConfig.gluttonyLockedFoodLevel(),
                RingConfig.gluttonyLockedSaturation());
        }
        catch (RuntimeException ignored) {
            // 写不进去也不能让玩家 tick 崩
        }
    }

    /** 摘下时复原:否则玩家会带着被锁的 0 饱食度离开饰品被饿死。 */
    private static void unlockValues(Player player) {
        try {
            FoodData food = player.getFoodData();
            food.setFoodLevel(MAX_FOOD_LEVEL);
            food.setSaturation(0.0F);
            food.setExhaustion(0.0F);
            DebugLog.info("[暴食之证] 锁定解除(%s):饱食度已恢复 %d", player.getName().getString(), MAX_FOOD_LEVEL);
        }
        catch (RuntimeException ignored) {
            // 同上
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        // 玩家下线:清掉"已锁定"标记(值是跟着玩家存档走的,下次上线会重新锁一次)
        if (event.getEntity() != null) {
            LOCKED.remove(event.getEntity().getUUID());
        }
    }

    /** 当前是否已经对这名玩家写过锁定量(自检/调试用)。 */
    public static boolean isLocked(Player player) {
        return player != null && LOCKED.contains(player.getUUID());
    }

    /**
     * 疲劳结算:每满 4 点疲劳扣 1 点饱和度。原版分支用 {@code > 4.0F},恰好 4.0 时不结算,
     * 这里用 {@code >= 4.0F} 补齐(与 {@code addExhaustion(4f)} 的直觉一致),并且一次结算完所有积压,
     * 不会与原版重复扣(原版扣掉的 4 点已经不在字段里了)。
     */
    private static void settleExhaustion(FoodData food) {
        int guard = 0;
        while (food.getExhaustionLevel() >= EXHAUSTION_PER_POINT && guard++ < 16) {
            food.setExhaustion(food.getExhaustionLevel() - EXHAUSTION_PER_POINT);
            food.setSaturation(Math.max(food.getSaturationLevel() - 1.0F, 0.0F));
        }
    }

    /**
     * 等价回血:生命未满且饱和度 &gt; 0 时,每 {@code healIntervalTicks} tick 恢复 {@code healAmount} 点生命。
     * 节奏用 {@code player.tickCount}(每 tick +1,无跨 tick 状态)取模实现,代价 {@code addExhaustion(6f)}
     * 会在下一 tick 的 {@link #settleExhaustion} 里落到饱和度上。
     */
    private static void regenerate(Player player, FoodData food) {
        if (player.getHealth() >= player.getMaxHealth()) {
            return;
        }
        if (food.getSaturationLevel() <= 0.0F) {
            return;
        }
        int interval = RingConfig.gluttonyHealIntervalTicks();
        if (interval <= 0 || player.tickCount % interval != 0) {
            return;
        }
        float amount = RingConfig.gluttonyHealAmount();
        if (amount > 0.0F) {
            player.heal(amount);
        }
        food.addExhaustion(HEAL_EXHAUSTION_COST);
    }

    /** 饱食度为 0 时原版每 80 tick 会给一次饥饿伤害,佩戴期间取消。 */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!event.getSource().is(DamageTypes.STARVE)) {
            return;
        }
        if (isActive((Player) event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 佩戴(装在 Curios 槽里)且配置开启且参与饥饿机制时返回 true。 */
    public static boolean isActive(Player player) {
        if (player == null) {
            return false;
        }
        // 原版 FoodData#tick 本身就只在服务端跑,这里保持一致,避免客户端本地值乱飘。
        if (player.level().isClientSide()) {
            return false;
        }
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (!RingConfig.gluttonyEnabled()) {
            return false;
        }
        return wearsCharm(player);
    }

    /**
     * 客户端侧的"是否放宽起跑/续跑的饱食度门槛"判定(见 {@code GluttonySprintMixin})。
     * 这里只关心"戴着该饰品 + 功能开着",不要用 {@link #isActive}(它排除了客户端与创造/旁观)。
     */
    public static boolean sprintAllowed(Player player) {
        return player != null && RingConfig.gluttonyEnabled() && wearsCharm(player);
    }

    /** 是否把该饰品装在 Curios 槽里(缺模组/缺 Curios 时安全返回 false)。 */
    public static boolean wearsCharm(Player player) {
        Item charm = RingConfig.gluttonyCharmItem();
        if (charm == null) {
            return false;
        }
        boolean worn = Superposition.hasCurio(player, charm);
        // 这是一次性提示(每种"戴上"只写一次),但默认静默:debugLog 关闭时连名字都不取。
        if (worn && !loggedOnce && RingConfig.debugLog()) {
            loggedOnce = true;
            DebugLog.info("[暴食之证] 效果已激活:玩家 {} 佩戴 {} (饱食度锁定 {},饱和度锁定 {},回血 {}/{} tick)",
                nameOf(player), RingConfig.GLUTTONY_CHARM_ID, RingConfig.gluttonyLockedFoodLevel(),
                RingConfig.gluttonyLockedSaturation(), RingConfig.gluttonyHealAmount(),
                RingConfig.gluttonyHealIntervalTicks());
        }
        return worn;
    }

    private static String nameOf(Player player) {
        try {
            UUID id = player.getUUID();
            return id == null ? "?" : id.toString();
        }
        catch (Throwable ignored) {
            return "?";
        }
    }
}
