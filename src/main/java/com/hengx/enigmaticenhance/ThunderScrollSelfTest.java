package com.hengx.enigmaticenhance;

import java.util.Collection;
import java.util.UUID;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * 4.8.4 临时自检({@code -Denigmaticenhance.selftest=scroll}):验证「万钧之护卷轴」从
 * <b>属性修饰符</b>改成<b>直接增伤</b>后的真实数值。
 *
 * <p>每条都用<b>血条实际掉了多少</b>判定,不看日志措辞:</p>
 * <ul>
 *   <li>A1 戴卷轴、配置 100% → 一发 20 点攻击,僵尸掉血 40;</li>
 *   <li>A2 不戴卷轴 → 掉血 20(原样);</li>
 *   <li>A3 总开关 {@code scaleOfProtectionEnabled=false} → 掉血 20;</li>
 *   <li>A3b 逐件 NBT「雷击已关闭」→ 掉血仍是 40(雷击开关只管雷击,不关增伤 —— 设计说明);</li>
 *   <li>A4 <b>关键</b>:{@code generic.attack_damage} 属性面板戴/不戴完全相同;</li>
 *   <li>A5 穿钻石全套的僵尸 → 伤害仍然被护甲衰减(直接增伤 ≠ 穿甲);</li>
 *   <li>A6 配置 {@code scaleOfProtectionAttackDamagePercent=50} → 掉血 30;</li>
 *   <li>A7 负向对照:恶魔的手骨(本来就是直接增伤)→ 同一套口径,属性面板同样不动。</li>
 * </ul>
 *
 * <h2>B 组(加严:新行为/边界)</h2>
 * <ul>
 *   <li>B1 箭矢/投掷物({@code DamageSource.arrow},direct=箭,causing=玩家)→ ×2;非玩家 owner 的箭 / 无 owner
 *       的伤害 → 不加成(证明只认"玩家是 source entity");</li>
 *   <li>B2 横扫:按原版 {@code Player#attack} 横扫分支同一套调用({@code 1 + getSweepingDamageRatio*攻击力},
 *       对周围每个目标一次 {@code hurt(playerAttack, sweep)})→ 每个目标都 ×2、总伤害 ×2、每个目标只有
 *       一次事件(不是 ×4);</li>
 *   <li>B3 力量 II:验证叠加顺序到底落在哪一层(结论见日志:力量是属性修饰符,本来就在事件金额里);</li>
 *   <li>B4 锋利 V:锋利平伤是"读属性之后"才加的,验证它在新口径下是否被翻倍;</li>
 *   <li>B5 连续开关 20 次:每一次都断言当前状态的伤害(开=40/关=20),无残留;</li>
 *   <li>B6 卸下再戴上 5 次:每次属性面板都不变,最后一次攻击仍是 40;</li>
 *   <li>B7 配置热重载:运行中改 {@code scaleAttackDamagePercent}(250 → 70、0 → 20、100 → 40),
 *       不重启、不调用任何"重挂"代码,下一击立刻生效;</li>
 *   <li>B8 其它模组读攻击力:多条读取路径(值/修饰符表)戴与不戴完全相同。</li>
 * </ul>
 *
 * <h2>C 组(负向对照:证明测试本身能失败)</h2>
 * <ul>
 *   <li>C1 把乘数临时改成 1.0({@code percent=0})→ 掉血回到 20,再改回 100 → 40;</li>
 *   <li>C2 完全不戴卷轴 → 掉血 = 基础 20。</li>
 * </ul>
 *
 * <p>测试目标一律把 {@code MAX_HEALTH} 拉到 200、{@code ARMOR} 归零(除非 A5 故意穿甲),
 * 这样"攻击 20 点"就是干净的 20 点,数值表可以直接对;A5 保留钻石全套的原版护甲公式
 * (20 点护甲 / 8 点韧性 → 20 点原始伤害衰减成 8,40 点衰减成 24)。</p>
 */
public final class ThunderScrollSelfTest {

    private static final String TAG = "[ThunderScrollSelfTest]";

    /** 与其它自检同一块场地高度(超平坦上方的空域)。 */
    private static final int Y = 110;

    /** 一发的原始伤害。 */
    private static final float SWING = 20.0F;

    private static boolean allPass = true;

    private static MinecraftServer server;
    private static FakePlayer player;
    private static ServerLevel level;

    /** 卷轴当前放在哪个饰品槽(charm/hands/…)。 */
    private static String scrollSlot;

    /** 改配置前的原值,收尾时还原。 */
    private static boolean oldScaleEnabled;
    private static boolean oldLightningEnabled;
    private static boolean oldCritEnabled;
    private static boolean oldDebugLog;
    private static double oldPercent;
    private static double oldCritChance;

    private ThunderScrollSelfTest() {}

