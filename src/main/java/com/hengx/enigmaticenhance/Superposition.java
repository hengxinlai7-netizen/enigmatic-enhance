package com.hengx.enigmaticenhance;

import java.lang.reflect.Method;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 与神秘遗物本体的联动查询(反射,缺模组时安全返回 false)。
 *
 * <h2>4.6.0(内存/CPU):反射结果必须缓存</h2>
 * 原来这里每次调用都写 {@code Class.forName(...)} + {@code getMethod(...)}:
 * <ul>
 *   <li>{@link #hasCurio} 被 {@link GluttonyHandler#wearsCharm} 在 <b>PlayerTickEvent 里每 tick、两端</b>调用;</li>
 *   <li>{@code BlessRingTickMixin}(救赎之戒的 curioTick,也是每 tick)会连着调
 *       {@code isPunishedOne} + {@code hasCurio} ×2;</li>
 *   <li>{@code Superposition.getCurioStack} 在受击/挖掘路径上被调。</li>
 * </ul>
 * {@code Class.forName} 是一次类加载器查找(失败还会抛/接异常),{@code getMethod} 要扫父类的 public 方法数组
 * 并复制结果 —— 单次大约几百字节 + 微秒级 CPU,乘以 20 tps × 玩家数就是每秒几十次纯浪费,
 * 而且这些垃圾全部是短命对象,直接推高 Young GC 频率。
 * <p>
 * 现在只在第一次用到时解析一次,之后只 {@code Method.invoke}(不分配,除非参数装箱)。
 * 语义完全不变:没装神秘遗物时解析结果就是 null,所有方法照旧返回 false / {@link ItemStack#EMPTY}。</p>
 */
public final class Superposition {

    private static final String HANDLER = "com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler";
    private static final String ADDON = "auviotre.enigmatic.addon.handlers.SuperAddonHandler";

    /** 解析结果(只解析一次;失败保持 null,语义 = "模组不在"). */
    private static Method hasCurioMethod;
    private static Method getCurioStackMethod;
    private static Method isPunishedOneMethod;
    private static Method legacyHasItemMethod;
    private static boolean resolved;

    private Superposition() {}

    /** 解析一次反射入口(幂等、可重入安全)。 */
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> handler = Class.forName(HANDLER);
            hasCurioMethod = handler.getMethod("hasCurio", LivingEntity.class, Item.class);
            getCurioStackMethod = handler.getMethod("getCurioStack", LivingEntity.class, Item.class);
            legacyHasItemMethod = handler.getMethod("hasItem", Player.class, Item.class);
        }
        catch (Throwable t) {
            hasCurioMethod = null;
            getCurioStackMethod = null;
            legacyHasItemMethod = null;
        }
        try {
            Class<?> addon = Class.forName(ADDON);
            isPunishedOneMethod = addon.getMethod("isPunishedOne", LivingEntity.class);
        }
        catch (Throwable t) {
            isPunishedOneMethod = null;
        }
    }

    /**
     * 取实体饰品栏里某个物品的 ItemStack(反射查 {@code SuperpositionHandler.getCurioStack})。
     * 没戴 / 缺模组时返回 {@link ItemStack#EMPTY},不抛异常。
     */
    public static ItemStack getCurioStack(LivingEntity entity, Item item) {
        if (entity == null || item == null) {
            return ItemStack.EMPTY;
        }
        resolve();
        Method method = getCurioStackMethod;
        if (method == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object result = method.invoke(null, entity, item);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }
        catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 饰品栏是否佩戴(curios)。 */
    public static boolean hasCurio(LivingEntity entity, Item item) {
        if (entity == null || item == null) {
            return false;
        }
        resolve();
        Method method = hasCurioMethod;
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(null, entity, item);
            return result instanceof Boolean && (Boolean) result;
        }
        catch (Throwable t) {
            return false;
        }
    }

    /**
     * 直接调用本体自己的 {@code SuperpositionHandler.hasItem(Player, Item)}(口径与本体完全一致,
     * 含它对末影箱/饰品栏的处理),而不是本类 {@link #hasItem} 那个近似实现。
     *
     * <p>4.6.0:给 {@link BookBag#hasBag} 的一次性前置判定用 —— 扩展的
     * {@code SuperAddonHandler.findBookInBag} 内部就是先调它,所以"它为 false ⇒ findBookInBag 必为空"
     * 是严格等价的,但省掉了 16 次重复的背包/饰品栏扫描。缺模组时返回 false。</p>
     */
    public static boolean legacyHasItem(Player player, Item item) {
        if (player == null || item == null) {
            return false;
        }
        resolve();
        Method method = legacyHasItemMethod;
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(null, player, item);
            return result instanceof Boolean && (Boolean) result;
        }
        catch (Throwable t) {
            return false;
        }
    }

    /** 饰品栏 / 双手 / 背包 任一位置。 */
    public static boolean hasItem(Player player, Item item) {
        if (player == null || item == null) {
            return false;
        }
        if (hasCurio(player, item)) {
            return true;
        }
        if (player.getMainHandItem().is(item) || player.getOffhandItem().is(item)) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }
    /**
     * 扩展自己的"被惩罚者"判定:SuperAddonHandler.isPunishedOne = hasBlessRing && isTheCursedOne。
     * 其中 hasBlessRing = 玩家持久数据含 BlessNextSpawn **或** 佩戴救赎之戒 ——
     * 所以只在饰品栏找戒指会漏判(戒指已经被清/在背包时),必须直接问它。
     */
    public static boolean isPunishedOne(net.minecraft.world.entity.LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        resolve();
        Method method = isPunishedOneMethod;
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(null, entity);
            return result instanceof Boolean && (Boolean) result;
        }
        catch (Throwable t) {
            return false;
        }
    }
}
