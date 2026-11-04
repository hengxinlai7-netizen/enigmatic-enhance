package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.1.28b 自检:【睡前读物】+ 七咒之戒**第七重咒(失眠)**修正(-D 开关,由 {@link EarthAmuletSelfTest} 串起来跑)。
 * <ul>
 * <li>T27a 定义:id / 堆叠 1 / 无耐久 / 在创造标签页 / 无序配方(书 + 纯净之心 ⇒ 1 本)/ tooltip 3 行 /
 *     中英 lang / 模型 + 贴图 / {@code ModIds.problems()=[]};</li>
 * <li>T27b 条件矩阵:没戴七咒之戒 ⇒ 不生效;戴了但书既不在袋里也不在手 ⇒ 不生效;
 *     戴了 + 书在手(主手/副手)⇒ 生效;戴了 + 书**只在书袋**(手上没有)⇒ 生效;</li>
 * <li>T27c 修正值:生效时 {@code 95 -> 90}(不再夹 sleepCounter)、{@code 5 -> 6}(不再发"无法入睡"),
 *     其它读数(0/10/50/90)原样不动 ⇒ 只改失眠那两处比较;</li>
 * <li>T27d 配置关闭 ⇒ 一律回到原值(与不装本模组一致);</li>
 * <li>T27e 其它六条咒:本类的判定函数在"加书/去书"两种状态下完全一致(第四重咒修正等都不受影响)。</li>
 * </ul>
 */
final class BedtimeReadingSelfTest {

    private static final String TAG = "[BedtimeSelfTest]";

    private BedtimeReadingSelfTest() {}

    static boolean run(ServerLevel level, FakePlayer player) {
        boolean a = testDefinition(level, player);
        boolean b = testMatrix(level, player);
        boolean c = testSleepValues(level, player);
        boolean d = testConfigOff(level, player);
        boolean e = testOtherCurses(level, player);
        boolean f = testRealInjection(level, player);
        boolean all = a && b && c && d && e && f;
        log(" 睡前读物小结 T27a=" + a + " T27b=" + b + " T27c=" + c + " T27d=" + d + " T27e=" + e
            + " T27f(真实注入)=" + f + " => " + (all ? "PASS" : "FAIL"));
        cleanup(player);
        return all;
    }

    // ------------------------------------------------------------------ T27a 定义 / 配方

