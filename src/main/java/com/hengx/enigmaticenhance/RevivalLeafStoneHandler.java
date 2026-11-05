package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「复苏之叶」飞行开关 —— 作用于<b>神秘遗物扩展自带的</b> {@code enigmaticaddons:revival_leaf}。
 *
 * <p>3.6.0 修正:之前错误地新增了一个独立物品 {@code enigmaticenhance:revival_leaf_stone},
 * 但需求是<b>改原物品</b>。现在整套逻辑只认扩展那枚术石,不再有自己的物品。</p>
 *
 * <p>开关状态写在物品 NBT({@value #NBT_FLIGHT_DISABLED}),<b>键不存在 = 允许</b>,
 * 与万钧之护卷轴 / 血战沙场之证共用 {@link ItemToggleHandler} 的同一条网络通道。</p>
 */
public final class RevivalLeafStoneHandler {

    /** true = 玩家关掉了"龙首飞行";键不存在 = 允许。 */
    public static final String NBT_FLIGHT_DISABLED = "enigmaticenhance:RevivalLeafFlightDisabled";

    /** 神秘遗物扩展的复苏之叶。 */
    public static final ResourceLocation ADDON_LEAF_ID =
        new ResourceLocation("enigmaticaddons", "revival_leaf");

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
            return "复苏之叶·龙首飞行";
        }

        @Override
        public boolean toggleAllowed() {
            return true;
        }
    };

    private RevivalLeafStoneHandler() {
    }

    /** 扩展那枚复苏之叶的物品实例(没装扩展时返回 null)。 */
    public static Item addonLeafOrNull() {
        try {
            return ForgeRegistries.ITEMS.getValue(ADDON_LEAF_ID);
        }
        catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** 这一叠是不是扩展的复苏之叶。 */
    public static boolean isStone(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && ADDON_LEAF_ID.equals(id);
    }

    public static boolean isDisabled(ItemStack stack) {
        return ItemToggleHandler.isDisabled(stack, NBT_FLIGHT_DISABLED);
    }

    /** 玩家身上(charm 槽)是否戴着"关掉了飞行"的复苏之叶。 */
    public static boolean flightDisabled(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Item leaf = addonLeafOrNull();
            if (leaf == null) {
                return false;
            }
            ItemStack worn = Superposition.getCurioStack(player, leaf);
            return !worn.isEmpty() && isDisabled(worn);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
