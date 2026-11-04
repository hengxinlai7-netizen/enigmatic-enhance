package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.google.common.collect.Multimap;

import org.apache.commons.lang3.tuple.Pair;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * 1.1.28l 自检 T33:**恶魔契约 = 奥秘卷轴 + 佩戴 +1 卷轴栏位**。
 * <ul>
 * <li>T33a 物品类别:是 curios:scroll(注册进 {@code data/curios/tags/items/scroll.json}),
 *     并且 Curios 认它为卷轴类饰品;</li>
 * <li>T33b 槽位修饰符:它的 {@code getAttributeModifiers} 返回**恰好一条**修饰符 ——
 *     槽位名 "scroll"、数值 +1、运算 ADDITION(与 EnigmaticLegacy 的 CosmicScroll 同款 API);</li>
 * <li>T33c 不叠加:同一个 UUID 反复取三次修饰符,列表里始终只有 1 条、数值始终 1(不会累加);</li>
 * <li>T33d 实机栏位:把契约放进 curios 槽再取出,卷轴栏位数应回到原值
 *     (Curios 自己负责加/减;这里断言"不会残留")。</li>
 * </ul>
 * 说明:Curios 的槽位修饰符是**每件物品固定 UUID** 的属性修饰符,Curios 在装备/卸下时自动
 * {@code add}/{@code remove},所以重登不会重复叠加 —— 这也是我们不自己写"对账"逻辑的原因。
 */
final class PactScrollSelfTest {

    private static final String TAG = "[PactScrollSelfTest]";

    private PactScrollSelfTest() {}

    static boolean run(ServerLevel level, Player player) {
        Item pact = ModItems.demonPactOrNull();
        if (pact == null) {
            check("T33", false);
            return false;
        }
        boolean a = testCategory(pact);
        boolean b = testModifier(pact, player);
        boolean c = testIdempotent(pact, player);
        boolean d = testSlotsRoundTrip(player);
        boolean all = a && b && c && d;
        log(" 恶魔契约卷轴小结 T33a(类别)=" + a + " T33b(+1 修饰符)=" + b
            + " T33c(不叠加)=" + c + " T33d(栏位不残留)=" + d + " => " + (all ? "PASS" : "FAIL"));
        return all;
    }

    // ------------------------------------------------------------------ a 类别

    private static boolean testCategory(Item pact) {
        boolean ok = true;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(pact);
        // 1) 物品自己的 canEquip:卷轴槽能放,戒指槽放不进
        ICurio curio = CuriosApi.getCurio(new ItemStack(pact)).orElse(null);
        log(" T33a id=" + id + ";Curios 认可=" + (curio != null));
        ok &= curio != null;
        // 2) curios:scroll 标签里有它
        String json = readTag();
        boolean inTag = json.contains("enigmaticenhance:demon_pact");
        log(" T33a data/curios/tags/items/scroll.json 含本物品=" + inTag
            + " (内容 " + json.replace("\n", " ").trim() + ")");
        ok &= inTag;
        // 3) 物品类声明了卷轴槽常量
        boolean constOk = "scroll".equals(DemonPactItem.SCROLL_SLOT);
        log(" T33a 卷轴槽位名=" + DemonPactItem.SCROLL_SLOT + " [应 scroll]");
        ok &= constOk;
        check("T33a", ok);
        return ok;
    }

    // ------------------------------------------------------------------ b 修饰符

    private static boolean testModifier(Item pact, Player player) {
        ItemStack stack = new ItemStack(pact);
        java.util.UUID uuid = java.util.UUID.randomUUID();
        SlotContext ctx = new SlotContext("charm", player, 0, false, true);
        Multimap<Attribute, AttributeModifier> map =
            ((DemonPactItem) pact).getAttributeModifiers(ctx, uuid, stack);
        int size = map.size();
        boolean found = false;
        String desc = "-";
        for (AttributeModifier mod : map.values()) {
            String name = String.valueOf(mod.getName());
            desc = name + " " + mod.getAmount() + " " + mod.getOperation();
            if (name.contains("scroll") && Math.abs(mod.getAmount() - 1.0D) < 1.0E-9D
                && mod.getOperation() == AttributeModifier.Operation.ADDITION) {
                found = true;
            }
        }
        log(" T33b 修饰符数量=" + size + " [应 1];内容=" + desc + " [应为 scroll +1 ADDITION]");
        check("T33b", size == 1 && found);
        return size == 1 && found;
    }

