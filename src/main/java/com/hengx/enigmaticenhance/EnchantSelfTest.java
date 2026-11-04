package com.hengx.enigmaticenhance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 1.1.29a 自检 T35:三个附魔的注册 / 获取限制 / 数值口径。
 * <ul>
 * <li>T35a 三个附魔都已注册,id 正确;</li>
 * <li>T35b 三个附魔都**不设获取限制**:非 treasure、tradeable、discoverable、在各自合法的物品上出现在附魔台;
 *     并用原版候选表直接验证"附魔台真的会给"(镐→自动熔炼、剑→魔法剑气,镐→剑气的反例为 false);</li>
 * <li>T35c 数值:魔法保护 4%/级(上限 16%)、剑气近战 ×0.2、命中数 = 等级、基础伤害 4×等级;</li>
 * <li>T35d 剑气只接受剑(canEnchant 剑 true / 镐 false),最高 3 级;</li>
 * <li>T35e 1.2.2b:自动熔炼的"获取途径"开关两个状态都测 ——
 *     默认放开时三项为 true;打开 {@code autoSmelt.lootOnlyAcquisition} 后三项回到 false(旧行为)。</li>
 * </ul>
 */
final class EnchantSelfTest {

    private static final String TAG = "[EnchantSelfTest]";

    private EnchantSelfTest() {}

    static boolean run(ServerLevel level, Player player) {
        boolean a = testRegistered();
        boolean b = testNoRestrictions();
        boolean c = testNumbers();
        boolean d = testSwordOnly();
        boolean e = testAutoSmeltAcquisitionToggle();
        boolean f = testAuraAttackAndCrit(player);
        boolean g = testAuraUseAnyItem();
        boolean all = a && b && c && d && e && f && g;
        log(" 附魔小结 T35a(注册)=" + a + " T35b(无获取限制)=" + b + " T35c(数值)=" + c
            + " T35d(只接受剑)=" + d + " T35e(获取途径开关)=" + e + " T35f(剑气吃攻击力+暴击)=" + f + " T35g(使用不限物品)=" + g
            + " => " + (all ? "PASS" : "FAIL"));
        return all;
    }