    static void register() {
        try {
            MinecraftForge.EVENT_BUS.register(ThunderScrollSelfTest.class);
            DebugLog.info(TAG + " registered");
        }
        catch (Throwable t) {
            EnigmaticEnhance.LOGGER.error(TAG + " 注册失败", t);
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        run(event.getServer());
    }

    public static void run(MinecraftServer srv) {
        server = srv;
        try {
            level = srv.overworld();
            level.getChunk(0, 0);
            level.setChunkForced(0, 0, true);
            // 正午的僵尸会自燃(自检里 A5 要给僵尸穿甲,tick 几下就可能点火),直接设成午夜。
            level.setDayTime(18000L);

            player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ea_scroll_test"));
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(8.5D, Y, 8.5D, 0.0F, 0.0F);
            player.getInventory().clearContent();
            buildArena();
            baseAttr = attr();   // 什么都没戴时的攻击力面板基准
            removeExternalCritChance();

            Item scroll = RingConfig.thunderScrollItem();
            if (scroll == null) {
                fail("A0", "物品 enigmaticaddons:thunder_scroll 不存在(没装神秘遗物扩展?)");
                stop();
                return;
            }

            oldScaleEnabled = RingConfig.SCALE_ENABLED.get();
            oldLightningEnabled = RingConfig.SCALE_LIGHTNING_ENABLED.get();
            oldCritEnabled = RingConfig.ALL_DAMAGE_CRIT_ENABLED.get();
            oldDebugLog = RingConfig.DEBUG_LOG.get();
            oldPercent = RingConfig.SCALE_ATTACK_DAMAGE_PERCENT.get();
            oldCritChance = RingConfig.ALL_DAMAGE_CRIT_CHANCE.get();

            // 打开模组自己的调试日志:万一有 1.5 倍的暴击混进来,能直接从 [Crit] 行看出是不是我们自己的
            setBool(RingConfig.DEBUG_LOG, true);

            setBool(RingConfig.SCALE_ENABLED, true);
            // 关掉雷击:它会往目标身上累积电荷,自检不需要,也避免"雷击伤害"混进掉血数。
            setBool(RingConfig.SCALE_LIGHTNING_ENABLED, false);
            // 关掉"全伤害暴击":它会把数值随机 ×1.5,让 20/40 这类干净数字对不上。
            // 概率直接归零 —— 这套环境里 allDamageCritEnabled() 的读取会偶发回落到 true(它的 catch
            // 分支返回 true),只关开关不够稳;概率 0 时 PlayerCritHandler 里 `nextFloat() >= 0` 必然成立,
            // 从构造上就打不出暴击。
            setBool(RingConfig.ALL_DAMAGE_CRIT_ENABLED, false);
            setDouble(RingConfig.ALL_DAMAGE_CRIT_CHANCE, 0.0D);
            setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, 100.0D);
            // 注意:这里**故意不调用 RingConfig.SPEC.save()** —— 保存会改文件 mtime,Forge 的配置监视器
            // 随后异步重读文件,把运行中 set() 进去的值按文件回滚(实测会让 B7 的 0% 又变回 100%)。
            // 需要的取值靠 setBool/setDouble 的内存写入 + 每次"写完立刻断言"保证。
            hold(150L);
            log(" 0 配置: 总开关=" + RingConfig.scaleEnabled()
                + " 雷击=" + RingConfig.scaleLightningEnabled()
                + " 暴击=" + RingConfig.allDamageCritEnabled()
                + " 增伤%=" + RingConfig.scaleAttackDamagePercent()
                + " 面板(空手)=" + f((float) attr()));

            ItemStack scrollStack = new ItemStack(scroll);

            // ---------------------------------------------------------- A1/A2/A3/A3b
            testDirectNumbers(scrollStack);

            // ---------------------------------------------------------- A4 属性面板
            testAttributePanel(scrollStack);

            // ---------------------------------------------------------- A5 护甲
            testArmor(scrollStack);

            // ---------------------------------------------------------- A6 配置 50%
            testHalfPercent(scrollStack);

            // ---------------------------------------------------------- A7 恶魔手骨
            testDemonBone();

            // ---------------------------------------------------------- B1 箭矢
            testArrow(scrollStack);

            // ---------------------------------------------------------- B2 横扫
            testSweep(scrollStack);

            // ---------------------------------------------------------- B3 力量 / B4 锋利
            testStrength(scrollStack);
            testSharpness(scrollStack);

            // ---------------------------------------------------------- B5 连续开关 / B6 反复戴卸
            testToggleStability(scrollStack);
            testReequip(scrollStack);

            // ---------------------------------------------------------- B7 配置热重载 / B8 其它模组读面板
            testHotConfig(scrollStack);
            testAttributeReads(scrollStack);

            // ---------------------------------------------------------- C1 / C2 负向对照
            testNegativeControls(scrollStack);

            log(" summary => " + (allPass ? "PASS" : "FAIL"));
        }
        catch (Throwable t) {
            allPass = false;
            EnigmaticEnhance.LOGGER.error(TAG + " ABORTED", t);
            log(" ABORTED " + t);
        }
        finally {
            restoreConfig();
            stop();
        }
    }

    // ------------------------------------------------------------------ A1 / A2 / A3 / A3b

    private static void testDirectNumbers(ItemStack scrollStack) {
        // ---- A1 戴卷轴 + 100%
        scrollSlot = equip(player, scrollStack);
        boolean worn = scrollSlot != null;
        Zombie z1 = plainTarget();
        float a1 = hit(z1, SWING);
        a1Unarmored = a1;
        pass("A1", "戴卷轴(" + scrollSlot + "槽,hasCurio=" + worn + " 增伤=" + ScrollHandler.attackDamageActiveFor(player)
            + ") 攻击 20 → 掉血 " + f(a1) + " [应 40]", worn && near(a1, 40.0F));
        z1.discard();

        // ---- A2 不戴卷轴
        boolean off = unquip();
        Zombie z2 = plainTarget();
        float a2 = hit(z2, SWING);
        pass("A2", "不戴卷轴(摘掉=" + off + ") 攻击 20 → 掉血 " + f(a2) + " [应 20]",
            off && near(a2, SWING));
        z2.discard();

        // ---- A3 总开关关闭(卷轴还戴着)
        scrollSlot = equip(player, scrollStack);
        setBool(RingConfig.SCALE_ENABLED, false);
        boolean gateOff = !ScrollHandler.attackDamageActiveFor(player);
        Zombie z3 = plainTarget();
        float a3 = hit(z3, SWING);
        pass("A3", "总开关 scaleOfProtectionEnabled=false(仍戴着卷轴,闸门判定=" + gateOff + ") 攻击 20 → 掉血 "
            + f(a3) + " [应 20]", gateOff && near(a3, SWING));
        z3.discard();
        setBool(RingConfig.SCALE_ENABLED, true);

        // ---- A3b 逐件 NBT「雷击已关闭」:只关雷击,不关增伤(设计说明,不是回归)
        ItemStack live = slotStack();
        boolean wrote = live != null && !live.isEmpty();
        if (wrote) {
            ScrollHandler.setLightningDisabled(live, true);
        }
        boolean nbtOff = ScrollHandler.isLightningDisabled(equippedScroll());
        Zombie z3b = plainTarget();
        float a3b = hit(z3b, SWING);
        pass("A3b", "逐件 NBT 雷击已关闭=" + nbtOff + "(只写在饰品栏里那张卷轴上,写入成功=" + wrote
            + ";雷击开关只管雷击) 攻击 20 → 掉血 " + f(a3b) + " [设计上应仍为 40]",
            nbtOff && near(a3b, 40.0F));
        z3b.discard();
        if (wrote) {
            ScrollHandler.setLightningDisabled(live, false);
        }
    }

    // ------------------------------------------------------------------ A4 属性面板

