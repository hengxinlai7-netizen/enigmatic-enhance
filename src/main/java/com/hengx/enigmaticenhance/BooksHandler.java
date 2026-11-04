package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 神秘遗物(Enigmatic Legacy)与其扩展(enigmaticaddons)的「书籍」:物品栏里 Ctrl+左键 禁用/启用,
 * 禁用后手持右键无法翻阅。
 * <p>
 * <b>目标判定</b>(不用任何名字/翻译键模糊匹配):
 * <ol>
 * <li>注册命名空间 ∈ {enigmaticlegacy, enigmaticaddons};</li>
 * <li>且物品类的**类层次**属于"会翻阅的书籍"—— 实测只有两条路径:
 *     <ul>
 *     <li>{@code com.aizistral.enigmaticlegacy.items.TheAcknowledgment}(启示之证,
 *         {@code use()} 里调 {@code PatchouliAPI.openBookGUI});扩展的
 *         {@code auviotre.enigmatic.addon.contents.items.TheBless}(恩惠之典)**继承**它,
 *         所以用 isAssignableFrom 一并覆盖;</li>
 *     <li>{@code vazkii.patchouli.common.item.ItemModBook} 及其子类(Patchouli 原生书)。</li>
 *     </ul>
 *     反例(有意排除,证据见报告):{@code hunter_guidebook}/{@code animal_guidebook}(野猎/兽友指南,
 *     无 use 覆写)、{@code sanguinary_handbook}(血腥狩猎手册,无 use 覆写)、{@code void_tome}
 *     (虚无之书,use 是消耗品效果)、{@code bless_amplifier}(神化之书,无 use 覆写)、
 *     {@code antique_bag}(古旧书袋,开的是背包 GUI 不是书)。</li>
 * </ol>
 * 状态写在物品自身 NBT 上({@value #NBT_DISABLED}),换背包/掉落/重登都保留。
 * 缺模组/缺 Patchouli 时全部安全返回 false。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BooksHandler {

    /** 物品自身 NBT 键:为 true 表示"已禁用翻阅"。 */
    public static final String NBT_DISABLED = "enigmaticenhance:Disabled";

    private static final String NS_LEGACY = "enigmaticlegacy";
    private static final String NS_ADDONS = "enigmaticaddons";

    private static volatile Class<?> acknowledgmentClass;
    private static volatile Class<?> patchouliBookClass;
    private static volatile boolean looked;

    private BooksHandler() {}

    /** 手持右键:已禁用的目标书籍直接取消(ForgeHooks.onItemRightClick 会据此跳过 Item#use,客户端/服务端都生效)。 */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!RingConfig.booksEnabled()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (isTargetBook(stack) && isDisabled(stack)) {
            event.setCanceled(true);
        }
    }

    /** 是否是本功能的目标书籍。 */
    public static boolean isTargetBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            return false;
        }
        String ns = id.getNamespace();
        if (!NS_LEGACY.equals(ns) && !NS_ADDONS.equals(ns)) {
            return false;
        }
        return isBookClass(item.getClass());
    }

    /** 类层次判定(带缓存;类不存在时安全)。 */
    private static boolean isBookClass(Class<?> itemClass) {
        lookup();
        Class<?> book = patchouliBookClass;
        if (book != null && book.isAssignableFrom(itemClass)) {
            return true;
        }
        Class<?> acknowledgment = acknowledgmentClass;
        return acknowledgment != null && acknowledgment.isAssignableFrom(itemClass);
    }

    private static void lookup() {
        if (looked) {
            return;
        }
        looked = true;
        acknowledgmentClass = classOrNull("com.aizistral.enigmaticlegacy.items.TheAcknowledgment");
        patchouliBookClass = classOrNull("vazkii.patchouli.common.item.ItemModBook");
    }

    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name);
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 物品是否被标记为"禁用翻阅"。 */
    public static boolean isDisabled(ItemStack stack) {
        if (stack == null || !stack.hasTag()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(NBT_DISABLED);
    }

    /** 写入/清除禁用标记(清除时若 NBT 空了就一并清掉,避免留下空 tag)。 */
    public static void setDisabled(ItemStack stack, boolean disabled) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (disabled) {
            stack.getOrCreateTag().putBoolean(NBT_DISABLED, true);
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(NBT_DISABLED);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    /** 切换禁用状态,返回切换后的状态(未开启切换或非目标物品时原样返回)。 */
    public static boolean toggle(ItemStack stack) {
        boolean disabled = isDisabled(stack);
        if (!RingConfig.booksEnabled() || !RingConfig.booksToggleEnabled() || !isTargetBook(stack)) {
            return disabled;
        }
        boolean next = !disabled;
        setDisabled(stack, next);
        return next;
    }
}
