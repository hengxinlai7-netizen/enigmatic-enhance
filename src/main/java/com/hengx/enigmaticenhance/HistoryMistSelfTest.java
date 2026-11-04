package com.hengx.enigmaticenhance;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.2.5c 自检:瓶装历史迷雾。
 *
 * <ul>
 *   <li>T43a 注册 / 堆叠 / 资源文件;</li>
 *   <li>T43b 大地之瓶 0.5h 后原地转化(背包计时);</li>
 *   <li>T43c 两重限制:10% 目标值 与 50% 自身值 取小 —— 两种"谁生效"都要覆盖;</li>
 *   <li>T43d 可反复记录,第二次覆盖第一次(只保留最新的生物);</li>
 *   <li>T43e 携带生效:属性真涨,瓶子拿走后回落。</li>
 * </ul>
 */
final class HistoryMistSelfTest {

    private static final String TAG = "[HistoryMistSelfTest]";

    private HistoryMistSelfTest() {
    }

    static boolean run(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        boolean a = testRegistered();
        boolean b = testTransformation(level, player);
        boolean c = testRecordMath(level, player);
        boolean d = testRecordOnce(level, player);
        boolean e = testModifiers(level, player);
        boolean f = testBagCompatibility();
        log(TAG + " 小结 T43a(注册)=" + a + " T43b(转化)=" + b + " T43c(双重限制)=" + c
            + " T43d(可重复·只留最新)=" + d + " T43e(携带生效)=" + e + " T43f(书袋兼容)=" + f + " => " + (a && b && c && d && e && f ? "PASS" : "FAIL"));
        return a && b && c && d && e && f;
    }

    // ------------------------------------------------------------------ a

