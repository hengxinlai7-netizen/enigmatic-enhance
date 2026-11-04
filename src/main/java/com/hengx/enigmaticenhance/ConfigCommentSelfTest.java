package com.hengx.enigmaticenhance;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * 1.1.25e 自检:**配置文件注释说人话**。
 * <ul>
 * <li>T22 把 jar 里所有 {@code *.toml}({@code META-INF/mods.toml} + 本模组自带的
 *     {@code defaultconfigs/enigmaticenhance-common.toml})逐行扫一遍:
 *     注释里不许出现类名/方法名/SRG 名/事件名/属性运算名等术语,不许以"带点号的全限定名"开头,
 *     每个选项的注释**不超过 2 行**;同时断言键名与默认值一个没变(53 项 + 抽查)。</li>
 * </ul>
 */
final class ConfigCommentSelfTest {

    private static final String TAG = "[ConfigSelfTest]";

    /** 注释里一律不许出现的术语(用户点名的清单 + 同类)。 */
    private static final String[] TERMS = {
        "m_", "SRG", "ADDITION", "MULTIPLY_TOTAL", "RecipeType", "ItemEntity", "S2C", "ForgeData",
        "PlayerEvent", "SimpleChannel", "NBT", "Mixin", "ItemStack", "DamageSource"
    };

