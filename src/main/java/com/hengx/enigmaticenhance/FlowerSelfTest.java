package com.hengx.enigmaticenhance;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.1.19 临时自检:魔法石英花属性进书袋 tooltip(-D 开关,默认不注册)。
 * <p>
 * 覆盖:① 有激活属性时 tooltip 行含属性 id + 数值(打印实际字符串);② 无激活属性 → "无激活属性";
 * ③ 签名稳定性(时长 100s→99s 的 20 tick 零发包、属性变化只发一次);④ bagTooltipEnabled=false → 0 行;
 * ⑤ debugLog=false → 零日志;⑥ ModIds.problems()=[]。
 * <p>
 * 用 {@link FakePlayer}(无 connection)走**生产同一条**签名/发包判定路径:真实玩家时"判定=发包"1:1,
 * FakePlayer 只统计判定次数,不会真发包。
 */
public final class FlowerSelfTest {

    private static final String TAG = "[石英花自检]";

    private FlowerSelfTest() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(FlowerSelfTest.class);
        DebugLog.info(TAG + " registered");
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        var server = event.getServer();
        FakePlayer fake = null;
        try {
            injectChineseLang();
            log(" F0 ModIds.problems()=" + ModIds.problems() + " [必须空]");
            fake = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.randomUUID(), "ea_flower_test"));
            // 1.1.21:HUD 已删除,同步开关改为 tooltip 总开关
            setBool(RingConfig.BAG_TOOLTIP_ENABLED, true);
            setBool(RingConfig.DEBUG_LOG, false);

            // ---------------------------------------------------------- ① 有激活属性
            int remaining = 2000; // 100 秒
            putAttributes(fake, 20.0D, 0.1D, remaining);
            List<BagHudEffects.Entry> entries = BagHudEffects.artificialFlower(fake);
            log(" F1 激活属性条目数=" + entries.size() + " [应为 2]");
            for (BagHudEffects.Entry e : entries) {
                log(" F1-line key=" + e.effectKey() + " id=" + e.value() + " detail=" + e.detail()
                    + " active=" + e.active());
                log(" F1-line 显示字符串=" + BagHudEffects.display(e).getString());
            }
            boolean f1 = entries.size() == 2;
            for (BagHudEffects.Entry e : entries) {
                String s = BagHudEffects.display(e).getString();
                f1 &= s.contains(e.value());
            }
            f1 &= BagHudEffects.display(entries.get(0)).getString().contains("+20");
            log(" F1-RESULT " + (f1 ? "PASS" : "FAIL"));

            // ---------------------------------------------------------- ② 无激活属性
            clearAttributes(fake);
            List<BagHudEffects.Entry> none = BagHudEffects.artificialFlower(fake);
            String noAttr = BagHudEffects.display(none.get(0)).getString();
            log(" F2 无属性条目数=" + none.size() + " key=" + none.get(0).effectKey());
            log(" F2 显示字符串=" + noAttr);
            boolean f2 = none.size() == 1 && BagHudEffects.FLOWER_KEY.equals(none.get(0).effectKey())
                && noAttr.contains("无激活属性");
            log(" F2-RESULT " + (f2 ? "PASS" : "FAIL"));

            // ---------------------------------------------------------- ③ 签名稳定性
            putAttributes(fake, 20.0D, 0.1D, remaining);
            BagHudSync.resetCache();
            boolean baseline = BagHudSync.sync(fake, BagHudEffects.artificialFlower(fake));
            int base = BagHudSync.pushes();
            int sentBase = BagHudSync.sent();
            for (int tick = 0; tick < 20; tick++) {
                remaining--; // 2000 -> 1980 tick = 100s -> 99s
                putAttributes(fake, 20.0D, 0.1D, remaining);
                BagHudSync.sync(fake, BagHudEffects.artificialFlower(fake));
            }
            int afterTicks = BagHudSync.pushes();
            int sentAfterTicks = BagHudSync.sent();
            int zeroTicks = afterTicks - base;
            log(" F3 基线同步=" + baseline + " 20 tick(remaining 2000→" + remaining + ")新增发包判定=" + zeroTicks
                + " [应为 0];新增 CHANNEL.send=" + (sentAfterTicks - sentBase) + " [应为 0]"
                + "(FakePlayer 的 connection 是 Forge 的 dummy,真发时只编码不进 socket)");
            putAttributes(fake, 25.0D, 0.1D, remaining); // 属性变化一次
            BagHudSync.sync(fake, BagHudEffects.artificialFlower(fake));
            int afterChange = BagHudSync.pushes();
            int sentAfterChange = BagHudSync.sent();
            log(" F3 属性变化(+20→+25)新增发包判定=" + (afterChange - afterTicks) + " [应为 1];新增 send="
                + (sentAfterChange - sentAfterTicks) + " [应为 1,编码成功]");
            BagHudSync.sync(fake, BagHudEffects.artificialFlower(fake)); // 再调一次,不许再发
            log(" F3 内容不变再调一次新增=" + (BagHudSync.pushes() - afterChange) + " [应为 0]");
            boolean f3 = zeroTicks == 0 && sentAfterTicks == sentBase && afterChange - afterTicks == 1
                && BagHudSync.pushes() == afterChange && sentAfterChange - sentAfterTicks == 1;
            log(" F3-RESULT " + (f3 ? "PASS" : "FAIL"));

            // ---------------------------------------------------------- ④ tooltip 开关
            Item bag = RingConfig.antiqueBagItem();
            setBool(RingConfig.BAG_TOOLTIP_ENABLED, false);
            int raw = lines(new ItemStack(bag));      // 基础行数(不追加任何东西)
            BagHudSync.accept(entries);               // 缓存里已有 2 条石英花属性
            int off = lines(new ItemStack(bag));      // 关闭时:即便有缓存也不许追加
            setBool(RingConfig.BAG_TOOLTIP_ENABLED, true);
            BagHudSync.resetClientCache();
            int unsynced = lines(new ItemStack(bag)); // 未同步:只有一行"(未同步)"
            BagHudSync.accept(entries);
            int on = lines(new ItemStack(bag));
            log(" F4 基础行数(关闭 tooltip)=" + raw + " 关闭时再测=" + off + " 未同步=" + unsynced
                + " 开启(缓存 2 条)=" + on);
            for (String text : tooltipTexts(new ItemStack(bag))) {
                if (text.contains("魔法石英花")) {
                    log(" F4-tooltip 行=" + text);
                }
            }
            boolean f4 = off == raw && unsynced == raw + 3 && on == raw + 3 + entries.size();
            log(" F4-RESULT " + (f4 ? "PASS" : "FAIL") + "(关闭时 = 基础行数;未同步 = 基础 + 空行 + 标题 + 未同步行;"
                + "开启 = 基础 + 空行 + 标题 + 每条 1 行 + 汇总)");

            // ---------------------------------------------------------- ⑤ debugLog=false 零输出
            // 直接读服务端自己的 logs/latest.log(Forge 的 FileAppender)当"输出探测器":
            // 先证明 debugLog=true 时同一个动作**会**留下 "[书袋HUD]" 行,再证明 false 时一行都不留。
            setBool(RingConfig.DEBUG_LOG, true);
            int loudBefore = hudLogHits();
            putAttributes(fake, 30.0D, 0.1D, remaining);
            BagHudSync.sync(fake, BagHudEffects.artificialFlower(fake)); // 必"变化"
            hold(150L);
            int loudAfter = hudLogHits();
            setBool(RingConfig.DEBUG_LOG, false);
            hold(80L);
            int quietBefore = hudLogHits();
            putAttributes(fake, 35.0D, 0.1D, remaining);
            BagHudSync.sync(fake, BagHudEffects.artificialFlower(fake)); // 同样"变化"
            hold(150L);
            int quietAfter = hudLogHits();
            log(" F5 debugLog=true 时新增加载日志行=" + (loudAfter - loudBefore) + " [应 >=1,证明探测器有效];"
                + "debugLog=false 时同一动作新增=" + (quietAfter - quietBefore) + " [应为 0]");
            boolean f5 = loudBefore >= 0 && loudAfter - loudBefore >= 1 && quietAfter - quietBefore == 0;
            log(" F5-RESULT " + (f5 ? "PASS" : "FAIL"));
            log(" ALL-RESULT " + (f1 && f2 && f3 && f4 && f5 && ModIds.problems().isEmpty() ? "PASS" : "FAIL"));
        }
        catch (Throwable t) {
            EnigmaticEnhance.LOGGER.error(TAG + " ABORTED", t);
        }
        finally {
            if (fake != null) {
                clearAttributes(fake);
            }
            setBool(RingConfig.BAG_TOOLTIP_ENABLED, true);
            setBool(RingConfig.DEBUG_LOG, false);
            RingConfig.SPEC.save();
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "stop"));
        }
    }

    // ------------------------------------------------------------------ 构造/清除 PLAYER_ATTRIBUTE_MAP

    @SuppressWarnings("unchecked")
    private static Map<ServerPlayer, Map<ImmutableMultimap<Attribute, AttributeModifier>, Integer>> flowerMap()
        throws ReflectiveOperationException {
        Class<?> cls = Class.forName("auviotre.enigmatic.addon.contents.items.ArtificialFlower");
        return (Map<ServerPlayer, Map<ImmutableMultimap<Attribute, AttributeModifier>, Integer>>)
            cls.getField("PLAYER_ATTRIBUTE_MAP").get(null);
    }

    /** 照扩展自己的写法登记(MAX_HEALTH ADDITION / MOVEMENT_SPEED MULTIPLY_BASE)。 */
    private static void putAttributes(ServerPlayer player, double health, double speed, int remaining) {
        try {
            ImmutableMultimap<Attribute, AttributeModifier> modifiers =
                ImmutableMultimap.<Attribute, AttributeModifier>builder()
                    .put(Attributes.MAX_HEALTH,
                        new AttributeModifier(HEALTH_ID, "selftest_health", health,
                            AttributeModifier.Operation.ADDITION))
                    .put(Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(SPEED_ID, "selftest_speed", speed,
                            AttributeModifier.Operation.MULTIPLY_BASE))
                    .build();
            Map<ImmutableMultimap<Attribute, AttributeModifier>, Integer> perPlayer = flowerMap().computeIfAbsent(player,
                p -> new HashMap<>());
            perPlayer.clear();
            perPlayer.put(modifiers, remaining);
        }
        catch (Throwable t) {
            log(" 构造 PLAYER_ATTRIBUTE_MAP 失败:" + t);
        }
    }

    private static void clearAttributes(ServerPlayer player) {
        try {
            flowerMap().remove(player);
        }
        catch (Throwable ignored) {
            // 扩展缺失:什么也不用清
        }
    }

    private static final UUID HEALTH_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID SPEED_ID = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    // ------------------------------------------------------------------ 文本

    private static void injectChineseLang() throws Exception {
        Map<String, String> data = new HashMap<>();
        try (InputStream in = FlowerSelfTest.class
            .getResourceAsStream("/assets/enigmaticenhance/lang/zh_cn.json")) {
            if (in == null) {
                log(" 找不到 zh_cn.json,按未翻译字符串输出");
                return;
            }
            Language.loadFromJson(in, data::put);
        }
        Language.inject(new MapLanguage(data));
        log(" 已注入 zh_cn 语言表(" + data.size() + " 条)");
    }

    /** 只用于自检:让 Component.getString() 在没有客户端的服务端也能解析出中文。 */
    private static final class MapLanguage extends Language {

        private final Map<String, String> data;

        MapLanguage(Map<String, String> data) {
            this.data = data;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            return data.getOrDefault(key, fallback);
        }

        @Override
        public boolean has(String key) {
            return data.containsKey(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return false;
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return FormattedCharSequence.forward(text.getString(), Style.EMPTY);
        }
    }

    private static List<String> tooltipTexts(ItemStack stack) {
        ItemTooltipEvent event = new ItemTooltipEvent(stack, null, new ArrayList<>(), TooltipFlag.Default.NORMAL);
        ItemTooltips.onItemTooltip(event);
        List<String> out = new ArrayList<>();
        for (Component line : event.getToolTip()) {
            out.add(line.getString());
        }
        return out;
    }

    private static int lines(ItemStack stack) {
        return tooltipTexts(stack).size();
    }

    // ------------------------------------------------------------------ 配置 / 日志小工具

    static void setBool(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value, boolean expected) {
        for (int i = 0; i < 10; i++) {
            try {
                value.set(expected);
                if (value.get() == expected) {
                    Thread.sleep(120L);
                    return;
                }
            }
            catch (Throwable ignored) {
                // 配置还没加载完:等一会儿再试
            }
            try {
                Thread.sleep(200L);
            }
            catch (InterruptedException e) {
                return;
            }
        }
    }

    /** 数服务端日志文件里 "[书袋HUD]" 的行数(文件不存在返回 -1)。 */
    private static int hudLogHits() {
        try {
            java.nio.file.Path path = java.nio.file.Path.of("logs", "latest.log");
            if (!java.nio.file.Files.isRegularFile(path)) {
                return -1;
            }
            int hits = 0;
            for (String line : java.nio.file.Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8)) {
                if (line.contains("[书袋HUD]")) {
                    ++hits;
                }
            }
            return hits;
        }
        catch (Throwable t) {
            return -1;
        }
    }

    private static void hold(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
