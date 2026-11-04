package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.1.28a 自检:【恶魔血杯】(-D 开关,由 {@link EarthAmuletSelfTest} 串起来跑)。
 * <ul>
 * <li>T26a 定义:id / 不可堆叠 / 无耐久 / {@code curios:charm} / 在创造标签页 / 没有任何配方产出它 /
 *     tooltip 4 行且无未替换占位符 / 中英 lang / 模型 + 贴图资源都在 jar 里 / {@code ModIds.problems()=[]};</li>
 * <li>T26b 封印:佩戴后加"速度 II + 力量 II" —— {@code getActiveEffects()} 数量 / 时长 / 等级不变、
 *     {@code hasEffect} 为 true,但两个效果的属性修饰符<b>一个都没挂上</b>,属性值与没加 buff 时一致;
 *     反例:先加"急迫 II"再戴上杯子 ⇒ 属性修饰符被摘掉、属性值回落;</li>
 * <li>T26c 再生:佩戴时把再生的 tick 手动跑 100 次,血量一点也不涨;摘下后再跑,立刻回血;</li>
 * <li>T26d 联动一:2 个正面效果 ⇒ 本体 {@code getMissingHealthPool} 比基线高 0.02;3 个 ⇒ 高 0.03;摘下回基线;</li>
 * <li>T26e 联动二:3 个负面效果 ⇒ 本体 {@code getCurseAmount} 比基线高 3;摘下回基线;</li>
 * <li>T26f 摘下恢复:速度/力量的属性修饰符立刻回来,数值也立刻生效(不需要重新喝药);</li>
 * <li>T26g 无残留:除饰品外不新增任何 effect / attribute / 修饰符(逐项打印 id 并断言来源不是本模组);</li>
 * <li>T26h 日志:debugLog=false 同动作零输出 / true 有输出。</li>
 * </ul>
 * 断言一律用**差值/修饰符是否存在**,不依赖绝对数值(其它模组也会给玩家挂修饰符,
 * 例如本模组自己的【全属性】就是一条 MULTIPLY_TOTAL 修饰符)。
 */
final class DemonBloodChaliceSelfTest {

    private static final String TAG = "[ChaliceSelfTest]";
    /** 运行时日志标记(与 {@link DemonBloodChaliceHandler#LOG_TAG} 一致,用于日志门控探测)。 */
    private static final String PROBE = DemonBloodChaliceHandler.LOG_TAG;
    private static final int DURATION = 1200;

    private DemonBloodChaliceSelfTest() {}

    // ------------------------------------------------------------------ 入口

    static boolean run(ServerLevel level, Player player) {
        boolean a = testDefinition(level);
        boolean b = testSeal(level, player);
        boolean c = testRegeneration(level, player);
        boolean d = testLinkages(level, player);
        boolean e = testUnequipRestore(level, player);
        boolean f = testNoResidue(level, player);
        boolean g = testLogGate(level, player);
        boolean all = a && b && c && d && e && f && g;
        log(" 恶魔血杯小结 T26a=" + a + " T26b=" + b + " T26c=" + c + " T26d/e(联动)=" + d
            + " T26f=" + e + " T26g(无残留)=" + f + " T26h(日志)=" + g + " => " + (all ? "PASS" : "FAIL"));
        reset(player);
        return all;
    }

    // ------------------------------------------------------------------ T26a 定义

