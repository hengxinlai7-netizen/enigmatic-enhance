package com.hengx.enigmaticenhance;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 古旧书袋里"会提供加分"的物品清单 + 每条的加成文本(**服务端计算**,客户端只显示)。
 *
 * <h2>清单依据(逐条反编译核对,详见 1.1.16 报告)</h2>
 * 两类判定方式:
 * <ul>
 * <li><b>显式读袋</b>:{@code SuperAddonHandler#findBookInBag(player, item)} —— 直接扫袋子里的书;</li>
 * <li><b>隐式读袋</b>:{@code SuperpositionHandler#hasItem(player, item)} —— 扩展给这个方法打了
 *     {@code MixinSuperpositionHandler#hasItemMix}:对 {@code AntiqueBag.isBook(...)} 认的物品,袋里有也算"持有"。
 *     所以凡是"用 hasItem 判定某本手册"的效果,书在袋里同样生效。</li>
 * </ul>
 * 表中每一项都是"该物品在袋里时由上述两种判定生效"的效果:
 * <pre>
 * enigmaticlegacy:the_twist         AddonEventHandler#onEntityHurt  +10% 对 BOSS/玩家(需七咒之人)
 * enigmaticlegacy:the_infinitum     AddonEventHandler#onEntityHurt  +20% 对 BOSS/玩家(需义人)
 *                                   AddonEventHandler#onEntityDamage 伤害的 10% 转为治疗(需义人)
 * enigmaticaddons:the_bless         AddonEventHandler#onEntityDamage 受击无敌帧 = TheBless.invulnerableTime tick
 * enigmaticlegacy:the_acknowledgment AddonEventHandler#onEntityHurt  命中时点燃目标(非数值)
 * enigmaticaddons:living_ode        AddonEventHandler#onEntityAttacked / onLivingChangeTarget 动物系效果(非数值)
 * enigmaticaddons:sanguinary_handbook AddonEventHandler#onEntityHurt 血腥手册的在袋效果(非数值)
 * </pre>
 * "已确认在袋生效"= 上表前 4 项(有显式 findBookInBag 或 hasItem + isBook 依据);后 3 项是 hasItem 路径,
 * 报告里单独标注。**注意:本类只读,不改变任何物品/书袋的行为。**
 *
 * <h2>1.1.19:魔法石英花的随机属性</h2>
 * 石英花的随机属性**不在物品 NBT 里结算**,而是由扩展把"玩家 → 当前修饰符集合 → 剩余 tick"登记在
 * {@code auviotre.enigmatic.addon.contents.items.ArtificialFlower.PLAYER_ATTRIBUTE_MAP} 里
 * (反编译 1.2.5/1.2.6:源码见报告;onTick 每 tick 把 Integer 减 1,≤1 时从玩家属性表移除)。
 * 这里在**服务端**用反射只读它,把每条修饰符拆成 tooltip 的一行:
 * {@link #FLOWER_ATTR_KEY} + 属性 id + amount + operation + 剩余 tick(放在 {@link Entry#detail()})。
 * 该 Map 为空 ⇒ 只显示一行 {@link #FLOWER_KEY}("无激活属性")。
 */
public final class BagHudEffects {

    /**
     * 一条 HUD 数据:哪本书、什么效果、数值(可空)、当前是否满足前置条件。
     * <p>
     * {@code detail} 是 1.1.19 新增的"细分内容":目前只有魔法石英花用,格式 {@code amount|operation|remaining}
     * (剩余时长故意不参与 {@link #signature(List)} —— 见该方法注释)。
     */
    public record Entry(String bookId, String effectKey, String value, boolean active, String detail) {

        /** 旧的四参写法(没有细分内容)。 */
        public Entry(String bookId, String effectKey, String value, boolean active) {
            this(bookId, effectKey, value, active, "");
        }
    }

    private static final String LEGACY = "enigmaticlegacy:";
    private static final String ADDONS = "enigmaticaddons:";

    /** 瓶装历史迷雾:放进书袋时把记录到的属性加成列出来(1.2.5d)。 */
    public static final String HISTORY_MIST_ID = "enigmaticenhance:history_mist";

    /** 历史迷雾"某一条记录属性"那一行(参数:属性名、数值)。 */
    public static final String MIST_ATTR_KEY = "baghud.effect.history_mist.attr";

    /** 历史迷雾"还没记录"那一行。 */
    public static final String MIST_EMPTY_KEY = "baghud.effect.history_mist";

    /** 1.2.6e:其余四个瓶子的书袋条目 id。 */
    public static final String SPIRIT_LIGHT_ID = "enigmaticenhance:spirit_light";
    public static final String STYX_WATER_ID = "enigmaticenhance:styx_water";
    public static final String WAR_FOG_ID = "enigmaticenhance:war_fog";
    public static final String SUN_BOTTLE_ID = "enigmaticenhance:bottled_sun";
    public static final String FISHING_HAUL_BOTTLE_ID = "enigmaticenhance:fishing_haul_bottle";

    /** 历史迷雾只显示数值最高的几条(用户要求:3 条)。 */
    public static final int MIST_MAX_LINES = 3;

    /** 历史迷雾"还有更多记录"那一行(参数:总条数)。 */
    public static final String MIST_MORE_KEY = "baghud.effect.history_mist.more";
    public static final String FLOWER_ID = ADDONS + "artificial_flower";

    /** 石英花"没有激活属性"那一行。 */
    public static final String FLOWER_KEY = "baghud.effect.artificial_flower";

    /** 石英花"某一条激活属性"那一行(参数:属性 id、数值、类型、剩余 tick)。 */
    public static final String FLOWER_ATTR_KEY = "baghud.effect.artificial_flower.attr";

    /** 属性修饰符类型 → 语言键前缀(后缀是 operation.name() 的小写)。 */
    private static final String FLOWER_OP_KEY = "baghud.effect.artificial_flower.op.";

    /** 扩展里登记"玩家当前石英花属性"的类 + 字段名(反编译确认,见报告)。 */
    private static final String FLOWER_CLASS = "auviotre.enigmatic.addon.contents.items.ArtificialFlower";
    private static final String FLOWER_FIELD = "PLAYER_ATTRIBUTE_MAP";

    private static final List<String> BOOKS = List.of(
        LEGACY + "the_twist",
        LEGACY + "the_infinitum",
        ADDONS + "the_bless",
        LEGACY + "the_acknowledgment",
        ADDONS + "ode_to_living",
        ADDONS + "false_justice",
        FLOWER_ID,
        LEGACY + "hunter_guidebook",
        ADDONS + "sanguinary_handbook",
        "enigmaticenhance:bedtime_reading",
        HISTORY_MIST_ID,
        SPIRIT_LIGHT_ID,
        STYX_WATER_ID,
        WAR_FOG_ID,
        SUN_BOTTLE_ID,
        FISHING_HAUL_BOTTLE_ID);

    /** 清单里的物品 id(给 {@link ModIds} 做静态校验用)。 */
    public static List<String> bookIds() {
        return BOOKS;
    }

    /** 历史迷雾 id 的预解析版本(见 {@link #HISTORY_MIST_ID})。 */
    private static final ResourceLocation HISTORY_MIST_LOCATION = new ResourceLocation(HISTORY_MIST_ID);

    private BagHudEffects() {}

    /** 空结果(4.6.0:无书袋的玩家每个 tick 都走这里,不再每次 new 一个 ArrayList)。 */
    private static final List<Entry> NO_ENTRIES = List.of();

    /** 服务端:算出这名玩家书袋里所有条目(4.8.4:改为**枚举袋内全部书**,白名单只决定文案)。 */
    public static List<Entry> collect(Player player) {
        if (player == null) {
            return NO_ENTRIES;
        }
        // 4.6.0(CPU/内存):书袋不在身上时,下面所有 findBook 都只会返回空,而每次 findBook 内部
        // 都要扫一遍背包 + 饰品栏。先问一次,省掉这些扫描(以及每次 curios 查询产生的 Optional 对象)。
        final boolean hasBag = BookBag.hasBag(player);
        if (!hasBag) {
            return NO_ENTRIES;
        }
        List<Entry> out = new ArrayList<>(16);

        // 4.8.4:直接枚举袋子里的每个物品(而不是按 16 个 id 逐个问"在不在袋里")。
        // 这样"能放进去但没效果"的书也能被列出来(见 BookEffects 的判定依据)。
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (ItemStack stack : BookBag.booksInBag(player)) {
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) {
                continue;
            }
            String bookId = idText(item, id);
            if (!seen.add(bookId)) {
                continue; // 同一个 id 放了两本:只算一条(去重)
            }
            collectOne(player, bookId, stack, out);
        }

        // 本模组自己的"三书齐修正第四重咒":不属于任何物品,单独一条
        // (三本书必须在袋里 ⇒ 书袋不在身上时必然不成立,上面已经用 hasBag 短路过了)
        if (FourthCurseFix.active(player)) {
            out.add(new Entry("enigmaticenhance:fourth_curse", "baghud.effect.fourthCurse", "", true));
        }
        if (out.isEmpty()) {
            return NO_ENTRIES;
        }
        sortEntries(out);
        return out;
    }

    /**
     * 4.9.1(CPU/内存):物品 → 注册 id 文本的缓存。
     *
     * <p>{@link #collect(Player)} 每 tick 都要给袋里每本书取一次 {@code id.toString()},而
     * {@code ResourceLocation#toString()} 每次都会新建一个 {@code "ns:path"} 字符串。
     * 物品的注册 id 在游戏运行期不会变,缓存一次即可:热路径上零分配(只剩一次 map 查找)。</p>
     */
    private static final java.util.concurrent.ConcurrentHashMap<Item, String> ID_TEXT =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static String idText(Item item, ResourceLocation id) {
        String cached = ID_TEXT.get(item);
        if (cached != null) {
            return cached;
        }
        String text = id.toString();
        String previous = ID_TEXT.putIfAbsent(item, text);
        return previous != null ? previous : text;
    }

    /** 单本书 → 若干条目(4.8.4 抽出来,方便阅读与自检)。 */
    private static void collectOne(Player player, String bookId, ItemStack stack, List<Entry> out) {
        switch (bookId) {
            case FLOWER_ID -> out.addAll(artificialFlower(player));
            case HISTORY_MIST_ID -> out.addAll(historyMist(player));
            case SPIRIT_LIGHT_ID -> out.add(new Entry(bookId, "baghud.effect.spirit_light", "", true));
            case STYX_WATER_ID -> out.add(new Entry(bookId, "baghud.effect.styx_water", "", true));
            case WAR_FOG_ID -> out.add(new Entry(bookId, "baghud.effect.war_fog", "", true));
            case SUN_BOTTLE_ID -> out.add(new Entry(bookId, "baghud.effect.bottled_sun", "", true));
            case FISHING_HAUL_BOTTLE_ID -> out.add(new Entry(bookId, "baghud.effect.fishing_haul_bottle",
                "+2", true));
            case "enigmaticenhance:bedtime_reading" -> out.add(new Entry(bookId,
                "baghud.effect.bedtime_reading", "", flag(player, FLAG_CURSED_ONE)));
            default -> {
                List<BookEffects.Line> lines = BookEffects.linesFor(player, bookId);
                if (lines != null && !lines.isEmpty()) {
                    for (BookEffects.Line line : lines) {
                        out.add(new Entry(bookId, line.key(), joinArgs(line.args()), true));
                    }
                }
                else if (lines != null) {
                    // 已登记但当前条件下没有可显示的内容:按"无加成"处理,避免出现空行
                    out.add(new Entry(bookId, BookEffects.KEY_NO_BONUS, "", true));
                }
                else if (isGoeticPassiveBook(stack)) {
                    // 诡厄的书、但我们没登记文案:如实说"未收录",而不是谎称没效果
                    out.add(new Entry(bookId, BookEffects.KEY_UNKNOWN, "", true));
                }
                else {
                    out.add(new Entry(bookId, BookEffects.KEY_NO_BONUS, "", true));
                }
            }
        }
    }

    /** 参数用 Unit Separator 拼进 {@code value}(客户端按它切回数组)。 */
    private static String joinArgs(String[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return String.join(ARG_SEPARATOR, args);
    }

    /** 条目排序:有效果的在前、未收录其次、无加成最后;组内按书 id 稳定排序。 */
    private static void sortEntries(List<Entry> entries) {
        entries.sort(Comparator
            .comparingInt((Entry e) -> rankOf(e.effectKey()))
            .thenComparing(Entry::bookId));
    }

    private static int rankOf(String effectKey) {
        if (BookEffects.KEY_NO_BONUS.equals(effectKey)) {
            return 2;
        }
        if (BookEffects.KEY_UNKNOWN.equals(effectKey)) {
            return 1;
        }
        return 0;
    }

    /** 诡厄的被动书(反射判断,缺模组时恒 false)。 */
    public static boolean isGoeticPassiveBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Class<?> api = goeticPassiveApi();
        if (api == null) {
            return false;
        }
        try {
            return api.isInstance(stack.getItem());
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static Class<?> goeticPassiveApi;
    private static boolean goeticResolved;

    private static synchronized Class<?> goeticPassiveApi() {
        if (goeticResolved) {
            return goeticPassiveApi;
        }
        goeticResolved = true;
        if (!ModList.get().isLoaded(GOETIC_MODID)) {
            return null;
        }
        try {
            goeticPassiveApi = Class.forName("com.qoocies.goeticlegacy.api.passive.IPassiveBook");
        }
        catch (Throwable t) {
            goeticPassiveApi = null;
        }
        return goeticPassiveApi;
    }

    /** 参数分隔符(Unit Separator):不会出现在任何 id/数字里,客户端按它切回参数数组。 */
    public static final String ARG_SEPARATOR = "\u001F";

    /** 4.8.4:tooltip 里最多显示多少条(多出来的折成"还有 N 条")。 */
    public static final int MAX_LINES = 12;

    /** 诡厄遗物的 modid(只用于 {@code ModList.isLoaded},不 import 它的任何类)。 */
    public static final String GOETIC_MODID = "goeticlegacy";

    // ------------------------------------------------------------------ 瓶装历史迷雾(1.2.5d)

    /**
     * 书袋里的瓶装历史迷雾:把它的 NBT 记录(Category:</float>属性 id → 数值)拆成 HUD 行。
     * <p>没有记录时给一行"未记录"。只在服务端调用(由 {@link #collect(Player)} 触发)。</p>
     */
    public static List<Entry> historyMist(Player player) {
        ItemStack stack = BookBag.findBook(player, HISTORY_MIST_LOCATION);
        if (stack.isEmpty()) {
            return List.of(new Entry(HISTORY_MIST_ID, MIST_EMPTY_KEY, "", false));
        }
        Map<ResourceLocation, Double> records = HistoryMistHandler.readRecords(stack);
        if (records.isEmpty()) {
            return List.of(new Entry(HISTORY_MIST_ID, MIST_EMPTY_KEY, "", false));
        }
        return mistEntries(records);
    }

    /**
     * 记录表 → HUD 行:只显示数值最高的 {@link #MIST_MAX_LINES} 条,其余用一行"还有 N 条"带过。
     * 纯函数(不碰玩家/物品),自检可直接断言。
     */
    public static List<Entry> mistEntries(Map<ResourceLocation, Double> records) {
        List<Entry> out = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return List.of(new Entry(HISTORY_MIST_ID, MIST_EMPTY_KEY, "", false));
        }
        List<Map.Entry<ResourceLocation, Double>> sorted = new ArrayList<>(records.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int shown = 0;
        for (Map.Entry<ResourceLocation, Double> record : sorted) {
            if (shown++ >= MIST_MAX_LINES) {
                break;
            }
            out.add(new Entry(HISTORY_MIST_ID, MIST_ATTR_KEY, record.getKey().toString(), true,
                amountText(record.getValue())));
        }
        if (sorted.size() > MIST_MAX_LINES) {
            out.add(new Entry(HISTORY_MIST_ID, MIST_MORE_KEY, String.valueOf(sorted.size() - MIST_MAX_LINES),
                true, ""));
        }
        // 顺序固定(先按 key 再按数值),保证 signature 逐 tick 稳定
        out.sort(Comparator.comparing(Entry::effectKey).thenComparing(Entry::value).thenComparing(Entry::detail));
        return out;
    }

    // ------------------------------------------------------------------ 魔法石英花(1.1.19)

    /**
     * 读扩展的 {@code ArtificialFlower.PLAYER_ATTRIBUTE_MAP},把该玩家当前生效的属性修饰符拆成条目。
     * <p>
     * 只在服务端调用({@link #collect(Player)} 由 {@link BagHudSync} 在服务端 tick 里调)。
     * 反射缺类/缺字段/非 ServerPlayer ⇒ 返回"无激活属性"那一行,绝不抛异常。
     * 返回列表**至少一行**:有属性 → 每条修饰符一行;没有 → 一行 {@link #FLOWER_KEY}。
     */
    public static List<Entry> artificialFlower(Player player) {
        List<Entry> out = new ArrayList<>();
        if (player instanceof ServerPlayer serverPlayer) {
            try {
                java.util.Map<?, ?> perPlayer = playerAttributeMap(serverPlayer);
                if (perPlayer != null) {
                    for (Map.Entry<?, ?> entry : perPlayer.entrySet()) {
                        if (!(entry.getKey() instanceof com.google.common.collect.Multimap<?, ?> modifiers)) {
                            continue;
                        }
                        int remaining = entry.getValue() instanceof Integer integer ? integer : 0;
                        for (Map.Entry<?, ?> modifier : modifiers.entries()) {
                            if (!(modifier.getKey() instanceof Attribute attribute)
                                || !(modifier.getValue() instanceof AttributeModifier value)) {
                                continue;
                            }
                            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
                            out.add(new Entry(FLOWER_ID, FLOWER_ATTR_KEY,
                                id == null ? "unknown" : id.toString(), true,
                                flowerAmountText(value.getAmount(), value.getOperation()) + "|"
                                    + value.getOperation().name().toLowerCase(Locale.ROOT) + "|" + remaining));
                        }
                    }
                }
            }
            catch (Throwable t) {
                if (RingConfig.debugLog() && !flowerReadFailed) {
                    flowerReadFailed = true;
                    EnigmaticEnhance.LOGGER.warn("[书袋HUD] 读 {}#{} 失败(石英花属性将显示为无)", FLOWER_CLASS,
                        FLOWER_FIELD, t);
                }
            }
        }
        if (out.isEmpty()) {
            return List.of(new Entry(FLOWER_ID, FLOWER_KEY, "", false));
        }
        // Map 的迭代顺序不稳定(HashMap):排一次序,保证签名逐 tick 一致(否则会白发包)。
        out.sort(Comparator.comparing(Entry::value).thenComparing(Entry::detail));
        return out;
    }

    /** 反射取该玩家的 {@code Map<ImmutableMultimap<Attribute,AttributeModifier>, Integer>}(没有则 null)。 */
    private static java.util.Map<?, ?> playerAttributeMap(ServerPlayer player) throws ReflectiveOperationException {
        Field field = flowerField();
        if (field == null) {
            return null;
        }
        Object map = field.get(null);
        if (!(map instanceof java.util.Map<?, ?> all)) {
            return null;
        }
        Object perPlayer = all.get(player);
        return perPlayer instanceof java.util.Map<?, ?> entries ? entries : null;
    }

    private static Field flowerField;
    private static boolean flowerResolved;
    private static boolean flowerReadFailed;

    private static synchronized Field flowerField() {
        if (flowerResolved) {
            return flowerField;
        }
        flowerResolved = true;
        try {
            flowerField = Class.forName(FLOWER_CLASS).getField(FLOWER_FIELD);
        }
        catch (Throwable t) {
            flowerField = null; // 扩展缺失:石英花也不可能在袋里,显示"无激活属性"
        }
        return flowerField;
    }

    /** 一条石英花属性行的显示形式(客户端 tooltip / HUD 共用)。 */
    public static MutableComponent display(Entry entry) {
        if (MIST_ATTR_KEY.equals(entry.effectKey())) {
            // value = 属性 id,detail = 已经带 +/- 的数值文本
            Component name = Component.literal(entry.value());
            try {
                Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(entry.value()));
                if (attribute != null) {
                    name = Component.translatable(attribute.getDescriptionId());
                }
            }
            catch (RuntimeException ignored) {
                // 保留字面量
            }
            return Component.translatable(MIST_ATTR_KEY, name, entry.detail());
        }

        if (FLOWER_ATTR_KEY.equals(entry.effectKey())) {
            // 4.6.0(内存/CPU):原来是 split("\\|", 3) —— 带转义反斜杠会走正则编译,
            // 而本方法是**客户端每帧渲染 tooltip**时调用的(悬停在物品上就一直跑)。
            // 改成手写扫描:零 Pattern、零 String[],只在真的需要三段文本时 substring。
            String detail = entry.detail();
            int first = detail.indexOf('|');
            int second = first < 0 ? -1 : detail.indexOf('|', first + 1);
            String amount = first < 0 ? detail : detail.substring(0, first);
            String operation = first < 0 ? ""
                : (second < 0 ? detail.substring(first + 1) : detail.substring(first + 1, second));
            String remaining = second < 0 ? "" : detail.substring(second + 1);
            // 4.8.4:属性名本地化(原来是原始 id,如 goety:spell_potency)
            return Component.translatable(FLOWER_ATTR_KEY, attributeName(entry.value()), amount,
                Component.translatable(FLOWER_OP_KEY + operation), duration(remaining));
        }

        // 4.8.4:统一的书本效果行(baghud.book.<ns>.<path>.lineN),参数放在 value 里
        if (entry.effectKey().startsWith("baghud.book.")) {
            if (BookEffects.KEY_NO_BONUS.equals(entry.effectKey())
                || BookEffects.KEY_UNKNOWN.equals(entry.effectKey())) {
                return Component.translatable(entry.effectKey(), bookName(entry.bookId()));
            }
            String[] args = splitArgs(entry.value());
            Object[] formatted = new Object[args.length];
            System.arraycopy(args, 0, formatted, 0, args.length);
            return Component.translatable(entry.effectKey(), formatted);
        }
        return Component.translatable(entry.effectKey(), entry.value());
    }

    /** 属性 id → 本地化名称;取不到就退回原始 id(绝不抛)。 */
    public static Component attributeName(String attributeId) {
        try {
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(attributeId));
            if (attribute != null) {
                return Component.translatable(attribute.getDescriptionId());
            }
        }
        catch (RuntimeException ignored) {
            // 保留字面量
        }
        return Component.literal(attributeId);
    }

    /** 书 id → 物品显示名;取不到退回 id。 */
    public static Component bookName(String bookId) {
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(bookId));
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return item.getDescription();
            }
        }
        catch (RuntimeException ignored) {
            // 保留字面量
        }
        return Component.literal(bookId);
    }

    /** {@code "a\u001Fb\u001Fc"} → 参数数组(空串 → 空数组)。 */
    static String[] splitArgs(String value) {
        if (value == null || value.isEmpty()) {
            return EMPTY_ARGS;
        }
        return value.split(ARG_SEPARATOR, -1);
    }

    private static final String[] EMPTY_ARGS = new String[0];

    /**
     * 剩余时长:不足 20 tick 打 tick,≥20 tick 打秒(一位小数)。
     * <p>4.8.4:原来一律打 "N tick",20 秒会显示成 "400 tick"。文本走 lang,数字在这边算。</p>
     */
    public static Component duration(String remainingText) {
        int ticks;
        try {
            ticks = (int) Double.parseDouble(remainingText.trim());
        }
        catch (RuntimeException ignored) {
            return Component.literal(remainingText);
        }
        if (ticks < 20) {
            return Component.translatable("baghud.time.ticks", String.valueOf(ticks));
        }
        String seconds = String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
        return Component.translatable("baghud.time.seconds", seconds);
    }

    /**
     * 4.8.4:石英花修饰符数值的显示形式。
     * <ul>
     * <li>{@code MULTIPLY_BASE}/{@code MULTIPLY_TOTAL}:0.1 表示 +10% ⇒ 打成 {@code +10%};</li>
     * <li>{@code ADDITION}:保留两位小数并带符号 ⇒ {@code +20.00} / {@code -0.50}。</li>
     * </ul>
     */
    static String flowerAmountText(double amount, AttributeModifier.Operation operation) {
        if (operation == AttributeModifier.Operation.MULTIPLY_BASE
            || operation == AttributeModifier.Operation.MULTIPLY_TOTAL) {
            double percent = amount * 100.0D;
            String text = BookEffects.number(percent);
            return (percent >= 0.0D ? "+" : "") + text + "%";
        }
        return String.format(Locale.ROOT, "%+.2f", amount);
    }

    /** 数值文本:整数不带小数点、正数带 +;如 +20 / -0.5 / +0.125。 */
    static String amountText(double amount) {        String number;
        if (amount == Math.rint(amount) && Math.abs(amount) < 1.0E9D) {
            number = String.valueOf((long) amount);
        }
        else {
            number = String.format(Locale.ROOT, "%.4f", amount);
            int end = number.length();
            while (end > 1 && number.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 1 && number.charAt(end - 1) == '.') {
                end--;
            }
            number = number.substring(0, end);
        }
        return (amount >= 0.0D ? "+" : "") + number;
    }

    // ------------------------------------------------------------------ 汇总 / 签名

    /** 汇总用的"可加总"数值:当前生效的伤害加成百分比之和。 */
    public static int damageBonusSum(List<Entry> entries) {
        int sum = 0;
        for (Entry e : entries) {
            if (!e.active()) {
                continue;
            }
            if (e.effectKey().endsWith(".twist") || e.effectKey().endsWith(".infinitum")) {
                sum += parseInt(e.value());
            }
        }
        return sum;
    }

    /** {@code "+10%"} / {@code "-5"} → 整数(取不到按 0)。 */
    private static int parseInt(String text) {
        try {
            return (int) Double.parseDouble(text.replace("%", "").toLowerCase(Locale.ROOT));
        }
        catch (Throwable t) {
            return 0;
        }
    }

    public static int activeCount(List<Entry> entries) {
        int n = 0;
        for (Entry e : entries) {
            if (e.active()) {
                n++;
            }
        }
        return n;
    }

    /**
     * 内容签名:物品 + 效果 + 数值 + 生效状态(变化才同步)。
     * <p>
     * 1.1.19:石英花属性行额外并入 <b>amount + operation</b>(属性 id 在 {@code value} 里,条数在条目数里),
     * 但 <b>剩余 tick 故意不进签名</b> —— 该值每 tick 都会变,放进来会导致每 tick 发一个包;
     * 粗粒度化(remaining/20)同样只是为了减少发包,而"剩余时长"对 tooltip 内容没有意义,
     * 所以直接排除:属性不变时 100s→99s 的 20 tick 内签名恒定 ⇒ 零发包。
     */
    public static String signature(List<Entry> entries) {
        // 4.6.0(内存):复用同一个 StringBuilder(ThreadLocal)。
        // 原来每 tick × 每个玩家都新建一个 StringBuilder + 逐次扩容的 char[](签名通常 200~500 字符,
        // 扩容到 512 意味着一次 1 KB 的 char[] 垃圾);现在只在第一次真正分配,之后 setLength(0) 复用。
        StringBuilder sb = SIGNATURE_BUILDER.get();
        sb.setLength(0);
        for (Entry e : entries) {
            sb.append(e.bookId()).append('|').append(e.effectKey()).append('|')
                .append(e.value()).append('|').append(e.active());
            if (MIST_ATTR_KEY.equals(e.effectKey())) {
                sb.append('|').append(e.detail());
            }

            if (FLOWER_ATTR_KEY.equals(e.effectKey())) {
                // 4.6.0(内存/CPU):原来是 `e.detail().split("\\|", 3)` —— 带转义反斜杠的写法会走
                // Pattern.compile(每 tick 每个石英花属性行编译一次正则),再加一个 String[] 和若干 substring。
                // 这里手写扫描,只把前两段直接写进 StringBuilder,零中间对象。
                appendFirstTwoDetailFields(sb, e.detail());
            }
            sb.append(';');
        }
        return sb.toString();
    }

    /** {@link #signature(List)} 专用:把 {@code "a|b|rest"} 的 a、b 两段直接追加(不建 String[]、不用正则)。 */
    private static void appendFirstTwoDetailFields(StringBuilder sb, String detail) {
        int first = detail.indexOf('|');
        sb.append('|');
        if (first < 0) {
            sb.append(detail);   // 没有分隔符:第一段是整串,第二段为空
            sb.append('|');
            return;
        }
        sb.append(detail, 0, first);
        sb.append('|');
        int second = detail.indexOf('|', first + 1);
        if (second < 0) {
            sb.append(detail, first + 1, detail.length());
        }
        else {
            sb.append(detail, first + 1, second);
        }
    }

    /** 签名用的 StringBuilder(服务端 tick 单线程;ThreadLocal 避免自检/其它线程互相干扰)。 */
    private static final ThreadLocal<StringBuilder> SIGNATURE_BUILDER =
        ThreadLocal.withInitial(() -> new StringBuilder(256));

    // ------------------------------------------------------------------ 读取本体/扩展的真实数值(反射,缺模组安全返回)

    /** {@link #flag(Player, int)} 的判定序号(用序号代替字符串,避免每次热路径拼查找键)。 */
    private static final int FLAG_CURSED_ONE = 0;

    /** {@link #flag(Player, int)} 对应的扩展方法名,下标与 FLAG_* 一致。 */
    private static final String[] FLAG_NAMES = { "isTheCursedOne" };

    /** 可能提供这两个判定的两个类(本体 / 扩展)。 */
    private static final String[] FLAG_OWNERS = {
        "com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler",
        "auviotre.enigmatic.addon.handlers.SuperAddonHandler" };

    /**
     * 4.6.0(内存/CPU):解析结果缓存 —— {@code [owner][flag]}。
     *
     * <p>原来 {@link #flag} 每次被调用都在循环里做两次 {@code Class.forName(...)} + {@code getMethod(...)}:
     * 而它由 {@link #collect(Player)} 在服务端 tick 里按玩家调用(书袋里有《扭曲》《无限》时才走到,
     * 但那正是这类玩家的常态),等于每 tick 每人次 2~4 次类加载器查找。缓存后只在第一次解析。</p>
     */
    private static final java.lang.reflect.Method[][] FLAG_METHODS =
        new java.lang.reflect.Method[FLAG_OWNERS.length][FLAG_NAMES.length];
    private static volatile boolean flagsResolved;

    private static synchronized void resolveFlags() {
        if (flagsResolved) {
            return;
        }
        flagsResolved = true;
        for (int owner = 0; owner < FLAG_OWNERS.length; owner++) {
            try {
                Class<?> cls = Class.forName(FLAG_OWNERS[owner]);
                for (int which = 0; which < FLAG_NAMES.length; which++) {
                    try {
                        FLAG_METHODS[owner][which] = cls.getMethod(FLAG_NAMES[which], Player.class);
                    }
                    catch (Throwable ignored) {
                        // 该方法不在这个类里:保持 null,换下一个类
                    }
                }
            }
            catch (Throwable ignored) {
                // 模组缺失:保持 null
            }
        }
    }

    /** 本体/扩展的布尔判定(isTheCursedOne)。 */
    private static boolean flag(Player player, int which) {
        if (!flagsResolved) {
            resolveFlags();
        }
        for (java.lang.reflect.Method[] methods : FLAG_METHODS) {
            java.lang.reflect.Method method = methods[which];
            if (method == null) {
                continue;
            }
            try {
                Object value = method.invoke(null, player);
                if (value instanceof Boolean b) {
                    return b;
                }
            }
            catch (Throwable ignored) {
                // 调用失败:试下一个类(与原来的顺序一致)
            }
        }
        return false;
    }
}