    /**
     * A4 是这次改动的关键证据:属性修饰符没了 ⇒ 面板数值戴/不戴完全一致。
     *
     * <p>两条独立证据:</p>
     * <ol>
     *   <li>{@code getAttributeValue(ATTACK_DAMAGE)} 前后相等(而且都等于基础值 1.0);</li>
     *   <li>直接给这张卷轴发一次 {@link CurioAttributeModifierEvent}(<b>旧代码就是在这里
     *       {@code addModifier(ATTACK_DAMAGE, ...)}</b>),事件里不再出现任何 ATTACK_DAMAGE 修饰符。
     *       这条不依赖 Curios 的"换饰品后重算属性"时机,是确定性的。</li>
     * </ol>
     */
    private static void testAttributePanel(ItemStack scrollStack) {
        double noScroll = attr();
        String slot = equip(player, scrollStack);
        double withScroll = attr();
        // 面板里也不能残留旧 UUID 的修饰符(旧存档/热重载残留的最后一道保险)
        boolean noOldUuid = !hasModifier(Attributes.ATTACK_DAMAGE, LEGACY_UUID);
        boolean eventClean = curiosEventHasNoAttackModifier(scrollStack);
        boolean same = Math.abs(withScroll - noScroll) < 1.0E-6D;
        pass("A4", "攻击力面板 不戴=" + f((float) noScroll) + " 戴=" + f((float) withScroll) + " [" + slot + "槽]"
            + " 差值=" + f((float) Math.abs(withScroll - noScroll))
            + "; 旧修饰符残留=" + !noOldUuid + "; CurioAttributeModifierEvent 里有 ATTACK_DAMAGE 修饰符="
            + !eventClean + " [三者都应为\"相同/无/无\"]", slot != null && same && noOldUuid && eventClean);
        unquip();
    }

    /** 手动发一次 Curios 的属性修饰符事件,回答"我们还会往 ATTACK_DAMAGE 上加东西吗"。 */
    private static boolean curiosEventHasNoAttackModifier(ItemStack scrollStack) {
        try {
            SlotContext ctx = new SlotContext("charm", player, 0, false, true);
            Multimap<Attribute, AttributeModifier> original =
                ImmutableMultimap.<Attribute, AttributeModifier>of();

            CurioAttributeModifierEvent event = new CurioAttributeModifierEvent(
                scrollStack.copy(), ctx, UUID.randomUUID(), original);
            MinecraftForge.EVENT_BUS.post(event);
            Collection<AttributeModifier> added = event.getModifiers().get(Attributes.ATTACK_DAMAGE);
            log(" A4 明细:事件里 ATTACK_DAMAGE 修饰符数=" + added.size() + "(含别的模组加的)");
            return added.isEmpty();
        }
        catch (Throwable t) {
            log(" A4 发 CurioAttributeModifierEvent 异常: " + t);
            return false;
        }
    }

    // ------------------------------------------------------------------ A5 护甲衰减

    private static void testArmor(ItemStack scrollStack) {
        // 对照:不戴卷轴、钻石全套(扩展的护甲增伤 ThunderScroll.modify 只在"戴卷轴"时生效,所以这条是纯护甲)
        Zombie c = plainTarget();
        diamondSet(c);
        int armorC = c.getArmorValue();
        float control = hit(c, SWING);
        float controlFinal = lastFinal;
        c.discard();

        // 戴卷轴、同样的钻石全套
        equip(player, scrollStack);
        Zombie t = plainTarget();
        diamondSet(t);
        int armorT = t.getArmorValue();
        float boosted = hit(t, SWING);
        float boostedFinal = lastFinal;
        t.discard();
        unquip();

        // 戴卷轴 + 无护甲:一键对照(前护甲 = 后护甲 = 40,说明"没护甲时一点不减")
        boolean ok = armorC > 0 && armorT == armorC
            && control > 0.0F && control < SWING                 // 护甲把 20 砍下来了
            && boosted > 0.0F && boosted < boostedFinal          // 护甲把"被加成的 80"也砍下来了
            && near(a1Unarmored, 40.0F);
        log(" A5 明细:戴卷轴时扩展自己的 ThunderScroll.modify 会按目标护甲加伤(20 护甲 → ×2),"
            + "所以戴卷轴的钻石僵尸前护甲 = " + f(boostedFinal) + " = 20 ×2(我们) ×2(扩展护甲增伤)");
        log(" A5 明细:护甲衰减由隔离服的 attributeslib.cfg 公式给出(默认 a/(a+armor),"
            + "a = if(damage<20, 10, 10+(damage-20)/2)):20 伤害 a=10 → 20×10/30 = 6.67;"
            + "80 伤害 a=40 → 80×40/60 = 53.33");
        pass("A5", "钻石全套(护甲=" + armorT + ") 不戴卷轴 20→" + f(control) + "(前护甲 " + f(controlFinal) + ")"
            + " / 戴卷轴 20→" + f(boosted) + "(前护甲 " + f(boostedFinal) + ")"
            + " / 无护甲戴卷轴 → " + f(a1Unarmored)
            + " [要点:护甲把 " + f(boostedFinal) + " 砍到 " + f(boosted) + ",直接增伤不是穿甲]", ok);
    }

    // ------------------------------------------------------------------ A6 配置 50%

