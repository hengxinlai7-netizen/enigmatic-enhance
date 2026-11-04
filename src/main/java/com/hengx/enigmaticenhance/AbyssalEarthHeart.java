package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「深渊侵蚀的大地之心」(1.2.2)。
 *
 * <h2>为什么不是一个新物品</h2>
 * 用户要求:它「本质上和原版受污染的大地之心一样,都是大地之心 + 一个特殊标签」。反编译核对过这个说法,
 * 本体确实如此({@code EnigmaticLegacy 2.30.1},{@code com/aizistral/enigmaticlegacy/api/items/ITaintable.class}):
 * <pre>
 * ITaintable#handleTaintable(ItemStack, Player)      // EarthHeart#m_6883_ 每 tick 调用
 *   7: getstatic/invokestatic SuperpositionHandler.isTheCursedOne(Player)   // 戴七咒之戒
 *  17..21: getBoolean(stack, "isTainted", false) / setBoolean(stack, "isTainted", true)
 *  27..41: 不满足条件时把它清回 false
 * ITaintable#isTainted(ItemStack) -> getBoolean(stack, "isTainted", false)
 * </pre>
 * 所以「受污染的大地之心」= {@code enigmaticlegacy:earth_heart} + {@code isTainted:1b},没有独立物品 id。
 * 本类沿用同一结构,但用**我们自己的**键 {@link #TAG},与 {@code isTainted} 完全平行:我们**只读**它,绝不写它。
 *
 * <h2>为什么用同一个物品</h2>
 * 物品 id 不变,意味着所有认 {@code enigmaticlegacy:earth_heart} 的配方(本体的无尽食物、采矿护符,
 * 以及其它模组里以大地之心为材料的配方)都会自动接受它 —— 这正是「可以当受污染的用」的一半含义。
 * 另一半(本体用 {@code crafting_shaped_cursed} 类型的那几条,例如扭曲之心)需要
 * {@code mixin/CursedShapedRecipeTaintMixin} 把我们的标签也算作受污染,见那边的注释。
 *
 * <h2>「受污染的不能当深渊侵蚀的用」怎么保证</h2>
 * 两个键互不写改:{@link #isAbyssalTainted} 只看 {@link #TAG},所以一颗只被七咒之戒污染过的心
 * ({@code isTainted:1b} 且没有 {@link #TAG})永远不算深渊侵蚀态。
 */
public final class AbyssalEarthHeart {

    /** 我们的标签键(布尔)。刻意用与本体不同的名字,永不与 {@code isTainted} 混用。 */
    public static final String TAG = "abyssalTaint";

    /** 本体「受污染」用的键。本类**只读**它,写它是本体自己的事(见 {@code ITaintable#handleTaintable})。 */
    public static final String EL_TAINT_TAG = "isTainted";

    /** 每名玩家多久检查一次背包(游戏刻;5 刻 = 0.25 秒,肉眼是"刚放进去就变了")。 */
    public static final int SCAN_INTERVAL_TICKS = 5;

    /** 触发物品集合的缓存(配方会在服务端启动 / 数据包重载时被换掉,所以连 RecipeManager 一起比对)。 */
    private static Set<Item> cachedTriggers;
    private static RecipeManager cachedRecipeManager;

    private AbyssalEarthHeart() {}

    // ------------------------------------------------------------------ 标签读写

    /** 是否是「深渊侵蚀」态(只看我们自己的键)。 */
    public static boolean isAbyssalTainted(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(TAG);
    }

    /**
     * 本体「受污染」态(只读)。
     * <p>
     * 直接读键而不是反射调用 {@code ITaintable#isTainted}:省一次反射、也不受接口变动影响,
     * 语义与上面那段字节码完全一致。
     */
    public static boolean elTainted(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(EL_TAINT_TAG);
    }

    /**
     * 打上深渊侵蚀标记。
     *
     * @return {@code true} = 这次真的改了;已经是侵蚀态时返回 {@code false}(不做多余的写,避免每 tick 抖动同步)
     */
    public static boolean markAbyssalTainted(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isAbyssalTainted(stack)) {
            return false;
        }
        stack.getOrCreateTag().putBoolean(TAG, true);
        return true;
    }

    /**
     * 「能不能当受污染的大地之心用」的判定:本体说是受污染,或者我们打了深渊侵蚀标记。
     * <p>
     * 本体那边的结果由调用方传入({@code mixin/CursedShapedRecipeTaintMixin} 直接把
     * {@code ITaintable#isTainted} 的返回值递进来),本类因此不需要引用任何神秘遗物的类型。
     */
    public static boolean countsAsTainted(boolean elTainted, ItemStack stack) {
        return elTainted || isAbyssalTainted(stack);
    }

    // ------------------------------------------------------------------ 触发来源

    /**
     * 触发来源 = 深渊之心本体 + 用深渊之心做出来的东西 + 配置里追加的 id。
     * <p>
     * 「做出来的东西」不写死清单:直接遍历已加载的配方,凡是有某个材料能匹配深渊之心、产物不是空物品的,
     * 产物都算进来。这样本体、扩展、以及别的模组(实例里 {@code enigmaticaddons} 的混沌鞘翅/暴戾卷轴、
     * {@code moonstone} 的腐坏之心、{@code toward_deeper_abyss} 的龙心罐等)的配方都自动覆盖,
     * 模组更新也不需要改代码。
     * <p>
     * 结果按 {@link RecipeManager} 实例缓存:数据包重载后 RecipeManager 会换新实例,缓存自然失效。
     */
    public static Set<Item> triggerItems(MinecraftServer server) {
        Item abyssal = RingConfig.abyssalHeartItem();
        if (abyssal == null) {
            return Set.of();
        }
        RecipeManager manager = server == null ? null : server.getRecipeManager();
        if (cachedTriggers != null && cachedRecipeManager == manager) {
            return cachedTriggers;
        }

        Set<Item> out = new LinkedHashSet<>();
        out.add(abyssal);
        if (server != null && RingConfig.abyssalEarthHeartAutoTriggers()) {
            out.addAll(deriveFromRecipes(server, abyssal));
        }
        for (String raw : RingConfig.abyssalEarthHeartExtraTriggers()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(raw.trim());
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) {
                out.add(item);
            }
        }

        cachedTriggers = Collections.unmodifiableSet(out);
        cachedRecipeManager = manager;
        return cachedTriggers;
    }

    /** 清掉触发来源缓存(自检里改配置 / 换维度后用)。 */
    public static void invalidateTriggers() {
        cachedTriggers = null;
        cachedRecipeManager = null;
    }

    /** 遍历全部配方,挑出"以深渊之心为材料"的产物。单个配方抛异常只跳过它。 */
    private static Set<Item> deriveFromRecipes(MinecraftServer server, Item abyssal) {
        Set<Item> out = new LinkedHashSet<>();
        ItemStack probe = new ItemStack(abyssal);
        RegistryAccess access = server.registryAccess();
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            try {
                boolean usesAbyssal = false;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient != null && ingredient.test(probe)) {
                        usesAbyssal = true;
                        break;
                    }
                }
                if (!usesAbyssal) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(access);
                if (result != null && !result.isEmpty()) {
                    out.add(result.getItem());
                }
            }
            catch (Throwable ignored) {
                // 第三方配方的 getIngredients() 偶有实现问题:只跳过这一条,不影响其它配方
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 转化

    /**
     * 对一名玩家做一轮检查:只要「背包(含快捷栏)/ 盔甲栏 / 副手 / 饰品栏」里任意一处有来源物品,
     * 就把它手上所有的大地之心打上深渊侵蚀标记。
     *
     * @return 这一轮真正被标记的物品数量(0 表示什么也没发生)
     */
    public static int convertPlayer(Player player) {
        if (player == null || player.level() == null || player.level().isClientSide()) {
            return 0;
        }
        if (!RingConfig.abyssalEarthHeartEnabled()) {
            return 0;
        }
        Item heart = RingConfig.earthHeartItem();
        if (heart == null) {
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        Set<Item> triggers = triggerItems(server);
        if (triggers.isEmpty()) {
            return 0;
        }

        List<ItemStack> stacks = collect(player);
        boolean sourcePresent = false;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && triggers.contains(stack.getItem())) {
                sourcePresent = true;
                break;
            }
        }
        if (!sourcePresent) {
            return 0;
        }

        int changed = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.is(heart) && markAbyssalTainted(stack)) {
                ++changed;
                DebugLog.info("{} {} 的大地之心被深渊侵蚀(来源 {} 个物品可选)", AbyssalEarthHeartHandler.LOG_TAG,
                    player.getName().getString(), triggers.size());
            }
        }
        if (changed > 0) {
            // 键改了就必须让客户端那边的物品栏菜单重新对一次,否则 tooltip 要等下次背包变化才更新。
            player.getInventory().setChanged();
            if (player.inventoryMenu != null) {
                player.inventoryMenu.broadcastChanges();
            }
        }
        return changed;
    }

    /**
     * 取「玩家的全部随身物品」:背包 36 格(含快捷栏)+ 盔甲 4 格 + 副手 1 格 + 饰品栏全部槽位。
     * <p>
     * 返回的是**引用**(不是副本),调用方可以直接改 NBT。缺 Curios 时饰品栏一段直接跳过。
     */
    public static List<ItemStack> collect(Player player) {
        List<ItemStack> out = new ArrayList<>(48);
        if (player == null) {
            return out;
        }
        Inventory inventory = player.getInventory();
        out.addAll(inventory.items);
        out.addAll(inventory.armor);
        out.addAll(inventory.offhand);
        if (ModList.get().isLoaded("curios")) {
            try {
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                    for (var entry : handler.getCurios().entrySet()) {
                        var handlerStacks = entry.getValue().getStacks();
                        for (int i = 0; i < handlerStacks.getSlots(); i++) {
                            out.add(handlerStacks.getStackInSlot(i));
                        }
                    }
                });
            }
            catch (Throwable ignored) {
                // NoClassDefFoundError(缺 Curios)/ 能力未就绪:当作没有饰品栏
            }
        }
        return out;
    }
}
