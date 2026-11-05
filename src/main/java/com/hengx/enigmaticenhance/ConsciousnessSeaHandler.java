package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 瓶装意识之海的运行时。
 *
 * <h2>4.2.1 效果(只保留这两条)</h2>
 * <ol>
 *   <li><b>手持生效</b> —— 拿在主手(当前选中格)或副手才生效,收进背包立刻失效;</li>
 *   <li><b>无法造成伤害</b> —— 生效期间持有者打出的伤害一律取消;</li>
 *   <li><b>无仇恨的生物会主动远离</b> —— 当前目标不是持有者的生物会朝反方向走开。</li>
 * </ol>
 *
 * <p>其余行为(不被设为目标、强制转移视线、对无仇恨伤害免疫)已按需求全部移除。</p>
 */
public final class ConsciousnessSeaHandler {

    private ConsciousnessSeaHandler() {
    }

    /** 这叠是不是瓶装意识之海。 */
    public static boolean isSea(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()
                || ModItems.SEA_OF_CONSCIOUSNESS == null
                || !ModItems.SEA_OF_CONSCIOUSNESS.isPresent()) {
                return false;
            }
            Item sea = ModItems.SEA_OF_CONSCIOUSNESS.get();
            return stack.is(sea);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 是否拿在手上(主手当前选中格 或 副手)。 */
    public static boolean inHand(Player player) {
        if (player == null) {
            return false;
        }
        return isSea(player.getMainHandItem()) || isSea(player.getOffhandItem());
    }

    /** 效果是否生效 = 拿在手上。 */
    public static boolean active(LivingEntity entity) {
        return entity instanceof Player player && inHand(player);
    }

    /** ① 生效中的玩家无法造成伤害。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        try {
            if (event.getSource().getEntity() instanceof Player player && active(player)) {
                event.setAmount(0.0F);
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯豁免逻辑,失败就放行
        }
    }

    /** ② 无仇恨的生物主动远离。 */
    @SubscribeEvent
    public static void onMobTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        try {
            if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
                return;
            }
            if (mob.tickCount % 20 != 0) {
                return;
            }
            for (Player p : mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(24.0D))) {
                if (!active(p)) {
                    continue;
                }
                if (mob.getTarget() == p) {
                    continue;   // 已经仇恨 ⇒ 不赶它走
                }
                fleeFrom(mob, p);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 距离玩家超过这个值就不再跑。 */
    private static final double FLEE_STOP = 12.0D;

    /** 逃跑目标点距离玩家多远。 */
    private static final double FLEE_TARGET = 16.0D;

    private static final double FLEE_SPEED = 1.2D;

    private static void fleeFrom(Mob mob, Player player) {
        try {
            double dx = mob.getX() - player.getX();
            double dz = mob.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.001D) {
                dx = 1.0D;
                dz = 0.0D;
                dist = 1.0D;
            }
            if (dist >= FLEE_STOP) {
                return;
            }
            double tx = player.getX() + dx / dist * FLEE_TARGET;
            double tz = player.getZ() + dz / dist * FLEE_TARGET;
            mob.getNavigation().moveTo(tx, mob.getY(), tz, FLEE_SPEED);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 手持大地之瓶睡觉 → 获得瓶装意识之海。 */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide || ModItems.SEA_OF_CONSCIOUSNESS == null
                || !ModItems.SEA_OF_CONSCIOUSNESS.isPresent()) {
                return;
            }
            boolean mainHas = isEarthBottle(player.getMainHandItem());
            boolean offHas = isEarthBottle(player.getOffhandItem());
            if (!mainHas && !offHas) {
                return;
            }
            if (mainHas) {
                player.getMainHandItem().shrink(1);
            }
            else {
                player.getOffhandItem().shrink(1);
            }
            ItemStack reward = new ItemStack(ModItems.SEA_OF_CONSCIOUSNESS.get());
            if (!player.getInventory().add(reward)) {
                player.drop(reward, false);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    private static boolean isEarthBottle(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.contains("earth") && (id.contains("bottle") || id.contains("vial") || id.contains("flask"));
    }
}