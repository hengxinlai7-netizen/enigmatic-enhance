package com.hengx.enigmaticenhance;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 4.6.7:启动期数据自检。
 *
 * <p>抓两类"数据一错就是灾难"的问题:</p>
 * <ol>
 *   <li><b>tag 悬空引用</b> —— 4.6.6 的护符事故:tag 里写了一个不存在的物品 id,
 *       整份 {@code curios:charm} 直接加载失败,所有护符类饰品瞬间不能戴。
 *       这里遍历 jar 内所有 {@code data/*}{@code /tags/**} 文件,把每个 id 拿到
 *       {@link ForgeRegistries} 里查一遍。</li>
 *   <li><b>配方形状冲突</b> —— 4.6.6 的生命刻度事故:形状 + 材料和原版指南针完全一样,
 *       导致我们自己的配方永远抢不到。这里把 jar 内所有 shaped 配方解析成
 *       "去边框后的材料网格",两两比对,并和原版常见形状比对。</li>
 * </ol>
 *
 * <p>时机:{@link FMLCommonSetupEvent}(物品/方块注册已完成、注册表已冻结)。
 * 只在启动跑一次;正常时只输出一行 INFO,有问题时一行 WARN + 细节走
 * {@link DebugLog}(需要 {@code debugLog=true})。</p>
 *
 * <p>资源清单来自构建期生成的 {@code enigmaticenhance/datacheck/index.txt}
 * (见 {@code build.gradle} 的 {@code processResources}):Forge 的 mod 文件系统
 * 不支持目录枚举,所以清单由构建脚本写死,运行时按清单逐个读资源。</p>
 *
 * <p>任何异常都被吞掉:自检只是"体检",绝不能因为它自己把游戏搞崩。</p>
 */
public final class DataSelfCheck {

    /** 构建期生成的资源清单(每行一个 jar 内路径)。 */
    private static final String INDEX = "/enigmaticenhance/datacheck/index.txt";

    /** 原版常见 shaped 形状(材料 id 与形状一起比,避免"同形不同料"的误报)。 */
    private static final String[] VANILLA_NAMES = {
        "minecraft:compass", "minecraft:clock", "minecraft:bucket", "minecraft:shears", "minecraft:piston"
    };
    private static final String[][] VANILLA_PATTERNS = {
        { " I ", "IRI", " I " },
        { " I ", "IGI", " I " },
        { "I I", " I " },
        { " I", "I " },
        { "PPP", "CIC", "CDC" }
    };
    /** 每个原版形状的符号 -> 材料 id。 */
    private static final String[][] VANILLA_KEYS = {
        { "I=minecraft:iron_ingot", "R=minecraft:redstone" },
        { "I=minecraft:gold_ingot", "G=minecraft:redstone" },
        { "I=minecraft:iron_ingot" },
        { "I=minecraft:iron_ingot" },
        { "P=#minecraft:planks", "C=minecraft:cobblestone", "I=minecraft:iron_ingot", "D=minecraft:redstone" }
    };

    /** 防止重复执行(客户端/服务端各自一次;同一进程内只跑一次)。 */
    private static volatile boolean done;

    private DataSelfCheck() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (done || !RingConfig.dataSelfCheckEnabled()) {
            return;
        }
        done = true;
        event.enqueueWork(DataSelfCheck::runOnce);
    }

    // ------------------------------------------------------------------ 主流程

    private static void runOnce() {
        try {
            Report report = check();
            if (report.problems() == 0) {
                EnigmaticEnhance.LOGGER.info("[神秘遗物增强] 数据自检通过: tag {} 个 / 配方 {} 个",
                    report.tagFiles, report.recipes);
                return;
            }
            EnigmaticEnhance.LOGGER.warn("[神秘遗物增强] 数据自检: {} 个 tag 缺失引用, {} 个配方冲突(详见 debugLog)",
                report.missing.size(), report.conflicts.size());
            for (String line : report.details) {
                DebugLog.info("{}", line);
            }
        }
        catch (Throwable t) {
            // 自检自己出错绝不能影响启动
            DebugLog.info("[数据自检] 跳过(内部错误): {}", String.valueOf(t));
        }
    }

    private static Report check() throws Exception {
        Report report = new Report();
        List<String> index = readIndex();
        if (index.isEmpty()) {
            DebugLog.info("[数据自检] 未找到资源清单 {} ,跳过(IDE 运行时不生成索引属正常)", INDEX);
            return report;
        }
        for (String path : index) {
            if (path.contains("/tags/") && path.endsWith(".json")) {
                report.tagFiles++;
                checkTag(path, report);
            }
            else if (path.contains("/recipes/") && path.endsWith(".json")) {
                checkRecipe(path, report);
            }
        }
        return report;
    }

    // ------------------------------------------------------------------ A. tag 完整性

    private static void checkTag(String path, Report report) throws Exception {
        // data/<ns>/tags/<类型>/<名字>.json
        String[] parts = path.split("/");
        if (parts.length < 5) {
            return;
        }
        String namespace = parts[1];
        String type = parts[3];
        String name = String.join("/", java.util.Arrays.copyOfRange(parts, 4, parts.length));
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        String tagName = namespace + ":" + name;

        JsonObject json = readJson(path);
        if (json == null || !json.has("values") || !json.get("values").isJsonArray()) {
            return;
        }
        for (JsonElement element : json.getAsJsonArray("values")) {
            boolean required = true;
            String raw;
            if (element.isJsonPrimitive()) {
                raw = element.getAsString();
            }
            else if (element.isJsonObject()) {
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("id")) {
                    continue;
                }
                raw = entry.get("id").getAsString();
                if (entry.has("required")) {
                    required = entry.get("required").getAsBoolean();
                }
            }
            else {
                continue;
            }
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;   // tag 套 tag,不查
            }
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                report.details.add("[神秘遗物增强] 自检: tag " + tagName + " 的条目不是合法 id: " + raw);
                report.missing.add(raw);
                continue;
            }
            Boolean exists = existsIn(type, id);
            if (exists == null || exists.booleanValue()) {
                continue;   // 判断不到的类型 / 存在
            }
            report.missing.add(raw);
            report.details.add("[神秘遗物增强] 自检: tag " + tagName + " 缺少" + registryLabel(type)
                + " " + raw + (required ? "" : " (required:false, 不影响加载)"));
        }
    }

    /** @return null = 这个 tag 类型不认识,跳过 */
    private static Boolean existsIn(String type, ResourceLocation id) {
        switch (type) {
            case "items":
                return ForgeRegistries.ITEMS.containsKey(id);
            case "blocks":
                return ForgeRegistries.BLOCKS.containsKey(id);
            case "entity_types":
                return ForgeRegistries.ENTITY_TYPES.containsKey(id);
            case "fluids":
                return ForgeRegistries.FLUIDS.containsKey(id);
            case "game_events":
                return net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.containsKey(id);
            default:
                return null;   // damage_type / functions / structures … 运行时不可靠,跳过
        }
    }

    private static String registryLabel(String type) {
        switch (type) {
            case "items":
                return "物品";
            case "blocks":
                return "方块";
            case "entity_types":
                return "实体类型";
            case "fluids":
                return "流体";
            case "game_events":
                return "游戏事件";
            default:
                return "资源";
        }
    }

    // ------------------------------------------------------------------ B. 配方冲突

    private static void checkRecipe(String path, Report report) throws Exception {
        JsonObject json = readJson(path);
        if (json == null || !json.has("type")) {
            return;
        }
        String type = json.get("type").getAsString();
        String recipeName = path.substring(path.lastIndexOf('/') + 1);
        if (recipeName.endsWith(".json")) {
            recipeName = recipeName.substring(0, recipeName.length() - 5);
        }
        report.recipes++;
        if ("minecraft:crafting_shaped".equals(type) || "enigmaticenhance:heart_shaped".equals(type)) {
            String shape = shapedKey(json);
            if (shape == null) {
                return;
            }
            String previous = report.shaped.put(shape, recipeName);
            if (previous != null) {
                report.conflicts.add(recipeName);
                report.details.add("[神秘遗物增强] 自检: 配方 " + previous + " 与 " + recipeName + " 形状+材料完全相同");
            }
        }
        else if ("minecraft:crafting_shapeless".equals(type)) {
            report.recipes++;
            String key = shapelessKey(json);
            if (key == null) {
                return;
            }
            String previous = report.shapeless.put(key, recipeName);
            if (previous != null) {
                report.conflicts.add(recipeName);
                report.details.add("[神秘遗物增强] 自检: 无序配方 " + previous + " 与 " + recipeName + " 材料完全相同");
            }
        }
    }

    /** 把 shaped 配方化成"去边框后的材料网格";同形同料才能相等。 */
    private static String shapedKey(JsonObject json) {
        if (!json.has("pattern") || !json.get("pattern").isJsonArray()) {
            return null;
        }
        JsonArray pattern = json.getAsJsonArray("pattern");
        JsonObject key = json.has("key") && json.get("key").isJsonObject()
            ? json.getAsJsonObject("key") : new JsonObject();
        int height = pattern.size();
        int width = 0;
        for (JsonElement row : pattern) {
            width = Math.max(width, row.getAsString().length());
        }
        String[][] grid = new String[height][width];
        for (int y = 0; y < height; y++) {
            String row = pattern.get(y).getAsString();
            for (int x = 0; x < width; x++) {
                char symbol = x < row.length() ? row.charAt(x) : ' ';
                grid[y][x] = symbol == ' ' ? null : ingredientOf(key, symbol);
            }
        }
        return trim(grid);
    }

    private static String shapelessKey(JsonObject json) {
        if (!json.has("ingredients") || !json.get("ingredients").isJsonArray()) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("ingredients")) {
            String resolved = resolve(element);
            if (resolved == null) {
                return null;
            }
            items.add(resolved);
        }
        java.util.Collections.sort(items);
        return "L:" + String.join("|", items);
    }

    private static String ingredientOf(JsonObject key, char symbol) {
        JsonElement element = key.get(String.valueOf(symbol));
        if (element == null) {
            return "?" + symbol;
        }
        String resolved = resolve(element);
        return resolved == null ? "?" + symbol : resolved;
    }

    /** {"item":"x"} / {"tag":"y"} / {"item":{...}} -> 规范化字符串 */
    private static String resolve(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("item") && object.get("item").isJsonPrimitive()) {
            return object.get("item").getAsString();
        }
        if (object.has("tag") && object.get("tag").isJsonPrimitive()) {
            return "#" + object.get("tag").getAsString();
        }
        return null;   // 铁砧/复杂 ingredient:不参与比较
    }

    /** 去掉四周全空的行/列后拼成字符串。 */
    private static String trim(String[][] grid) {
        int top = grid.length;
        int bottom = -1;
        int left = grid[0].length;
        int right = -1;
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[y].length; x++) {
                if (grid[y][x] != null) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }
        if (bottom < 0) {
            return "S:empty";
        }
        StringBuilder sb = new StringBuilder("S:").append(right - left + 1).append('x').append(bottom - top + 1).append(';');
        for (int y = top; y <= bottom; y++) {
            if (y > top) {
                sb.append('/');
            }
            for (int x = left; x <= right; x++) {
                if (x > left) {
                    sb.append(',');
                }
                String cell = grid[y][x];
                sb.append(cell == null ? "" : cell);
            }
        }
        return sb.toString();
    }

    /** 构建原版形状的 key(用 pattern 字符串 + 符号表,和我们的配方走同一套规范化)。 */
    private static void registerVanilla(Report report) {
        for (int i = 0; i < VANILLA_NAMES.length; i++) {
            JsonObject json = new JsonObject();
            JsonArray pattern = new JsonArray();
            for (String row : VANILLA_PATTERNS[i]) {
                pattern.add(row);
            }
            json.add("pattern", pattern);
            JsonObject key = new JsonObject();
            for (String pair : VANILLA_KEYS[i]) {
                int eq = pair.indexOf('=');
                JsonObject ingredient = new JsonObject();
                String value = pair.substring(eq + 1);
                if (value.startsWith("#")) {
                    ingredient.addProperty("tag", value.substring(1));
                }
                else {
                    ingredient.addProperty("item", value);
                }
                key.add(pair.substring(0, eq), ingredient);
            }
            json.add("key", key);
            String shape = shapedKey(json);
            if (shape != null) {
                report.shaped.put(shape, VANILLA_NAMES[i]);
            }
        }
    }

    // ------------------------------------------------------------------ IO

    private static List<String> readIndex() throws Exception {
        List<String> out = new ArrayList<>();
        try (InputStream in = DataSelfCheck.class.getResourceAsStream(INDEX)) {
            if (in == null) {
                return out;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        out.add(line);
                    }
                }
            }
        }
        return out;
    }

    private static JsonObject readJson(String path) {
        try (InputStream in = DataSelfCheck.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                return null;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 结果

    private static final class Report {
        int tagFiles;
        int recipes;
        final List<String> missing = new ArrayList<>();
        final List<String> conflicts = new ArrayList<>();
        final Set<String> details = new LinkedHashSet<>();
        final Map<String, String> shaped = new LinkedHashMap<>();
        final Map<String, String> shapeless = new LinkedHashMap<>();

        Report() {
            registerVanilla(this);
        }

        int problems() {
            return missing.size() + conflicts.size();
        }
    }
}
