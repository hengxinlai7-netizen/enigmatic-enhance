package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.2.2 自检:【深渊侵蚀的大地之心】(-D 开关,由 {@link EarthAmuletSelfTest} 串起来跑)。
 * <p>
 * 覆盖:
 * <ul>
 * <li>T36 标签:干净的心两个键都 false;标记后我们的键 true、<b>本体的 {@code isTainted} 仍然 false</b>;重复标记是幂等的;</li>
 * <li>T37 来源:深渊之心本体必定在内;本体用深渊之心做的三件(孤寂之戒/邪术平底锅/无止之言)由<b>配方扫描</b>自动进集合;</li>
 * <li>T38 转化:背包 / 装备栏(盔甲槽) / 饰品栏 任一处有来源时,背包里的心被标记;来源拿走后新放的心不受影响;
 *     重复调用返回 0(幂等);</li>
 * <li>T39 不干涉原版:只被七咒之戒污染过的心({@code isTainted:1b})<b>不算</b>深渊侵蚀态;我们的标记也不会清掉它的 {@code isTainted};</li>
 * <li>T40 配方:扭曲之心({@code crafting_shaped_cursed})认得干净心吗(不该认)、深渊侵蚀心(该认)、本体污染心(该认);
 *     本模组的大地护符配方认得深渊侵蚀心(该认)、干净心(不该认)。</li>
 * </ul>
 * 说明:全部直接调 {@link AbyssalEarthHeart} 的公开入口,不依赖玩家 tick ——
 * {@link FakePlayer} 不会进世界,也就不会被 {@code TickEvent.PlayerTickEvent} 驱动。
 */
final class AbyssalEarthHeartSelfTest {

    private static final String TAG = "[AbyssalEarthHeartSelfTest]";

    /** 本体蒸馏出这三条"以深渊之心为材料"的配方,见 {@code data/enigmaticlegacy/recipes/*.json}。 */
    private static final List<String> EL_BYPRODUCTS = List.of(
        "enigmaticlegacy:desolation_ring",
        "enigmaticlegacy:eldritch_pan",
        "enigmaticlegacy:the_infinitum");

    private AbyssalEarthHeartSelfTest() {}

    static boolean run(ServerLevel level, FakePlayer player) {
        boolean t36 = testTagBasics();
        boolean t37 = testTriggerSet(level);
        boolean t38 = testConversion(level, player);
        boolean t39 = testVanillaTaintKept(level, player);
        boolean t40 = testRecipes(level, player);
        boolean t41 = testTooltip(player);
        boolean t42 = testHeartShapedRecipes(level, player);
        boolean all = t36 && t37 && t38 && t39 && t40 && t41 && t42;
        log(" 深渊侵蚀小结 T36=" + t36 + " T37=" + t37 + " T38=" + t38 + " T39=" + t39 + " T40=" + t40
            + " T41=" + t41 + " T42=" + t42 + " => " + (all ? "PASS" : "FAIL"));
        return all;
    }

    // ------------------------------------------------------------------ T36 标签

