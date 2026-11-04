package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.2.22:附魔师珍珠(神秘遗物)的判定工具。
 *
 * <p>只按物品 id 判定,避免编译期依赖神秘遗物的类:
 * {@code enigmaticlegacy:enchanter_pearl}。背包、副手、Curios 都算携带。</p>
 */
public final class PearlUtil {

    private static final String PEARL_PATH = "enchanter_pearl";

    private PearlUtil() {
    }

    private static boolean isPearl(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && PEARL_PATH.equals(id.getPath());
    }

    /** 玩家是否携带附魔师珍珠。 */
    public static boolean wearing(Player player) {
        if (player == null) {
            return false;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isPearl(player.getInventory().getItem(i))) {
                return true;
            }
        }
        if (isPearl(player.getOffhandItem())) {
            return true;
        }
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(PearlUtil::isPearl).isPresent())
                .orElse(false);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}