package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.1.28h 自检 T31:**物品名 lang 完整性**。
 * <p>
 * 起因:1.1.28g 的 jar 里 `demon_hand_bone` 只有 tooltip 键、**少了 `item.enigmaticenhance.demon_hand_bone`**,
 * 于是游戏里物品名显示成原始 key。这里遍历**所有本模组注册的物品**,对每个物品用它自己真正使用的
 * 名称键({@link Item#getDescriptionId()})去查中英两份 lang,缺任何一个就 FAIL —— 以后再漏会立刻暴露。
 * <p>
 * 同时也检查每个物品的 tooltip 前缀键("tooltip." + 名称键去掉 "item.")至少有一条,
 * 避免"有名字没说明"或反之。
 */
final class LangSelfTest {

    private static final String TAG = "[LangSelfTest]";

    private LangSelfTest() {}

    static boolean run(ServerLevel level, Player player) {
        log(" --- T31 物品名 / tooltip 的 lang 完整性(中英各一份)");
        var zh = read("zh_cn");
        var en = read("en_us");
        boolean ok = zh != null && en != null;
        if (!ok) {
            log(" T31 读不到 lang 文件 [FAIL]");
            check("T31", false);
            return false;
        }
        List<String> missing = new ArrayList<>();
        int items = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
            if (id == null || !EnigmaticEnhance.MODID.equals(id.getNamespace())) {
                continue;
            }
            ++items;
            String nameKey = item.getDescriptionId();
            String tooltipPrefix = nameKey.startsWith("item.")
                ? "tooltip." + nameKey.substring("item.".length()) + "."
                : nameKey + ".tooltip.";
            boolean zhName = zh.has(nameKey);
            boolean enName = en.has(nameKey);
            boolean zhTip = hasPrefix(zh, tooltipPrefix);
            boolean enTip = hasPrefix(en, tooltipPrefix);
            log(" T31 " + id + " -> " + nameKey + ":zh=" + zhName + " en=" + enName
                + " tooltip(zh/en)=" + zhTip + "/" + enTip);
            if (!zhName) {
                missing.add("zh_cn:" + nameKey);
            }
            if (!enName) {
                missing.add("en_us:" + nameKey);
            }
            if (!zhTip) {
                missing.add("zh_cn:" + tooltipPrefix + "*");
            }
            if (!enTip) {
                missing.add("en_us:" + tooltipPrefix + "*");
            }
        }
        log(" T31 本模组物品数=" + items + " [应 >= 7];缺失项=" + missing);
        ok &= items >= 7 && missing.isEmpty();
        check("T31", ok);
        return ok;
    }

    private static boolean hasPrefix(com.google.gson.JsonObject root, String prefix) {
        for (String key : root.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static com.google.gson.JsonObject read(String lang) {
        try (java.io.InputStream in = LangSelfTest.class
            .getResourceAsStream("/assets/enigmaticenhance/lang/" + lang + ".json")) {
            if (in == null) {
                return null;
            }
            try (var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        catch (Throwable t) {
            log(" T31 解析 " + lang + " 失败: " + t);
            return null;
        }
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
