package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.6.15:瓶装意识之海的运行时。
 *
 * <ul>
 *   <li>持有者**无法造成伤害**(攻击别人的伤害被取消);</li>
 *   <li>持有者**无法受到伤害**(被攻击的伤害被取消);</li>
 *   <li>生物**不会把持有者当作目标**;</li>
 *   <li>睡觉时若手里拿着「大地之瓶」→ 转化为该物品。</li>
 * </ul>
 */
public final class ConsciousnessSeaHandler {

    private ConsciousnessSeaHandler() {
    }

    public static boolean holding(LivingEntity entity) {
        try {
            if (!(entity instanceof Player player) || ModItems.SEA_OF_CONSCIOUSNESS == null
                || !ModItems.SEA_OF_CONSCIOUSNESS.isPresent()) {
                return false;
            }
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            return main.is(ModItems.SEA_OF_CONSCIOUSNESS.get()) || off.is(ModItems.SEA_OF_CONSCIOUSNESS.get());
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 2.11.12:不再提供"伤害 -100%",只保留"不被注意/不被仇恨"。 */
    /** 生物不会把持有者设为目标。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        try {
            net.minecraft.world.entity.LivingEntity newTarget = event.getNewTarget();
            if (newTarget != null && holding(newTarget)) {
                // 2.11.12:即使被打过也继续无视(只保留"不被注意"这一条)
                event.setNewTarget(null);
                event.setCanceled(true);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 1.6.29:让生物**彻底无视**持瓶玩家(不再盯着看)。 */
    @SubscribeEvent
    public static void onMobTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        try {
            if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
                return;
            }
            if (mob.tickCount % 10 != 0) {
                return;
            }
            Player holder = null;
            for (Player p : mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(32.0D))) {
                if (holding(p)) {
                    holder = p;
                    break;
                }
            }
            if (holder == null) {
                return;
            }
            // 2.11.12:被打过也继续无视
            // 清目标 + 清"看向玩家"的意图:把视线强制转回正前方
            if (mob.getTarget() == holder) {
                mob.setTarget(null);
            }
            mob.getLookControl().setLookAt(mob.getX(), mob.getY() + mob.getEyeHeight(), mob.getZ() + 1.0D);
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
            // 2.11.24:先确定是哪只手的瓶子,然后在发放奖励后消耗掉 1 个
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