package com.hengx.enigmaticenhance;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 只读地查询「古旧书袋」(enigmaticaddons:antique_bag)里有没有某本书。
 * <p>
 * 反编译依据(enigmaticaddons 1.2.5/1.2.6):
 * <ul>
 * <li>书袋本身是普通物品 {@code auviotre.enigmatic.addon.contents.items.AntiqueBag}(extends ItemBase),
 *     右键开一个 12 格 GUI;真正的内容物不在物品 NBT 里,而是挂在**玩家(实体)**上的 capability
 *     {@code AntiqueBagCapability.INVENTORY}({@code IAntiqueBagHandler},内部是 {@code ItemStackHandler(12)}),
 *     随玩家存档保存、Clone 时用 {@code writeTag/readTag} 复制。</li>
 * <li>扩展自己提供了现成的只读查询
 *     {@code auviotre.enigmatic.addon.handlers.SuperAddonHandler.findBookInBag(Player, Item)}:
 *     它先要求玩家身上/末影箱里有古旧书袋({@code SuperpositionHandler.hasItem(...ANTIQUE_BAG)}),再查 capability,
 *     命中就返回那本书的 ItemStack,否则返回空。</li>
 * </ul>
 * 这里**直接复用扩展这个 API**(反射调用,缺扩展时安全返回 false),因此"书袋在身上 + 书在袋里"的判定
 * 与扩展自己的口径完全一致,也不会对书袋/书做任何写入。
 */
public final class BookBag {

    private static Method findBookInBag;
    private static boolean resolved;

    private BookBag() {}

    /** 该物品是否在玩家的古旧书袋里(书袋必须在玩家身上,否则扩展自己也认为"不在袋里")。 */
    public static boolean hasBook(Player player, ResourceLocation itemId) {
        if (player == null || itemId == null) {
            return false;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == Items.AIR) {
            return false;
        }
        ItemStack found = findBookInBag(player, item);
        return found != null && !found.isEmpty();
    }

    /**
     * 4.6.0(内存/CPU):玩家身上有没有古旧书袋 —— 与扩展 {@code findBookInBag} 的<b>前置判定逐字对应</b>。
     *
     * <p>反编译 {@code SuperAddonHandler.findBookInBag(Player, Item)} 的头两句是:
     * <pre>
     * if (!SuperpositionHandler.hasItem(player, EnigmaticAddonItems.ANTIQUE_BAG)
     *     &amp;&amp; !player.getEnderChestInventory().hasAnyOf(Set.of(ANTIQUE_BAG)))
     *     return ItemStack.EMPTY;      // 书袋既不在身上也不在末影箱 ⇒ 一定找不到书
     * </pre>
     * 也就是说"书袋不在(身上 + 末影箱)"是"书一定不在袋里"的充分条件。</p>
     *
     * <p>为什么要这个:护符/书袋 HUD 每 tick 要按<b>16 个物品 id</b>各查一次 {@code findBook},
     * 每次 {@code findBookInBag} 都会重新做一遍"扫背包 + 扫饰品栏 + 扫末影箱"。
     * 书袋根本不在身上的玩家(绝大多数)这 16 次全部白扫。先问一次,判据相同 ⇒ 不改变行为。</p>
     */
    public static boolean hasBag(Player player) {
        if (player == null) {
            return false;
        }
        Item bag = bagItem();
        if (bag == null) {
            return false;
        }
        if (Superposition.legacyHasItem(player, bag)) {
            return true;
        }
        // 末影箱那一路(与扩展同一判据)。集合只建一次:每 tick 每玩家都要问,别每次新建 Set。
        java.util.Set<Item> singleton = bagSingleton;
        return singleton != null && player.getEnderChestInventory().hasAnyOf(singleton);
    }

    /** 书袋物品 + 单元素 Set 的解析结果(懒解析一次;第一次 tick 时注册表早已冻结)。 */
    private static Item bagItem;
    private static java.util.Set<Item> bagSingleton;
    private static boolean bagResolved;

