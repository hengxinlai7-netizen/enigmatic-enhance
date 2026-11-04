package com.hengx.enigmaticenhance;

import java.lang.reflect.Method;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 对神秘遗物(Enigmatic Legacy)相关逻辑的**纯反射**访问。
 * <p>
 * 全部走反射的原因:主流程(配置/事件/命令)绝不能引用可能缺失的模组类,否则会 NoClassDefFoundError。
 * 这里只用到珍珠自己的 {@code EnchanterPearl#isPresent(Player)} 与神秘遗物提供的两个静态工具方法,
 * 与方法名/描述符无关的类名(类名只在运行时通过物品实例拿),缺模组时一律安全返回 null/false。
 */
public final class PearlCompat {

    private static Method isPresent;
    private static Method mergeEnchantments;
    private static Method maybeApplyEternalBinding;
    private static boolean isPresentFailed;
    private static boolean helperFailed;

    private PearlCompat() {}

    /**
     * 珍珠的原始触发条件 —— 直接调用神秘遗物自己的实现:
     * {@code EnchanterPearl#isPresent(Player) == SuperpositionHandler.isTheCursedOne(player)
     *   && SuperpositionHandler.hasCurio(player, this)}。
     * 即"身为七咒之人(佩戴七咒之戒)且把珍珠装在 Curios 槽里"。
     */
    public static boolean pearlPresent(Player player) {
        if (player == null) {
            return false;
        }
        Item pearl = RingConfig.enchanterPearlItem();
        if (pearl == null) {
            return false;
        }
        try {
            Method m = isPresent;
            if (m == null) {
                if (isPresentFailed) {
                    return false;
                }
                m = pearl.getClass().getMethod("isPresent", Player.class);
                isPresent = m;
            }
            Object result = m.invoke(pearl, player);
            return result instanceof Boolean && (Boolean) result;
        }
        catch (Throwable t) {
            isPresentFailed = true;
            return false;
        }
    }

    /** {@code SuperpositionHandler.mergeEnchantments(ItemStack, ItemStack, boolean, boolean)}。 */
    public static ItemStack mergeEnchantments(ItemStack base, ItemStack other) {
        Object result = invokeHelper("mergeEnchantments", new Class<?>[] {ItemStack.class, ItemStack.class,
            boolean.class, boolean.class}, base, other, Boolean.FALSE, Boolean.FALSE);
        return result instanceof ItemStack ? (ItemStack) result : base;
    }

    /** {@code SuperpositionHandler.maybeApplyEternalBinding(ItemStack)}。 */
    public static ItemStack maybeApplyEternalBinding(ItemStack stack) {
        Object result = invokeHelper("maybeApplyEternalBinding", new Class<?>[] {ItemStack.class}, stack);
        return result instanceof ItemStack ? (ItemStack) result : stack;
    }

    private static Object invokeHelper(String name, Class<?>[] types, Object... args) {
        if (helperFailed) {
            return null;
        }
        try {
            Method m = "mergeEnchantments".equals(name) ? mergeEnchantments : maybeApplyEternalBinding;
            if (m == null) {
                Class<?> handler = Class.forName("com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler");
                m = handler.getMethod(name, types);
                if ("mergeEnchantments".equals(name)) {
                    mergeEnchantments = m;
                }
                else {
                    maybeApplyEternalBinding = m;
                }
            }
            return m.invoke(null, args);
        }
        catch (Throwable t) {
            helperFailed = true;
            return null;
        }
    }

    /** 反射是否已经失败过(自检/诊断用)。 */
    public static boolean reflectionFailed() {
        return isPresentFailed || helperFailed;
    }
}