    private static boolean testRegistered() {
        boolean ok = true;
        String[] ids = { "enigmaticenhance:auto_smelt", "enigmaticenhance:magic_protection",
            "enigmaticenhance:magic_sword_aura" };
        Enchantment[] list = { ModEnchantments.autoSmeltOrNull(), ModEnchantments.magicProtectionOrNull(),
            ModEnchantments.magicSwordAuraOrNull() };
        for (int i = 0; i < ids.length; i++) {
            String id = list[i] == null ? null
                : String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKey(list[i]));
            boolean match = ids[i].equals(id);
            log(" T35a " + ids[i] + " 注册=" + (list[i] != null) + ";实际 id=" + id + " [应一致]");
            ok &= match;
        }
        check("T35a", ok);
        return ok;
    }

    private static boolean testNoRestrictions() {
        boolean ok = true;
        Enchantment[] list = { ModEnchantments.autoSmeltOrNull(), ModEnchantments.magicProtectionOrNull(),
            ModEnchantments.magicSwordAuraOrNull() };
        // 每件附魔"自己的合法物品":旧断言拿剑/胸甲/镐三件都要求可附,
        // 等于把"没有获取限制"误测成"任何物品都能附"(1.2.2b 修正)。
        ItemStack[] ownItems = { new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.IRON_SWORD) };
        String[] ownNames = { "镐", "胸甲", "剑" };
        for (int i = 0; i < list.length; i++) {
            Enchantment e = list[i];
            if (e == null) {
                check("T35b", false);
                return false;
            }
            boolean treasure = e.isTreasureOnly();
            boolean trade = e.isTradeable();
            boolean discover = e.isDiscoverable();
            boolean table = e.canApplyAtEnchantingTable(ownItems[i]);
            log(" T35b " + net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKey(e)
                + " treasure=" + treasure + "[应 false];tradeable=" + trade + "[应 true];discoverable="
                + discover + "[应 true];附魔台可用(" + ownNames[i] + ")=" + table + "[应 true]");
            ok &= !treasure && trade && discover && table;
        }

        // 1.2.2b:再看真正的游戏路径 —— 附魔台候选列表会不会把它给出来(而不是只读标志位)。
        // 能量要落在该附魔的 minCost..maxCost 里才有意义:
        // 自动熔炼 1..41、魔法剑气 5..30、魔法保护 1..21。
        java.util.Set<Enchantment> pick30 = availableAt(30, new ItemStack(Items.IRON_PICKAXE));
        java.util.Set<Enchantment> sword20 = availableAt(20, new ItemStack(Items.IRON_SWORD));
        java.util.Set<Enchantment> chest15 = availableAt(15, new ItemStack(Items.DIAMOND_CHESTPLATE));
        java.util.Set<Enchantment> pick15 = availableAt(15, new ItemStack(Items.IRON_PICKAXE));
        boolean smeltOnPick = list[0] != null && pick30.contains(list[0]);
        boolean auraOnSword = list[2] != null && sword20.contains(list[2]);
        boolean auraOnPick = list[2] != null && pick30.contains(list[2]);
        boolean protectionOnChest = list[1] != null && chest15.contains(list[1]);
        boolean protectionOnPick = list[1] != null && pick15.contains(list[1]);
        log(" T35b 附魔台候选:镐·能量30→自动熔炼=" + smeltOnPick + "[应 true];剑·能量20→魔法剑气=" + auraOnSword
            + "[应 true];镐·能量30→魔法剑气=" + auraOnPick + "[应 false 只认剑];胸甲·能量15→魔法保护="
            + protectionOnChest + "[应 true]");
        // 1.2.2c:魔法保护改成只认护甲之后,镐子不该再拿到它(改之前这里是 true)
        log(" T35b 附魔台候选对照:镐·能量15→魔法保护=" + protectionOnPick
            + "[应 false —— 1.2.2c 起只认护甲]");

        // 1.2.2b 踩到的坑(留着当证据):装了神化时,上面这个候选表由神化的
        // RealEnchantmentHelper 接管,它读的是 config/apotheosis/enchantments.cfg 里缓存的
        // Discoverable / Lootable / Tradeable —— 那份缓存**首次启动时按当时的代码生成**。
        // 所以只改代码不够:缓存里的 false 会一直盖住代码里的 true,附魔台照样不给。
        // 本条断言就是发现这件事的地方(改缓存前:镐·能量30→自动熔炼=false)。
        ok &= smeltOnPick && auraOnSword && !auraOnPick && protectionOnChest && !protectionOnPick;

        check("T35b", ok);
        return ok;
    }

    /** 直接问原版附魔台候选表:能量 {@code power} 下,这件物品能拿到哪些附魔。 */
    private static java.util.Set<Enchantment> availableAt(int power, ItemStack stack) {
        java.util.Set<Enchantment> out = new java.util.HashSet<>();
        try {
            for (net.minecraft.world.item.enchantment.EnchantmentInstance instance
                : net.minecraft.world.item.enchantment.EnchantmentHelper.getAvailableEnchantmentResults(
                    power, stack, false)) {
                out.add(instance.enchantment);
            }
        }
        catch (Throwable t) {
            log(" T35b 附魔台候选查询失败: " + t);
        }
        return out;
    }

    private static boolean testNumbers() {
        boolean ok = true;
        // 魔法保护:10 点魔法伤害,等级合计 4 -> 8.4;等级 0 -> 10;等级 8(超上限)仍 8.4
        float p0 = MagicCombatHandler.magicDamage(10.0F, 0);
        float p4 = MagicCombatHandler.magicDamage(10.0F, 4);
        float p8 = MagicCombatHandler.magicDamage(10.0F, 8);
        log(" T35c 魔法保护:lv0=" + p0 + "[应 10];lv4=" + p4 + "[应 8.4];lv8(超上限)=" + p8 + "[应 8.4]");
        ok &= Math.abs(p0 - 10.0F) < 1.0E-4F && Math.abs(p4 - 8.4F) < 1.0E-4F && Math.abs(p8 - 8.4F) < 1.0E-4F;
        // 剑气:近战 10 点 -> 2.0;无附魔 10
        float m0 = MagicCombatHandler.meleeDamage(10.0F, 0);
        float m1 = MagicCombatHandler.meleeDamage(10.0F, 1);
        float m3 = MagicCombatHandler.meleeDamage(10.0F, 3);
        log(" T35c 剑气近战:lv0=" + m0 + "[应 10];lv1=" + m1 + "[应 2];lv3=" + m3 + "[应 2(不随等级变)]");
        ok &= Math.abs(m0 - 10.0F) < 1.0E-4F && Math.abs(m1 - 2.0F) < 1.0E-4F && Math.abs(m3 - 2.0F) < 1.0E-4F;
        // 命中数 = 等级;基础伤害 4×等级
        // 1.1.29c:伤害 = 剑面板 × 法强,不再随等级变;等级只决定命中数
        float panel = 7.0F;
        for (int lv = 1; lv <= 3; lv++) {
            int hits = MagicCombatHandler.pierceCount(lv);
            float dmg = MagicCombatHandler.auraDamage(panel, 1.0F);
            log(" T35c 剑气 lv" + lv + ": 命中数=" + hits + "[应 " + lv + "];伤害=" + dmg
                + "[应 " + panel + " = 面板 × 法强]");
            ok &= hits == lv && Math.abs(dmg - panel) < 1.0E-4F;
        }
        float fallback = MagicCombatHandler.auraDamage(0.0F, 1.0F);
        float boosted = MagicCombatHandler.auraDamage(panel, 2.0F);
        log(" T35c 面板=0 回退=" + fallback + "[应 1.0];法强 2 倍=" + boosted + "[应 " + (panel * 2.0F) + "]");
        ok &= Math.abs(fallback - 1.0F) < 1.0E-4F && Math.abs(boosted - panel * 2.0F) < 1.0E-4F;
        check("T35c", ok);
        return ok;
    }

    /**
     * 1.2.3:剑气改吃玩家攻击力 + 原版暴击。
     * <ul>
     *     <li>面板 = {@code player.getAttributeValue(ATTACK_DAMAGE)} —— 用一条临时 +5 的修饰符证明它真的跟着属性走
     *         (力量药水/饰品/宝石给的都是这类修饰符);</li>
     *     <li>暴击条件照抄 {@code Player#attack}:整套真值表逐个断言;</li>
     *     <li>暴击倍率 1.5;伤害类型仍是 {@code indirectMagic}(那里没动,见实体代码)。</li>
     * </ul>
     */
    private static boolean testAuraAttackAndCrit(Player player) {
        boolean ok = true;
        if (player == null) {
            check("T35f", false);
            return false;
        }
        // 1) 面板吃玩家的攻击力属性
        float panelBefore = MagicCombatHandler.attackPanel(player);
        var attribute = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        java.util.UUID id = java.util.UUID.randomUUID();
        attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id,
            "selftest_attack_bonus", 5.0D,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        float panelAfter = MagicCombatHandler.attackPanel(player);
        attribute.removeModifier(id);
        float restored = MagicCombatHandler.attackPanel(player);
        log(" T35f 剑气面板:原始=" + panelBefore + ";+5 攻击力后=" + panelAfter + "[应 " + (panelBefore + 5.0F)
            + "];移除后=" + restored + "[应 " + panelBefore + "]");
        ok &= Math.abs(panelAfter - panelBefore - 5.0F) < 1.0E-4F
            && Math.abs(restored - panelBefore) < 1.0E-4F;

        // 2) 原版暴击条件(下落 + 不在地面 + 不贴墙 + 不在水 + 不失明 + 不骑乘 + 不疾跑)
        boolean critAll = MagicCombatHandler.isVanillaCrit(true, false, false, false, false, false, false);
        boolean noFall = MagicCombatHandler.isVanillaCrit(false, false, false, false, false, false, false);
        boolean onGround = MagicCombatHandler.isVanillaCrit(true, true, false, false, false, false, false);
        boolean climb = MagicCombatHandler.isVanillaCrit(true, false, true, false, false, false, false);
        boolean water = MagicCombatHandler.isVanillaCrit(true, false, false, true, false, false, false);
        boolean blind = MagicCombatHandler.isVanillaCrit(true, false, false, false, true, false, false);
        boolean ride = MagicCombatHandler.isVanillaCrit(true, false, false, false, false, true, false);
        boolean sprint = MagicCombatHandler.isVanillaCrit(true, false, false, false, false, false, true);
        boolean live = MagicCombatHandler.isVanillaCrit(player);
        log(" T35f 原版暴击:全满足=" + critAll + "[应 true];未下落=" + noFall + "[应 false];在地面=" + onGround
            + "[应 false];贴墙梯=" + climb + "[应 false];水中=" + water + "[应 false];失明=" + blind
            + "[应 false];骑乘=" + ride + "[应 false];疾跑=" + sprint + "[应 false];实测玩家(站地)=" + live
            + "[应 false]");
        ok &= critAll && !noFall && !onGround && !climb && !water && !blind && !ride && !sprint && !live;

        // 3) 倍率与最终伤害
        float flat = MagicCombatHandler.critDamage(10.0F, false);
        float crit = MagicCombatHandler.critDamage(10.0F, true);
        log(" T35f 暴击倍率:非暴击=" + flat + "[应 10];暴击=" + crit + "[应 15(×1.5)]");
        ok &= Math.abs(flat - 10.0F) < 1.0E-4F && Math.abs(crit - 15.0F) < 1.0E-4F;

        check("T35f", ok);
        return ok;
    }
    /**
     * 1.2.4:使用端不再限制物品类型。
     * <ul>
     *     <li>获取端照旧:{@code canEnchant}/{@code canApplyAtEnchantingTable} 只认剑;</li>
     *     <li>使用端:{@link MagicCombatHandler#auraLevel(net.minecraft.world.item.ItemStack)} 直接读
     *         <b>手上这一件</b>的附魔等级,所以非剑物品(用指令/书/NBT 拿到附魔后)也能放剑气。</li>
     * </ul>
     */
    private static boolean testAuraUseAnyItem() {
        Enchantment aura = ModEnchantments.magicSwordAuraOrNull();
        if (aura == null) {
            check("T35g", false);
            return false;
        }
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        boolean swordCan = aura.canEnchant(sword) && aura.canApplyAtEnchantingTable(sword);
        boolean pickCan = aura.canEnchant(pick) || aura.canApplyAtEnchantingTable(pick);
        log(" T35g 获取端:剑可附=" + swordCan + "[应 true];镐可附=" + pickCan + "[应 false 正常途径只认剑]");

        // 使用端:直接用 stack.enchant 绕过 canEnchant(等价于指令/书/NBT 给非剑物品上附魔)
        ItemStack enchantedPick = new ItemStack(Items.IRON_PICKAXE);
        enchantedPick.enchant(aura, 2);
        int pickLevel = MagicCombatHandler.auraLevel(enchantedPick);
        int swordLevel = MagicCombatHandler.auraLevel(new ItemStack(Items.IRON_SWORD));
        int emptyLevel = MagicCombatHandler.auraLevel(ItemStack.EMPTY);
        log(" T35g 使用端:镐(附魔2级)剑气等级=" + pickLevel + "[应 2];无附魔剑=" + swordLevel
            + "[应 0];空手=" + emptyLevel + "[应 0]");
        boolean fast = !(pickCan) && swordCan && pickLevel == 2 && swordLevel == 0 && emptyLevel == 0;
        check("T35g", fast);
        return fast;
    }
    private static boolean testSwordOnly() {
        Enchantment aura = ModEnchantments.magicSwordAuraOrNull();
        if (aura == null) {
            check("T35d", false);
            return false;
        }
        boolean sword = aura.canEnchant(new ItemStack(Items.IRON_SWORD));
        boolean pick = aura.canEnchant(new ItemStack(Items.IRON_PICKAXE));
        boolean lvl = aura.getMaxLevel() == 3;
        log(" T35d canEnchant(剑)=" + sword + "[应 true];canEnchant(镐)=" + pick + "[应 false];maxLevel="
            + aura.getMaxLevel() + "[应 3]");
        boolean ok = sword && !pick && lvl;
        check("T35d", ok);
        return ok;
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    // ------------------------------------------------------------ T35e 获取途径开关

    /**
     * 1.2.2b:自动熔炼的获取途径既要能放开、也要能整体退回旧行为。
     * 两个状态都断言,并在 finally 里把配置恢复成默认(false),避免污染后面的自检。
     */
    private static boolean testAutoSmeltAcquisitionToggle() {
        Enchantment smelt = ModEnchantments.autoSmeltOrNull();
        if (smelt == null) {
            check("T35e", false);
            return false;
        }
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        boolean ok = true;
        try {
            setLootOnly(false);
            boolean open = AutoSmeltEnchantment.acquisitionOpen();
            boolean openFlags = smelt.isDiscoverable() && smelt.isTradeable()
                && smelt.canApplyAtEnchantingTable(pick);
            log(" T35e lootOnly=false: 放开=" + open + " 三项=" + openFlags
                + " [应 true true];仍是镐才可附=" + smelt.canEnchant(pick) + " [应 true]");
            ok &= open && openFlags && smelt.canEnchant(pick);

            setLootOnly(true);
            boolean closed = !AutoSmeltEnchantment.acquisitionOpen();
            boolean closedFlags = !smelt.isDiscoverable() && !smelt.isTradeable()
                && !smelt.canApplyAtEnchantingTable(pick);
            boolean stillDigger = smelt.canEnchant(pick);
            log(" T35e lootOnly=true : 退回=" + closed + " 三项全关=" + closedFlags
                + " [应 true true];物品限制不受影响(镐)=" + stillDigger + " [应 true]");
            ok &= closed && closedFlags && stillDigger;
        }
        finally {
            setLootOnly(false);
        }
        check("T35e", ok);
        return ok;
    }

    /** 改配置值(等配置加载完再改,失败就重试)。 */
    private static void setLootOnly(boolean value) {
        for (int i = 0; i < 10; i++) {
            try {
                RingConfig.AUTO_SMELT_LOOT_ONLY.set(value);
                if (RingConfig.autoSmeltLootOnly() == value) {
                    return;
                }
            }
            catch (Throwable ignored) {
                // 配置还没加载完
            }
            try {
                Thread.sleep(150L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
