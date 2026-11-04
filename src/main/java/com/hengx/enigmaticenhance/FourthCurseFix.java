package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 「七咒之戒第四重咒」的修正(纯**额外效果**,不改动/不拦截那三本书自身的任何作用)。
 *
 * <h2>第四重咒是哪一条</h2>
 * 神秘遗物本体自己的书页 {@code book.enigmaticlegacy.entry.cursed_ring.page.4} 明确编号:
 * <pre>第四重咒 —— 削弱你对怪物的攻击力量</pre>
 * 对应的 tooltip 顺序 {@code tooltip.enigmaticlegacy.cursedRing7} = "你对怪物的伤害降低 %s%"。
 * 实现方式是事件而非属性:{@code EnigmaticEventHandler#onEntityHurt(LivingHurtEvent)} 里,
 * 受害者是 {@code Monster}/{@code EnderDragon}、攻击者是玩家且 {@code SuperpositionHandler.isTheCursedOne} 时,
 * {@code event.setAmount(amount * CursedRing.monsterDamageDebuff.getValue().asModifierInverted())}
 * ({@code MonsterDamageDebuff} 默认 50 ⇒ ×0.5);主手拿着 倒转之启/无止之言/邪术之锅 时本体自己会跳过这一步。
 *
 * <h2>修正条件</h2>
 * 总开关开 + 佩戴七咒之戒(curio)+ 古旧书袋在玩家身上 + 袋内**同时**有 倒转之启、恩惠之典、无止之言。
 * 任一条不满足时立刻回到原版(本类不保留任何状态,判定是每次事件即时算的)。
 *
 * <h2>修正方式</h2>
 * 只把"第四重咒的那个惩罚系数"换成中性值:见 {@code FourthCurseMixin} /
 * {@code FourthCurseAddonMixin}(拦的是同一个 {@code CursedRing.monsterDamageDebuff} 字段读取),
 * 其余一切(第一重咒的受伤加倍、第三重咒的护甲削减、三本书各自的在袋效果……)都不碰。
 */
public final class FourthCurseFix {

    /**
     * 本次 {@code LivingHurtEvent} 处理期间"是否把第四重咒的惩罚换成修正值"。
     * 在 HEAD 注入处写、在同一方法里读取字段的地方读,同一次调用内完成;ThreadLocal 保证线程安全。
     */
    private static final ThreadLocal<Boolean> NEUTRALIZE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private FourthCurseFix() {}

    /** HEAD 注入调用:根据这次事件算好修正与否。 */
    public static void decide(LivingHurtEvent event) {
        NEUTRALIZE.set(active(attacker(event)));
    }

    /** 字段读取注入调用:是否用修正值。 */
    public static boolean neutralizing() {
        return NEUTRALIZE.get();
    }

    /** 本次事件的攻击者(没有玩家攻击者时为 null)。 */
    public static Player attacker(LivingHurtEvent event) {
        return event.getSource().getEntity() instanceof Player player ? player : null;
    }

    /** 完整生效条件(每次即时计算,不缓存任何状态)。 */
    public static boolean active(Player player) {
        if (player == null || !RingConfig.fourthCurseFixEnabled()) {
            return false;
        }
        Item ring = RingConfig.cursedRingItem();
        if (ring == null || !Superposition.hasCurio(player, ring)) {
            return false; // 没戴七咒之戒
        }
        // 书袋必须在玩家身上;三本书必须都在袋里(两者都由扩展的 findBookInBag 一并判定)
        return BookBag.hasBook(player, RingConfig.THE_TWIST_ID)
            && BookBag.hasBook(player, RingConfig.THE_BLESS_ID)
            && BookBag.hasBook(player, RingConfig.THE_INFINITUM_ID);
    }

    /** 修正后仍然保留的惩罚百分比(0 = 完全不削减)。 */
    public static int residualPercent() {
        return (int) Math.round(RingConfig.fourthCurseFixResidualPercent());
    }
}
