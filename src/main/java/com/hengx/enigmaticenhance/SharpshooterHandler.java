package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.2.17:「神射手护符」的运行时。
 *
 * <p>本类只负责两件事:判断"这名玩家戴没戴护符",以及<b>弹射物伤害 +20%</b>。
 * 箭矢的<b>初速固定 2 / 必暴击 / 零偏移</b>需要改原版 {@code AbstractArrow#shoot},
 * 由 {@code mixin/ArrowSharpshooterMixin} 完成(它调用这里的 {@link #wearing} 判定)。</p>
 */
public final class SharpshooterHandler {

    /** 弹射物伤害倍率(1.20 = +20%)。 */
    public static final float DAMAGE_MULTIPLIER = 1.20F;

    /** 箭矢固定初速。 */
    public static final double MIN_SPEED = 2.0D;

    private SharpshooterHandler() {
    }

    /** 玩家是否佩戴神射手护符。 */
    public static boolean wearing(Player player) {
        if (player == null) {
            return false;
        }
        Item charm = ModItems.sharpshooterCharmOrNull();
        if (charm == null) {
            return false;
        }
        try {
            return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio((ItemStack stack) -> stack.is(charm)).isPresent())
                .orElse(false);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 这个弹射物是不是由"戴护符的玩家"射出的。 */
    public static boolean fromSharpshooter(Projectile projectile) {
        if (!(projectile.getOwner() instanceof Player player)) {
            return false;
        }
        return wearing(player);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.sharpshooterEnabled()) {
            return;
        }
        try {
            if (event.getSource().getDirectEntity() == null) {
                return;
            }
            if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) {
                return;
            }
            if (!fromSharpshooter(arrow)) {
                return;
            }
            event.setAmount(event.getAmount() * DAMAGE_MULTIPLIER);
            DebugLog.info("[Sharpshooter] 弹射物伤害 {} -> {}", event.getAmount() / DAMAGE_MULTIPLIER,
                event.getAmount());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯加成逻辑,失败就当没戴
        }
    }
}