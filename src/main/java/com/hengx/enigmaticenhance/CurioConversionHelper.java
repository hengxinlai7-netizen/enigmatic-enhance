package com.hengx.enigmaticenhance;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 死亡转化在 {@code convertHandsOnly=false} 时的饰品栏部分。
 * <p>
 * 本类直接引用 Curios API,所以<b>只有</b>在 Curios 已加载(调用方先判 {@code ModList})时才会被类加载 ——
 * 没装 Curios 的整合包永远不会加载它,不会出现 NoClassDefFoundError。
 */
final class CurioConversionHelper {

    private CurioConversionHelper() {}

    /** 把饰品栏里第一枚冒险者之证消耗 1 个并产出 1 个绝望者证章。 */
    static boolean convertFirst(ServerPlayer player, Item charm, Item insignia) {
        boolean[] done = { false };
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
            handler.findFirstCurio(charm).ifPresent(result -> {
                ItemStack stack = result.stack();
                if (!stack.isEmpty()) {
                    stack.shrink(1);
                    done[0] = true;
                }
            }));
        if (done[0]) {
            DeathConversion.give(player, new ItemStack(insignia));
        }
        return done[0];
    }
}