    /** "#" 后面紧跟"带点号的全限定名"(例如 com.foo.Bar)也算术语泄漏。 */
    private static final Pattern FQN = Pattern.compile("^#\\s*[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    /** 55(1.1.29a)+ 3(1.2.2 深渊侵蚀的大地之心)+ 1(1.2.2b 自动熔炼获取途径)= 59。 */
    private static final int EXPECTED_KEYS = 59;

    /** 抽查:段名.键名 → 默认值文本。 */
    private static final Map<String, String> EXPECTED = Map.ofEntries(
        Map.entry("enigmaticenhance.adventureCharmAllowMining", "true"),
        Map.entry("enigmaticenhance.ring.omniBonus", "0.2"),
        Map.entry("enigmaticenhance.ring.freezeBetrayal", "true"),
        Map.entry("enigmaticenhance.scroll.scaleOfProtectionAttackDamagePercent", "100.0"),
        Map.entry("enigmaticenhance.bagHud.bagTooltipEnabled", "true"),
        Map.entry("enigmaticenhance.bloodbath.bloodbathLockValue", "1.0"),
        Map.entry("enigmaticenhance.cursedRing.fourthCurseFixResidualPercent", "0.0"),
        Map.entry("enigmaticenhance.gluttony.lockedSaturation", "20.0"),
        Map.entry("enigmaticenhance.pearl.fixEnchantingTable", "true"),
        Map.entry("enigmaticenhance.books.enabled", "true"),
        Map.entry("enigmaticenhance.autoSmelt.yieldToWonderenchant", "true"),
        Map.entry("enigmaticenhance.autoSmelt.lootOnlyAcquisition", "false"),
        Map.entry("enigmaticenhance.demonPact.lethal", "true"),
        Map.entry("enigmaticenhance.travelerHat.speedMode", "\"PERCENT\""),
        Map.entry("enigmaticenhance.travelerHat.maxBiomes", "256"),
        Map.entry("enigmaticenhance.bedtimeReading.fixSeventhCurse", "true"),
        Map.entry("enigmaticenhance.demonBone.healBlockSeconds", "5"),
        Map.entry("enigmaticenhance.abyssalEarthHeart.enabled", "true"),
        Map.entry("enigmaticenhance.abyssalEarthHeart.extraTriggerItems", "[]"),
        Map.entry("enigmaticenhance.debugLog", "false"));

    private ConfigCommentSelfTest() {}

    static boolean run() {
        log(" --- T22 配置文件注释体检");
        boolean ok = true;
        int maxBlock = 0;
        List<String> tomls = new ArrayList<>();
        List<String> allCommentLines = new ArrayList<>();
        Map<String, String> config = new LinkedHashMap<>();
        try {
            for (String name : tomlNames()) {
                List<String> lines = readToml(name);
                if (lines.isEmpty()) {
                    continue;
                }
                tomls.add(name);
                maxBlock = Math.max(maxBlock, checkComments(name, lines, allCommentLines));
                if (name.endsWith("enigmaticenhance-common.toml")) {
                    parse(name, lines, config);
                }
            }
        }
        catch (Throwable t) {
            log(" T22 读取 jar 内 toml 失败: " + t);
            ok = false;
        }

        boolean termsClean = allCommentLines.stream().noneMatch(ConfigCommentSelfTest::hasTerm);
        log(" T22 扫到 toml=" + tomls + ";注释行=" + allCommentLines.size() + ";含术语=" + !termsClean
            + " [应 false];最长连续注释块=" + maxBlock + " 行 [应 <=2(不含 Forge 的 Range 行)]");
        ok &= !tomls.isEmpty() && allCommentLines.size() > 0 && termsClean && maxBlock <= 2;

        log(" T22 键值项=" + config.size() + " [应 " + EXPECTED_KEYS + "]");
        ok &= config.size() == EXPECTED_KEYS;
        int bad = 0;
        for (Map.Entry<String, String> entry : EXPECTED.entrySet()) {
            String actual = config.get(entry.getKey());
            if (!entry.getValue().equals(actual)) {
                ++bad;
                log(" T22 默认值不符: " + entry.getKey() + " 期望 " + entry.getValue() + " 实际 " + actual);
            }
        }
        log(" T22 抽查键名/默认值不符数=" + bad + " [应 0]");
        ok &= bad == 0;

        check("T22", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 读取

    /** 列出 jar(或开发目录)里的 *.toml 条目。 */
    private static List<String> tomlNames() throws Exception {
        List<String> names = new ArrayList<>();
        URL url = EnigmaticEnhance.class.getProtectionDomain().getCodeSource().getLocation();
        Path path = Paths.get(url.toURI());
        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path, 8)) {
                stream.filter(p -> p.toString().endsWith(".toml")).forEach(p ->
                    names.add(path.relativize(p).toString().replace('\\', '/')));
            }
            return names;
        }
        try (JarFile jar = new JarFile(path.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".toml")) {
                    names.add(entry.getName());
                }
            }
        }
        return names;
    }

    private static List<String> readToml(String name) throws Exception {
        URL url = EnigmaticEnhance.class.getProtectionDomain().getCodeSource().getLocation();
        Path path = Paths.get(url.toURI());
        if (Files.isDirectory(path)) {
            Path file = path.resolve(name);
            return Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
        }
        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry entry = jar.getJarEntry(name);
            if (entry == null) {
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                return lines;
            }
        }
    }

    // ------------------------------------------------------------------ 检查

    /** 收集注释行并返回该文件里"最长连续用户注释块"的行数(Forge 的 Range 行不算、但也不打断块)。 */
    private static int checkComments(String name, List<String> lines, List<String> allComments) {
        int max = 0;
        int run = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#Range:") || line.startsWith("#Default:") || line.startsWith("#Accepted values:")) {
                continue;
            }
            if (line.startsWith("#")) {
                allComments.add(line);
                run++;
                max = Math.max(max, run);
            }
            else {
                run = 0;
            }
        }
        return max;
    }

    private static boolean hasTerm(String line) {
        for (String term : TERMS) {
            if (line.contains(term)) {
                log(" T22 术语泄漏(" + term + "): " + line);
                return true;
            }
        }
        if (FQN.matcher(line).find()) {
            log(" T22 注释以全限定名开头: " + line);
            return true;
        }
        return false;
    }



    /** 解析 "[section]" + "key = value"(只用于本模组的配置文件)。 */
    private static void parse(String name, List<String> lines, Map<String, String> out) {
        String section = "";
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            out.put(section.isEmpty() ? key : section + "." + key, value);
        }
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
