package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.2.6c:「瓶装战争迷雾」的全部逻辑。
 *
 * <h2>1. 转化</h2>
 * 副手拿着大地之瓶时击杀生物,累计 {@value #KILLS_REQUIRED} 只后,副手那瓶就地变成战争迷雾。
 * 击杀数存在瓶子 NBT({@value #KILLS_KEY}),换瓶会重新计数。
 *
 * <h2>2. 无差别仇恨</h2>
 * 每秒扫一次:以持有者为中心 {@value #RADIUS} 格内的每一只 {@link Mob},
 * 让它在附近随便挑一个活体当目标(可以同类、也可以是玩家)。
 * 已经有目标且目标还活着时有一半概率放过(免得每 tick 抖),驯服的宠物不参与。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WarFogHandler {

    /** 副手大地之瓶需要累计的击杀数。 */
    public static final int KILLS_REQUIRED = 50;

    /** 影响半径(格)。 */
    public static final double RADIUS = 10.0D;

    /** 击杀数的 NBT 键。 */
    public static final String KILLS_KEY = "WarFogKills";

    public static final ResourceLocation WAR_FOG_ID = new ResourceLocation("enigmaticenhance", "war_fog");

    private WarFogHandler() {
    }

    // ------------------------------------------------------------------ 转化

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.warFogEnabled()) {
            return;
        }
        try {
            if (!(event.getSource().getEntity() instanceof Player player)) {
                return;
            }
            if (event.getEntity() instanceof Player) {
                return;
            }
            Item bottle = ModItems.earthBottleOrNull();
            Item fog = ModItems.warFogOrNull();
            if (bottle == null || fog == null) {
                return;
            }
            ItemStack offhand = player.getOffhandItem();
            if (offhand.isEmpty() || offhand.getItem() != bottle) {
                return;
            }
            int kills = offhand.getOrCreateTag().getInt(KILLS_KEY) + 1;
            if (kills >= KILLS_REQUIRED) {
                // 1.2.12d:只消耗副手 1 个大地之瓶
                BottleConversionUtil.convertOne(player, InteractionHand.OFF_HAND, new ItemStack(fog));
                DebugLog.info("[WarFog] {} 副手大地之瓶击杀 {} 只 -> 瓶装战争迷雾",
                    player.getName().getString(), KILLS_REQUIRED);
            }
            else {
                offhand.getOrCreateTag().putInt(KILLS_KEY, kills);
            }
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[WarFog] 击杀计数异常", ex);
        }
    }

    /** 当前击杀计数(给自检/调试)。 */
    public static int kills(ItemStack bottle) {
        if (bottle == null || bottle.isEmpty() || !bottle.hasTag()) {
            return 0;
        }
        return bottle.getTag().getInt(KILLS_KEY);
    }

    // ------------------------------------------------------------------ 无差别仇恨

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.warFogEnabled()) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player == null || player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }
        try {
            if (!hasWarFog(player)) {
                return;
            }
            madden(player);
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[WarFog] 仇恨异常", ex);
        }
    }

    /** 让玩家周围 10 格内的生物互相仇恨(无差别)。返回被改写的生物数。 */
    public static int madden(Player player) {
        if (player == null) {
            return 0;
        }
        AABB box = player.getBoundingBox().inflate(RADIUS);
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, box);
        if (nearby.isEmpty()) {
            return 0;
        }
        int changed = 0;
        for (LivingEntity candidate : nearby) {
            if (!(candidate instanceof Mob mob) || candidate == player || !mob.isAlive()) {
                continue;
            }
            if (isTamed(mob)) {
                continue; // 不挑别人驯服的宠物
            }
            LivingEntity current = mob.getTarget();
            boolean keepCurrent = current != null && current.isAlive() && current != mob
                && player.level().random.nextFloat() < 0.5F;
            if (keepCurrent) {
                continue;
            }
            LivingEntity target = pickTarget(player, mob, nearby);
            if (target != null) {
                mob.setTarget(target);
                changed++;
            }
        }
        return changed;
    }

    /** 从附近活体里随机挑一个(排除自己),优先挑"不是自己同类"的,体现无差别。 */
    @Nullable
    public static LivingEntity pickTarget(Player owner, Mob mob, List<LivingEntity> nearby) {
        List<LivingEntity> pool = new ArrayList<>(nearby.size());
        List<LivingEntity> otherKinds = new ArrayList<>(nearby.size());
        for (LivingEntity e : nearby) {
            if (e == mob || !e.isAlive() || e.isSpectator()) {
                continue;
            }
            pool.add(e);
            if (e.getType() != mob.getType()) {
                otherKinds.add(e);
            }
        }
        List<LivingEntity> from = otherKinds.isEmpty() ? pool : otherKinds;
        if (from.isEmpty()) {
            return null;
        }
        return from.get(mob.level().random.nextInt(from.size()));
    }

    /** 玩家身上(背包/副手/古旧书袋)是否有瓶装战争迷雾。 */
    public static boolean hasWarFog(Player player) {
        Item fog = ModItems.warFogOrNull();
        if (fog == null || player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot >= 36 && slot != 40) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == fog) {
                return true;
            }
        }
        ItemStack inBag = BookBag.findBook(player, WAR_FOG_ID);
        return !inBag.isEmpty() && inBag.getItem() == fog;
    }

    /** 是不是别人驯服的宠物(有主就不动它)。 */
    private static boolean isTamed(LivingEntity entity) {
        try {
            if (entity instanceof net.minecraft.world.entity.TamableAnimal tame) {
                return tame.isTame();
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 部分模组实体没有 TamableAnimal 支持,忽略
        }
        return false;
    }
}
