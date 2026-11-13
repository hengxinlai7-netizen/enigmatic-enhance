package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.1.14 修复:把雷击电荷扩散给"原本会被横扫打到"的附近目标。
 *
 * <h2>根因(逐指令核对过)</h2>
 * <ol>
 * <li>扩展 {@code AddonEventHandler#onEntityHurt} 的电荷累积条件是:<b>攻击者戴着卷轴 + 主手能
 *     {@code SWORD_SWEEP}(剑)</b>,然后给<b>本次伤害事件的受害者</b>加 {@code 60 + rand(80) + 伤害*10}。
 *     也就是说"附近目标要涨电荷,前提是它们各自吃到了伤害事件"。</li>
 * <li>原版 {@code Player#attack} 的横扫伤害是 {@code f4 = 1.0F + 横扫倍率 * 攻击伤害}
 *     (official 映射字节码:偏移 608 {@code fconst_1} → 610 {@code getSweepingDamageRatio} → 613 {@code fload_2} → 614 {@code fmul} → 615 {@code fadd})。
 *     扩展原本把倍率抬到 {@code max(1.0, 原值)},所以横扫能打出与攻击伤害同级的伤害,每条命中都产生伤害事件。</li>
 * <li>1.1.12 把"+100% 横扫伤害"换成"+100% 攻击伤害"后,本模组让横扫倍率回到原版公式
 *     ({@code level>0 ? level/(level+1) : 0},没附魔时 <b>0</b>),于是横扫伤害恒为 <b>1.0</b>。</li>
 * <li>{@code LivingEntity#hurt} 在无敌帧内的判定是 {@code if (amount <= this.lastHurt) return false;}
 *     (official 映射字节码:偏移 248–257 {@code invulnerableTime > 10} → 260–267 检查 BYPASSES_COOLDOWN
 *     → 270–276 {@code amount <= lastHurt} → 279–280 {@code ireturn false})。
 *     附近目标的 {@code lastHurt} 通常 ≥1.0(它们刚被上一次横扫/平砍打过),所以 <b>1.0 的横扫伤害被直接丢弃</b>:
 *     既没有伤害、也没有 {@code LivingHurtEvent} → 扩展的电荷永远不会落到它们头上。</li>
 * </ol>
 * 结果:落雷只能靠"直接被打的那一只"攒电荷,而落雷又要求目标周围 2.2 格内没有戴卷轴的玩家
 * ({@code onTick} 的判定),玩家贴身输出时永远不满足 —— 表现就是"攻击再多次也完全不落雷"。
 *
 * <h2>修法(纯额外效果)</h2>
 * 不再恢复横扫伤害,而是**由本模组补上"给附近目标加电荷"这一步**:命中时按原版横扫同一套判定取附近目标
 * ({@code ItemStack#getSweepHitBox(player, victim)}),对它们施加与扩展**完全相同**的电荷公式,
 * <b>不造成任何伤害、不改动它们的血量/击退</b>,因此既不违背"横扫伤害改成攻击伤害"的既定需求,
 * 也让落雷恢复工作。开关:{@code scaleOfProtectionChargeSpread}(默认 true);
 * 想完全回到 1.1.12 之前的横扫手感可开 {@code scaleOfProtectionRestoreSweepBoost}。
 *
 * <h2>4.8.2:开关没关住的问题</h2>
 * <p>用户反馈「关闭雷击还是能打出来」。这条扩散路径是<b>第二条</b>加电荷的路径,
 * 以前它只看 {@code scaleChargeSpread()} 和逐件 NBT,没有统一走
 * {@link ScrollHandler#lightningAllowed} 那个总闸;现在:</p>
 * <ul>
 *   <li>方法开头先过 {@code lightningAllowed(player)}(总开关 + 雷击子开关 + 逐件开关);</li>
 *   <li>循环里逐目标再确认一次(配置可能中途重载);</li>
 *   <li>残留电荷由 {@link ThunderChargeGuard} 在开关关闭时清空,避免"关了还劈"。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LightningChargeSpread {

    private LightningChargeSpread() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        // 4.8.2:第一道闸 —— 总开关 + 雷击子开关 + 逐件开关(Ctrl+左键)。
        // 之前只靠 lightningActiveScroll() 里的 scaleChargeSpread(),一旦配置组合不同
        // (或者取不到饰品栏里的卷轴)就会漏放,导致"关了雷击照样给附近目标加电荷 → 照样落雷"。
        if (!ScrollHandler.lightningAllowed(player)) {
            return;
        }
        ItemStack scroll = ScrollHandler.lightningActiveScroll(player);
        if (scroll.isEmpty()) {
            return; // 没戴卷轴 / 雷击被关 / 扩散开关关
        }
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.canPerformAction(ToolActions.SWORD_SWEEP)) {
            return; // 与扩展一致:只有"能横扫的主手物品"才会累积电荷
        }
        LivingEntity victim = event.getEntity();
        for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class,
            weapon.getSweepHitBox(player, victim))) {
            if (nearby == player || nearby == victim || player.isAlliedTo(nearby)) {
                continue; // 受害者自己由扩展处理,别重复加
            }
            // 4.8.2:逐个目标再确认一次(循环里配置可能被重载)
            if (!ScrollHandler.lightningAllowed(player)) {
                return;
            }
            if (!ScrollHandler.rollLightningChance()) {
                continue;
            }
            CompoundTag tag = nearby.getPersistentData();
            tag.putInt("Electric", tag.getInt("Electric")
                + ScrollHandler.chargeIncrement(player.getRandom(), event.getAmount()));
        }
        if (RingConfig.debugLog()) {
            DebugLog.info("[卷轴雷击] 横扫电荷扩散:攻击者 {} 伤害 {} 附近目标已加电荷",
                player.getGameProfile().getName(), event.getAmount());
        }
    }
}