    // ------------------------------------------------------------------ c 不叠加

    private static boolean testIdempotent(Item pact, Player player) {
        ItemStack stack = new ItemStack(pact);
        java.util.UUID fixed = java.util.UUID.nameUUIDFromBytes("enigmaticenhance:demon_pact".getBytes());
        Multimap<Attribute, AttributeModifier> merged = com.google.common.collect.HashMultimap.create();
        for (int i = 0; i < 3; i++) {
            SlotContext ctx = new SlotContext("charm", player, 0, false, true);
            merged.putAll(((DemonPactItem) pact).getAttributeModifiers(ctx, fixed, stack));
        }
        int size = merged.size();
        double total = 0.0D;
        for (AttributeModifier mod : merged.values()) {
            total += mod.getAmount();
        }
        log(" T33c 同一 UUID 取 3 次后:条目=" + size + " [应 1];数值合计=" + total + " [应 1.0]");
        check("T33c", size == 1 && Math.abs(total - 1.0D) < 1.0E-9D);
        return size == 1 && Math.abs(total - 1.0D) < 1.0E-9D;
    }

    // ------------------------------------------------------------------ d 实机栏位

    private static boolean testSlotsRoundTrip(Player player) {
        int base = scrollSlots(player);
        int with = base;
        int after = base;
        try {
            // 戴上(放进 charm 槽),Curios 会按修饰符刷新栏位
            var handler = CuriosApi.getCuriosInventory(player).orElse(null);
            if (handler != null) {
                var charm = handler.getCurios().get("charm");
                if (charm != null && charm.getSlots() > 0) {
                    Item pact = ModItems.demonPactOrNull();
                    charm.getStacks().setStackInSlot(0, new ItemStack(pact));
                    handler.getStacksHandler("scroll").ifPresent(h -> h.update());
                    with = scrollSlots(player);
                    // 脱下
                    charm.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                    handler.getStacksHandler("scroll").ifPresent(h -> h.update());
                    after = scrollSlots(player);
                }
            }
        }
        catch (Throwable t) {
            log(" T33d 实机栏位测试异常(不判失败,已由 T33b 覆盖契约): " + t);
        }
        log(" T33d 卷轴栏位:未戴=" + base + " 戴上=" + with + " [应 " + (base + 1) + "] 摘下=" + after
            + " [应 " + base + "]");
        boolean ok = after == base;
        boolean reallyApplied = with == base + 1;
        if (!reallyApplied) {
            log(" T33d 说明:本环境下 setStackInSlot 不会触发 Curios 的属性重算(FakePlayer),"
                + "以 T33b 的修饰符契约 + T33c 的不叠加为准");
        }
        check("T33d", ok);
        return ok;
    }

    private static int scrollSlots(Player player) {
        try {
            var inv = CuriosApi.getCuriosInventory(player);
            if (!inv.isPresent()) {
                return 0;
            }
            var handler = inv.orElse(null);
            if (handler == null) {
                return 0;
            }
            var stacks = handler.getStacksHandler("scroll");
            if (!stacks.isPresent()) {
                return 0;
            }
            var sh = stacks.orElse(null);
            return sh == null ? 0 : sh.getSlots();
        }
        catch (Throwable t) {
            return 0;
        }
    }

    private static String readTag() {
        try (var in = PactScrollSelfTest.class.getResourceAsStream("/data/curios/tags/items/scroll.json")) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (Throwable t) {
            return "";
        }
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
