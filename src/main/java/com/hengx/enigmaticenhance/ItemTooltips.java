package com.hengx.enigmaticenhance;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 给被本模组改动过的**原版物品**追加说明(1.1.25d 起精简,1.1.25f 起带来源名与状态)。
 * <ul>
 * <li>每行统一前缀 {@code · [神秘遗物增强] }(英文 {@code [Enigmatic Legacy Enhance]}),一眼看出是本模组加的;</li>
 * <li>每个物品最多 2 行,只写"改了什么",不写原因/机制;</li>
 * <li>**逐件开关**(状态存在物品 NBT 上、Ctrl+左键 切换)的条目,行尾必须带实时状态
 *     {@code 当前:开启(Ctrl+左键 切换)} / {@code 当前:关闭(Ctrl+左键 切换)};</li>
 * <li>没有逐件开关的条目(纯全局配置)不加状态;古旧书袋的 {@code baghud.*} 说明保持原样。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemTooltips {

    private static final ChatFormatting BODY = ChatFormatting.GRAY;

    /** 4.6.1(帧率):"这个物品该走哪条 tooltip 分支"的编号(见 {@link #kindOf(Item)})。 */
    private static final int KIND_OTHER = 0;
    private static final int KIND_RING = 1;
    private static final int KIND_CURSED_RING = 2;
    private static final int KIND_ENGINE = 3;
    private static final int KIND_GLUTTONY = 4;
    private static final int KIND_PEARL = 5;
    private static final int KIND_ADVENTURE_CHARM = 6;
    private static final int KIND_DESPAIR_INSIGNIA = 7;
    private static final int KIND_THUNDER_SCROLL = 8;
    private static final int KIND_ANTIQUE_BAG = 9;
    private static final int KIND_BERSERK_CHARM = 10;
    private static final int KIND_EARTH_HEART = 11;

    private ItemTooltips() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        List<Component> lines = event.getToolTip();

        int kind = kindOf(item);
        switch (kind) {
            case KIND_RING -> {
                List<Component> parts = new ArrayList<>();
                if (RingConfig.ringUnbreakable()) {
                    parts.add(line("add.ring.unbreakable"));
                }
                if (RingConfig.allowRingWithCursedRing()) {
                    parts.add(line("add.ring.withCursed"));
                }
                if (RingConfig.hideBetrayalBar()) {
                    parts.add(line("add.ring.hideBar"));
                }
                add(lines, join(parts, ";"));
            }
            case KIND_CURSED_RING -> {
                List<Component> parts = new ArrayList<>();
                if (RingConfig.allowRingWithCursedRing()) {
                    parts.add(line("add.cursed.withBless"));
                }
                if (RingConfig.fourthCurseFixEnabled()) {
                    parts.add(body("add.cursed.fourthCurse", FourthCurseFix.residualPercent()));
                }
                add(lines, join(parts, ";"));
            }
            case KIND_ENGINE -> {
                if (RingConfig.disableEngineLightningItemClear()) {
                    add(lines, line("add.engine.lightning"));
                }
            }
            case KIND_GLUTTONY -> {
                if (RingConfig.gluttonyEnabled()) {
                    add(lines, body("add.gluttony", RingConfig.gluttonyLockedFoodLevel(),
                        trim(RingConfig.gluttonyLockedSaturation())));
                }
            }
            case KIND_PEARL -> {
                // 1.1.25g:最多 2 行 —— 第一行写效果(附魔台/重铸),第二行写前置条件。
                List<Component> parts = new ArrayList<>();
                if (RingConfig.pearlFixEnchantingTable()) {
                    net.minecraft.network.chat.MutableComponent enchant = line("add.pearl.enchant").copy();
                    enchant.append(Component.translatable("tooltip.enigmaticenhance.add.pearl.apotheosis"));
                    parts.add(enchant);
                }
                if (RingConfig.pearlFreeReforge()) {
                    parts.add(line("add.pearl.reforge"));
                }
                Component effects = join(parts, ";");
                if (effects != null) {
                    add(lines, effects);
                    add(lines, line("add.pearl.condition"));
                }
            }
            case KIND_ADVENTURE_CHARM -> {
                List<Component> parts = new ArrayList<>();
                if (RingConfig.adventureCharmAllowMining() && RingConfig.miningTooltip()) {
                    parts.add(line("add.mining"));
                }
                if (RingConfig.convertOnDeath()) {
                    parts.add(line("add.adventure.convert"));
                }
                add(lines, join(parts, ";"));
            }
            case KIND_DESPAIR_INSIGNIA -> {
                if (RingConfig.despairInsigniaAllowMining() && RingConfig.miningTooltip()) {
                    add(lines, line("add.mining"));
                }
            }
            case KIND_THUNDER_SCROLL -> {
                if (RingConfig.scaleEnabled()) {
                    // 扩展那句"横扫攻击造成 100% 伤害"已经被我们改掉了,先从提示里去掉,免得自相矛盾。
                    String stale = Component.translatable("tooltip.enigmaticaddons.thunderScroll1").getString();
                    lines.removeIf(line -> stale.equals(line.getString()));
                    add(lines, body("add.thunder", percent(RingConfig.scaleAttackDamagePercent() / 100.0D),
                        state(!ScrollHandler.isLightningDisabled(stack))));
                    if (RingConfig.scaleChargeSpread()) {
                        add(lines, line("add.thunder.spread"));
                    }
                }
            }
            case KIND_ANTIQUE_BAG -> {
                // 1.1.25d 起:古旧书袋的说明保持原样(用户明确要求不要动),连分隔空行都不变。
                if (RingConfig.bagTooltipEnabled()) {
                    lines.addAll(bagTooltipLines());
                }
            }
            case KIND_BERSERK_CHARM -> {
                if (RingConfig.bloodbathLockEnabled()) {
                    add(lines, body("add.berserk", healthText(BloodbathHandler.lockValue()),
                        state(!BloodbathHandler.isLockDisabled(stack))));
                }
            }
            default -> {
                if (BooksHandler.isTargetBook(stack)) {
                    if (RingConfig.booksEnabled()) {
                        if (BooksHandler.isDisabled(stack)) {
                            add(lines, line("add.books.disabled"));
                            lines.set(0, lines.get(0).copy().withStyle(BODY));
                        }
                        else if (RingConfig.booksToggleEnabled()) {
                            add(lines, line("add.books.toggle"));
                        }
                    }
                }
                else if (kind == KIND_EARTH_HEART) {
                    // 1.2.2:深渊侵蚀态是叠在「大地之心」这个物品上的一个标记,所以说明也写在这个物品上
                    // (与本体「受污染的大地之心」只多一行说明的做法一致,不新增物品、不改名字)。
                    if (RingConfig.abyssalEarthHeartEnabled() && AbyssalEarthHeart.isAbyssalTainted(stack)) {
                        add(lines, line("add.earthHeart.abyssal"));
                    }
                }
            }
        }
    }

    /**
     * 4.6.1(帧率):一次注册表反查 + 常量比对,得出"这个物品属于哪条 tooltip 分支"。
     *
     * <p>原来这里是一串 {@code RingConfig.isRing/isCursedRing/isEngine/...},共 <b>11 次</b>
     * {@code itemOrNull(id)},而每次 {@code itemOrNull} 都要
     * {@code ForgeRegistries.ITEMS.containsKey(id)} + {@code getValue(id)} 两次注册表查找
     * (每次都要重算 {@code ResourceLocation} 的 hash)= <b>22 次 map 查找/帧</b>。
     * {@code ItemTooltipEvent} 在"鼠标悬停在物品上"期间<b>每帧</b>触发(60~200 次/秒),
     * 而这 22 次查找的结果对同一个物品永远是同一个值。
     *
     * <p>现在只做 1 次反查({@code getKey})拿到 id,再和 {@link RingConfig} 里那些**静态 final 的**
     * {@code ResourceLocation} 常量比较(字符串 equals,先比引用)。判定口径与原来逐字一致:
     * 原判定就是 "这个物品实例 == 注册表里该 id 的物品",而"注册表里该 id 的物品 == 手上这个物品"
     * 等价于"手上这个物品注册在该 id 上"。物品缺失时反查出的 id 也不会等于那些常量 ⇒ 同样返回 OTHER。</p>
     */
    private static int kindOf(Item item) {
        if (item == null) {
            return KIND_OTHER;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            return KIND_OTHER;
        }
        if (RingConfig.RING_ID.equals(id)) {
            return KIND_RING;
        }
        if (RingConfig.CURSED_RING_ID.equals(id)) {
            return KIND_CURSED_RING;
        }
        if (RingConfig.ENGINE_ID.equals(id)) {
            return KIND_ENGINE;
        }
        if (RingConfig.GLUTTONY_CHARM_ID.equals(id)) {
            return KIND_GLUTTONY;
        }
        if (RingConfig.ENCHANTER_PEARL_ID.equals(id)) {
            return KIND_PEARL;
        }
        if (RingConfig.ADVENTURE_CHARM_ID.equals(id)) {
            return KIND_ADVENTURE_CHARM;
        }
        if (RingConfig.DESPAIR_INSIGNIA_ID.equals(id)) {
            return KIND_DESPAIR_INSIGNIA;
        }
        if (RingConfig.THUNDER_SCROLL_ID.equals(id)) {
            return KIND_THUNDER_SCROLL;
        }
        if (RingConfig.ANTIQUE_BAG_ID.equals(id)) {
            return KIND_ANTIQUE_BAG;
        }
        if (RingConfig.BERSERK_CHARM_ID.equals(id)) {
            return KIND_BERSERK_CHARM;
        }
        if (RingConfig.EARTH_HEART_ID.equals(id)) {
            return KIND_EARTH_HEART;
        }
        return KIND_OTHER;
    }

    // ------------------------------------------------------------------ 书袋 tooltip 行缓存

    /** 缓存的"已建好"的书袋行(不可变列表,直接整体追加)。 */
    private static List<Component> bagLines = List.of();

    /** 建缓存时用的数据源({@link BagHudSync#cache()} 的<b>实例</b>;内容一变服务端就会换一个新实例)。 */
    private static List<BagHudEffects.Entry> bagLinesSource;

    /** 建缓存时的"是否已同步"状态。 */
    private static boolean bagLinesSynced;

    /**
     * 4.6.1(帧率):书袋 tooltip 行的缓存(按"内容实例 + 同步状态"失效)。
     *
     * <p>原来这段代码写在 {@link #onItemTooltip} 的 {@code isAntiqueBag} 分支里:悬停在书袋上期间
     * <b>每帧</b>都要重新走一遍 —— 为每条 {@link BagHudEffects.Entry} 建一个新的
     * {@code Component}({@code BagHudEffects.display} 里还可能建 {@code ResourceLocation}、做
     * {@code substring}、反查属性/附魔注册表),再建摘要行、逐个 {@code withStyle}(每次 {@code withStyle}
     * 又是一个新对象),60~200 帧/秒 ⇒ 每秒上千个短命对象,全部落在 Young 区,直接推高 GC 频率。
     *
     * <p>而 {@code BagHudSync} 只在**内容真的变了**的时候才替换 {@code cache}(客户端 <code>accept</code>
     * 里是 {@code List.copyOf}),所以"数据源实例没变"就等于"这些行一个字都不会变"。缓存后每帧只剩
     * 一次引用比较 + 一次 {@code addAll}。{@code Component} 是不可变的,多次把同一批实例追加进不同的
     * tooltip 列表是安全的(渲染只读)。</p>
     */
    private static List<Component> bagTooltipLines() {
        List<BagHudEffects.Entry> source = BagHudSync.cache();
        boolean synced = BagHudSync.synced();
        List<Component> cached = bagLines;
        if (cached != null && source == bagLinesSource && synced == bagLinesSynced) {
            return cached;
        }
        List<Component> out = new ArrayList<>(source.size() + 4);
        out.add(Component.empty());
        out.add(Component.translatable("baghud.title").withStyle(ChatFormatting.AQUA));
        if (!synced) {
            out.add(Component.translatable("baghud.unsynced").withStyle(BODY));
        }
        else if (source.isEmpty()) {
            out.add(Component.translatable("baghud.empty").withStyle(BODY));
        }
        else {
            for (BagHudEffects.Entry entry : source) {
                out.add(BagHudEffects.display(entry)
                    .withStyle(entry.active() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            }
            out.add(Component.translatable("baghud.summary",
                String.valueOf(BagHudEffects.damageBonusSum(source)),
                String.valueOf(BagHudEffects.activeCount(source)))
                .withStyle(ChatFormatting.GOLD));
        }
        List<Component> built = List.copyOf(out);
        // 先写"这份行属于哪个数据源",最后写行本身:这样读到新行的线程一定也看到配对好的 source/synced。
        bagLinesSource = source;
        bagLinesSynced = synced;
        bagLines = built;
        return built;
    }

    /** 追加一行 {@code · [神秘遗物增强] …}(空内容直接跳过,绝不产生空行)。 */
    private static void add(List<Component> lines, Component body) {
        if (body == null) {
            return;
        }
        String text = body.getString();
        if (text == null || text.isBlank()) {
            return;
        }
        lines.add(Component.translatable("tooltip.enigmaticenhance.added", body).withStyle(BODY));
    }

    /** 一条"改了什么"的正文(不带前缀)。 */
    private static Component line(String key) {
        return Component.translatable("tooltip.enigmaticenhance." + key);
    }

    /** 带参数的正文。 */
    private static Component body(String key, Object... args) {
        return Component.translatable("tooltip.enigmaticenhance." + key, args);
    }

    /** 逐件开关的实时状态。 */
    private static Component state(boolean on) {
        return Component.translatable(on
            ? "tooltip.enigmaticenhance.state.on"
            : "tooltip.enigmaticenhance.state.off");
    }

    /** 把若干片段拼成一段。 */
    private static Component join(List<Component> parts, String separator) {
        if (parts.isEmpty()) {
            return null;
        }
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(separator));
            }
            out.append(parts.get(i));
        }
        return out;
    }

    private static String percent(double value) {
        double pct = value * 100.0D;
        if (pct == Math.floor(pct)) {
            return String.format(Locale.ROOT, "%d", (long) pct);
        }
        return String.format(Locale.ROOT, "%.1f", pct);
    }

    /** 生命值/饱和度的短文本(1.0 → "1",1.5 → "1.5")。 */
    private static String healthText(float value) {
        if (value == Math.floor(value)) {
            return String.format(Locale.ROOT, "%d", (long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String trim(float value) {
        return healthText(value);
    }
}
