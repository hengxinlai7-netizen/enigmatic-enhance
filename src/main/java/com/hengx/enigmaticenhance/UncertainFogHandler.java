package com.hengx.enigmaticenhance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.2.11a:「瓶装不定之雾」的全部逻辑。
 *
 * <h2>获得</h2>
 * 手持大地之瓶右键**下界传送门方块** ⇒ 大地之瓶变成瓶装不定之雾。
 *
 * <h2>效果</h2>
 * 雾在背包/副手/古旧书袋里时,周围 {@value #RADIUS} 格内的**非玩家生物无法传送**:
 * 挂 {@code EntityTeleportEvent}(末影人的随机传送、Boss 的瞬移、命令传送都走这个事件),
 * 只要它在持有者附近就整个取消。诡厄巫法的 BOSS 与「觉醒」的无名者那类靠事件/瞬移实现的
 * 传送同样被拦住;个别模组直接改坐标(不走事件)的极端实现拦不住,这一点按需求写进描述即可。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UncertainFogHandler {

    /** 生效半径(格)。 */
    /** 默认生效半径(格);实际值读配置 {@code uncertainFog.radius}。 */
    public static final double DEFAULT_RADIUS = 16.0D;

    /** 1.2.11b:被拦下的瞬移次数(自检用,证明 mixin 真的生效)。 */
    public static final java.util.concurrent.atomic.AtomicInteger BLOCKED =
        new java.util.concurrent.atomic.AtomicInteger();

    /** 1.2.11b:mixin 被调用到的次数(自检用,证明注入生效)。 */
    public static final java.util.concurrent.atomic.AtomicInteger ATTEMPTS =
        new java.util.concurrent.atomic.AtomicInteger();

    private UncertainFogHandler() {
    }

    // ------------------------------------------------------------------ 获得

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide()) {
                return;
            }
            Item bottle = ModItems.earthBottleOrNull();
            Item fog = ModItems.uncertainFogOrNull();
            if (bottle == null || fog == null) {
                return;
            }
            ItemStack held = player.getItemInHand(event.getHand());
            if (held.isEmpty() || held.getItem() != bottle) {
                return;
            }
            var state = event.getLevel().getBlockState(event.getPos());
            if (!state.is(Blocks.NETHER_PORTAL)) {
                return;
            }
            // 1.2.12d:只消耗手里 1 个大地之瓶(原来会把整摞吞掉)
            BottleConversionUtil.convertOne(player, event.getHand(), new ItemStack(fog));
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            DebugLog.info("[UncertainFog] {} 用大地之瓶转化出瓶装不定之雾", player.getName().getString());
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[UncertainFog] 转化异常", ex);
        }
    }

    // ------------------------------------------------------------------ 禁止传送

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
                return;
            }
            Level level = entity.level();
            if (level.isClientSide() || !(level instanceof ServerLevel)) {
                return;
            }
            if (isSuppressedNearby(entity)) {
                event.setCanceled(true);
                DebugLog.info("[UncertainFog] 拦下 {} 的传送(次元={} → {})",
                    entity.getName().getString(), event.getPrev(), event.getTarget());
            }
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[UncertainFog] 传送拦截异常", ex);
        }
    }

    /** mixin 拦下一次瞬移时调用。 */
    /** mixin 每次进入 randomTeleport 都会调一次(不管是否拦截)。 */
    public static void noteAttempt() {
        ATTEMPTS.incrementAndGet();
    }

    public static void noteBlocked() {
        BLOCKED.incrementAndGet();
    }


    /** 当前生效半径(配置读不到就用默认 16)。 */
    public static double radius() {
        try {
            return Math.max(1.0D, RingConfig.uncertainFogRadius());
        }
        catch (RuntimeException | LinkageError ignored) {
            return DEFAULT_RADIUS;
        }
    }

    /** 附近有没有玩家带着这瓶雾。 */
    public static boolean isSuppressedNearby(LivingEntity entity) {
        Item fog = ModItems.uncertainFogOrNull();
        if (fog == null || entity == null) {
            return false;
        }
        double radius = radius();
        var box = entity.getBoundingBox().inflate(radius);
        for (Player player : entity.level().getEntitiesOfClass(Player.class, box)) {
            if (carries(player, fog)) {
                return true;
            }
        }
        return false;
    }

    /** 背包 / 副手 / 古旧书袋。 */
    public static boolean carries(Player player, Item fog) {
        if (player == null) {
            return false;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == fog) {
                return true;
            }
        }
        ItemStack inBag = BookBag.findBook(player, ModIds.UNCERTAIN_FOG_ID);
        return !inBag.isEmpty() && inBag.getItem() == fog;
    }
}
