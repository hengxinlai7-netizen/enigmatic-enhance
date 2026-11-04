package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 2.6.1:「复苏之叶术石」的运行时 —— Ctrl+左键 开关"允许龙首授予飞行"。
 *
 * <p>开关状态存在物品 NBT({@value #NBT_FLIGHT_DISABLED}),<b>键不存在 = 允许(u=true)</b>,
 * 与万钧之护卷轴 / 血战沙场之证共用 {@link ItemToggleHandler} 的同一条网络通道。</p>
 */
public final class RevivalLeafStoneHandler {

    /** true = 玩家关掉了"龙首飞行";键不存在 = 允许。 */
    public static final String NBT_FLIGHT_DISABLED = "enigmaticenhance:RevivalLeafFlightDisabled";

    public static final ItemToggleHandler.Target TOGGLE_TARGET = new ItemToggleHandler.Target() {

        @Override
        public boolean matches(ItemStack stack) {
            return isStone(stack);
        }

        @Override
        public String nbtKey() {
            return NBT_FLIGHT_DISABLED;
        }

        @Override
        public String displayName() {
            return "复苏之叶术石·飞行";
        }

        @Override
        public boolean toggleAllowed() {
            return true;
        }
    };

    private RevivalLeafStoneHandler() {
    }

    public static boolean isStone(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item stone = ModItems.revivalLeafStoneOrNull();
        return stone != null && stack.getItem() == stone;
    }

    public static boolean isDisabled(ItemStack stack) {
        return ItemToggleHandler.isDisabled(stack, NBT_FLIGHT_DISABLED);
    }

    /** 玩家身上(charm 槽)是否戴着"关掉了飞行"的复苏之叶术石。 */
    public static boolean flightDisabled(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Item stone = ModItems.revivalLeafStoneOrNull();
            if (stone == null) {
                return false;
            }
            ItemStack worn = Superposition.getCurioStack(player, stone);
            return !worn.isEmpty() && isDisabled(worn);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
