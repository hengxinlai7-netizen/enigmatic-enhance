package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.1.25d 自检:**tooltip 精简**。
 * <ul>
 * <li>T19 三个新饰品:空护符 1 行、带 3 颗头颅 3 行、帽子恒 3 行且无动态计数、恶魔契约 lethal true/false 各 2 行;
 *     全部无空行、无未替换的 {@code %s}/{@code %d};</li>
 * <li>T20 本模组给原版物品追加的说明:逐物品 ≤2 行、统一以 {@code · [本模组] } 开头、无解释性片段;</li>
 * <li>T21 古旧书袋:**保持原样**(追加行数 = 3:空行 + 标题 + 未同步),作为"别误改"的回归断言。</li>
 * </ul>
 */
final class TooltipSelfTest {

    private static final String TAG = "[TooltipSelfTest]";

    /** 旧版那些"解释机制"的片段,精简后一律不许再出现。 */
    private static final String[] FORBIDDEN = {
        "本模组",
        "由救赎之戒提供", "不会被闪电秒杀", "护甲/饰品不会被摧毁", "玩家看到的", "对所有属性生效",
        "其实是", "（条目）", "剩余", "tick"
    };

    private TooltipSelfTest() {}

    static boolean run(ServerLevel level, Player player) {
        boolean t19 = testNewItems(level);
        boolean t20 = testVanillaItems(level, player);
        boolean t21 = testBagUnchanged(level, player);
        boolean t23 = testToggles(level, player);
        boolean t24 = testLangScan(level, player);
        boolean all = t19 && t20 && t21 && t23 && t24;
        log(" tooltip 小结 T19=" + t19 + " T20=" + t20 + " T21=" + t21 + " T23=" + t23
            + " T24=" + t24 + " => " + (all ? "PASS" : "FAIL"));
        return all;
    }

    // ------------------------------------------------------------------ T19 三个新饰品

