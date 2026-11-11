package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * 「恶魔契约」(1.1.25)的只读查询:物品判定 + 佩戴查询。
 * <p>
 * 这件饰品**没有 NBT**:它的效果是纯粹的"佩戴时改写受到伤害的数值",不需要任何状态。
 */
public final class DemonPactData {

    private DemonPactData() {}

    public static boolean isPact(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ModItems.demonPactOrNull();
    }

    /** 玩家饰品栏(curios:charm)里的恶魔契约;没有/异常都返回空堆。 */
    public static ItemStack equippedPact(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        try {
            // 4.6.2(内存):.map(...).orElse(...) ⇒ orElse(null) + 判空,不再每调一次新建
            // 一个 LazyOptional / lambda / 内层 Optional(理由见 EarthAmuletData#equippedAmulet)。
            var handler = CuriosApi.getCuriosInventory(player).orElse(null);
            if (handler == null) {
                return ItemStack.EMPTY;
            }
            SlotResult found = handler.findFirstCurio(DemonPactData::isPact).orElse(null);
            return found == null ? ItemStack.EMPTY : found.stack();
        }
        catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 物品 id(日志/自检用)。 */
    public static ResourceLocation itemId() {
        var item = ModItems.demonPactOrNull();
        return item == null ? null : ForgeRegistries.ITEMS.getKey(item);
    }
}
