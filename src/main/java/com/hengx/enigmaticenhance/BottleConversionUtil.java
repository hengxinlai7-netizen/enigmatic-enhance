package com.hengx.enigmaticenhance;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 1.2.12d:「手里的大地之瓶 → 另一种瓶子」这类转化的统一写法。
 *
 * <p>大地之瓶 {@code stacksTo(16)},而转化产物都是不可堆叠的。
 * 如果直接 {@code player.setItemInHand(hand, new ItemStack(产物))},
 * 手里**整摞**瓶子会被一次替换掉(玩家反馈的"消耗所有大地之瓶"就是这个)。</p>
 *
 * <p>正确做法:</p>
 * <ul>
 *   <li>手里不止 1 个 ⇒ 只扣 1 个,剩余留在原槽位,产物进背包(满了掉脚下);</li>
 *   <li>手里只有 1 个 ⇒ 原地换成产物(保持"拿在手上"的手感)。</li>
 * </ul>
 */
public final class BottleConversionUtil {

    private BottleConversionUtil() {
    }

    /**
     * 把手里 1 个大瓶换成产物。
     *
     * @return 是否发生了转化(手里没有大瓶时返回 false)
     */
    public static boolean convertOne(Player player, InteractionHand hand, ItemStack result) {
        if (player == null || hand == null || result == null || result.isEmpty()) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return false;
        }
        if (held.getCount() > 1) {
            ItemStack remaining = held.copy();
            remaining.shrink(1);
            player.setItemInHand(hand, remaining);
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
        else {
            player.setItemInHand(hand, result);
        }
        return true;
    }
}
