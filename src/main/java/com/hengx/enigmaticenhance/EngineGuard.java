package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 拦下失落引擎(enigmaticaddons:lost_engine)的惩罚雷击。
 * <p>
 * 引擎的负面是一道直接打下来的雷击(受击即死)。这里在雷击**进入世界时**取消它:
 * 只要附近 16 格内有佩戴失落引擎的玩家,且这枚雷击没有该模组自己的 {@code HarmlessThunder} 标签,
 * 就直接取消 —— 雷击不会落地,自然也不会造成伤害/点燃。
 * <p>
 * 开关:{@code engine.disableLightningItemClear}(默认 true)。
 * 只判"有没有戴引擎",不再和七咒/救赎那套挂钩。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EngineGuard {

    private static final double RADIUS = 16.0D;

    private EngineGuard() {}

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!RingConfig.disableEngineLightningItemClear() || event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof LightningBolt)) {
            return;
        }
        LightningBolt bolt = (LightningBolt) event.getEntity();
        if (bolt.getTags().contains("HarmlessThunder")) {
            return;
        }
        // 只处理"由玩家引发"的雷击:自然雷(cause=null)完全不碰
        if (!(bolt.getCause() instanceof Player)) {
            return;
        }
        Item engine = RingConfig.engineItem();
        if (engine == null) {
            return;
        }
        for (Player player : event.getLevel().getEntitiesOfClass(Player.class, bolt.getBoundingBox().inflate(RADIUS))) {
            if (wears(player, engine)) {
                event.setCanceled(true);
                if (RingConfig.debugLog()) {
                    DebugLog.info("[失落引擎] 拦下惩罚雷击:玩家 {} 佩戴引擎(距雷击 {} 格)",
                        player.getGameProfile().getName(), player.distanceTo(bolt));
                }
                return;
            }
        }
    }

    /** 饰品栏 / 双手 / 背包 任一位置带着该物品。 */
    private static boolean wears(Player player, Item item) {
        // 4.6.0(内存):原来这里自己写了一遍 Class.forName + getMethod(和 Superposition 里的重复),
        // 现在统一走 Superposition 的缓存入口 —— 行为一致(缺模组时同样落到下面手上/背包的检查)。
        if (Superposition.hasCurio(player, item)) {
            return true;
        }
        if (player.getMainHandItem().is(item) || player.getOffhandItem().is(item)) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }
}