    private static void testHalfPercent(ItemStack scrollStack) {
        equip(player, scrollStack);
        setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, 50.0D);
        double percent = RingConfig.scaleAttackDamagePercent();
        Zombie z = plainTarget();
        float dealt = hit(z, SWING);
        z.discard();
        pass("A6", "配置 scaleOfProtectionAttackDamagePercent=" + f((float) percent) + " 攻击 20 → 掉血 "
            + f(dealt) + " [应 30]", near(dealt, 30.0F));
        setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, 100.0D);
        unquip();
    }

    // ------------------------------------------------------------------ A7 负向对照:恶魔的手骨

    /**
     * 恶魔的手骨本来就是"直接乘这一击",用同样的原子口径跑一遍:证明两条路径同构
     * (都改 {@code LivingHurtEvent} 的 amount、都不动 ATTACK_DAMAGE 属性)。
     *
     * <p>手骨对"目标最大生命 &gt; 自身最大生命"额外 +30%(自检目标 200 血、玩家 20 血 ⇒ ×1.3),
     * 目标命中前没有任何负面效果(⇒ 只有 ×1.3 这一档),所以事件金额 20 → 26;此外手骨在三个条件
     * 同时成立时会补一发"目标最大生命 1%"的<b>真实伤害</b>(直写血量、不走事件),200×1% = 2,
     * 所以血条总共掉 28 = 26 + 2。</p>
     */
    private static void testDemonBone() {
        Item bone = ModItems.demonHandBoneOrNull();
        if (bone == null) {
            fail("A7", "物品 demon_hand_bone 不存在");
            return;
        }
        boolean offFirst = !Superposition.hasCurio(player, RingConfig.thunderScrollItem());
        double before = attr();
        String slot = equip(player, new ItemStack(bone));
        boolean worn = DemonBoneHandler.wearing(player);
        double after = attr();
        Zombie z = plainTarget();
        int debuffsBefore = DemonBoneHandler.countHarmful(z);
        float dealt = hit(z, SWING);
        float eventFinal = lastFinal;
        z.discard();
        unquip();
        boolean same = Math.abs(after - before) < 1.0E-6D;
        pass("A7", "恶魔手骨(" + slot + "槽,wearing=" + worn + ") 攻击 20 → 事件金额 " + f(eventFinal)
            + " [应 26 = 20×1.3] + 手骨 1% 生命真实伤害 2 → 血条掉 " + f(dealt) + " [应 28];属性面板 "
            + f((float) before) + " → " + f((float) after) + " [应相同,和卷轴同一个口径];目标命中前负面数="
            + debuffsBefore,
            offFirst && worn && near(eventFinal, 26.0F) && near(dealt, 28.0F) && same);
    }

    // ------------------------------------------------------------------ B1 箭矢 / 投掷物

    /**
     * B1:弹射物。用<b>和原版箭矢命中完全相同的 DamageSource</b>
     * ({@code DamageSources#arrow(arrow, owner)}:direct=箭、causing=射手)打一发。
     *
     * <p>这正是新行为与旧行为的差别所在:属性修饰符只抬"攻击力属性",箭的伤害读的是
     * {@code power} 附魔,所以以前箭<b>不</b>吃加成;现在按 source entity 判定,玩家射出的箭会 ×2。</p>
     */
    private static void testArrow(ItemStack scrollStack) {
        // 不戴卷轴(对照)
        unquip();
        Arrow controlArrow = arrowOf(player);
        Zombie c = plainTarget();
        float control = hitWith(player.damageSources().arrow(controlArrow, player), c, SWING);
        c.discard();

        // 戴卷轴
        String slot = equip(player, scrollStack);
        Arrow arrow = arrowOf(player);
        Zombie t = plainTarget();
        float boosted = hitWith(player.damageSources().arrow(arrow, player), t, SWING);
        t.discard();

        // 非玩家射出的箭:不能吃加成
        Zombie shooter = plainTarget();
        Arrow mobArrow = arrowOf(shooter);
        Zombie t2 = plainTarget();
        float fromMob = hitWith(player.damageSources().arrow(mobArrow, shooter), t2, SWING);
        t2.discard();
        shooter.discard();

        // 没有 source entity 的伤害:不能吃加成
        Zombie t3 = plainTarget();
        float noEntity = hitWith(player.damageSources().generic(), t3, SWING);
        t3.discard();
        unquip();

        pass("B1", "箭矢 DamageSource.arrow(direct=箭,causing=玩家)[" + slot + "槽] 不戴=" + f(control)
            + " 戴=" + f(boosted) + " 倍数=" + f(boosted / control) + " [应 2.0];"
            + "非玩家射手=" + f(fromMob) + " [应 20];无 source entity=" + f(noEntity) + " [应 20]",
            near(control, 20.0F) && near(boosted, 40.0F) && near(fromMob, 20.0F) && near(noEntity, 20.0F));
    }

    private static Arrow arrowOf(LivingEntity owner) {
        Arrow arrow = new Arrow(net.minecraft.world.entity.EntityType.ARROW, level);
        arrow.setOwner(owner);
        return arrow;
    }

    // ------------------------------------------------------------------ B2 横扫

    /**
     * B2:横扫。原版 {@code Player#attack} 的横扫分支是
     * <pre>float f3 = 1.0F + EnchantmentHelper.getSweepingDamageRatio(this) * f;   // f = 冷却满时的攻击力
     * for (LivingEntity e : level.getEntitiesOfClass(...target.getSweepHitBox...))
     *     e.hurt(this.damageSources().playerAttack(this), f3);</pre>
     * 这里用<b>同一套调用</b>复现(每个被横扫目标一次 {@code playerAttack} 命中)。
     * 不走完整的 {@code player.attack()},是因为 FakePlayer 没有真实动作状态
     * ({@code attackStrengthTicker}/{@code onGround}/{@code isSprinting})时原版横扫前置条件不稳定,
     * 那样测出来的是"前置条件"而不是"伤害结算"。
     */
    private static void testSweep(ItemStack scrollStack) {
        float ratio = net.minecraft.world.item.enchantment.EnchantmentHelper.getSweepingDamageRatio(player);
        float attackDamage = (float) attr();
        float sweep = 1.0F + ratio * attackDamage;

        // 不戴卷轴:3 个目标
        unquip();
        float controlEach = 0.0F;
        float controlTotal = 0.0F;
        int controlEvents = 0;
        Zombie[] controlTargets = sweepTargets(3);
        for (Zombie z : controlTargets) {
            float dealt = hitWith(player.damageSources().playerAttack(player), z, sweep);
            controlEach = dealt;
            controlTotal += dealt;
            controlEvents += hurtPosts;
            z.discard();
        }

        // 戴卷轴:3 个目标
        String slot = equip(player, scrollStack);
        float boostedEach = 0.0F;
        float boostedTotal = 0.0F;
        int boostedEvents = 0;
        float boostedMax = 0.0F;
        Zombie[] boostedTargets = sweepTargets(3);
        for (Zombie z : boostedTargets) {
            float dealt = hitWith(player.damageSources().playerAttack(player), z, sweep);
            boostedEach = dealt;
            boostedTotal += dealt;
            boostedMax = Math.max(boostedMax, dealt);
            boostedEvents += hurtPosts;
            z.discard();
        }
        unquip();

        boolean ok = near(controlEach, sweep)                    // 对照就是横扫值本身
            && near(boostedEach, sweep * 2.0F)                   // 每个目标都吃 ×2
            && near(boostedTotal, controlTotal * 2.0F)           // 总伤害 ×2
            && near(boostedMax, sweep * 2.0F)                    // 没有任何目标是 ×4
            && controlEvents == 3 && boostedEvents == 3;         // 每个目标只有一次事件(不重复乘)
        log(" B2 明细:横扫公式 1 + " + f(ratio) + "×" + f(attackDamage) + " = " + f(sweep)
            + ";每目标事件次数 对照=" + controlEvents + " 戴卷轴=" + boostedEvents);
        pass("B2", "横扫[" + slot + "槽] 每目标:不戴=" + f(controlEach) + " 戴=" + f(boostedEach)
            + " / 总伤害:不戴=" + f(controlTotal) + " 戴=" + f(boostedTotal) + "(倍数 "
            + f(boostedTotal / controlTotal) + ") / 单目标最大值=" + f(boostedMax)
            + " [每个都 ×2、总 ×2、无 ×4]", ok);
    }

    private static Zombie[] sweepTargets(int count) {
        Zombie[] out = new Zombie[count];
        for (int i = 0; i < count; i++) {
            Zombie z = new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
            double angle = (i - 1) * 0.9D;
            z.moveTo(8.5D + Math.sin(angle) * 1.6D, Y, 8.5D + Math.cos(angle) * 1.6D, 0.0F, 0.0F);
            z.setNoAi(true);
            z.setPersistenceRequired();
            level.addFreshEntity(z);
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            z.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
            z.setHealth(200.0F);
            z.invulnerableTime = 0;
            out[i] = z;
        }
        return out;
    }

    // ------------------------------------------------------------------ B3 力量药水

    /**
     * B3:力量 II + 卷轴。
     *
     * <p>力量是 {@code ATTACK_DAMAGE} 上的 <b>ADDITION</b> 修饰符,所以它本来就在
     * `getAttributeValue(ATTACK_DAMAGE)` 里、也就本来就在 {@code LivingHurtEvent} 的金额里。
     * 旧做法(属性乘区)与新做法(受击直接乘)在这一点上<b>没有区别</b>:两者都是
     * {@code (基础 + 力量) × 2}。这里把两个口径都算出来对照,如实记录。</p>
     */
    private static void testStrength(ItemStack scrollStack) {
        setBool(RingConfig.SCALE_ENABLED, true);
        setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, 100.0D);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 1200, 1));
        double withStrength = attr();

        unquip();
        float flat = net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(
            player.getMainHandItem(), net.minecraft.world.entity.MobType.UNDEAD);
        float eventAmount = (float) withStrength + flat;      // 等价于 Player#attack 传给 hurt 的数
        Zombie c = plainTarget();
        float control = hitWith(player.damageSources().playerAttack(player), c, eventAmount);
        c.discard();

        String slot = equip(player, scrollStack);
        Zombie t = plainTarget();
        float boosted = hitWith(player.damageSources().playerAttack(player), t, eventAmount);
        t.discard();
        unquip();
        player.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);

        pass("B3", "力量II(攻击力属性 " + f((float) withStrength) + " = 1 基础 + 6 力量,平伤 +" + f(flat)
            + ")[" + slot + "槽] 不戴=" + f(control) + " 戴=" + f(boosted) + " 倍数=" + f(boosted / control)
            + " [实际 = (1+6)×2 = 14;力量本来就在事件金额里,所以口径上'基础×2+力量'(=8)不成立,"
            + "旧做法同样是 14]", near(control, eventAmount) && near(boosted, eventAmount * 2.0F));
    }

    // ------------------------------------------------------------------ B4 锋利 V

    /**
     * B4:锋利 V 的剑。
     *
     * <p>锋利是"读完攻击力属性之后"才加的平伤({@code EnchantmentHelper.getDamageBonus}),所以两种口径
     * <b>不一样</b>:旧 = {@code 2×攻击力 + 锋利} ,新 = {@code 2×(攻击力 + 锋利)}。
     * 也就是说锋利那几点平伤现在<b>也会被翻倍</b> —— 这是本次改动唯一会让单次伤害变高的场景。</p>
     */
    private static void testSharpness(ItemStack scrollStack) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        float sharpFlat = net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(
            sword, net.minecraft.world.entity.MobType.UNDEAD);
        double weaponAttr = attr();

        unquip();
        float eventAmount = (float) weaponAttr + sharpFlat;
        Zombie c = plainTarget();
        float control = hitWith(player.damageSources().playerAttack(player), c, eventAmount);
        c.discard();

        String slot = equip(player, scrollStack);
        Zombie t = plainTarget();
        float boosted = hitWith(player.damageSources().playerAttack(player), t, eventAmount);
        t.discard();
        unquip();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        float oldFormula = (float) (weaponAttr * 2.0D) + sharpFlat;   // 旧口径(属性乘区)预测
        pass("B4", "钻石剑 锋利V(攻击力属性 " + f((float) weaponAttr) + ",锋利平伤 +" + f(sharpFlat)
            + ",事件金额 " + f(eventAmount) + ")[" + slot + "槽] 不戴=" + f(control) + " 戴=" + f(boosted)
            + " 倍数=" + f(boosted / control) + " [新口径 = 2×(" + f((float) weaponAttr) + "+" + f(sharpFlat)
            + ") = " + f(eventAmount * 2.0F) + ";旧口径会是 " + f(oldFormula) + "]",
            near(control, eventAmount) && near(boosted, eventAmount * 2.0F));
    }

    // ------------------------------------------------------------------ B5 连续开关

    /** B5:连续开关 20 次,每一次都断言当前状态下的伤害(开=40 / 关=20),证明没有残留。 */
    private static void testToggleStability(ItemStack scrollStack) {
        String slot = equip(player, scrollStack);
        int okCount = 0;
        StringBuilder seq = new StringBuilder();
        Zombie z = plainTarget();
        try {
            for (int i = 0; i < 20; i++) {
                boolean on = (i % 2) == 0;
                setBool(RingConfig.SCALE_ENABLED, on);                       // 总开关
                ScrollHandler.setLightningDisabled(slotStack(), !on);        // 逐件 NBT 也跟着翻
                String gateBefore = critState();
                z.setHealth(200.0F);
                float dealt = hitWith(player.damageSources().playerAttack(player), z, SWING);
                boolean good = near(dealt, on ? 40.0F : 20.0F);
                if (good) {
                    ++okCount;
                }
                else {
                    log(" B5 异常 i=" + i + " 期望=" + (on ? "40" : "20") + " 实测=" + f(dealt)
                        + " 事件金额=" + f(lastFinal) + " 写配置后[" + gateBefore + "]"
                        + " LOW阶段=" + critGateAtLow + " crit_chance属性=" + f((float) critChance()));
                }
                seq.append(on ? "开" : "关").append('=').append(f(dealt)).append(' ');
                if (i == 0) {
                    log(" B5 明细 i=0 断言前暴击状态[" + gateBefore + "]");
                }
            }
        }
        finally {
            z.discard();
        }
        setBool(RingConfig.SCALE_ENABLED, true);
        ScrollHandler.setLightningDisabled(slotStack(), false);
        boolean noResidue = !hasModifier(Attributes.ATTACK_DAMAGE, LEGACY_UUID)
            && near((float) attr(), (float) baseAttr);
        pass("B5", "连续开关 20 次[" + slot + "槽] 符合预期 " + okCount + "/20;序列 " + seq.toString().trim()
            + ";残留修饰符=" + hasModifier(Attributes.ATTACK_DAMAGE, LEGACY_UUID)
            + " 面板=" + f((float) attr()) + "(基准 " + f((float) baseAttr) + ")", okCount == 20 && noResidue);
        unquip();
    }

    // ------------------------------------------------------------------ B6 反复戴卸

    /** B6:卸下再戴上 5 次,每次属性面板都必须不动;最后一次戴着攻击仍是 40。 */
    private static void testReequip(ItemStack scrollStack) {
        quietProbe = true;
        int panelOk = 0;
        try {
            for (int i = 0; i < 5; i++) {
                equip(player, scrollStack);
                double wearing = attr();
                unquip();
                double off = attr();
                if (Math.abs(wearing - off) < 1.0E-6D && Math.abs(off - baseAttr) < 1.0E-6D) {
                    ++panelOk;
                }
            }
        }
        finally {
            quietProbe = false;
        }
        String slot = equip(player, scrollStack);
        Zombie z = plainTarget();
        float dealt = hitWith(player.damageSources().playerAttack(player), z, SWING);
        z.discard();
        unquip();
        pass("B6", "卸下/戴上 5 次[" + slot + "槽] 面板每次都与不戴相同 " + panelOk + "/5;最后一次攻击 → "
            + f(dealt) + " [应 40],面板 " + f((float) attr()) + " [应 " + f((float) baseAttr) + "]",
            panelOk == 5 && near(dealt, 40.0F) && near((float) attr(), (float) baseAttr));
    }

    // ------------------------------------------------------------------ B7 配置热重载

    /**
     * B7:运行中改配置。
     *
     * <p>旧做法要把修饰符"重挂"一遍才生效(所以当年才有 {@code ScrollHandler#reapplyAll} 那个
     * {@code ModConfigEvent.Reloading} 监听器);现在每次受击现读配置,改完<b>下一击</b>就是新数值 ——
     * 这里不调用任何重挂代码,直接改值再打,验证"立即生效"。</p>
     */
    private static void testHotConfig(ItemStack scrollStack) {
        String slot = equip(player, scrollStack);
        quietProbe = true;
        float at100;
        float at250;
        float at0;
        float backTo100;
        try {
            setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, 100.0D);
            at100 = oneHit();
            setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, 250.0D);
            at250 = oneHit();
            setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, 0.0D);
            at0 = oneHit();
            setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, 100.0D);
            backTo100 = oneHit();
        }
        finally {
            quietProbe = false;
        }
        pass("B7", "运行中改配置(不重启/不重挂)[" + slot + "槽] 100%→" + f(at100) + " 250%→" + f(at250)
            + " 0%→" + f(at0) + " 再回 100%→" + f(backTo100)
            + " [应 40 / 70 / 20 / 40]", near(at100, 40.0F) && near(at250, 70.0F) && near(at0, 20.0F)
            && near(backTo100, 40.0F));
        unquip();
    }

    private static float oneHit() {
        Zombie z = plainTarget();
        try {
            return hitWith(player.damageSources().playerAttack(player), z, SWING);
        }
        finally {
            z.discard();
        }
    }

    // ------------------------------------------------------------------ B8 其它模组读面板

    /** B8:模拟别的模组读攻击力(直接读属性值 / 读修饰符表),戴与不戴必须完全一致。 */
    private static void testAttributeReads(ItemStack scrollStack) {
        unquip();
        double offValue = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        int offMods = modifierCount();
        double offPaths = player.getAttribute(Attributes.ATTACK_DAMAGE) == null ? -1.0D
            : player.getAttribute(Attributes.ATTACK_DAMAGE).getValue();

        String slot = equip(player, scrollStack);
        double onValue = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        int onMods = modifierCount();
        double onPaths = player.getAttribute(Attributes.ATTACK_DAMAGE) == null ? -1.0D
            : player.getAttribute(Attributes.ATTACK_DAMAGE).getValue();
        // 再从一次真实受击里读(等价于"其它模组在事件里读攻击力")
        Zombie z = plainTarget();
        readInsideEvent = Double.NaN;
        hitWith(player.damageSources().playerAttack(player), z, SWING);
        z.discard();
        double inEvent = readInsideEvent;
        unquip();

        pass("B8", "攻击力属性[" + slot + "槽] 不戴=(" + f((float) offValue) + "," + offMods + " 个修饰符) 戴=("
            + f((float) onValue) + "," + onMods + " 个修饰符);AttributeInstance.getValue 不戴=" + f((float) offPaths)
            + " 戴=" + f((float) onPaths) + ";受击事件内读到=" + f((float) inEvent)
            + " [五处全等,且都等于基础 1.00]", near((float) offValue, (float) onValue)
            && offMods == onMods && near((float) offPaths, (float) onPaths) && near((float) inEvent, (float) onValue));
    }

    // ------------------------------------------------------------------ C1 / C2 负向对照

    /** C1/C2:证明这套判定"能失败" —— 乘数回到 1.0 时伤害就回到基础。 */
    private static void testNegativeControls(ItemStack scrollStack) {
        // C2 完全不戴卷轴
        unquip();
        float noScroll = oneHit();

        // C1 戴着卷轴,但把乘数临时改成 1.0(percent=0 → 1+0 = ×1)
        String slot = equip(player, scrollStack);
        double old = RingConfig.scaleAttackDamagePercent();
        setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, 0.0D);
        float multiplierOne = oneHit();
        setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, old);
        float restored = oneHit();
        unquip();

        pass("C2", "负向对照:完全不戴卷轴 → " + f(noScroll) + " [应 = 基础 20]", near(noScroll, 20.0F));
        pass("C1", "负向对照:戴着卷轴、乘数临时改成 1.0(percent=0)[" + slot + "槽] → " + f(multiplierOne)
            + " [应 = 不戴时的 " + f(noScroll) + "];改回 " + f((float) old) + "% → " + f(restored) + " [应 40]",
            near(multiplierOne, noScroll) && near(restored, 40.0F));
    }

    // ------------------------------------------------------------------ 环境去随机化

    /** 读一次两个关键开关,并把读取异常暴露出来(诊断 B5 的抖动)。 */
    private static String critState() {
        try {
            return "卷轴=" + RingConfig.SCALE_ENABLED.get()
                + ",卷轴生效=" + RingConfig.scaleEnabled()
                + ",暴击raw=" + RingConfig.ALL_DAMAGE_CRIT_ENABLED.get()
                + ",暴击生效=" + RingConfig.allDamageCritEnabled();
        }
        catch (Throwable t) {
            return "读取抛异常:" + t;
        }
    }

    /** 当前 {@code attributeslib:crit_chance} 的值(没有该属性时 NaN)。 */
    private static double critChance() {
        try {
            var id = new net.minecraft.resources.ResourceLocation("attributeslib", "crit_chance");
            Attribute attribute = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(id);
            if (attribute == null) {
                return Double.NaN;
            }
            var instance = player.getAttribute(attribute);
            return instance == null ? Double.NaN : instance.getValue();
        }
        catch (Throwable t) {
            return Double.NaN;
        }
    }

    /**
     * 隔离服里装了 ApothicAttributes/Apotheosis,它们的<b>暴击</b>会在<b>我们之后</b>的
     * {@code LivingHurtEvent} 里随机把伤害 ×1.5(实测 20 次命中里 3 次)。这不是被测逻辑的一部分,
     * 但会让"40.00 / 20.00"这种精确断言随机失败。
     *
     * <p>处理办法:把玩家身上的 {@code attributeslib:crit_chance} 归零({@code crit_damage} 归 1.0),
     * 只影响自检环境,不改任何模组文件。找不到属性就如实打印,不假装成功。</p>
     */
    private static void removeExternalCritChance() {
        for (String path : new String[] { "crit_chance", "crit_damage" }) {
            try {
                var id = new net.minecraft.resources.ResourceLocation("attributeslib", path);
                Attribute attribute = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(id);
                if (attribute == null) {
                    log(" 环境: 属性 " + id + " 不存在(没有 ApothicAttributes?)");
                    continue;
                }
                var instance = player.getAttribute(attribute);
                if (instance == null) {
                    log(" 环境: 玩家身上没有 " + id);
                    continue;
                }
                double before = instance.getValue();
                instance.setBaseValue("crit_damage".equals(path) ? 1.0D : 0.0D);
                log(" 环境: " + id + " 由 " + f((float) before) + " 改为 " + f((float) instance.getValue())
                    + "(基础值)");
            }
            catch (Throwable t) {
                log(" 环境: 处理 " + path + " 异常 " + t);
            }
        }
    }

    /**
     * 从源头掐掉"全伤害暴击":{@code PlayerCritHandler} 用的是伪造的 {@code CriticalHitEvent},
     * 它看到事件被取消就直接 return(连 1.5 倍都不会加)。这样即使配置读取抖动让开关"看起来是开的",
     * 自检的数值也不会被 ×1.5 污染。只影响自检进程,不改模组逻辑。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void blockCriticalHit(net.minecraftforge.event.entity.player.CriticalHitEvent event) {
        if (blockCrit) {
            event.setCanceled(true);
        }
    }

    private static boolean blockCrit = true;

    // ------------------------------------------------------------------ 场景工具

    /** 干净目标:200 血、护甲归零(让"20 点攻击"真的就是 20 点)。 */
    private static Zombie plainTarget() {
        Zombie zombie = new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
        zombie.moveTo(9.5D, Y, 9.5D, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
        zombie.setHealth(200.0F);
        zombie.invulnerableTime = 0;
        return zombie;
    }

    /** 钻石全套(不附保护),并 tick 到护甲属性真的生效。 */
    private static void diamondSet(Zombie z) {
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        ItemStack[] pieces = {
            new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)
        };
        for (int i = 0; i < 4; i++) {
            z.setItemSlot(slots[i], pieces[i]);
        }
        int before = z.getArmorValue();
        for (int i = 0; i < 10 && z.getArmorValue() == before; i++) {
            z.tick();
        }
        z.invulnerableTime = 0;
        z.setHealth(200.0F);
    }

    /** 走完整伤害管线的一发,返回血条实际掉了多少。 */
    private static float hit(LivingEntity target, float amount) {
        return hitWith(player.damageSources().playerAttack(player), target, amount);
    }

    /** 走完整伤害管线的一发(可指定 DamageSource),返回血条实际掉了多少。 */
    private static float hitWith(DamageSource source, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        probeUuid = target.getUUID();
        TRACE.setLength(0);
        hurtPosts = 0;
        damagePosts = 0;
        lastFinal = amount;
        float before = target.getHealth();
        target.hurt(source, amount);
        target.invulnerableTime = 0;
        float dealt = before - target.getHealth();
        if (!quietProbe) {
            log("   探针 " + f(amount) + "→" + f(dealt) + " HurtEvent=" + hurtPosts + " DamageEvent=" + damagePosts
                + " 阶段[" + TRACE + "] 血 " + f(before) + "→" + f(target.getHealth()) + "/" + f(target.getMaxHealth())
                + " 护甲=" + f((float) target.getAttributeValue(Attributes.ARMOR))
                + " 韧性=" + f((float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS))
                + " 吸收=" + f(target.getAbsorptionAmount())
                + " 保护=" + net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageProtection(
                    target.getArmorSlots(), player.damageSources().playerAttack(player)));
        }
        probeUuid = null;
        return dealt;
    }

    private static UUID probeUuid;
    private static final StringBuilder TRACE = new StringBuilder();
    private static int hurtPosts;
    private static int damagePosts;

    /** B5/B6/B7 这类"连打很多次"的段落不打印每条探针。 */
    private static boolean quietProbe;

    /** 上一次 {@link #hit} 的"护甲前最终事件金额"(LivingHurtEvent 走完所有处理器)。 */
    private static float lastFinal;
    /** A1 的实测掉血(无护甲 + 戴卷轴),A5 拿它当"没有护甲衰减"的对照。 */
    private static float a1Unarmored = Float.NaN;
    /** 自检开始时(什么都没戴)的攻击力面板基准值,B5/B6/B8 用它当"面板没变"的锚。 */
    private static double baseAttr = Double.NaN;
    /** B8:在 LivingHurtEvent 里(等价于别的模组在事件里读)读到的攻击力。 */
    private static double readInsideEvent = Double.NaN;
    /** LOW 阶段读到的"全伤暴击"开关(判断 1.5 是不是我们自己的 PlayerCritHandler)。 */
    private static boolean critGateAtLow;

    /** 攻击力属性上挂了多少个修饰符(别的模组/面板读的就是这张表)。 */
    private static int modifierCount() {
        var instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
        return instance == null ? -1 : instance.getModifiers().size();
    }

    private static boolean tracing(LivingHurtEvent event) {
        return probeUuid != null && probeUuid.equals(event.getEntity().getUUID());
    }

    private static void trace(String tag, float amount, boolean canceled) {
        TRACE.append(tag).append('=').append(f(amount)).append(canceled ? "(取消)" : "").append(' ');
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public static void probeHighest(LivingHurtEvent event) {
        if (tracing(event)) {
            ++hurtPosts;
            trace("最高", event.getAmount(), event.isCanceled());
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH, receiveCanceled = true)
    public static void probeHigh(LivingHurtEvent event) {
        if (tracing(event)) {
            trace("高(我们之后)", event.getAmount(), event.isCanceled());
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.NORMAL, receiveCanceled = true)
    public static void probeNormal(LivingHurtEvent event) {
        if (tracing(event)) {
            trace("普通", event.getAmount(), event.isCanceled());
            // B8:模拟"别的模组在受击事件里读攻击力"
            if (event.getSource().getEntity() instanceof Player attacker) {
                readInsideEvent = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
            }
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW, receiveCanceled = true)
    public static void probeLow(LivingHurtEvent event) {
        if (tracing(event)) {
            trace("低", event.getAmount(), event.isCanceled());
            critGateAtLow = RingConfig.allDamageCritEnabled();
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void probeLowest(LivingHurtEvent event) {
        if (tracing(event)) {
            trace("最低", event.getAmount(), event.isCanceled());
            lastFinal = event.getAmount();
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void probeDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (probeUuid != null && probeUuid.equals(event.getEntity().getUUID())) {
            ++damagePosts;
            trace("护甲后", event.getAmount(), event.isCanceled());
        }
    }

    private static double attr() {
        return player.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    private static final UUID LEGACY_UUID = UUID.fromString("3c9f2f6e-1d4b-4a7e-9c2f-7b5a1e0d6f21");

    private static boolean hasModifier(net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id) {
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 饰品栏工具

    /** 把物品塞进任意一个有格子的饰品槽(隔离环境里 scroll 槽容量是 0,见 HellBladeThunderSelfTest)。 */
    private static String equip(FakePlayer player, ItemStack stack) {
        try {
            var lazy = CuriosApi.getCuriosInventory(player);
            if (lazy == null || !lazy.isPresent()) {
                log(" Curios 拿不到玩家饰品栏");
                return null;
            }
            var handler = lazy.orElse(null);
            if (handler == null) {
                return null;
            }
            for (String slot : new String[] { "hands", "charm", "ring", "head", "back", "spellstone", "scroll" }) {
                var stacks = handler.getCurios().get(slot);
                int slots = stacks == null ? -1 : stacks.getStacks().getSlots();
                if (slots < 1) {
                    continue;
                }
                stacks.getStacks().setStackInSlot(0, stack.copy());
                if (Superposition.hasCurio(player, stack.getItem())) {
                    return slot;
                }
            }
        }
        catch (Throwable t) {
            log(" 放饰品异常: " + t);
        }
        return null;
    }

    /** 清掉饰品栏里我们放过的所有第一格(卷轴/手骨都只有一件)。 */
    private static boolean unquip() {
        try {
            var lazy = CuriosApi.getCuriosInventory(player);
            var handler = lazy == null ? null : lazy.orElse(null);
            if (handler != null) {
                for (String candidate : new String[] { "hands", "charm", "ring", "head", "back", "spellstone" }) {
                    var stacks = handler.getCurios().get(candidate);
                    if (stacks == null || stacks.getStacks().getSlots() < 1) {
                        continue;
                    }
                    if (!stacks.getStacks().getStackInSlot(0).isEmpty()) {
                        stacks.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                    }
                }
            }
        }
        catch (Throwable t) {
            log(" 清饰品异常: " + t);
        }
        boolean gone = !Superposition.hasCurio(player, RingConfig.thunderScrollItem());
        Item bone = ModItems.demonHandBoneOrNull();
        return gone && (bone == null || !Superposition.hasCurio(player, bone));
    }

    /** 饰品栏里那张卷轴的<b>活引用</b>(改 NBT 必须改这一份,不能改拷贝)。 */
    private static ItemStack slotStack() {
        try {
            var lazy = CuriosApi.getCuriosInventory(player);
            var handler = lazy == null ? null : lazy.orElse(null);
            if (handler != null) {
                for (String candidate : new String[] { "hands", "charm", "ring", "head", "back", "spellstone" }) {
                    var stacks = handler.getCurios().get(candidate);
                    if (stacks == null || stacks.getStacks().getSlots() < 1) {
                        continue;
                    }
                    ItemStack stack = stacks.getStacks().getStackInSlot(0);
                    if (!stack.isEmpty() && RingConfig.isThunderScroll(stack.getItem())) {
                        return stack;
                    }
                }
            }
        }
        catch (Throwable t) {
            log(" 取槽内卷轴异常: " + t);
        }
        return equippedScroll();
    }

    /** 当前饰品栏里那张卷轴(没有就是空堆)。 */
    private static ItemStack equippedScroll() {
        return Superposition.getCurioStack(player, RingConfig.thunderScrollItem());
    }

    private static void buildArena() {
        for (int x = 4; x <= 14; x++) {
            for (int z = 4; z <= 14; z++) {
                level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, Y, z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, Y + 1, z), Blocks.AIR.defaultBlockState());
            }
        }
    }

    // ------------------------------------------------------------------ 配置 / 断言

    private static void restoreConfig() {
        try {
            setBool(RingConfig.SCALE_ENABLED, oldScaleEnabled);
            setBool(RingConfig.SCALE_LIGHTNING_ENABLED, oldLightningEnabled);
            setBool(RingConfig.ALL_DAMAGE_CRIT_ENABLED, oldCritEnabled);
            setDouble(RingConfig.ALL_DAMAGE_CRIT_CHANCE, oldCritChance);
            setBool(RingConfig.DEBUG_LOG, oldDebugLog);
            setDouble(RingConfig.SCALE_ATTACK_DAMAGE_PERCENT, oldPercent);
            RingConfig.SPEC.save();
        }
        catch (Throwable ignored) {
            // 还原失败不影响结论
        }
        log(" 收尾: 配置已还原(总开关=" + RingConfig.scaleEnabled() + " 雷击=" + RingConfig.scaleLightningEnabled()
            + " 暴击=" + RingConfig.allDamageCritEnabled() + " 增伤%=" + RingConfig.scaleAttackDamagePercent() + ")");
    }

    private static void stop() {
        System.out.println(TAG + " ALL-RESULT " + (allPass ? "PASS" : "FAIL"));
        server.execute(() -> server.getCommands()
            .performPrefixedCommand(server.createCommandSourceStack(), "stop"));
    }

    private static void setBool(ForgeConfigSpec.BooleanValue value, boolean expected) {
        for (int i = 0; i < 10; i++) {
            try {
                value.set(expected);
                if (value.get() == expected) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 重试
            }
            hold(20L);
        }
    }

    private static void setDouble(ForgeConfigSpec.DoubleValue value, double expected) {
        for (int i = 0; i < 10; i++) {
            try {
                value.set(expected);
                if (Math.abs(value.get() - expected) < 1.0E-9D) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 重试
            }
            hold(20L);
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

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 1.0E-3F;
    }

    private static String f(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static void pass(String name, String detail, boolean ok) {
        allPass &= ok;
        log(" " + name + " " + detail + (ok ? "" : "  [FAIL]"));
        log(" " + name + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void fail(String name, String detail) {
        allPass = false;
        log(" " + name + " " + detail + "  [FAIL]");
        log(" " + name + "-RESULT FAIL");
    }

    private static void log(String message) {
        EnigmaticEnhance.LOGGER.info(TAG + message);
    }
}
