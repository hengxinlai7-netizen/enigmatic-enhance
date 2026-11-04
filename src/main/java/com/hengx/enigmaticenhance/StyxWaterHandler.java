package com.hengx.enigmaticenhance;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.2.6b:「瓶装冥河之水」的全部逻辑。
 *
 * <h2>1. 转化</h2>
 * 玩家触发<b>不死图腾</b>时({@link LivingUseTotemEvent}),如果副手拿的是大地之瓶,
 * 就把那瓶大地之瓶变成瓶装冥河之水(图腾本身照常生效,不会被本模组动)。
 *
 * <h2>2. 免死一次 / 每天刷新</h2>
 * 玩家受到致命伤害时({@link LivingHurtEvent},若剩余生命 ≤ 伤害),如果身上(背包/副手/书袋)
 * 有冥河之水且"今天还没用过",就取消这次伤害、把生命恢复到一半,并记下当天。
 * 跨天(游戏内 {@code dayTime / 24000} 变大)自动恢复可用。
 * 记录写在玩家持久数据({@value #USED_DAY_KEY})里,所以死亡/重登都不会白刷。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StyxWaterHandler {

    /** 玩家持久数据里记录"上次免死是哪一天"的键。 */
    public static final String USED_DAY_KEY = "StyxUsedDay";

    public static final ResourceLocation STYX_WATER_ID =
        new ResourceLocation("enigmaticenhance", "styx_water");

    private StyxWaterHandler() {
    }

    // ------------------------------------------------------------------ 转化

    /** 触发不死图腾:副手的大地之瓶 → 瓶装冥河之水。 */
    @SubscribeEvent
    public static void onTotemUse(LivingUseTotemEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            Item bottle = ModItems.earthBottleOrNull();
            Item styx = ModItems.styxWaterOrNull();
            if (bottle == null || styx == null) {
                return;
            }
            ItemStack offhand = player.getOffhandItem();
            if (offhand.isEmpty() || offhand.getItem() != bottle) {
                return;
            }
            // 只有"用的就是副手那个图腾"或玩家副手确实拿着大地之瓶时才算;这里直接看副手。
            // 1.2.12d:只消耗副手 1 个大地之瓶
            BottleConversionUtil.convertOne(player, InteractionHand.OFF_HAND, new ItemStack(styx));
            DebugLog.info("[StyxWater] {} 触发不死图腾,副手大地之瓶 -> 瓶装冥河之水",
                player.getName().getString());
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[StyxWater] 图腾转化异常", ex);
        }
    }

    // ------------------------------------------------------------------ 免死

    /** 致命伤害:有冥河之水且今天没用过 ⇒ 免死(生命回一半)。 */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            if (player.level().isClientSide()) {
                return;
            }
            float remaining = player.getHealth() - event.getAmount();
            if (remaining > 0.0F) {
                return; // 死不了
            }
            if (!hasStyxWater(player) || !canSaveToday(player)) {
                return;
            }
            event.setCanceled(true);
            player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.5F));
            markUsedToday(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.85F);
            DebugLog.info("[StyxWater] {} 免死一次(第 {} 天)", player.getName().getString(), currentDay(player));
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[StyxWater] 免死结算异常", ex);
        }
    }

    // ------------------------------------------------------------------ 状态

    /** 当前是第几个游戏内天数(用世界时间算)。 */
    public static long currentDay(Player player) {
        return player == null ? 0L : player.level().getDayTime() / 24000L;
    }

    /** 今天还能不能免死。 */
    public static boolean canSaveToday(Player player) {
        if (player == null) {
            return false;
        }
        long usedDay = player.getPersistentData().getLong(USED_DAY_KEY);
        if (usedDay == 0L) {
            return true; // 从没用过(第 0 天也当作没用过)
        }
        return currentDay(player) > usedDay;
    }

    /** 记下"今天用过了"。 */
    public static void markUsedToday(Player player) {
        if (player != null) {
            player.getPersistentData().putLong(USED_DAY_KEY, Math.max(1L, currentDay(player)));
        }
    }

    /** 免死状态文本(给 tooltip/调试)。 */
    public static String stateText(Player player) {
        if (player == null) {
            return "unknown";
        }
        return (canSaveToday(player) ? "ready" : "used") + "@day" + currentDay(player);
    }

    /** 玩家身上(背包/副手/古旧书袋)是否有瓶装冥河之水。 */
    public static boolean hasStyxWater(Player player) {
        Item styx = ModItems.styxWaterOrNull();
        if (styx == null || player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot >= 36 && slot != 40) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == styx) {
                return true;
            }
        }
        ItemStack inBag = BookBag.findBook(player, STYX_WATER_ID);
        return !inBag.isEmpty() && inBag.getItem() == styx;
    }

    /** 便捷判定:这瓶水能不能用在不死图腾上(给自检/调试)。 */
    public static boolean isTotem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING);
    }
}
