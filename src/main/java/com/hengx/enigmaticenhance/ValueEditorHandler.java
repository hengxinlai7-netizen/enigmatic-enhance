package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 1.3.1:数值编辑器的交互入口与"死亡重置"。 */
public final class ValueEditorHandler {

    private ValueEditorHandler() {
    }

    /** 左键:手持编辑器攻击实体 → 打开该实体的编辑界面(取消这次攻击)。 */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide) {
                return;
            }
            if (!(player.getMainHandItem().getItem() instanceof ValueEditorItem)) {
                return;
            }
            if (event.getTarget() instanceof LivingEntity target && target != player) {
                event.setCanceled(true);
                ValueEditorItem.open(player, target);
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }

    /** 玩家死亡/重生 → 清掉编辑器写入的所有修饰符。 */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        try {
            if (!event.isWasDeath()) {
                return;
            }
            int removed = ValueEditorUtil.clearAll(event.getEntity());
            if (removed > 0) {
                DebugLog.info("[ValueEditor] 死亡重置:清除 {} 条属性修饰符", removed);
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }
}