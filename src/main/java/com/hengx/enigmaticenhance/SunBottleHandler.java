package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.2.6d:「瓶装太阳」的全部逻辑。
 *
 * <h2>1. 转化</h2>
 * 手持大地之瓶(主手或副手),在<b>白天 10:00~14:00</b>({@code dayTime 4000~7999})
 * 右键<b>金块</b>时,手里那瓶大地之瓶变成瓶装太阳。
 *
 * <h2>2. 效果</h2>
 * <ul>
 *   <li>对亡灵生物造成的伤害 +10%({@link #undeadDamage});</li>
 *   <li>持续获得"饱和"与"生命回复 I":每 100 tick 刷新一次(时长 200 tick),离开背包后自然过期。</li>
 * </ul>
 * 判定与其它瓶子一致:背包 / 副手 / 古旧书袋。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SunBottleHandler {

    /** 白天 10:00 对应的 dayTime(游戏内 0 = 06:00)。 */
    public static final long NOON_START = 4000L;

    /** 14:00 对应的 dayTime。 */
    public static final long NOON_END = 8000L;

    /** 对亡灵伤害倍率。 */
    public static final float UNDEAD_MULTIPLIER = 1.1F;

    /** 效果刷新间隔与持续时间(tick)。 */
    public static final int EFFECT_REFRESH_TICKS = 100;
    public static final int EFFECT_DURATION_TICKS = 200;

    public static final ResourceLocation SUN_BOTTLE_ID =
        new ResourceLocation("enigmaticenhance", "bottled_sun");

    private SunBottleHandler() {
    }

    // ------------------------------------------------------------------ 转化

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.sunBottleEnabled()) {
            return;
        }
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide()) {
                return;
            }
            if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.GOLD_BLOCK)) {
                return;
            }
            Item bottle = ModItems.earthBottleOrNull();
            Item sun = ModItems.sunBottleOrNull();
            if (bottle == null || sun == null) {
                return;
            }
            if (!isSunTime(player.level().getDayTime())) {
                return;
            }
            ItemStack held = player.getItemInHand(event.getHand());
            if (held.isEmpty() || held.getItem() != bottle) {
                return;
            }
            // 1.2.12d:只消耗 1 个大地之瓶
            BottleConversionUtil.convertOne(player, event.getHand(), new ItemStack(sun));
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            DebugLog.info("[SunBottle] {} 在 {} 右键金块 -> 瓶装太阳", player.getName().getString(),
                player.level().getDayTime());
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[SunBottle] 转化异常", ex);
        }
    }

    /** dayTime 是否落在 10:00~14:00(换算:0 = 06:00,6000 = 正午)。 */
    public static boolean isSunTime(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= NOON_START && t < NOON_END;
    }

    // ------------------------------------------------------------------ 效果

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.sunBottleEnabled()) {
            return;
        }
        try {
            if (!(event.getSource().getEntity() instanceof Player player)) {
                return;
            }
            if (!hasSunBottle(player)) {
                return;
            }
            float amount = undeadDamage(event.getAmount(),
                event.getEntity() != null && event.getEntity().getMobType() == MobType.UNDEAD);
            event.setAmount(amount);
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[SunBottle] 伤害结算异常", ex);
        }
    }

    /** 对亡灵 +10%,其它不变。 */
    public static float undeadDamage(float amount, boolean undead) {
        return undead ? amount * UNDEAD_MULTIPLIER : amount;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.sunBottleEnabled()) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player == null || player.level().isClientSide() || player.tickCount % EFFECT_REFRESH_TICKS != 0) {
            return;
        }
        try {
            if (hasSunBottle(player)) {
                applyEffects(player);
            }
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[SunBottle] 效果刷新异常", ex);
        }
    }

    /** 给玩家刷一次饱和 + 生命回复 I。 */
    public static void applyEffects(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, EFFECT_DURATION_TICKS, 0, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EFFECT_DURATION_TICKS, 0, true, false, true));
    }

    /** 玩家身上(背包/副手/古旧书袋)是否有瓶装太阳。 */
    public static boolean hasSunBottle(Player player) {
        Item sun = ModItems.sunBottleOrNull();
        if (sun == null || player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot >= 36 && slot != 40) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == sun) {
                return true;
            }
        }
        ItemStack inBag = BookBag.findBook(player, SUN_BOTTLE_ID);
        return !inBag.isEmpty() && inBag.getItem() == sun;
    }

}