    private static boolean testTagBasics() {
        log(" --- T36 深渊侵蚀标签(与本体 isTainted 平行)");
        Item heart = RingConfig.earthHeartItem();
        Item abyssal = RingConfig.abyssalHeartItem();
        log(" T36 大地之心=" + idOf(heart) + " 深渊之心=" + idOf(abyssal)
            + " [应为 enigmaticlegacy:earth_heart / enigmaticlegacy:abyssal_heart]");
        if (heart == null || abyssal == null) {
            check("T36", false);
            return false;
        }
        boolean ok = true;
        ItemStack clean = new ItemStack(heart);
        ok &= !AbyssalEarthHeart.isAbyssalTainted(clean);
        ok &= !AbyssalEarthHeart.elTainted(clean);
        log(" T36 干净的心: 我们的键=" + AbyssalEarthHeart.isAbyssalTainted(clean)
            + " 本体键=" + AbyssalEarthHeart.elTainted(clean) + " [应 false false]");

        ok &= AbyssalEarthHeart.markAbyssalTainted(clean);
        ok &= AbyssalEarthHeart.isAbyssalTainted(clean);
        ok &= !AbyssalEarthHeart.elTainted(clean);
        log(" T36 标记后: 我们的键=" + AbyssalEarthHeart.isAbyssalTainted(clean)
            + " 本体键=" + AbyssalEarthHeart.elTainted(clean) + " [应 true false —— 后者证明没写原版的键]");

        ok &= !AbyssalEarthHeart.markAbyssalTainted(clean);
        log(" T36 二次标记返回 false(幂等)=" + !AbyssalEarthHeart.isAbyssalTainted(new ItemStack(heart)));

        ItemStack copy = clean.copy();
        ok &= AbyssalEarthHeart.isAbyssalTainted(copy);
        log(" T36 复制后仍带标记=" + AbyssalEarthHeart.isAbyssalTainted(copy));
        log(" T36 键名 = " + AbyssalEarthHeart.TAG + " [应为 abyssalTaint,不能是 " + AbyssalEarthHeart.EL_TAINT_TAG + "]");
        ok &= "abyssalTaint".equals(AbyssalEarthHeart.TAG);

        check("T36", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T37 来源

    private static boolean testTriggerSet(ServerLevel level) {
        log(" --- T37 触发来源(配方自动推导)");
        AbyssalEarthHeart.invalidateTriggers();
        Set<Item> triggers = AbyssalEarthHeart.triggerItems(level.getServer());
        boolean ok = !triggers.isEmpty() && triggers.contains(RingConfig.abyssalHeartItem());
        log(" T37 来源数量=" + triggers.size() + " 含深渊之心本体="
            + triggers.contains(RingConfig.abyssalHeartItem()));
        log(" T37 来源清单=" + triggers.stream().map(AbyssalEarthHeartSelfTest::idOf).sorted().toList());

        for (String raw : EL_BYPRODUCTS) {
            ResourceLocation id = new ResourceLocation(raw);
            Item item = ForgeRegistries.ITEMS.getValue(id);
            boolean recipeExists = level.getRecipeManager().byKey(id).isPresent();
            boolean inSet = item != null && triggers.contains(item);
            log(" T37 " + raw + " 配方存在=" + recipeExists + " 在来源集合里=" + inSet);
            if (recipeExists) {
                ok &= inSet;
            }
        }

        check("T37", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T38 转化

    private static boolean testConversion(ServerLevel level, FakePlayer player) {
        log(" --- T38 四处容器的转化");
        Item heart = RingConfig.earthHeartItem();
        Item abyssal = RingConfig.abyssalHeartItem();
        if (heart == null || abyssal == null) {
            check("T38", false);
            return false;
        }
        boolean ok = true;
        resetInventory(player);
        AbyssalEarthHeart.invalidateTriggers();

        // (1) 背包:心 + 深渊之心
        ItemStack heartA = new ItemStack(heart);
        player.getInventory().items.set(0, heartA);
        player.getInventory().items.set(1, new ItemStack(abyssal));
        int first = AbyssalEarthHeart.convertPlayer(player);
        boolean bagOk = first >= 1 && AbyssalEarthHeart.isAbyssalTainted(heartA)
            && !AbyssalEarthHeart.elTainted(heartA);
        log(" T38 背包路径: 转化数=" + first + " 已标记=" + AbyssalEarthHeart.isAbyssalTainted(heartA)
            + " 本体键=" + AbyssalEarthHeart.elTainted(heartA) + " [应 >=1 true false]");
        ok &= bagOk;

        // (2) 幂等:同一颗心不会重复写
        int second = AbyssalEarthHeart.convertPlayer(player);
        log(" T38 重复调用转化数=" + second + " [应 0]");
        ok &= second == 0;

        // (3) 来源拿走后,新放进来的心不受影响
        player.getInventory().items.set(1, ItemStack.EMPTY);
        ItemStack heartB = new ItemStack(heart);
        player.getInventory().items.set(2, heartB);
        int none = AbyssalEarthHeart.convertPlayer(player);
        boolean noSourceOk = none == 0 && !AbyssalEarthHeart.isAbyssalTainted(heartB);
        log(" T38 无来源: 转化数=" + none + " 新心已标记=" + AbyssalEarthHeart.isAbyssalTainted(heartB)
            + " [应 0 false]");
        ok &= noSourceOk;

        // (4) 装备栏(盔甲槽)里的来源也要算
        boolean armorOk = false;
        Item armorSource = firstInstalledItem("enigmaticlegacy:desolation_ring");
        if (armorSource == null) {
            log(" T38 装备栏路径: 没找到 enigmaticlegacy:desolation_ring,跳过");
        }
        else {
            player.getInventory().armor.set(3, new ItemStack(armorSource));
            int fromArmor = AbyssalEarthHeart.convertPlayer(player);
            armorOk = fromArmor >= 1 && AbyssalEarthHeart.isAbyssalTainted(heartB);
            log(" T38 装备栏路径(盔甲槽放孤寂之戒): 转化数=" + fromArmor
                + " 已标记=" + AbyssalEarthHeart.isAbyssalTainted(heartB) + " [应 >=1 true]");
            player.getInventory().armor.set(3, ItemStack.EMPTY);
            ok &= armorOk;
        }

        // (5) 饰品栏里的东西必须被收进扫描范围(collect 包含 curios 的每个槽)
        boolean curioCollected = false;
        Item amulet = ModItems.earthAmuletOrNull();
        boolean curioEquipped = false;
        try {
            curioEquipped = equipCurio(player, "charm", new ItemStack(amulet));
            if (curioEquipped) {
                for (ItemStack stack : AbyssalEarthHeart.collect(player)) {
                    if (stack.is(amulet)) {
                        curioCollected = true;
                        break;
                    }
                }
            }
        }
        catch (Throwable t) {
            log(" T38 饰品栏路径: 异常 " + t);
        }
        log(" T38 饰品栏: 成功戴入=" + curioEquipped + " 被 collect 收进来=" + curioCollected
            + " [戴入成功时应 true]");
        if (curioEquipped) {
            ok &= curioCollected;
        }

        resetInventory(player);
        check("T38", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T39 不干涉原版

    private static boolean testVanillaTaintKept(ServerLevel level, FakePlayer player) {
        log(" --- T39 原版受污染的大地之心不受影响");
        Item heart = RingConfig.earthHeartItem();
        Item abyssal = RingConfig.abyssalHeartItem();
        if (heart == null || abyssal == null) {
            check("T39", false);
            return false;
        }
        boolean ok = true;

        // (1) 只被七咒之戒污染过的心:不能被当深渊侵蚀的用
        ItemStack taintedOnly = new ItemStack(heart);
        taintedOnly.getOrCreateTag().putBoolean(AbyssalEarthHeart.EL_TAINT_TAG, true);
        boolean taintedOnlyOk = AbyssalEarthHeart.elTainted(taintedOnly)
            && !AbyssalEarthHeart.isAbyssalTainted(taintedOnly);
        log(" T39 只受污染: 本体键=" + AbyssalEarthHeart.elTainted(taintedOnly)
            + " 我们的键=" + AbyssalEarthHeart.isAbyssalTainted(taintedOnly) + " [应 true false]");
        ok &= taintedOnlyOk;

        // (2) 有来源时我们加自己的键,但原版的键既不清也不改
        resetInventory(player);
        AbyssalEarthHeart.invalidateTriggers();
        ItemStack tainted = new ItemStack(heart);
        tainted.getOrCreateTag().putBoolean(AbyssalEarthHeart.EL_TAINT_TAG, true);
        player.getInventory().items.set(0, tainted);
        player.getInventory().items.set(1, new ItemStack(abyssal));
        int changed = AbyssalEarthHeart.convertPlayer(player);
        boolean bothOk = changed >= 1 && AbyssalEarthHeart.isAbyssalTainted(tainted)
            && AbyssalEarthHeart.elTainted(tainted);
        log(" T39 既有污染又有来源: 转化数=" + changed + " 本体键=" + AbyssalEarthHeart.elTainted(tainted)
            + " 我们的键=" + AbyssalEarthHeart.isAbyssalTainted(tainted) + " [应 >=1 true true]");
        ok &= bothOk;
        resetInventory(player);

        check("T39", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T40 配方

    private static boolean testRecipes(ServerLevel level, FakePlayer player) {
        log(" --- T40 配方桥接(能当受污染的大地之心用)");
        Item heart = RingConfig.earthHeartItem();
        if (heart == null) {
            check("T40", false);
            return false;
        }
        boolean ok = true;

        ItemStack clean = new ItemStack(heart);
        ItemStack abyssalTainted = new ItemStack(heart);
        AbyssalEarthHeart.markAbyssalTainted(abyssalTainted);
        ItemStack elTainted = new ItemStack(heart);
        elTainted.getOrCreateTag().putBoolean(AbyssalEarthHeart.EL_TAINT_TAG, true);

        // (1) 扭曲之心:本体用 crafting_shaped_cursed 的那条配方
        ResourceLocation twisted = new ResourceLocation("enigmaticlegacy", "twisted_heart");
        if (level.getRecipeManager().byKey(twisted).isEmpty()) {
            log(" T40 扭曲之心配方不存在(本体可能关掉了它),跳过这一条");
        }
        else {
            Crafted onClean = craft(level, player, ItemStack.EMPTY, new ItemStack(Items.GHAST_TEAR),
                ItemStack.EMPTY, new ItemStack(Items.BLAZE_POWDER), clean, new ItemStack(Items.BLAZE_POWDER),
                new ItemStack(Items.REDSTONE), new ItemStack(Items.ENDER_EYE), new ItemStack(Items.REDSTONE));
            Crafted onAbyssal = craft(level, player, ItemStack.EMPTY, new ItemStack(Items.GHAST_TEAR),
                ItemStack.EMPTY, new ItemStack(Items.BLAZE_POWDER), abyssalTainted, new ItemStack(Items.BLAZE_POWDER),
                new ItemStack(Items.REDSTONE), new ItemStack(Items.ENDER_EYE), new ItemStack(Items.REDSTONE));
            Crafted onElTainted = craft(level, player, ItemStack.EMPTY, new ItemStack(Items.GHAST_TEAR),
                ItemStack.EMPTY, new ItemStack(Items.BLAZE_POWDER), elTainted, new ItemStack(Items.BLAZE_POWDER),
                new ItemStack(Items.REDSTONE), new ItemStack(Items.ENDER_EYE), new ItemStack(Items.REDSTONE));

            boolean cleanRefused = onClean == null || !twisted.equals(onClean.recipe().getId());
            boolean abyssalAccepted = onAbyssal != null && twisted.equals(onAbyssal.recipe().getId());
            boolean elAccepted = onElTainted != null && twisted.equals(onElTainted.recipe().getId());
            log(" T40 扭曲之心: 干净心=" + (onClean == null ? "无" : onClean.recipe().getId())
                + " [应不匹配]; 深渊侵蚀心=" + (onAbyssal == null ? "无" : onAbyssal.recipe().getId())
                + " [应匹配]; 本体污染心=" + (onElTainted == null ? "无" : onElTainted.recipe().getId())
                + " [应匹配]");
            ok &= cleanRefused && abyssalAccepted && elAccepted;
        }

        // (2) 本模组的大地护符:深渊侵蚀心 + 3 金粒 ⇒ 大地护符
        ItemStack nugget = new ItemStack(Items.GOLD_NUGGET);
        Crafted amuletOnAbyssal = craft(level, player, abyssalTainted.copy(), nugget.copy(), nugget.copy(), nugget.copy());
        Crafted amuletOnClean = craft(level, player, clean.copy(), nugget.copy(), nugget.copy(), nugget.copy());
        boolean amuletOk = "enigmaticenhance:earth_amulet_abyssal".equals(idOfCraft(amuletOnAbyssal));
        boolean amuletCleanRefused = amuletOnClean == null || !"enigmaticenhance:earth_amulet".equals(idOfCraft(amuletOnClean));
        log(" T40 大地护符: 深渊侵蚀心=" + idOfCraft(amuletOnAbyssal)
            + " [应 enigmaticenhance:earth_amulet_abyssal]; 干净心=" + idOfCraft(amuletOnClean) + " [应不匹配]");
        ok &= amuletOk && amuletCleanRefused;

        check("T40", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T41 说明行

    /** 只有深渊侵蚀态才多一行说明;干净的心、只被七咒污染过的心都不许多。 */
    private static boolean testTooltip(FakePlayer player) {
        log(" --- T41 物品说明行");
        Item heart = RingConfig.earthHeartItem();
        if (heart == null) {
            check("T41", false);
            return false;
        }
        ItemStack clean = new ItemStack(heart);
        ItemStack abyssal = new ItemStack(heart);
        AbyssalEarthHeart.markAbyssalTainted(abyssal);
        ItemStack taintedOnly = new ItemStack(heart);
        taintedOnly.getOrCreateTag().putBoolean(AbyssalEarthHeart.EL_TAINT_TAG, true);

        List<String> cleanLines = tooltip(clean, player);
        List<String> abyssalLines = tooltip(abyssal, player);
        List<String> taintedLines = tooltip(taintedOnly, player);
        String expected = net.minecraft.network.chat.Component
            .translatable("tooltip.enigmaticenhance.add.earthHeart.abyssal").getString();

        boolean ok = cleanLines.isEmpty() && abyssalLines.size() == 1
            && abyssalLines.get(0).contains(expected) && taintedLines.isEmpty();
        log(" T41 干净心附加行=" + cleanLines.size() + " [应 0]; 深渊侵蚀心附加行=" + abyssalLines
            + " [应 1 行且含 \"" + expected + "\"]; 只受污染的心附加行=" + taintedLines.size() + " [应 0]");
        check("T41", ok);
        return ok;
    }

    /** 直接走本模组的说明挂载入口(与游戏内同一条路径),返回它追加的行。 */
    private static List<String> tooltip(ItemStack stack, FakePlayer player) {
        List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
        ItemTooltips.onItemTooltip(new net.minecraftforge.event.entity.player.ItemTooltipEvent(stack, player, lines,
            net.minecraft.world.item.TooltipFlag.Default.NORMAL));
        List<String> out = new ArrayList<>();
        for (net.minecraft.network.chat.Component line : lines) {
            out.add(line.getString());
        }
        return out;
    }

    // ------------------------------------------------------------------ T42 四条注册配方

    /**
     * 1.2.2c:四条「注册配方」(enigmaticenhance:heart_shaped)。
     * <ul>
     * <li>旅行家帽子 {@code heart:"any"} —— 干净 / 受污染 / 深渊侵蚀的心都该合出来;</li>
     * <li>恶魔的血杯 / 恶魔契约 / 恶魔的手骨 {@code heart:"abyssal"} —— 只有深渊侵蚀的心能合,
     *     干净的和"只被七咒之戒污染过"的都必须拒绝。</li>
     * </ul>
     * 形状照用户给的图:旅行帽 {@code " L "/"LHL"/"L L"};血杯 {@code " F "/"GHG"/"EGE"};
     * 契约 {@code "EHE"/"RDR"/"RTR"};手骨 {@code " EB"/"EHE"/"BE "}。
     */
    private static boolean testHeartShapedRecipes(ServerLevel level, FakePlayer player) {
        log(" --- T42 四条注册配方(旅行帽任意心 / 恶魔三件要深渊侵蚀)");
        Item heart = RingConfig.earthHeartItem();
        if (heart == null) {
            check("T42", false);
            return false;
        }
        ItemStack clean = new ItemStack(heart);
        ItemStack abyssal = new ItemStack(heart);
        AbyssalEarthHeart.markAbyssalTainted(abyssal);
        ItemStack elTainted = new ItemStack(heart);
        elTainted.getOrCreateTag().putBoolean(AbyssalEarthHeart.EL_TAINT_TAG, true);

        Item leather = item("minecraft:leather");
        Item fungus = item("minecraft:crimson_fungus");
        Item glass = item("minecraft:glass");
        Item essence = item("enigmaticlegacy:evil_essence");
        Item redstone = item("minecraft:redstone");
        Item scroll = item("enigmaticlegacy:darkest_scroll");
        Item twisted = item("enigmaticlegacy:twisted_heart");
        Item bone = item("minecraft:bone");
        if (leather == null || fungus == null || glass == null || essence == null || redstone == null
            || scroll == null || twisted == null || bone == null) {
            log(" T42 有材料物品缺失,跳过: leather=" + leather + " fungus=" + fungus + " glass=" + glass
                + " essence=" + essence + " redstone=" + redstone + " scroll=" + scroll + " twisted=" + twisted
                + " bone=" + bone);
            check("T42", false);
            return false;
        }

        boolean ok = true;
        // 旅行家帽子:任意心都行
        Item[] palette = { leather, essence, bone, redstone, scroll, twisted };
        // 旅行家帽子:任意形态的心都行
        ok &= expectHeartRecipe(level, player, "enigmaticenhance:traveler_hat", " L ", "LHL", "L L", palette, clean, true);
        ok &= expectHeartRecipe(level, player, "enigmaticenhance:traveler_hat", " L ", "LHL", "L L", palette, abyssal, true);
        ok &= expectHeartRecipe(level, player, "enigmaticenhance:traveler_hat", " L ", "LHL", "L L", palette, elTainted, true);

        // 恶魔三件:只有深渊侵蚀的心行(注意恶魔契约的 H 在**上中**,不是正中)
        String[][] demon = {
            { "enigmaticenhance:demon_blood_chalice", " F ", "GHG", "EGE" },
            { "enigmaticenhance:demon_pact", "EHE", "RDR", "RTR" },
            { "enigmaticenhance:demon_hand_bone", " EB", "EHE", "BE " } };
        for (String[] spec : demon) {
            ok &= expectHeartRecipe(level, player, spec[0], spec[1], spec[2], spec[3], palette, abyssal, true);
            ok &= expectHeartRecipe(level, player, spec[0], spec[1], spec[2], spec[3], palette, clean, false);
            ok &= expectHeartRecipe(level, player, spec[0], spec[1], spec[2], spec[3], palette, elTainted, false);
        }
        check("T42", ok);
        return ok;
    }

    /**
     * 按图案拼 9 格,把心放在 'H' 那一格。
     * 字母表:{@code L} 皮革、{@code E} 邪恶精髓、{@code B} 骨头、{@code R} 红石、
     * {@code D} 至暗卷轴、{@code T} 扭曲之心、{@code F} 绯红菌、{@code G} 玻璃,空格 = 空。
     */
    private static ItemStack[] grid(String r0, String r1, String r2, Item[] palette, ItemStack heart) {
        Item[] map = new Item[128];
        map['L'] = palette[0];
        map['E'] = palette[1];
        map['B'] = palette[2];
        map['R'] = palette[3];
        map['D'] = palette[4];
        map['T'] = palette[5];
        ItemStack[] out = new ItemStack[9];
        String[] rows = { r0, r1, r2 };
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                char ch = rows[row].charAt(col);
                ItemStack stack;
                if (ch == ' ') {
                    stack = ItemStack.EMPTY;
                }
                else if (ch == 'H') {
                    stack = heart == null ? ItemStack.EMPTY : heart.copy();
                }
                else if (ch == 'F') {
                    stack = new ItemStack(item("minecraft:crimson_fungus"));
                }
                else if (ch == 'G') {
                    stack = new ItemStack(item("minecraft:glass"));
                }
                else {
                    stack = map[ch] == null ? ItemStack.EMPTY : new ItemStack(map[ch]);
                }
                out[row * 3 + col] = stack;
            }
        }
        return out;
    }

    /** 按图案摆好(心在 H 的位置),断言是否匹配到 {@code expectedRecipe}。 */
    private static boolean expectHeartRecipe(ServerLevel level, FakePlayer player, String expectedRecipe,
        String r0, String r1, String r2, Item[] palette, ItemStack heartStack, boolean shouldMatch) {
        ItemStack[] slots = grid(r0, r1, r2, palette, heartStack);
        Crafted crafted = craftGrid(level, player, slots);
        boolean matched = crafted != null && expectedRecipe.equals(crafted.recipe().getId().toString());
        String heartState = AbyssalEarthHeart.isAbyssalTainted(heartStack) ? "深渊侵蚀"
            : AbyssalEarthHeart.elTainted(heartStack) ? "只受污染" : "干净";
        log(" T42 " + expectedRecipe + " @ " + heartState + " → "
            + (crafted == null ? "不匹配" : crafted.recipe().getId()) + " [应" + (shouldMatch ? "匹配" : "不匹配") + "]");
        return matched == shouldMatch;
    }

    private static Crafted craftGrid(ServerLevel level, FakePlayer player, ItemStack[] slots) {
        CraftingContainer grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
        for (int i = 0; i < slots.length && i < grid.getContainerSize(); i++) {
            grid.setItem(i, slots[i] == null ? ItemStack.EMPTY : slots[i].copy());
        }
        Optional<CraftingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        if (found.isEmpty()) {
            return null;
        }
        CraftingRecipe recipe = found.get();
        return new Crafted(grid, recipe, recipe.assemble(grid, level.registryAccess()));
    }

    /** 按注册名取物品(缺失返回 null)。 */
    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? null : item;
    }

    // ------------------------------------------------------------------ 工具

    private record Crafted(CraftingContainer grid, CraftingRecipe recipe, ItemStack output) {}

    private static Crafted craft(ServerLevel level, FakePlayer player, ItemStack... items) {
        CraftingContainer grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
        for (int i = 0; i < items.length && i < grid.getContainerSize(); i++) {
            grid.setItem(i, items[i] == null ? ItemStack.EMPTY : items[i].copy());
        }
        Optional<CraftingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        if (found.isEmpty()) {
            return null;
        }
        CraftingRecipe recipe = found.get();
        return new Crafted(grid, recipe, recipe.assemble(grid, level.registryAccess()));
    }

    private static String idOfCraft(Crafted crafted) {
        return crafted == null ? "无" : crafted.recipe().getId().toString();
    }

    private static String idOf(Item item) {
        if (item == null || item == Items.AIR) {
            return "无";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? "无" : id.toString();
    }

    private static Item firstInstalledItem(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : item;
    }

    /** 把一件饰品放进指定槽的第 0 格;Curios 不接受该物品时返回 false。 */
    private static boolean equipCurio(FakePlayer player, String slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var ref = new boolean[] { false };
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var handlerStacks = handler.getCurios().get(slot);
            if (handlerStacks == null || handlerStacks.getStacks().getSlots() < 1) {
                return;
            }
            handlerStacks.getStacks().setStackInSlot(0, stack);
            ref[0] = handlerStacks.getStacks().getStackInSlot(0).is(stack.getItem());
        });
        return ref[0];
    }

    private static void resetInventory(FakePlayer player) {
        player.getInventory().clearContent();
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            player.getInventory().armor.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            player.getInventory().offhand.set(i, ItemStack.EMPTY);
        }
        try {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                for (var entry : handler.getCurios().entrySet()) {
                    var handlerStacks = entry.getValue().getStacks();
                    for (int i = 0; i < handlerStacks.getSlots(); i++) {
                        handlerStacks.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            });
        }
        catch (Throwable ignored) {
            // 缺 Curios:没有饰品栏要清
        }
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