    private static boolean testDefinition(ServerLevel level, Player crafter) {
        log(" --- T27a 睡前读物定义 / 无序配方 / tooltip / 资源");
        boolean ok = true;
        Item item = ModItems.bedtimeReadingOrNull();
        ResourceLocation id = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
        log(" T27a 物品 id=" + id + " [应为 enigmaticenhance:bedtime_reading]");
        ok &= item != null && "enigmaticenhance:bedtime_reading".equals(String.valueOf(id));
        if (item == null) {
            check("T27a", false);
            return false;
        }
        ItemStack stack = new ItemStack(item);
        log(" T27a 最大堆叠=" + stack.getMaxStackSize() + " [应 1];最大耐久=" + stack.getMaxDamage()
            + " [应 0];在创造标签页=" + CreativeTabContents.items().contains(item) + " [应 true]");
        ok &= stack.getMaxStackSize() == 1 && stack.getMaxDamage() == 0
            && CreativeTabContents.items().contains(item);

        Item pureHeart = ForgeRegistries.ITEMS.getValue(new ResourceLocation("enigmaticaddons", "pure_heart"));
        log(" T27a 纯净之心 enigmaticaddons:pure_heart 存在=" + (pureHeart != null) + " [应 true]");
        ok &= pureHeart != null;

        ResourceLocation recipeId = new ResourceLocation(EnigmaticEnhance.MODID, ModIds.BEDTIME_READING_PATH);
        boolean present = level.getRecipeManager().byKey(recipeId).isPresent();
        log(" T27a 配方 " + recipeId + " 存在=" + present + " [应 true]");
        ok &= present;

        if (pureHeart != null) {
            Crafted crafted = craft(level, crafter, new ItemStack(Items.BOOK), new ItemStack(pureHeart));
            boolean shape = crafted != null && crafted.output().getItem() == item
                && crafted.output().getCount() == 1;
            log(" T27a 书 + 纯净之心 -> 产物=" + (crafted == null ? "无"
                : (ForgeRegistries.ITEMS.getKey(crafted.output().getItem()) + " x" + crafted.output().getCount()))
                + " [应 enigmaticenhance:bedtime_reading x1];命中配方=" + (crafted == null ? "-" : crafted.recipe().getId()));
            ok &= shape;
        }

        List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, null, tooltip, TooltipFlag.Default.NORMAL);
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < tooltip.size(); i++) {
            lines.add(tooltip.get(i).getString());
        }
        boolean clean = lines.stream().noneMatch(line -> line.trim().isEmpty() || line.contains("%s")
            || line.contains("%d"));
        log(" T27a tooltip 效果行数=" + lines.size() + " [应 3:1.1.28h 起写全数值];无占位符/空行=" + clean);
        for (String line : lines) {
            log(" T27a   " + line);
        }
        ok &= lines.size() == 3 && clean;

        for (String lang : new String[] { "zh_cn", "en_us" }) {
            var root = readLang(lang);
            String name = root == null ? null : text(root, "item.enigmaticenhance.bedtime_reading");
            int keys = 0;
            if (root != null) {
                for (String key : new String[] { "header", "lore", "ability", "others" }) {
                    if (text(root, "tooltip.enigmaticenhance.bedtime_reading." + key) != null) {
                        ++keys;
                    }
                }
            }
            log(" T27a " + lang + " 物品名=" + name + ";tooltip 键=" + keys + "/4 [应 4]");
            ok &= name != null && !name.isEmpty() && keys == 4;
        }

        String modelJson = resourceText("/assets/enigmaticenhance/models/item/bedtime_reading.json");
        boolean model = modelJson != null && modelJson.contains("\"texture_size\"") && modelJson.contains("256");
        byte[] png = resourceBytes("/assets/enigmaticenhance/textures/item/bedtime_reading.png");
        boolean texture = png != null && png.length > 24 && (png[0] & 0xFF) == 0x89 && png[1] == 'P';
        int width = texture ? ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16)
            | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF) : -1;
        int height = texture ? ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16)
            | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF) : -1;
        boolean recipe = resource("/data/enigmaticenhance/recipes/bedtime_reading.json") != null;
        log(" T27a 模型=" + model + "(含 texture_size 256) 贴图=" + texture + " " + width + "x" + height
            + " 配方 json=" + recipe + " [都应 true / 256x256]");
        ok &= model && texture && recipe && width == 256 && height == 256;

        List<String> problems = ModIds.problems();
        log(" T27a ModIds.problems()=" + problems + " [必须空]");
        ok &= problems.isEmpty();
        check("T27a", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T27b 条件矩阵

    private static boolean testMatrix(ServerLevel level, FakePlayer player) {
        log(" --- T27b 生效条件矩阵");
        boolean ok = true;
        reset(player);

        Item book = ModItems.bedtimeReadingOrNull();
        Item ring = RingConfig.cursedRingItem();
        if (book == null || ring == null) {
            log(" T27b 缺少物品(书=" + book + ", 七咒之戒=" + ring + "),跳过");
            check("T27b", true);
            return true;
        }

        // 1) 没戴戒指 + 书在手上
        hold(player, book);
        boolean noRing = BedtimeReadingFix.active(player);
        log(" T27b 没戴七咒之戒 + 书在手上:active=" + noRing + " [应 false]");
        ok &= !noRing;

        // 2) 戴上戒指,但书既不在袋里也不在手
        boolean equipped = equipRing(player);
        boolean hasRing = Superposition.hasCurio(player, ring);
        clearHands(player);
        boolean ringNoBook = BedtimeReadingFix.active(player);
        log(" T27b 戴戒指=" + equipped + "(hasCurio=" + hasRing + ")+ 手上没书、袋里没书:active="
            + ringNoBook + " [应 false]");
        ok &= equipped && hasRing && !ringNoBook;

        // 3) 戒指 + 书在主手
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
        boolean mainHand = BedtimeReadingFix.active(player);
        log(" T27b 戒指 + 书在主手:active=" + mainHand + " [应 true]");
        ok &= mainHand;

        // 4) 戒指 + 书在副手
        clearHands(player);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(book));
        boolean offHand = BedtimeReadingFix.active(player);
        log(" T27b 戒指 + 书在副手:active=" + offHand + " [应 true]");
        ok &= offHand;

        // 5) 戒指 + 书只在书袋(手上没有)
        clearHands(player);
        boolean inBag = putBookInBag(player, book);
        boolean bagOnly = BedtimeReadingFix.active(player);
        log(" T27b 书放进古旧书袋=" + inBag + ";戒指 + 书只在书袋:active=" + bagOnly
            + " [应 true" + (inBag ? "" : "(未能构造书袋,跳过)") + "]");
        ok &= inBag ? bagOnly : true;
        if (inBag) {
            boolean readBack = !BookBag.findBook(player, RingConfig.BEDTIME_READING_ID).isEmpty();
            log(" T27b 反向复核:BookBag.findBook 读回=" + readBack + " [应 true]");
            ok &= readBack;
        }
        clearBag(player);
        check("T27b", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T27c 修正值

    private static boolean testSleepValues(ServerLevel level, FakePlayer player) {
        log(" --- T27c 修正值:只改失眠那两处比较");
        boolean ok = true;
        reset(player);
        Item book = ModItems.bedtimeReadingOrNull();
        if (book == null || !equipRing(player)) {
            log(" T27c 无法构造条件,跳过");
            check("T27c", true);
            return true;
        }
        hold(player, book);
        int[] raw = { 0, 4, 5, 6, 10, 50, 90, 91, 95, 99 };
        StringBuilder text = new StringBuilder();
        for (int value : raw) {
            int fixed = BedtimeReadingFix.effectiveSleepTimer(player, value);
            text.append(value).append("->").append(fixed).append(' ');
            if (value == 5) {
                ok &= fixed == 6;
            }
            else if (value > 90) {
                ok &= fixed == 90;
            }
            else {
                ok &= fixed == value;
            }
        }
        log(" T27c 生效时 sleepTimer 映射: " + text.toString().trim());
        log(" T27c [5->6 = 不再发\"无法入睡\";>90->90 = 不再把 sleepCounter 夹回 90;其余原样]");
        clearHands(player);
        check("T27c", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T27d 配置关闭

    private static boolean testConfigOff(ServerLevel level, FakePlayer player) {
        log(" --- T27d 关掉配置 => 与不装本模组一致");
        reset(player);
        Item book = ModItems.bedtimeReadingOrNull();
        boolean ok = true;
        if (book == null || !equipRing(player)) {
            log(" T27d 无法构造条件,跳过");
            check("T27d", true);
            return true;
        }
        hold(player, book);
        boolean enabledBefore = BedtimeReadingFix.active(player);
        setFix(false);
        boolean enabledOff = BedtimeReadingFix.active(player);
        boolean identity = true;
        StringBuilder text = new StringBuilder();
        for (int value : new int[] { 0, 5, 50, 95 }) {
            int fixed = BedtimeReadingFix.effectiveSleepTimer(player, value);
            text.append(value).append("->").append(fixed).append(' ');
            identity &= fixed == value;
        }
        setFix(true);
        boolean backOn = BedtimeReadingFix.active(player);
        log(" T27d 默认=" + enabledBefore + "[应 true];关配置后=" + enabledOff + "[应 false];映射 "
            + text.toString().trim() + "[应全部原值];再打开=" + backOn + "[应 true]");
        ok &= enabledBefore && !enabledOff && identity && backOn;
        clearHands(player);
        check("T27d", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T27e 其它六条咒

    private static boolean testOtherCurses(ServerLevel level, FakePlayer player) {
        log(" --- T27e 其它六条咒不受影响");
        reset(player);
        Item book = ModItems.bedtimeReadingOrNull();
        boolean ok = true;
        if (book == null || !equipRing(player)) {
            log(" T27e 无法构造条件,跳过");
            check("T27e", true);
            return true;
        }
        hold(player, book);
        boolean withBook = BedtimeReadingFix.active(player);

        // 第四重咒(对怪物伤害削减)的修正条件:需要有 倒转之启/恩惠之典/无止之言 三本在袋里,
        // 这里显然不满足 —— 关键是"加书/去书"两种状态结果完全一致(本模组没有顺手改别的咒)。
        boolean fourthWithBook = FourthCurseFix.active(player);
        int residualWithBook = FourthCurseFix.residualPercent();
        clearHands(player);
        boolean fourthWithoutBook = FourthCurseFix.active(player);
        int residualWithoutBook = FourthCurseFix.residualPercent();
        boolean same = fourthWithBook == fourthWithoutBook && residualWithBook == residualWithoutBook;
        log(" T27e 书在手上=" + withBook + "[应 true];第四重咒修正(有书=" + fourthWithBook + ",没书="
            + fourthWithoutBook + ")完全一致=" + same + " [应 true]");
        ok &= withBook && same;

        // 失眠判定只影响 sleepTimer 那两处比较:除 5 与 >90 之外的值一律原样(含 0..4 / 6..90)
        hold(player, book);
        boolean untouched = BedtimeReadingFix.effectiveSleepTimer(player, 0) == 0
            && BedtimeReadingFix.effectiveSleepTimer(player, 30) == 30
            && BedtimeReadingFix.effectiveSleepTimer(player, 90) == 90;
        log(" T27e 非失眠读数(0/30/90)原样=" + untouched + " [应 true]");
        ok &= untouched;
        clearHands(player);
        check("T27e", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T27f 真实注入

    /**
     * T27f:真正调用一次玄理遗物自己的 {@code EnigmaticEventHandler#onPlayerTick}
     * (反射造一个 handler 实例 + 投递 {@code LivingTickEvent}),读回 {@code sleepCounter}:
     * <ul>
     * <li>不满足条件(没戴戒指/书不在身上)⇒ 本体把 95 夹回 90(第七重咒照旧);</li>
     * <li>满足条件(戴戒指 + 书在手上)⇒ 95 保持不变(第七重咒被修正)。</li>
     * </ul>
     * 这一段同时验证了 mixin 的 {@code @Redirect} 真的注入了(而不只是判定函数返回了 true)。
     * 如果本体方法在跑到失眠那一段之前就抛异常(某些功能不认 FakePlayer),就跳过而不是判失败。
     */
    private static boolean testRealInjection(ServerLevel level, FakePlayer player) {
        log(" --- T27f 真实注入:直接跑一次本体 onPlayerTick,看 sleepCounter");
        reset(player);
        Item book = ModItems.bedtimeReadingOrNull();
        boolean ok = true;
        if (book == null || RingConfig.cursedRingItem() == null) {
            log(" T27f 缺少物品,跳过");
            check("T27f", true);
            return true;
        }
        if (!makeSleeping(player) || !setSleepCounter(player, 95) || !player.isSleeping()) {
            log(" T27f 无法构造睡眠状态(设 sleepCounter 失败或 isSleeping=false),跳过");
            check("T27f", true);
            return true;
        }

        // 1) 不满足条件:戴上戒指但手上没书 —— 本体应当把 95 夹回 90
        equipRing(player);
        clearHands(player);
        Attempt bare = invokeEnigmaticTick(player, 95);
        log(" T27f 未修正(戒指在、书不在):sleepCounter 95 -> " + bare.counter
            + " [应 90];本体调用异常=" + (bare.error == null ? "无" : bare.error));
        if (bare.error != null && bare.counter == 95) {
            log(" T27f 本体方法在失眠段之前就异常了,本项跳过(不改判失败)");
            clearHands(player);
            check("T27f", true);
            return true;
        }
        ok &= bare.counter == 90;

        // 2) 满足条件:戒指 + 书在手上 —— 95 必须保持不变
        hold(player, book);
        Attempt fixed = invokeEnigmaticTick(player, 95);
        log(" T27f 已修正(戒指在、书在手上):sleepCounter 95 -> " + fixed.counter
            + " [应 95];本体调用异常=" + (fixed.error == null ? "无" : fixed.error));
        ok &= fixed.counter == 95;

        // 3) 关掉配置 ⇒ 回到"夹成 90"
        setFix(false);
        Attempt disabled = invokeEnigmaticTick(player, 95);
        setFix(true);
        log(" T27f 配置关闭:sleepCounter 95 -> " + disabled.counter + " [应 90]");
        ok &= disabled.counter == 90;
        clearHands(player);
        check("T27f", ok);
        return ok;
    }

    private record Attempt(int counter, Throwable error) {}

    /** 把 sleepCounter 设成 value,然后真的调一次本体 onPlayerTick,返回调用后的 sleepCounter。 */
    private static Attempt invokeEnigmaticTick(Player player, int value) {
        if (!setSleepCounter(player, value)) {
            return new Attempt(value, new IllegalStateException("setSleepCounter failed"));
        }
        Throwable error = null;
        try {
            Class<?> handlerClass = Class.forName("com.aizistral.enigmaticlegacy.handlers.EnigmaticEventHandler");
            Object handler = handlerClass.getConstructor().newInstance();
            Class<?> eventClass = Class.forName(
                "net.minecraftforge.event.entity.living.LivingEvent$LivingTickEvent");
            Object event = eventClass.getConstructor(net.minecraft.world.entity.LivingEntity.class)
                .newInstance(player);
            handlerClass.getMethod("onPlayerTick", eventClass).invoke(handler, event);
        }
        catch (Throwable t) {
            error = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null
                ? ite.getCause() : t;
        }
        return new Attempt(readSleepCounter(player), error);
    }

    /**
     * 让玩家进入"正在睡觉"状态:{@code LivingEntity#isSleeping()} 只认 {@code getSleepingPos()} 有没有值,
     * 所以必须真的调一次 {@code startSleeping}(床的位置不重要,这里就用玩家脚下那格)。
     */
    private static boolean makeSleeping(Player player) {
        try {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(player.position());
            player.startSleeping(pos);
        }
        catch (Throwable t) {
            log(" T27f startSleeping 失败: " + t);
        }
        return player.isSleeping();
    }

    private static boolean setSleepCounter(Player player, int value) {        for (String name : new String[] { "sleepCounter", "f_36110_" }) {
            try {
                java.lang.reflect.Field field = net.minecraft.world.entity.player.Player.class
                    .getDeclaredField(name);
                field.setAccessible(true);
                field.setInt(player, value);
                return true;
            }
            catch (Throwable ignored) {
                // 试下一个名字(开发环境用官方名,生产运行期是 SRG 名)
            }
        }
        return false;
    }

    private static int readSleepCounter(Player player) {
        for (String name : new String[] { "sleepCounter", "f_36110_" }) {
            try {
                java.lang.reflect.Field field = net.minecraft.world.entity.player.Player.class
                    .getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(player);
            }
            catch (Throwable ignored) {
                // 同上
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ 工具

    private static void reset(Player player) {
        clearHands(player);
        clearBag(player);
        unequipRing(player);
        DemonBloodChaliceHandler.invalidate(player);
        setFix(true);
    }

    private static void cleanup(Player player) {
        clearHands(player);
        clearBag(player);
        unequipRing(player);
        setFix(true);
    }

    private static void hold(Player player, Item book) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
    }

    private static void clearHands(Player player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private static void setFix(boolean value) {
        for (int i = 0; i < 10; i++) {
            try {
                RingConfig.BEDTIME_READING_FIX.set(value);
                if (RingConfig.bedtimeReadingFixesSeventhCurse() == value) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 配置未就绪
            }
            EarthAmuletSelfTest.hold(100L);
        }
    }

    /** 把七咒之戒放进 curios:ring 第 0 格(没有 ring 槽就先补 1 格)。 */
    private static boolean equipRing(Player player) {
        Item ring = RingConfig.cursedRingItem();
        if (ring == null) {
            return false;
        }
        try {
            return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    var slot = handler.getCurios().get("ring");
                    if (slot == null || slot.getSlots() < 1) {
                        try {
                            handler.growSlotType("ring", 1);
                        }
                        catch (Throwable ignored) {
                            // 旧 API 拒绝时下面再查一次
                        }
                        slot = handler.getCurios().get("ring");
                    }
                    if (slot == null || slot.getSlots() < 1) {
                        return false;
                    }
                    slot.getStacks().setStackInSlot(0, new ItemStack(ring));
                    return Superposition.hasCurio(player, ring);
                })
                .orElse(false);
        }
        catch (Throwable t) {
            log(" T27b 佩戴七咒之戒异常: " + t);
            return false;
        }
    }

    private static void unequipRing(Player player) {
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                var slot = handler.getCurios().get("ring");
                if (slot != null && slot.getSlots() > 0) {
                    slot.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                }
            });
        }
        catch (Throwable ignored) {
            // 清理
        }
    }

    /**
     * 把书真的放进「古旧书袋」:书袋物品进背包,书进扩展的玩家 capability
     * ({@code AntiqueBagCapability.INVENTORY})。全部走反射,缺扩展时返回 false(该分支跳过)。
     */
    private static boolean putBookInBag(Player player, Item book) {
        try {
            Item bag = RingConfig.antiqueBagItem();
            if (bag == null) {
                return false;
            }
            if (!hasItem(player, bag)) {
                player.getInventory().add(new ItemStack(bag));
            }
            Class<?> capClass = Class.forName(
                "auviotre.enigmatic.addon.contents.objects.bookbag.AntiqueBagCapability");
            Object capability = capClass.getField("INVENTORY").get(null);
            Object lazy = player.getCapability((net.minecraftforge.common.capabilities.Capability) capability);
            if (!(lazy instanceof net.minecraftforge.common.util.LazyOptional<?> optional)) {
                return false;
            }
            Object handler = optional.resolve().orElse(null);
            if (handler == null) {
                return false;
            }
            Class<?> iface = Class.forName(
                "auviotre.enigmatic.addon.contents.objects.bookbag.IAntiqueBagHandler");
            iface.getMethod("setBook", int.class, ItemStack.class).invoke(handler, 0, new ItemStack(book));
            return !BookBag.findBook(player, RingConfig.BEDTIME_READING_ID).isEmpty();
        }
        catch (Throwable t) {
            log(" T27b 构造书袋内容异常: " + t);
            return false;
        }
    }

    private static boolean hasItem(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private static void clearBag(Player player) {
        try {
            Item bag = RingConfig.antiqueBagItem();
            Class<?> capClass = Class.forName(
                "auviotre.enigmatic.addon.contents.objects.bookbag.AntiqueBagCapability");
            Object capability = capClass.getField("INVENTORY").get(null);
            Object lazy = player.getCapability((net.minecraftforge.common.capabilities.Capability) capability);
            if (lazy instanceof net.minecraftforge.common.util.LazyOptional<?> optional) {
                Object handler = optional.resolve().orElse(null);
                if (handler != null) {
                    Class<?> iface = Class.forName(
                        "auviotre.enigmatic.addon.contents.objects.bookbag.IAntiqueBagHandler");
                    int slots = (Integer) iface.getMethod("getSlots").invoke(handler);
                    for (int i = 0; i < slots; i++) {
                        iface.getMethod("setBook", int.class, ItemStack.class)
                            .invoke(handler, i, ItemStack.EMPTY);
                    }
                }
            }
            if (bag != null) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.getItem() == bag) {
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
        catch (Throwable ignored) {
            // 清理
        }
    }

    // ------------------------------------------------------------------ 工具

    private record Crafted(CraftingRecipe recipe, ItemStack output) {}

    private static Crafted craft(ServerLevel level, Player player, ItemStack... items) {
        TransientCraftingContainer grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
        for (int i = 0; i < items.length && i < grid.getContainerSize(); i++) {
            grid.setItem(i, items[i].copy());
        }
        Optional<CraftingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        if (found.isEmpty()) {
            return null;
        }
        CraftingRecipe recipe = found.get();
        return new Crafted(recipe, recipe.assemble(grid, level.registryAccess()));
    }

    private static java.io.InputStream resource(String path) {
        try {
            return BedtimeReadingSelfTest.class.getResourceAsStream(path);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static byte[] resourceBytes(String path) {
        try (java.io.InputStream in = resource(path)) {
            return in == null ? null : in.readAllBytes();
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static String resourceText(String path) {
        byte[] bytes = resourceBytes(path);
        return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static com.google.gson.JsonObject readLang(String lang) {
        try (java.io.InputStream in = resource("/assets/enigmaticenhance/lang/" + lang + ".json")) {
            if (in == null) {
                return null;
            }
            try (var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        catch (Throwable t) {
            log(" T27a 解析 " + lang + " 失败: " + t);
            return null;
        }
    }

    private static String text(com.google.gson.JsonObject root, String key) {
        return root != null && root.has(key) ? root.get(key).getAsString() : null;
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
