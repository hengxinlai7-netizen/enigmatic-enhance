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

    /** 魔法石英花(既是书袋物品 id,也是 {@link #collect} 里的分支)。 */
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

    /**
     * 4.6.0(内存):与 {@link #BOOKS} 逐项对应的 {@link ResourceLocation},<b>静态建好一次</b>。
     *
     * <p>原来 {@link #collect(Player)} 里写的是 {@code new ResourceLocation(id)},而这个方法在
     * {@link BagHudSync#onServerTick} 里<b>每 tick、每个在线玩家</b>都要跑一遍全部 16 个 id:
     * 20 tps × 16 = <b>每人每秒 320 次</b>字符串解析 + 校验 + 新对象(每个 ≈ 40~64B,含 String 里的
     * path 段),一个 10 人服就是每秒 3200 个对象、约 150 KB/s 的纯垃圾,全部落在 Young 区。
     * 预解析之后这条路径上这部分分配归零,而且 ResourceLocation 的构造校验也一次都不做了。</p>
     */
    private static final List<ResourceLocation> BOOK_LOCATIONS;

    /** 历史迷雾 id 的预解析版本(见 {@link #HISTORY_MIST_ID})。 */
    private static final ResourceLocation HISTORY_MIST_LOCATION = new ResourceLocation(HISTORY_MIST_ID);

    static {
        ResourceLocation[] locations = new ResourceLocation[BOOKS.size()];
        for (int i = 0; i < BOOKS.size(); i++) {
            locations[i] = new ResourceLocation(BOOKS.get(i));
        }
        BOOK_LOCATIONS = List.of(locations);
    }

    private BagHudEffects() {}

    /**
     * 4.6.1(CPU):与 {@link #BOOKS} 逐项对应的物品实例,<b>懒解析一次就缓存</b>。
     *
     * <p>{@link #collect(Player)} 在 {@code BagHudSync#onServerTick} 里每 tick、每个在线玩家都要按
     * 16 个 id 查一次书袋,而 {@code BookBag.findBook(player, ResourceLocation)} 每次都要先
     * {@code ForgeRegistries.ITEMS.getValue(id)}:重算 {@code ResourceLocation} 的 hash + 查一次 map。
     * 4.6.0 已经把 {@code new ResourceLocation(id)} 换成静态 {@link #BOOK_LOCATIONS},这里再把
     * "id → Item"这一段也缓存掉,热路径上只剩反射进 {@code findBookInBag}(那一步没法绕开)。
     * </p>
     * <p>解析失败(注册表还没就绪 / 该模组缺失)时<b>不缓存失败</b>,下次调用重试 —— 与原来
     * {@code getValue} 返回 null 的分支语义相同。</p>
     */
    private static final Item[] BOOK_ITEMS = new Item[BOOKS.size()];

    /** 取清单里第 {@code index} 本书的物品实例(解析不到返回 null)。 */
    private static Item bookItem(int index) {
        Item cached = BOOK_ITEMS[index];
        if (cached != null) {
            return cached;
        }
        try {
            Item found = ForgeRegistries.ITEMS.getValue(BOOK_LOCATIONS.get(index));
            if (found != null && found != net.minecraft.world.item.Items.AIR) {
                BOOK_ITEMS[index] = found;
                return found;
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 注册表没就绪:本 tick 跳过这一本(等价于原来 getValue 返回 null)
        }
        return null;
    }

    /** 空结果(4.6.0:无书袋的玩家每个 tick 都走这里,不再每次 new 一个 ArrayList)。 */
    private static final List<Entry> NO_ENTRIES = List.of();

    /** 服务端:算出这名玩家书袋里所有"会提供加成"的条目。 */
    public static List<Entry> collect(Player player) {
        if (player == null) {
            return NO_ENTRIES;
        }
        // 4.6.0(内存):列表改成"第一次真的要加条目时才建",并预置容量。
        // 绝大多数玩家书袋里没有这几本书 ⇒ 一次分配都不发生(原来恒定 1 个 ArrayList/人/tick)。
        List<Entry> out = null;
        // 4.6.0(CPU/内存):书袋不在身上时,下面 16 次 findBook 全部只会返回空,而每次 findBook 内部
        // 都要扫一遍背包 + 饰品栏(扩展自己的前置判据就是 SuperpositionHandler.hasItem)。
        // 先问一次,省掉这 16 遍扫描(以及每次 curios 查询产生的 Optional 对象)。
        //
        // 4.6.1:这个结果下面"第四重咒修正"那条也要用(它要求三本书都在袋里 ⇒ 书袋必须在身上),
        // 所以提到外面算一次、两处共用。
        final boolean hasBag = BookBag.hasBag(player);
        if (hasBag) {
            for (int index = 0; index < BOOKS.size(); index++) {
                String id = BOOKS.get(index);
                // 4.6.1:物品实例也缓存(见 BOOK_ITEMS):省掉每 tick 每人 16 次注册表查找。
                Item bookItem = bookItem(index);
                if (bookItem == null || BookBag.findBook(player, bookItem).isEmpty()) {
                    continue;
                }
                if (out == null) {
                    out = new ArrayList<>(6);
                }
                switch (id) {
                    case "enigmaticlegacy:the_twist" -> out.add(new Entry(id, "baghud.effect.twist", "10%",
                        flag(player, FLAG_CURSED_ONE)));
                    case "enigmaticlegacy:the_infinitum" -> {
                        boolean worthy = flag(player, FLAG_WORTHY_ONE);
                        out.add(new Entry(id, "baghud.effect.infinitum", "20%", worthy));
                        out.add(new Entry(id, "baghud.effect.infinitumLifesteal", "10%", worthy));
                    }
                    case "enigmaticaddons:the_bless" -> out.add(new Entry(id, "baghud.effect.bless",
                        String.valueOf(blessInvulnerableTime()), true));
                    case FLOWER_ID -> out.addAll(artificialFlower(player));
                    case HISTORY_MIST_ID -> out.addAll(historyMist(player));
                    case SPIRIT_LIGHT_ID -> out.add(new Entry(id, "baghud.effect.spirit_light", "", true));
                    case STYX_WATER_ID -> out.add(new Entry(id, "baghud.effect.styx_water", "", true));
                    case WAR_FOG_ID -> out.add(new Entry(id, "baghud.effect.war_fog", "", true));
                    case SUN_BOTTLE_ID -> out.add(new Entry(id, "baghud.effect.bottled_sun", "", true));
                    case FISHING_HAUL_BOTTLE_ID -> out.add(new Entry(id, "baghud.effect.fishing_haul_bottle",
                        "+2", true));
                    case "enigmaticenhance:bedtime_reading" -> out.add(new Entry(id,
                        "baghud.effect.bedtime_reading", "", flag(player, FLAG_CURSED_ONE)));
                    default -> out.add(new Entry(id, "baghud.effect." + id.substring(id.indexOf(':') + 1),
                        "", true));
                }
            }
        }
        // 本模组自己的"三书齐修正第四重咒":不属于任何物品,单独一条
        //
        // 4.6.1(CPU):原来这个判断**每 tick 每玩家**都跑,而它内部要做 1 次 curios 查询(七咒之戒)
        // + 3 次 BookBag.hasBook —— 而 hasBook 走的是扩展的 findBookInBag,每一次都会完整扫一遍
        // "背包 + 饰品栏 + 末影箱"。也就是说:**没有书袋的玩家**(绝大多数)每 tick 也白白被扫 3 遍。
        // 但三本书必须在袋里,书袋不在身上时它必然不成立 ⇒ 直接用上面已经算过的 hasBag 短路。
        if (hasBag && FourthCurseFix.active(player)) {
            if (out == null) {
                out = new ArrayList<>(4);
            }
            out.add(new Entry("enigmaticenhance:fourth_curse", "baghud.effect.fourthCurse", "", true));
        }
        return out == null ? NO_ENTRIES : out;
    }

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
                                amountText(value.getAmount()) + "|"
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
            return Component.translatable(FLOWER_ATTR_KEY, entry.value(), amount,
                Component.translatable(FLOWER_OP_KEY + operation), remaining);
        }
        return Component.translatable(entry.effectKey(), entry.value());
    }

    /** 数值文本:整数不带小数点、正数带 +;如 +20 / -0.5 / +0.125。 */
    static String amountText(double amount) {
        String number;
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

    /** {@link #flag(Player, int)} 的两种判定(用序号代替字符串,避免每次热路径拼查找键)。 */
    private static final int FLAG_CURSED_ONE = 0;
    private static final int FLAG_WORTHY_ONE = 1;

    /** {@link #flag(Player, int)} 对应的扩展方法名,下标与 FLAG_* 一致。 */
    private static final String[] FLAG_NAMES = { "isTheCursedOne", "isTheWorthyOne" };

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

    /** 本体/扩展的布尔判定,如 isTheCursedOne / isTheWorthyOne。 */
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

    /**
     * 恩惠之典在袋时的受击无敌帧(AI:取扩展真实配置值)。
     *
     * <p>4.6.0(内存/CPU):字段与 {@code getValue()} 都缓存。原来每次调用(书袋里有《恩惠之典》时
     * <b>每 tick 每玩家</b>一次)都做 {@code Class.forName} + {@code getField} + {@code getMethod},
     * 三次反射解析。解析失败(缺扩展)时保持 null,返回 0 —— 与原来的 catch 分支一致。</p>
     */
    private static java.lang.reflect.Field blessField;
    private static java.lang.reflect.Method blessGetValue;
    private static Class<?> blessValueClass;
    private static boolean blessResolved;

    private static int blessInvulnerableTime() {
        try {
            if (!blessResolved) {
                blessResolved = true;
                try {
                    blessField = Class.forName("auviotre.enigmatic.addon.contents.items.TheBless")
                        .getField("invulnerableTime");
                }
                catch (Throwable t) {
                    blessField = null;
                }
            }
            if (blessField == null) {
                return 0;
            }
            Object param = blessField.get(null);
            if (param == null) {
                return 0;
            }
            // getValue() 声明在 param 的运行时类型上:按类型缓存一次(类型变了就重解析,不会串)
            if (blessGetValue == null || blessValueClass != param.getClass()) {
                blessValueClass = param.getClass();
                blessGetValue = blessValueClass.getMethod("getValue");
            }
            Object value = blessGetValue.invoke(param);
            return value instanceof Integer i ? i : parseInt(String.valueOf(value));
        }
        catch (Throwable t) {
            return 0;
        }
    }

    private static int parseInt(String text) {
        try {
            return (int) Double.parseDouble(text.replace("%", "").toLowerCase(Locale.ROOT));
        }
        catch (Throwable t) {
            return 0;
        }
    }
}