    private static boolean testDefinition(ServerLevel level) {
        log(" --- T26a 恶魔血杯定义 / 槽位 / 无配方 / tooltip / 资源");
        boolean ok = true;
        Item item = ModItems.demonBloodChaliceOrNull();
        ResourceLocation id = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
        log(" T26a 物品 id=" + id + " [应为 enigmaticenhance:demon_blood_chalice]");
        ok &= item != null && "enigmaticenhance:demon_blood_chalice".equals(String.valueOf(id));
        if (item == null) {
            check("T26a", false);
            return false;
        }
        ItemStack stack = new ItemStack(item);
        boolean charm = false;
        try {
            charm = CuriosApi.getItemStackSlots(stack, level).containsKey(ModIds.CURIO_SLOT_CHARM);
        }
        catch (Throwable t) {
            log(" T26a Curios 槽位查询异常: " + t);
        }
        log(" T26a 最大堆叠=" + stack.getMaxStackSize() + " [应 1];最大耐久=" + stack.getMaxDamage()
            + " [应 0];curios:charm=" + charm + " [应 true];稀有度=" + stack.getRarity());
        ok &= stack.getMaxStackSize() == 1 && stack.getMaxDamage() == 0 && charm;

        boolean inTab = CreativeTabContents.items().contains(item);
        log(" T26a 在创造标签页清单里=" + inTab + " [应 true];本模组物品总数=" + CreativeTabContents.items().size());
        ok &= inTab;

        // 1.2.2c 起有配方了(注册配方 enigmaticenhance:heart_shaped,heart="abyssal"):只断言"只有我们自己那一条"
        List<String> sources = new java.util.ArrayList<>();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            try {
                if (recipe.getResultItem(level.registryAccess()).is(item)) {
                    sources.add(recipe.getId().toString());
                }
            }
            catch (Throwable ignored) {
                // 与我们无关的配方取产物失败
            }
        }
        boolean onlyOurs = sources.size() == 1 && "enigmaticenhance:demon_blood_chalice".equals(sources.get(0));
        log(" T26a 产出恶魔血杯的配方=" + sources + " [应只有 enigmaticenhance:demon_blood_chalice]");
        ok &= onlyOurs;