    private static boolean testRegistered() {
        Item bottle = ModItems.earthBottleOrNull();
        Item mist = ModItems.historyMistOrNull();
        boolean ok = bottle != null && mist != null;
        if (!ok) {
            log(TAG + " T43a 物品缺失 bottle=" + bottle + " mist=" + mist);
            check("T43a", false);
            return false;
        }
        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(mist));
        ItemStack stack = new ItemStack(mist);
        boolean single = stack.getMaxStackSize() == 1;
        boolean model = resource("assets/enigmaticenhance/models/item/history_mist.json");
        boolean texture = resource("assets/enigmaticenhance/textures/item/history_mist.png");
        log(TAG + " T43a id=" + id + "[应 enigmaticenhance:history_mist];最大堆叠=" + stack.getMaxStackSize()
            + "[应 1];模型=" + model + "[应 true];材质=" + texture + "[应 true]");
        ok = "enigmaticenhance:history_mist".equals(id) && single && model && texture;
        check("T43a", ok);
        return ok;
    }

    // ------------------------------------------------------------------ b

    private static boolean testTransformation(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        Item bottle = ModItems.earthBottleOrNull();
        Item mist = ModItems.historyMistOrNull();
        if (bottle == null || mist == null) {
            check("T43b", false);
            return false;
        }
        var inventory = player.getInventory();
        ItemStack previous = inventory.getItem(0);

        // 差 20 tick 到点:一次 tick 就该转化
        ItemStack nearly = new ItemStack(bottle);
        nearly.getOrCreateTag().putInt(HistoryMistHandler.TIMER_KEY, HistoryMistHandler.TRANSFORM_TICKS - 20);
        inventory.setItem(0, nearly);
        HistoryMistHandler.tickTransformation(player);
        ItemStack after = inventory.getItem(0);
        boolean converted = !after.isEmpty() && after.getItem() == mist;

        // 刚放进去的:一枚 tick 后只累计到 20,不该转化
        ItemStack fresh = new ItemStack(bottle);
        inventory.setItem(0, fresh);
        HistoryMistHandler.tickTransformation(player);
        ItemStack stillBottle = inventory.getItem(0);
        int timer = stillBottle.getTag() == null ? -1 : stillBottle.getTag().getInt(HistoryMistHandler.TIMER_KEY);
        boolean notYet = stillBottle.getItem() == bottle && timer == 20;

        inventory.setItem(0, previous);
        log(TAG + " T43b 到点转化=" + converted + "[应 true];新瓶一 tick 后=" + (stillBottle.getItem() == bottle)
            + " 计时=" + timer + "[应 20,不转化]");
        boolean ok = converted && notYet;
        check("T43b", ok);
        return ok;
    }

    // ------------------------------------------------------------------ c

    /**
     * 两重限制:记录值 = min(目标 × 0.1, 自身 × 0.5)。
     * 用一头牛(最大生命 10)当目标,分别把玩家的最大生命设成 4 和 1:
     * <ul>
     *   <li>自身 4 ⇒ 10% 生效:min(1.0, 2.0) = 1.0;</li>
     *   <li>自身 1 ⇒ 50% 生效:min(1.0, 0.5) = 0.5。</li>
     * </ul>
     */
    /**
     * 两重限制:记录值 = min(目标 × 0.1, 自身 × 0.5)。
     * 用纯函数分别验证"10% 生效"和"50% 生效",再用真实生物/玩家核对 computeRecords 与之一致。
     */
    private static boolean testRecordMath(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        double tenBind = HistoryMistHandler.recordedAmount(10.0D, 4.0D);    // min(1.0, 2.0) = 1.0
        double capBind = HistoryMistHandler.recordedAmount(10.0D, 1.0D);    // min(1.0, 0.5) = 0.5
        double zero = HistoryMistHandler.recordedAmount(10.0D, 0.0D);       // 自身 0 => 记不了
        boolean pureOk = Math.abs(tenBind - 1.0D) < 1.0E-9D
            && Math.abs(capBind - 0.5D) < 1.0E-9D && zero == 0.0D;

        Cow cow = EntityType.COW.create(level);
        if (cow == null) {
            check("T43c", false);
            return false;
        }
        level.addFreshEntity(cow);
        boolean consistent = true;
        int entries = 0;
        StringBuilder sample = new StringBuilder();
        var records = HistoryMistHandler.computeRecords(player, cow);
        for (var entry : records.entrySet()) {
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(entry.getKey());
            if (attribute == null) {
                consistent = false;
                continue;
            }
            double expected = HistoryMistHandler.recordedAmount(cow.getAttributeValue(attribute),
                player.getAttributeValue(attribute));
            if (Math.abs(expected - entry.getValue()) > 1.0E-9D) {
                consistent = false;
            }
            entries++;
            if (sample.length() < 120) {
                sample.append(entry.getKey().getPath()).append("=").append(entry.getValue()).append(" ");
            }
        }
        boolean nonEmpty = entries > 0;
        log(TAG + " T43c 纯函数:10%生效 min(1.0,2.0)=" + tenBind + "[应 1.0];50%生效 min(1.0,0.5)=" + capBind
            + "[应 0.5];自身0=" + zero + "[应 0];真实记录条目=" + entries + " 与 formula 一致=" + consistent);
        log(TAG + " T43c 样本: " + sample);
        cow.discard();
        boolean ok = pureOk && nonEmpty && consistent;
        check("T43c", ok);
        return ok;
    }

    // ------------------------------------------------------------------ d

    /** 可反复使用:第二次记录会覆盖第一次(只保留最新的生物)。 */
    private static boolean testRecordOnce(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        Item mist = ModItems.historyMistOrNull();
        Cow cow = EntityType.COW.create(level);
        net.minecraft.world.entity.monster.Zombie zombie = EntityType.ZOMBIE.create(level);
        if (mist == null || cow == null || zombie == null) {
            check("T43d", false);
            return false;
        }
        level.addFreshEntity(cow);
        level.addFreshEntity(zombie);
        ItemStack stack = new ItemStack(mist);
        log(TAG + " T43d 诊断: 牛属性数=" + cow.getAttributes().getSyncableAttributes().size()
            + " 玩家属性数=" + player.getAttributes().getSyncableAttributes().size()
            + " 玩家最大生命=" + player.getAttributeValue(Attributes.MAX_HEALTH)
            + " 牛最大生命=" + cow.getAttributeValue(Attributes.MAX_HEALTH)
            + " computeRecords=" + HistoryMistHandler.computeRecords(player, cow).size());
        boolean first = HistoryMistHandler.record(player, cow, stack);
        var afterCow = new java.util.LinkedHashMap<>(HistoryMistHandler.readRecords(stack));
        boolean second = HistoryMistHandler.record(player, zombie, stack);
        var afterZombie = HistoryMistHandler.readRecords(stack);
        var expected = HistoryMistHandler.computeRecords(player, zombie);
        // 覆盖后的内容必须等于"僵尸此刻应记录的值",而不是牛的那份
        boolean overwritten = afterZombie.size() == expected.size() && matches(afterZombie, expected);
        boolean changed = !matches(afterCow, afterZombie) || afterCow.size() != afterZombie.size();
        log(TAG + " T43d 第一次(牛)=" + first + "[应 true];第二次(僵尸)=" + second + "[应 true 可重复];"
            + "记录被覆盖=" + overwritten + "[应 true];两份记录不同=" + changed + "[应 true];条目=" + afterZombie.size());
        cow.discard();
        zombie.discard();
        boolean ok = first && second && overwritten && changed && !afterZombie.isEmpty();
        check("T43d", ok);
        return ok;
    }

    /**
     * 1.2.5d:瓶装历史迷雾可以放进古旧书袋 —— 验证
     * <ul>
     *   <li>它被写进 {@code minecraft:bookshelf_books} 标签(AntiqueBag.isBook 认的就是这个标签);</li>
     *   <li>书袋 HUD 的渲染/签名能处理"记录属性"那一行(属性名 + 数值),并在数值变化时改变签名。</li>
     * </ul>
     */
    private static boolean testBagCompatibility() {
        // 直接查运行时标签:AntiqueBag.isBook 认的就是 minecraft:bookshelf_books
        // (不能读 jar 里的同名 json —— enigmaticaddons 也提供了一份,classloader 会先拿到它的)
        Item mistItem = ModItems.historyMistOrNull();
        boolean tagOk = mistItem != null && new ItemStack(mistItem).is(net.minecraft.tags.ItemTags.BOOKSHELF_BOOKS);

        var entry = new BagHudEffects.Entry(BagHudEffects.HISTORY_MIST_ID, BagHudEffects.MIST_ATTR_KEY,
            "minecraft:generic.max_health", true, "+2");
        String shown = BagHudEffects.display(entry).getString();
        // 服务端 JVM 默认是英文,属性名会解析成 "Max Health"/"最大生命" 之类;这里只断言"名字被翻译了 + 数值在"
        boolean rendered = shown.contains("+2") && !shown.contains("minecraft:generic.max_health");

        var changed = new BagHudEffects.Entry(BagHudEffects.HISTORY_MIST_ID, BagHudEffects.MIST_ATTR_KEY,
            "minecraft:generic.max_health", true, "+3");
        boolean signatureSeesValue = !BagHudEffects.signature(java.util.List.of(entry))
            .equals(BagHudEffects.signature(java.util.List.of(changed)));

        log(TAG + " T43f 书袋标签含本物品=" + tagOk + "[应 true];HUD 行渲染=" + shown
            + "[应含属性名与数值];数值变化会重发=" + signatureSeesValue + "[应 true]");
        boolean ok = tagOk && rendered && signatureSeesValue;
        check("T43f", ok);
        return ok;
    }


    private static boolean matches(java.util.Map<ResourceLocation, Double> a, java.util.Map<ResourceLocation, Double> b) {
        for (var entry : b.entrySet()) {
            Double other = a.get(entry.getKey());
            if (other == null || Math.abs(other - entry.getValue()) > 1.0E-6D) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ e

    private static boolean testModifiers(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        Item mist = ModItems.historyMistOrNull();
        if (mist == null) {
            check("T43e", false);
            return false;
        }
        var inventory = player.getInventory();
        ItemStack previous = inventory.getItem(1);
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        double before = player.getAttributeValue(Attributes.MAX_HEALTH);
        try {
            ItemStack stack = new ItemStack(mist);
            // 直接写一条已知记录:最大生命 +2(模拟记录完成)
            var list = new net.minecraft.nbt.ListTag();
            var one = new net.minecraft.nbt.CompoundTag();
            one.putString("Attribute", "minecraft:generic.max_health");
            one.putDouble("Amount", 2.0D);
            list.add(one);
            stack.getOrCreateTag().put(HistoryMistHandler.RECORDS_KEY, list);
            stack.getOrCreateTag().putBoolean(HistoryMistHandler.USED_KEY, true);
            inventory.setItem(1, stack);

            HistoryMistHandler.syncModifiers(player);
            double withMist = player.getAttributeValue(Attributes.MAX_HEALTH);
            boolean added = Math.abs(withMist - before - 2.0D) < 1.0E-6D;

            // 摘掉瓶子:加成必须消失
            inventory.setItem(1, ItemStack.EMPTY);
            HistoryMistHandler.syncModifiers(player);
            double afterRemoval = player.getAttributeValue(Attributes.MAX_HEALTH);
            boolean removed = Math.abs(afterRemoval - before) < 1.0E-6D;

            log(TAG + " T43e 携带前=" + before + ";携带后=" + withMist + "[应 +2];拿走瓶子后=" + afterRemoval
                + "[应回落到 " + before + "]");
            boolean ok = added && removed;
            check("T43e", ok);
            return ok;
        }
        finally {
            inventory.setItem(1, previous);
            HistoryMistHandler.syncModifiers(player);
            if (health != null) {
                // 清掉可能残留的修饰符
                health.removeModifiers();
            }
        }
    }

    // ------------------------------------------------------------------ 工具

    /** 资源是否打进 jar(classpath 里能读到)。 */
    private static boolean resource(String path) {
        try (var in = HistoryMistSelfTest.class.getClassLoader().getResourceAsStream(path)) {
            return in != null;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private static void log(String line) {
        EnigmaticEnhance.LOGGER.info(line);
    }

    private static void check(String test, boolean ok) {
        log(TAG + " " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }
}