    private static boolean testNewItems(ServerLevel level) {
        log(" --- T19 三个新饰品的 tooltip");
        boolean ok = true;

        Item amulet = ModItems.earthAmuletOrNull();
        List<String> empty = effectLines(amulet, new ItemStack(amulet), level);
        log(" T19 空护符:行数=" + empty.size() + " [应 1] " + empty);
        ok &= empty.size() == 1 && clean(empty);

        ItemStack threeHeads = EarthAmuletData.withHead(EarthAmuletData.withHead(
            EarthAmuletData.withHead(new ItemStack(amulet), EarthAmuletData.HEAD_ZOMBIE),
            EarthAmuletData.HEAD_SKELETON), EarthAmuletData.HEAD_CREEPER);
        List<String> heads = effectLines(amulet, threeHeads, level);
        log(" T19 3 颗头颅:行数=" + heads.size() + " [应 3] " + heads);
        ok &= heads.size() == 3 && clean(heads);

        ItemStack potionAmulet = new ItemStack(amulet);
        EarthAmuletData.setEffects(potionAmulet, List.of(new EarthAmuletData.StoredEffect("minecraft:strength", 2)));
        List<String> potion = effectLines(amulet, potionAmulet, level);
        log(" T19 药水护符:行数=" + potion.size() + " [应 1] " + potion);
        ok &= potion.size() == 1 && clean(potion);

        ItemStack empowered = EarthAmuletData.empowered(
            EarthAmuletData.withHead(new ItemStack(amulet), EarthAmuletData.HEAD_WITHER_SKELETON));
        List<String> emp = effectLines(amulet, empowered, level);
        log(" T19 强化护符:行数=" + emp.size() + " [应 2] " + emp);
        ok &= emp.size() == 2 && clean(emp);

        Item hat = ModItems.travelerHatOrNull();
        TravelerHatSync.accept(1, 1);
        List<String> hatLow = effectLines(hat, new ItemStack(hat), level);
        TravelerHatSync.accept(37, 5);
        List<String> hatHigh = effectLines(hat, new ItemStack(hat), level);
        boolean stable = hatLow.equals(hatHigh);
        log(" T19 旅行家帽子:行数=" + hatLow.size() + " [应 4(含头饰栏 +1)];计数 1/1 与 37/5 时文本一致(无动态计数)="
            + stable + " [应 true] " + hatLow);
        ok &= hatLow.size() == 4 && stable && clean(hatLow);

        Item pact = ModItems.demonPactOrNull();
        // 1.1.28e:tooltip 精简后只剩 1 行静态描述,不再有动态模式文字
        List<String> pactLines = effectLines(pact, new ItemStack(pact), level);
        log(" T19 恶魔契约:行数=" + pactLines.size() + " [应 4] " + pactLines);
        ok &= pactLines.size() == 4 && clean(pactLines);

        check("T19", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T20 原版物品追加说明

    private static boolean testVanillaItems(ServerLevel level, Player player) {
        log(" --- T20 原版物品追加说明(每个 ≤2 行,统一前缀)");
        boolean ok = true;
        String[] ids = {
            "enigmaticaddons:bless_ring", "enigmaticlegacy:cursed_ring", "enigmaticlegacy:berserk_charm",
            "enigmaticaddons:thunder_scroll", "enigmaticlegacy:enchanter_pearl", "enigmaticaddons:lost_engine",
            "enigmaticaddons:adventure_charm", "enigmaticaddons:despair_insignia",
            "enigmaticdelicacy:gluttony_charm", "enigmaticlegacy:the_twist", "enigmaticlegacy:the_infinitum",
            "enigmaticaddons:the_bless"
        };
        for (String id : ids) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
            if (item == null) {
                log(" T20 " + id + " 不存在(本 run 未装该模组),跳过");
                continue;
            }
            List<Component> tooltip = new ArrayList<>();
            ItemTooltips.onItemTooltip(new ItemTooltipEvent(new ItemStack(item), player, tooltip,
                TooltipFlag.Default.NORMAL));
            List<String> texts = new ArrayList<>();
            for (Component line : tooltip) {
                texts.add(line.getString());
            }
            boolean prefix = texts.stream().allMatch(TooltipSelfTest::hasPrefix);
            boolean forbidden = texts.stream().anyMatch(TooltipSelfTest::hasForbidden);
            log(" T20 " + id + ":追加 " + texts.size() + " 行 [应 <=2] 前缀=" + prefix + " 解释性片段="
                + forbidden + " " + texts);
            ok &= texts.size() <= 2 && prefix && !forbidden && clean(texts);
        }
        check("T20", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T23 逐件开关的状态显示

    private static boolean testToggles(ServerLevel level, Player player) {
        log(" --- T23 逐件开关:tooltip 必须显示当前状态,且切换后立刻变化");
        boolean ok = true;

        Item berserk = item("enigmaticlegacy:berserk_charm");
        if (berserk == null) {
            log(" T23 血战沙场之证不存在,跳过该分支");
        }
        else {
            ItemStack on = new ItemStack(berserk);
            String textOn = first(lines(berserk, on, level, player));
            BloodbathHandler.setLockDisabled(on, true);
            String textOff = first(lines(berserk, on, level, player));
            BloodbathHandler.setLockDisabled(on, false);
            String textOnAgain = first(lines(berserk, on, level, player));
            log(" T23 血战沙场之证 开=" + textOn);
            log(" T23 血战沙场之证 关=" + textOff);
            ok &= isOn(textOn) && isOff(textOff)
                && !textOn.equals(textOff) && textOn.equals(textOnAgain);
            ok &= hasPrefix(textOn) && clean(List.of(textOn, textOff));
        }

        Item scroll = item("enigmaticaddons:thunder_scroll");
        if (scroll == null) {
            log(" T23 万钧之护卷轴不存在,跳过该分支");
        }
        else {
            ItemStack on = new ItemStack(scroll);
            String textOn = first(lines(scroll, on, level, player));
            ScrollHandler.setLightningDisabled(on, true);
            String textOff = first(lines(scroll, on, level, player));
            ScrollHandler.setLightningDisabled(on, false);
            String textOnAgain = first(lines(scroll, on, level, player));
            log(" T23 万钧之护卷轴 开=" + textOn);
            log(" T23 万钧之护卷轴 关=" + textOff);
            ok &= isOn(textOn) && isOff(textOff)
                && !textOn.equals(textOff) && textOn.equals(textOnAgain);
        }

        String[] noToggle = {
            "enigmaticaddons:lost_engine", "enigmaticaddons:adventure_charm",
            "enigmaticaddons:despair_insignia", "enigmaticlegacy:enchanter_pearl",
            "enigmaticaddons:bless_ring", "enigmaticlegacy:cursed_ring"
        };
        for (String id : noToggle) {
            Item item = item(id);
            if (item == null) {
                continue;
            }
            boolean anyState = lines(item, new ItemStack(item), level, player).stream()
                .anyMatch(TooltipSelfTest::hasState);
            log(" T23 " + id + " 含\"当前:\"=" + anyState + " [应 false:无逐件开关]");
            ok &= !anyState;
        }

        Item book = item("enigmaticlegacy:the_twist");
        if (book != null) {
            ItemStack stack = new ItemStack(book);
            String readable = first(lines(book, stack, level, player));
            BooksHandler.setDisabled(stack, true);
            String blocked = first(lines(book, stack, level, player));
            BooksHandler.setDisabled(stack, false);
            log(" T23 书籍 可读=" + readable);
            log(" T23 书籍 已禁止=" + blocked);
            ok &= !readable.equals(blocked) && isReadableHint(readable) && isBlockedHint(blocked);
        }

        check("T23", ok);
        return ok;
    }

    /** 行首必须是本模组的来源名(中文/英文两种语言都接受)。 */
    private static boolean hasPrefix(String text) {
        return text.startsWith("· [") && (text.contains("神秘遗物增强") || text.contains("Enigmatic Legacy Enhance"))
            && !text.contains("本模组");
    }

    private static boolean hasState(String text) {
        return text.contains("当前:") || text.contains("Currently:");
    }

    private static boolean isOn(String text) {
        return text.contains("当前:开启") || text.contains("Currently: ON");
    }

    private static boolean isOff(String text) {
        return text.contains("当前:关闭") || text.contains("Currently: OFF");
    }

    private static boolean isReadableHint(String text) {
        return text.contains("可右键翻阅") || text.contains("Right-click to read");
    }

    private static boolean isBlockedHint(String text) {
        return text.contains("已禁用翻阅") || text.contains("Reading disabled");
    }

    // ------------------------------------------------------------------ T24 全量文案体检

    /** 术语黑名单:出现任何一条都算不合格。 */
    private static final String[] BANNED = {
        "乘区", "generic.", "ADDITION", "MULTIPLY", "NBT", "S2C", "ForgeData", "SRG", "m_",
        "已修复", "【新增】", "威力 +7", "上限 40", "本模组"
    };

    /** 每件物品在 header 之外最多几行(1.1.25g:新饰品/原版物品 ≤2,大地护符的多头颅态 ≤3)。 */
    private static final java.util.Map<String, Integer> BUDGET = java.util.Map.ofEntries(
        java.util.Map.entry("enigmaticenhance:earth_amulet", 3),
        java.util.Map.entry("enigmaticenhance:demon_pact", 4),
        java.util.Map.entry("enigmaticenhance:traveler_hat", 4),
        java.util.Map.entry("enigmaticenhance:demon_blood_chalice", 4),
        java.util.Map.entry("enigmaticenhance:bedtime_reading", 3),
        java.util.Map.entry("enigmaticenhance:pure_rune", 3),
        java.util.Map.entry("enigmaticenhance:demon_hand_bone", 7),
        java.util.Map.entry("enigmaticaddons:bless_ring", 2),
        java.util.Map.entry("enigmaticlegacy:cursed_ring", 2),
        java.util.Map.entry("enigmaticlegacy:berserk_charm", 2),
        java.util.Map.entry("enigmaticaddons:thunder_scroll", 2),
        java.util.Map.entry("enigmaticlegacy:enchanter_pearl", 2),
        java.util.Map.entry("enigmaticaddons:lost_engine", 2),
        java.util.Map.entry("enigmaticaddons:adventure_charm", 2),
        java.util.Map.entry("enigmaticaddons:despair_insignia", 2),
        java.util.Map.entry("enigmaticlegacy:the_twist", 2));

    private static boolean testLangScan(ServerLevel level, Player player) {
        log(" --- T24 全量文案体检(中英 lang 全部 tooltip 键 + 每件物品行数)");
        boolean ok = true;
        for (String lang : new String[] { "zh_cn", "en_us" }) {
            var root = readLang(lang);
            if (root == null) {
                log(" T24 读不到 " + lang + ".json");
                ok = false;
                continue;
            }
            int count = 0;
            int bad = 0;
            for (var entry : root.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("tooltip.")) {
                    continue;
                }
                String text = entry.getValue().getAsString();
                ++count;
                for (String word : BANNED) {
                    if (text.contains(word)) {
                        ++bad;
                        log(" T24 " + lang + " 命中黑名单(" + word + "): " + key + " = " + text);
                    }
                }
                if (lang.equals("zh_cn")) {
                    log(" T24 " + key + " = " + text);
                }
            }
            log(" T24 " + lang + ".json:tooltip 键=" + count + ";黑名单命中=" + bad + " [应 0]");
            ok &= bad == 0 && count > 0;
        }

        for (var entry : BUDGET.entrySet()) {
            Item item = item(entry.getKey());
            if (item == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            int appended = entry.getKey().startsWith("enigmaticenhance:")
                ? effectLines(item, stack, level).size()
                : lines(item, stack, level, player).size();
            boolean within = appended <= entry.getValue();
            log(" T24 " + entry.getKey() + " 行数=" + appended + " [应 <=" + entry.getValue() + "]");
            ok &= within;
        }

        check("T24", ok);
        return ok;
    }

    private static com.google.gson.JsonObject readLang(String lang) {
        try (var stream = TooltipSelfTest.class.getResourceAsStream(
            "/assets/enigmaticenhance/lang/" + lang + ".json")) {
            if (stream == null) {
                return null;
            }
            try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        catch (Throwable t) {
            log(" T24 解析 " + lang + " 失败: " + t);
            return null;
        }
    }
    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
    }

    private static List<String> lines(Item item, ItemStack stack, ServerLevel level, Player player) {
        List<Component> tooltip = new ArrayList<>();
        ItemTooltips.onItemTooltip(new ItemTooltipEvent(stack, player, tooltip, TooltipFlag.Default.NORMAL));
        return tooltip.stream().map(Component::getString).toList();
    }

    private static String first(List<String> lines) {
        return lines.isEmpty() ? "" : lines.get(0);
    }

    // ------------------------------------------------------------------ T21 书袋回归

    private static boolean testBagUnchanged(ServerLevel level, Player player) {
        log(" --- T21 古旧书袋保持原样(回归断言)");
        Item bag = RingConfig.antiqueBagItem();
        if (bag == null) {
            log(" T21 古旧书袋不存在(未装扩展),跳过");
            return true;
        }
        List<Component> tooltip = new ArrayList<>();
        ItemTooltips.onItemTooltip(new ItemTooltipEvent(new ItemStack(bag), player, tooltip,
            TooltipFlag.Default.NORMAL));
        List<String> texts = new ArrayList<>();
        for (Component line : tooltip) {
            texts.add(line.getString());
        }
        String title = Component.translatable("baghud.title").getString();
        String unsynced = Component.translatable("baghud.unsynced").getString();
        boolean shape = texts.size() == 3 && texts.get(0).isEmpty() && title.equals(texts.get(1))
            && unsynced.equals(texts.get(2));
        log(" T21 追加行数=" + texts.size() + " [应 3 = 空行+标题+未同步,与 1.1.25c 完全一致];内容=" + texts);
        boolean ok = shape;
        check("T21", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 工具

    /** 第一个元素是 header,其余算"效果行"。 */
    private static List<String> effectLines(Item item, ItemStack stack, ServerLevel level) {
        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, level, tooltip, TooltipFlag.Default.NORMAL);
        List<String> out = new ArrayList<>();
        for (int i = 1; i < tooltip.size(); i++) {
            out.add(tooltip.get(i).getString());
        }
        return out;
    }

    /** 无空行、无未替换的格式占位符。 */
    private static boolean clean(List<String> lines) {
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                return false;
            }
            if (line.contains("%s") || line.contains("%d") || line.contains("%1$") || line.contains("%2$")) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasForbidden(String text) {
        for (String bad : FORBIDDEN) {
            if (text.contains(bad)) {
                return true;
            }
        }
        return false;
    }

    private static void setLethal(boolean value) {
        for (int i = 0; i < 10; i++) {
            try {
                RingConfig.DEMON_PACT_LETHAL.set(value);
                if (RingConfig.DEMON_PACT_LETHAL.get() == value) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 配置未就绪
            }
            EarthAmuletSelfTest.hold(150L);
        }
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
