package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 4.5.0:「复苏之叶」飞行开关(从 4.4.0 之前取回,并在主模组内独立提供)。
 * 作用于<b>神秘遗物扩展自带的</b> {@code enigmaticaddons:revival_leaf}。
 *
 * <p>开关状态写在物品 NBT({@value #NBT_FLIGHT_DISABLED}),<b>键不存在 = 允许</b>,
 * 与万钧之护卷轴 / 血战沙场之证共用 {@link ItemToggleHandler} 的同一条网络通道。</p>
 *
 * <h2>4.5.0:从「事后撤销」改成「不阻止写入」</h2>
 * <p>旧实现(4.2.2)在 {@code handleFlight} 的 RETURN 里把 {@code mayfly} 改回 {@code false} ——
 * 那是<b>事后</b>撤销,分辨不出"这次 mayfly 是谁写 true 的":同一个 boolean 被所有来源共用
 * (创造/旁观/龙首/其它飞行模组),别的模组同一 tick 也写 true 时我们那一脚就把它踩掉了,
 * 再顺手写 {@code flying = false} / 调 {@code onUpdateAbilities()} 更是直接把玩家踢下飞行。</p>
 *
 * <p>现在改为在 {@code handleFlight} 内部<b>拦住扩展自己的写入</b>(见 {@code RevivalLeafFlightMixin}):
 * 开关关闭时,{@code mayfly} / {@code flying} / {@code onUpdateAbilities()} 这三类写入一处都不执行,
 * 于是根本没有"撤销"这个动作,也就永远踩不到创造模式、旁观模式或其它模组给的飞行。</p>
 *
 * <p>本类只负责:读写开关状态、回答"这一次 {@code handleFlight} 要不要被压制",
 * 以及给 {@link ItemToggleHandler} 提供判定。**不再监听任何事件**。</p>
 */
public final class RevivalLeafStoneHandler {

    /** true = 玩家关掉了"飞行";键不存在 = 允许。 */
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
            return "复苏之叶·飞行";
        }

        @Override
        public boolean toggleAllowed() {
            return true;
        }
    };

    /** 本次 {@code handleFlight} 调用是否被开关压制(fail-open:默认 false = 扩展原行为)。 */
    private static final ThreadLocal<Boolean> SUPPRESS = new ThreadLocal<>();

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

    /**
     * 玩家身上(饰品槽 / 背包 / 副手)是否带着"关掉了飞行"的复苏之叶。
     * <p>旧版只查饰品槽;戴着的时候它不在 {@code getInventory()} 里,但拿在手上时又不在饰品槽,
     * 所以两处都要查,开关才在任何位置都"有反应"。</p>
     */
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
            if (!worn.isEmpty() && isDisabled(worn)) {
                return true;
            }
            for (ItemStack stack : player.getInventory().items) {
                if (isStone(stack) && isDisabled(stack)) {
                    return true;
                }
            }
            for (ItemStack stack : player.getInventory().offhand) {
                if (isStone(stack) && isDisabled(stack)) {
                    return true;
                }
            }
            return false;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 供 RevivalLeafFlightMixin 使用
    // ------------------------------------------------------------------

    /** {@code handleFlight} 的 HEAD:算一次"这次调用要不要压制",整次调用复用。 */
    public static void enterHandleFlight(Player player) {
        try {
            SUPPRESS.set(player != null && flightDisabled(player));
        }
        catch (RuntimeException | LinkageError ignored) {
            SUPPRESS.set(Boolean.FALSE);   // 出错一律放行 = 扩展原行为
        }
    }

    /** {@code handleFlight} 的 RETURN:清掉本次记录(不撤销任何东西)。 */
    public static void exitHandleFlight() {
        try {
            SUPPRESS.remove();
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 本次调用是否跳过扩展对玩家能力({@code mayfly} / {@code flying} / {@code onUpdateAbilities})的写入。 */
    public static boolean suppressed() {
        return Boolean.TRUE.equals(SUPPRESS.get());
    }

    /** 显式写 NBT(供自检/命令使用;正常路径走 {@link ItemToggleHandler})。 */
    public static void setFlightDisabled(ItemStack stack, boolean disabled) {
        try {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            if (disabled) {
                CompoundTag tag = stack.getOrCreateTag();
                tag.putBoolean(NBT_FLIGHT_DISABLED, true);
                return;
            }
            CompoundTag tag = stack.getTag();
            if (tag == null) {
                return;
            }
            tag.remove(NBT_FLIGHT_DISABLED);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}
