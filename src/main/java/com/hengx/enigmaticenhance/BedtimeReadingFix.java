package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 「睡前读物」修正七咒之戒**第七重咒**的全部判定逻辑(1.1.28b)。
 *
 * <h2>第七重咒到底是哪一条</h2>
 * 七咒之戒的七条诅咒在 tooltip 里从 {@code tooltip.enigmaticlegacy.cursedRing4} 开始逐条列出
 * ({@code cursedRing3} 是那行"七个诅咒将会降临到佩戴者身上："的表头)。本体自己的书页
 * {@code book.enigmaticlegacy.entry.cursed_ring.page.4} 把第 4 条称作"第四重咒",
 * 对应 {@code tooltip.enigmaticlegacy.cursedRing7}("你对怪物的伤害降低"),可见
 * <b>tooltip 键号 = 重咒序号 + 3</b>。于是:
 * <pre>
 * cursedRing4  受到的伤害加倍                第 1 重咒
 * cursedRing5  中立生物主动攻击你            第 2 重咒
 * cursedRing6  盔甲效力降低                  第 3 重咒
 * cursedRing7  你对怪物的伤害降低            第 4 重咒  &lt;- 本项目既有的 FourthCurseFix
 * cursedRing8  一旦着火就永远燃烧            第 5 重咒
 * cursedRing9  每次死亡灵魂破裂              第 6 重咒
 * cursedRing10 无药可医的失眠症              第 7 重咒  &lt;- 本类修正
 * </pre>
 *
 * <h2>失眠是怎么实现的(反编译 Enigmaticlegacy 2.30.1 核实)</h2>
 * 它既不是属性也不是事件,而是 {@code EnigmaticEventHandler#onPlayerTick} 里的<b>每 tick 行为</b>:
 * <pre>
 * if (!CursedRing.disableInsomnia.getValue())
 *     if (player.isSleeping() &amp;&amp; SuperpositionHandler.hasCurio(player, CURSED_RING)) {
 *         if (player.getSleepTimer() == 5)  { 发一条"无法入睡"的聊天提示 }
 *         else if (player.getSleepTimer() &gt; 90) { player.sleepCounter = 90; }   // &lt;= 真正的惩罚
 *     }
 * </pre>
 * 原版睡觉是 {@code sleepCounter} 从 0 涨到 100 才算"睡够";这一段每 tick 把它夹回 90,
 * 于是永远睡不够、夜里永远跳不过去 —— 这就是"失眠"。
 *
 * <h2>怎么修</h2>
 * 见 {@code EnigmaticEventHandlerSleepMixin}:在该方法里把两处 {@code Player#getSleepTimer()} 调用
 * 重定向到 {@link #effectiveSleepTimer(Player, int)},只改这一次比较用的读数:
 * <ul>
 * <li>== 5 → 6:不再触发那条"无法入睡"提示;</li>
 * <li>&gt; 90 → 90:不再把 {@code sleepCounter} 夹回 90,睡觉得以正常完成;</li>
 * <li>其它值原样返回,所以这一段里的任何其它逻辑(以及方法里其它所有诅咒)都不受影响。</li>
 * </ul>
 * 也就是说:只把"第七重咒的那一条惩罚"变成中性,其余六重咒、七咒之戒本身、
 * 书袋与书、以及本项目的血杯/第四重咒修正全都照旧。判定是每次调用即时算的,不缓存任何状态。
 *
 * <h2>生效条件(每次即时计算)</h2>
 * 总开关开 + 佩戴七咒之戒({@code curios},沿用 {@link Superposition#hasCurio}) +
 * (书在**古旧书袋**里(复用 {@link BookBag#findBook}) **或** 主手/副手拿着这本书)。
 */
public final class BedtimeReadingFix {

    private BedtimeReadingFix() {}

    /** 完整生效条件(每次即时计算,不缓存)。 */
    public static boolean active(Player player) {
        if (player == null || !RingConfig.bedtimeReadingFixesSeventhCurse()) {
            return false;
        }
        Item ring = RingConfig.cursedRingItem();
        if (ring == null || !Superposition.hasCurio(player, ring)) {
            return false; // 没戴七咒之戒:第七重咒本来就不存在
        }
        Item book = ModItems.bedtimeReadingOrNull();
        if (book == null) {
            return false;
        }
        return inBag(player) || inHand(player, book);
    }

    /** 书在古旧书袋里(书袋必须在玩家身上,口径与扩展自己的 {@code findBookInBag} 一致)。 */
    public static boolean inBag(Player player) {
        ItemStack found = BookBag.findBook(player, RingConfig.BEDTIME_READING_ID);
        return found != null && !found.isEmpty();
    }

    /** 书在**主手或副手**上。 */
    public static boolean inHand(Player player, Item book) {
        return player.getMainHandItem().is(book) || player.getOffhandItem().is(book);
    }

    /**
     * 给 {@code EnigmaticEventHandler#onPlayerTick} 里那次 {@code getSleepTimer()} 读数用的修正值。
     * <p>
     * 未生效 ⇒ 原值(与不打补丁逐指令一致);生效 ⇒ 让它既不等于 5(不提示)、也不大于 90(不夹 sleepCounter)。
     * 除了这两个比较点,返回值不会被用到别处。
     */
    public static int effectiveSleepTimer(Player player, int vanilla) {
        if (player == null || !active(player)) {
            return vanilla;
        }
        if (vanilla == 5) {
            return 6;
        }
        return Math.min(vanilla, 90);
    }
}