        List<net.minecraft.network.chat.Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, level, tooltip, TooltipFlag.Default.NORMAL);
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < tooltip.size(); i++) {
            lines.add(tooltip.get(i).getString());
        }
        boolean clean = lines.stream().noneMatch(line -> line.trim().isEmpty() || line.contains("%s")
            || line.contains("%d"));
        log(" T26a tooltip 效果行数=" + lines.size() + " [应 4:1.1.28h 起写全数值];无占位符/空行=" + clean);
        for (String line : lines) {
            log(" T26a   " + line);
        }
        ok &= lines.size() == 4 && clean;

        // 中英 lang:物品名 + 5 条说明键
        for (String lang : new String[] { "zh_cn", "en_us" }) {
            var root = readLang(lang);
            String name = root == null ? null : text(root, "item.enigmaticenhance.demon_blood_chalice");
            int tooltipKeys = 0;
            if (root != null) {
                for (String key : new String[] { "header", "lore", "detail", "ability", "restore" }) {
                    if (text(root, "tooltip.enigmaticenhance.demon_blood_chalice." + key) != null) {
                        ++tooltipKeys;
                    }
                }
            }
            log(" T26a " + lang + " 物品名=" + name + ";tooltip 键=" + tooltipKeys + "/5 [应 5]");
            ok &= name != null && !name.isEmpty() && tooltipKeys == 5;
        }

        // 模型 + 贴图资源(直接按 jar 内路径读);1.1.28d 起贴图升到 32x32,模型里写明 texture_size
        String modelJson = resourceText("/assets/enigmaticenhance/models/item/demon_blood_chalice.json");
        boolean model = modelJson != null && modelJson.contains("\"texture_size\"")
            && modelJson.contains("256");
        byte[] png = resourceBytes("/assets/enigmaticenhance/textures/item/demon_blood_chalice.png");
        boolean texture = png != null && png.length > 24 && (png[0] & 0xFF) == 0x89 && png[1] == 'P';
        int width = texture ? ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16)
            | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF) : -1;
        int height = texture ? ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16)
            | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF) : -1;
        log(" T26a 模型 json=" + model + "(含 texture_size 256)[应 true];贴图 PNG=" + texture + " 尺寸=" + width
            + "x" + height + " (" + (png == null ? 0 : png.length) + " 字节)[应 256x256]");
        ok &= model && texture && width == 256 && height == 256;

        List<String> problems = ModIds.problems();
        log(" T26a ModIds.problems()=" + problems + " [必须空]");
        ok &= problems.isEmpty();
        check("T26a", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T26b 封印

    private static boolean testSeal(ServerLevel level, Player player) {
        log(" --- T26b 效果保留但不生效(属性型)");
        reset(player);
        double baseSpeed = value(player, Attributes.MOVEMENT_SPEED);
        double baseAttack = value(player, Attributes.ATTACK_DAMAGE);
        boolean equipped = equip(player);
        log(" T26b 佩戴=" + equipped + " [应 true];sealed=" + DemonBloodChaliceHandler.isSealed(player)
            + " [应 true]");
        boolean ok = equipped && DemonBloodChaliceHandler.isSealed(player);

        int before = player.getActiveEffects().size();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 1));
        int after = player.getActiveEffects().size();
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        boolean speedPresent = speed != null && speed.getAmplifier() == 1 && speed.getDuration() > 0
            && speed.getEffect().isBeneficial();
        boolean strengthPresent = strength != null && strength.getAmplifier() == 1 && strength.getDuration() > 0
            && strength.getEffect().isBeneficial();
        log(" T26b 效果数量 " + before + " -> " + after + " [应 +2];hasEffect(速度)="
            + player.hasEffect(MobEffects.MOVEMENT_SPEED) + " hasEffect(力量)="
            + player.hasEffect(MobEffects.DAMAGE_BOOST) + " [都应 true]");
        log(" T26b 速度 II:amplifier=" + (speed == null ? "-" : speed.getAmplifier()) + " duration="
            + (speed == null ? "-" : speed.getDuration()) + " isBeneficial="
            + (speed == null ? "-" : speed.getEffect().isBeneficial()));
        log(" T26b 力量 II:amplifier=" + (strength == null ? "-" : strength.getAmplifier()) + " duration="
            + (strength == null ? "-" : strength.getDuration()) + " isBeneficial="
            + (strength == null ? "-" : strength.getEffect().isBeneficial()));
        ok &= after == before + 2 && speedPresent && strengthPresent
            && player.hasEffect(MobEffects.MOVEMENT_SPEED) && player.hasEffect(MobEffects.DAMAGE_BOOST);

        double sealedSpeed = value(player, Attributes.MOVEMENT_SPEED);
        double sealedAttack = value(player, Attributes.ATTACK_DAMAGE);
        boolean speedMods = hasEffectModifiers(player, MobEffects.MOVEMENT_SPEED);
        boolean strengthMods = hasEffectModifiers(player, MobEffects.DAMAGE_BOOST);
        log(" T26b 佩戴时 移动速度=" + sealedSpeed + " [基线 " + baseSpeed + ",应相等];攻击力=" + sealedAttack
            + " [基线 " + baseAttack + ",应相等]");
        log(" T26b 速度效果挂了属性修饰符=" + speedMods + " [应 false];力量效果挂了属性修饰符=" + strengthMods
            + " [应 false]");
        ok &= same(sealedSpeed, baseSpeed) && same(sealedAttack, baseAttack) && !speedMods && !strengthMods;

        // 反例:先加"急迫 II"(属性型),再戴上杯子 ⇒ 已有的属性修饰符必须被摘掉
        reset(player);
        double baseAttackSpeed = value(player, Attributes.ATTACK_SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, DURATION, 1));
        double hastedAttackSpeed = value(player, Attributes.ATTACK_SPEED);
        boolean hasted = hasEffectModifiers(player, MobEffects.DIG_SPEED);
        equip(player);
        double purgedAttackSpeed = value(player, Attributes.ATTACK_SPEED);
        boolean stillHasted = hasEffectModifiers(player, MobEffects.DIG_SPEED);
        log(" T26b 预先喝急迫:攻击速度 " + baseAttackSpeed + " -> " + hastedAttackSpeed + "(应更高);戴上杯子后="
            + purgedAttackSpeed + " [应回落 " + baseAttackSpeed + "];修饰符仍在=" + stillHasted + " [应 false]");
        ok &= hasted && hastedAttackSpeed > baseAttackSpeed && same(purgedAttackSpeed, baseAttackSpeed)
            && !stillHasted;
        check("T26b", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T26c 再生

    private static boolean testRegeneration(ServerLevel level, Player player) {
        log(" --- T26c 再生 buff:佩戴时不回血,摘下后立刻回血");
        boolean ok = true;
        reset(player);
        float half = Math.max(2.0F, player.getMaxHealth() * 0.5F);
        player.setHealth(half);
        equip(player);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 4));
        MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
        if (regen == null) {
            log(" T26c 再生效果没加上 [FAIL]");
            check("T26c", false);
            return false;
        }
        float before = player.getHealth();
        for (int i = 0; i < 100; i++) {
            regen.tick(player, () -> { });
        }
        float after = player.getHealth();
        log(" T26c 佩戴 + 100 次再生 tick:血量 " + before + " -> " + after + " [应一点不涨]");
        ok &= same(after, before);

        unequip(player);
        int healed = 0;
        for (int i = 0; i < 100 && player.getHealth() < player.getMaxHealth(); i++) {
            regen.tick(player, () -> { });
            ++healed;
        }
        float restored = player.getHealth();
        log(" T26c 摘下后再 tick " + healed + " 次:血量 " + after + " -> " + restored + " [应变高]");
        ok &= restored > after;
        player.removeAllEffects();
        check("T26c", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T26d / T26e 两条联动

    private static boolean testLinkages(ServerLevel level, Player player) {
        log(" --- T26d/e 联动:正面效果->血战沙场判定;负面效果->诅咒数");
        reset(player);
        player.setHealth(player.getMaxHealth() * 0.5F);
        float baseMissing = elMissingHealth(player);
        int baseCurses = elCurseAmount(player);
        boolean available = baseMissing >= 0.0F && baseCurses >= 0;
        log(" T26d/e 神秘遗物本体入口可用=" + available + ";基线已损失判定=" + baseMissing
            + " [应 0.5];基线诅咒数=" + baseCurses + " [应 0]");
        log(" T26d/e 混入探针: bloodbath=" + DemonBloodChaliceHandler.BLOODBATH_HOOK_OK + " curse=" + DemonBloodChaliceHandler.CURSE_HOOK_OK);
        if (!available) {
            log(" T26d/e 未装神秘遗物本体,跳过联动断言");
            check("T26d/e", true);
            return true;
        }
        boolean ok = Math.abs(baseMissing - 0.5F) < 1.0E-4F;

        equip(player);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 0));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 0));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, DURATION, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DURATION, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION, 0));
        int positive = DemonBloodChaliceHandler.positiveEffects(player);
        int negative = DemonBloodChaliceHandler.negativeEffects(player);
        float missing2 = elMissingHealth(player);
        int curses3 = elCurseAmount(player);
        log(" T26d/e 佩戴 + " + positive + " 个正面 / " + negative + " 个负面效果:已损失判定 " + baseMissing
            + " -> " + missing2 + " [应 +0.02];诅咒数 " + baseCurses + " -> " + curses3 + " [应 +3]");
        ok &= positive == 2 && negative == 3;
        ok &= Math.abs(missing2 - (baseMissing + 0.02F)) < 1.0E-4F;
        ok &= curses3 == baseCurses + 3;

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 0));
        float missing3 = elMissingHealth(player);
        log(" T26d/e 再加 1 个正面效果(" + DemonBloodChaliceHandler.positiveEffects(player)
            + " 个):已损失判定=" + missing3 + " [应 +0.03]");
        ok &= Math.abs(missing3 - (baseMissing + 0.03F)) < 1.0E-4F;

        unequip(player);
        float afterMissing = elMissingHealth(player);
        int afterCurses = elCurseAmount(player);
        log(" T26d/e 摘下:已损失判定=" + afterMissing + " [应回 " + baseMissing + "];诅咒数=" + afterCurses
            + " [应回 " + baseCurses + "]");
        ok &= same(afterMissing, baseMissing) && afterCurses == baseCurses;
        check("T26d/e", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T26f 摘下恢复

    private static boolean testUnequipRestore(ServerLevel level, Player player) {
        log(" --- T26f 摘下后 buff 立即生效(不需要重新喝药)");
        reset(player);
        double baseSpeed = value(player, Attributes.MOVEMENT_SPEED);
        double baseAttack = value(player, Attributes.ATTACK_DAMAGE);
        equip(player);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 1));
        boolean sealedSpeedMods = hasEffectModifiers(player, MobEffects.MOVEMENT_SPEED);
        boolean sealedStrengthMods = hasEffectModifiers(player, MobEffects.DAMAGE_BOOST);
        double sealedSpeed = value(player, Attributes.MOVEMENT_SPEED);
        double sealedAttack = value(player, Attributes.ATTACK_DAMAGE);

        unequip(player);
        boolean restoredSpeedMods = hasEffectModifiers(player, MobEffects.MOVEMENT_SPEED);
        boolean restoredStrengthMods = hasEffectModifiers(player, MobEffects.DAMAGE_BOOST);
        double restoredSpeed = value(player, Attributes.MOVEMENT_SPEED);
        double restoredAttack = value(player, Attributes.ATTACK_DAMAGE);
        log(" T26f 佩戴时:速度修饰符=" + sealedSpeedMods + " 力量修饰符=" + sealedStrengthMods
            + " [都应 false];速度=" + sealedSpeed + " 攻击=" + sealedAttack);
        log(" T26f 摘下后:速度修饰符=" + restoredSpeedMods + " 力量修饰符=" + restoredStrengthMods
            + " [都应 true];速度=" + restoredSpeed + " [应 > " + baseSpeed + "];攻击=" + restoredAttack
            + " [应 > " + baseAttack + "]");
        boolean ok = !sealedSpeedMods && !sealedStrengthMods && restoredSpeedMods && restoredStrengthMods
            && restoredSpeed > baseSpeed && restoredAttack > baseAttack
            && same(sealedSpeed, baseSpeed) && same(sealedAttack, baseAttack);
        check("T26f", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T26g 无残留

    private static boolean testNoResidue(ServerLevel level, Player player) {
        log(" --- T26g 除饰品外不新增任何 effect / attribute / 修饰符");
        reset(player);
        Map<String, TreeSet<String>> bare = modifierSnapshot(player);
        equip(player);
        Map<String, TreeSet<String>> worn = modifierSnapshot(player);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 1));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 1));
        Map<String, TreeSet<String>> withBuffs = modifierSnapshot(player);

        boolean ok = true;
        log(" T26g 属性修饰符条目:裸装=" + countModifiers(bare) + " 佩戴杯子=" + countModifiers(worn)
            + " 佩戴+3 个 buff=" + countModifiers(withBuffs) + " [三者必须相同]");
        ok &= bare.equals(worn) && bare.equals(withBuffs);

        StringBuilder effects = new StringBuilder();
        boolean foreign = false;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect());
            if (effects.length() > 0) {
                effects.append(", ");
            }
            effects.append(id);
            if (id == null || EnigmaticEnhance.MODID.equals(id.getNamespace())) {
                foreign = true;
            }
        }
        log(" T26g 当前效果列表=" + effects + " [每一个都必须来自原版/其它模组]");
        ok &= !foreign;

        StringBuilder attrs = new StringBuilder();
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(instance.getAttribute());
            if (attrs.length() > 0) {
                attrs.append(", ");
            }
            attrs.append(id);
        }
        log(" T26g 玩家同步属性列表=" + attrs);
        check("T26g", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T26h 日志门控

    private static boolean testLogGate(ServerLevel level, Player player) {
        log(" --- T26h debugLog 门控(默认 false 零输出)");
        reset(player);
        boolean ok = true;

        EarthAmuletSelfTest.setDebugLog(false);
        EarthAmuletSelfTest.hold(200L);
        int quietBefore = EarthAmuletSelfTest.logHits(PROBE);
        equip(player);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION, 1));
        DemonBloodChaliceHandler.purify(player);
        unequip(player);
        EarthAmuletSelfTest.hold(300L);
        int quietAfter = EarthAmuletSelfTest.logHits(PROBE);
        log(" T26h debugLog=false 同动作新增日志行=" + (quietAfter - quietBefore) + " [应为 0]");
        ok &= quietBefore >= 0 && quietAfter >= 0 && quietAfter - quietBefore == 0;
        if (quietBefore < 0) {
            log(" T26h 警告: 读不到 logs/latest.log,日志门控无法判定");
        }

        EarthAmuletSelfTest.setDebugLog(true);
        EarthAmuletSelfTest.hold(200L);
        int loudBefore = EarthAmuletSelfTest.logHits(PROBE);
        equip(player);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, DURATION, 1));
        DemonBloodChaliceHandler.purify(player);
        unequip(player);
        EarthAmuletSelfTest.hold(300L);
        int loudAfter = EarthAmuletSelfTest.logHits(PROBE);
        log(" T26h debugLog=true 同动作新增日志行=" + (loudAfter - loudBefore) + " [应 >=1]");
        ok &= loudBefore >= 0 && loudAfter >= 0 && loudAfter - loudBefore >= 1;
        check("T26h", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 工具

    /** 清干净:摘下饰品、清空全部效果、回满血。 */
    private static void reset(Player player) {
        unequip(player);
        DemonBloodChaliceHandler.invalidate(player);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
    }

    /** 直接放进 curios:charm 第 0 格,并手动走一次佩戴结算(FakePlayer 不会被 Curios 驱动)。 */
    private static boolean equip(Player player) {
        Item item = ModItems.demonBloodChaliceOrNull();
        if (item == null) {
            return false;
        }
        try {
            boolean placed = CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    var charm = handler.getCurios().get(ModIds.CURIO_SLOT_CHARM);
                    if (charm == null || charm.getSlots() < 1) {
                        return false;
                    }
                    charm.getStacks().setStackInSlot(0, new ItemStack(item));
                    return true;
                })
                .orElse(false);
            if (!placed) {
                log(" 装备恶魔血杯失败:没有可用的 charms 槽");
                return false;
            }
            DemonBloodChaliceHandler.onEquipped(player);
            return DemonBloodChaliceHandler.wearing(player);
        }
        catch (Throwable t) {
            log(" 装备恶魔血杯异常: " + t);
            return false;
        }
    }

    private static void unequip(Player player) {
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                var charm = handler.getCurios().get(ModIds.CURIO_SLOT_CHARM);
                if (charm != null && charm.getSlots() > 0) {
                    charm.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                }
            });
        }
        catch (Throwable ignored) {
            // 自检清理
        }
        DemonBloodChaliceHandler.onUnequipped(player);
        DemonBloodChaliceHandler.invalidate(player);
    }

    /** 该效果的**全部**属性修饰符是否都在玩家身上(空表也算"没挂",不算通过)。 */
    private static boolean hasEffectModifiers(Player player, MobEffect effect) {
        Map<Attribute, AttributeModifier> modifiers = effect.getAttributeModifiers();
        if (modifiers.isEmpty()) {
            return false;
        }
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entrySet()) {
            AttributeInstance instance = player.getAttribute(entry.getKey());
            if (instance == null || instance.getModifier(entry.getValue().getId()) == null) {
                return false;
            }
        }
        return true;
    }

    /** 属性 → 修饰符 UUID 集合的快照(只比 UUID,不比数值,避免被其它模组的数值变化干扰)。 */
    private static Map<String, TreeSet<String>> modifierSnapshot(Player player) {
        Map<String, TreeSet<String>> snapshot = new LinkedHashMap<>();
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(instance.getAttribute());
            TreeSet<String> uuids = new TreeSet<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                uuids.add(modifier.getId().toString());
            }
            snapshot.put(String.valueOf(id), uuids);
        }
        return snapshot;
    }

    private static int countModifiers(Map<String, TreeSet<String>> snapshot) {
        int total = 0;
        for (TreeSet<String> uuids : snapshot.values()) {
            total += uuids.size();
        }
        return total;
    }

    private static double value(Player player, Attribute attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    private static boolean same(double a, double b) {
        return Math.abs(a - b) < 1.0E-6D;
    }

    // ------------------------------------------------------------------ 神秘遗物本体入口(反射)

    /** {@code SuperpositionHandler#getMissingHealthPool(Player)}(= 血战沙场之证的已损失生命判定)。 */
    private static float elMissingHealth(Player player) {
        try {
            Class<?> handler = Class.forName("com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler");
            Object result = handler.getMethod("getMissingHealthPool", Player.class).invoke(null, player);
            return result instanceof Float value ? value : -1.0F;
        }
        catch (Throwable t) {
            return -1.0F;
        }
    }

    /** {@code SuperpositionHandler#getCurseAmount(Player)}(= 千咒卷轴读的诅咒数)。 */
    private static int elCurseAmount(Player player) {
        try {
            Class<?> handler = Class.forName("com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler");
            Object result = handler.getMethod("getCurseAmount", Player.class).invoke(null, player);
            return result instanceof Integer value ? value : -1;
        }
        catch (Throwable t) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ 资源 / lang

    private static java.io.InputStream resource(String path) {
        try {
            return DemonBloodChaliceSelfTest.class.getResourceAsStream(path);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static byte[] resourceBytes(String path) {
        try (java.io.InputStream in = resource(path)) {
            return in == null ? null : in.readAllBytes();
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static String resourceText(String path) {
        byte[] bytes = resourceBytes(path);
        return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static com.google.gson.JsonObject readLang(String lang) {
        try (java.io.InputStream in = resource("/assets/enigmaticenhance/lang/" + lang + ".json")) {
            if (in == null) {
                return null;
            }
            try (var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
        catch (Throwable t) {
            log(" T26a 解析 " + lang + " 失败: " + t);
            return null;
        }
    }

    private static String text(com.google.gson.JsonObject root, String key) {
        return root != null && root.has(key) ? root.get(key).getAsString() : null;
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