    private static Item bagItem() {
        if (bagResolved) {
            return bagItem;
        }
        bagResolved = true;
        try {
            Item found = ForgeRegistries.ITEMS.getValue(RingConfig.ANTIQUE_BAG_ID);
            if (found != null && found != Items.AIR) {
                bagItem = found;
                bagSingleton = java.util.Set.of(found);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            bagItem = null;
            bagSingleton = null;
        }
        return bagItem;
    }

    /** 1.1.16:返回袋里那本书的 ItemStack(没有/书袋不在身上 → 空)。 */
    public static ItemStack findBook(net.minecraft.world.entity.player.Player player, ResourceLocation itemId) {
        if (player == null || itemId == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return findBookInBag(player, item);
    }

    /**
     * 4.6.1(CPU):已经解析成 {@link Item} 的重载。
     *
     * <p>{@code BagHudEffects#collect(Player)} 在服务端 tick 里对<b>每一名在线玩家</b>按 16 个 id
     * 各查一次书袋,原来每次都要 {@code ForgeRegistries.ITEMS.getValue(itemId)} —— 重算
     * {@code ResourceLocation} 的 hash 再查一次 map(每人每 tick 16 次,10 人服就是 320 次/秒)。
     * 调用方把解析结果缓存下来之后走这个重载,这段开销归零。</p>
     *
     * <p>判定与 {@link #findBook(net.minecraft.world.entity.player.Player, ResourceLocation)} 逐字一致:
     * null / 空气 ⇒ {@link ItemStack#EMPTY},其余走同一个 {@code findBookInBag}。</p>
     */
    public static ItemStack findBook(net.minecraft.world.entity.player.Player player, Item item) {
        if (player == null || item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return findBookInBag(player, item);
    }
    /** 反射调用扩展的 {@code SuperAddonHandler.findBookInBag}。 */
    private static ItemStack findBookInBag(Player player, Item item) {        try {
            Method method = method();
            if (method == null) {
                return ItemStack.EMPTY;
            }
            Object result = method.invoke(null, player, item);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }
        catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static synchronized Method method() {
        if (resolved) {
            return findBookInBag;
        }
        resolved = true;
        try {
            Class<?> handler = Class.forName("auviotre.enigmatic.addon.handlers.SuperAddonHandler");
            findBookInBag = handler.getMethod("findBookInBag", Player.class, Item.class);
        }
        catch (Throwable t) {
            findBookInBag = null;
        }
        return findBookInBag;
    }

    // ------------------------------------------------------------------ 4.8.4:枚举袋内全部书

    /** 扩展的书袋能力: {@code AntiqueBagCapability.INVENTORY}。 */
    private static final String CAPABILITY_CLASS =
        "auviotre.enigmatic.addon.contents.objects.bookbag.AntiqueBagCapability";

    private static Object inventoryCapability;
    private static boolean capabilityResolved;
    private static Method capGetSlots;
    private static Method capGetBook;
    private static Class<?> capHandlerClass;

    /**
     * 4.8.4:**枚举古旧书袋里现有的每一本书**(而不是按白名单一个个问)。
     *
     * <p>为什么要枚举:{@link BagHudEffects} 现在要把"袋里所有书"都列出来(有效果的写效果,
     * 没效果的写「无加成」),白名单只决定文案,不决定列不列。</p>
     *
     * <p>实现与扩展自己一致:拿 {@code AntiqueBagCapability.INVENTORY} 能力 →
     * {@code IAntiqueBagHandler} → {@code getSlots()}/{@code getBook(i)}。
     * 和 {@code com.qoocies.goeticlegacy.utils.enigmaticaddons.EnigmaticAddonsBookBagHelper#getAllItems}
     * 走的是同一套能力,所以诡厄那边认的书袋内容和我们这里的判定完全同源。</p>
     *
     * <p>缺扩展 / 无书袋 / 反编译不匹配 ⇒ 返回空表,绝不抛异常。</p>
     */
    public static List<ItemStack> booksInBag(Player player) {
        if (player == null) {
            return List.of();
        }
        try {
            if (!resolveCapability()) {
                return List.of();
            }
            net.minecraftforge.common.util.LazyOptional<?> optional =
                player.getCapability((net.minecraftforge.common.capabilities.Capability<?>) inventoryCapability);
            if (!optional.isPresent()) {
                return List.of();
            }
            Object handler = optional.orElse(null);
            if (handler == null) {
                return List.of();
            }
            int slots = ((Number) capGetSlots.invoke(handler)).intValue();
            List<ItemStack> out = new ArrayList<>(slots);
            for (int i = 0; i < slots; i++) {
                Object stack = capGetBook.invoke(handler, i);
                if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    out.add(itemStack);
                }
            }
            return out;
        }
        catch (Throwable t) {
            return List.of();
        }
    }

    private static synchronized boolean resolveCapability() {
        if (capabilityResolved) {
            return inventoryCapability != null;
        }
        capabilityResolved = true;
        try {
            Class<?> capClass = Class.forName(CAPABILITY_CLASS);
            inventoryCapability = capClass.getField("INVENTORY").get(null);
            capHandlerClass = Class.forName(
                "auviotre.enigmatic.addon.contents.objects.bookbag.IAntiqueBagHandler");
            capGetSlots = capHandlerClass.getMethod("getSlots");
            capGetBook = capHandlerClass.getMethod("getBook", int.class);
        }
        catch (Throwable t) {
            inventoryCapability = null;
            capGetSlots = null;
            capGetBook = null;
        }
        return inventoryCapability != null;
    }
}
