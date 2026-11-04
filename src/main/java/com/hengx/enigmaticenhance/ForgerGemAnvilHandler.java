package com.hengx.enigmaticenhance;

import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.6.33:「锻造者宝石」新增效果 —— 两本**同附魔**的书在铁砧里可以升到**合法最高等级**。
 *
 * <p>判定:</p>
 * <ul>
 *   <li>玩家背包/副手里有 {@code enigmaticaddons:forger_gem} 或 {@code enigmaticaddons:forger_crystal};</li>
 *   <li>左右槽都是附魔书,且**附魔完全相同**(同一种附魔、同级);</li>
 *   <li>结果 = 该附魔 +1 级,且不超过 {@link Enchantment#getMaxLevel()}。</li>
 * </ul>
 */
public final class ForgerGemAnvilHandler {

    private ForgerGemAnvilHandler() {
    }

    private static boolean hasGem(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isGem(player.getInventory().getItem(i))) {
                return true;
            }
        }
        if (isGem(player.getOffhandItem())) {
            return true;
        }
        // 1.6.43:锻造者宝石是 Curios 饰品,用编译期 API 扫所有槽位
        try {
            if (top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(ForgerGemAnvilHandler::isGem).isPresent())
                .orElse(false)) {
                return true;
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 没有 Curios 就跳过
        }
        return false;
    }

    private static boolean isGem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return "enigmaticaddons:forger_gem".equals(id) || "enigmaticaddons:forger_crystal".equals(id);
    }

    /** 1.7.3:神化(Apotheosis)会把锋利等附魔上限从 5 提到 9;用它的 EnchHooks.getMaxLevel 拿真实上限,没有神化就退回原版。 */
    private static volatile java.lang.reflect.Method apothMaxLevel;
    private static int maxLevel(Enchantment e) {
        try {
            java.lang.reflect.Method m = apothMaxLevel;
            if (m == null) {
                Class<?> hooks = Class.forName("dev.shadowsoffire.apotheosis.ench.asm.EnchHooks");
                m = hooks.getMethod("getMaxLevel", Enchantment.class);
                apothMaxLevel = m;
            }
            return ((Number) m.invoke(null, e)).intValue();
        }
        catch (Throwable t) {
            return e.getMaxLevel();
        }
    }

    /** 1.7.0:给锻造者宝石/晶体追加说明(铁砧升满级)。 */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        try {
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty() || !isGem(stack)) {
                return;
            }
            event.getToolTip().add(Component.literal("§7§o手持时:铁砧中两本同附魔同级书可直接升到合法最高等级").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        try {
            ItemStack left = event.getLeft();
            ItemStack right = event.getRight();
            Player player = event.getPlayer();
            if (player == null || left.isEmpty() || right.isEmpty()) {
                return;
            }
            if (!left.is(Items.ENCHANTED_BOOK) || !right.is(Items.ENCHANTED_BOOK)) {
                return;
            }
            if (!hasGem(player)) {
                return;
            }
            Map<Enchantment, Integer> leftEnch = EnchantmentHelper.getEnchantments(left);
            Map<Enchantment, Integer> rightEnch = EnchantmentHelper.getEnchantments(right);
            if (leftEnch.size() != 1 || rightEnch.size() != 1) {
                return;   // 只处理"单附魔书",避免复杂组合
            }
            Map.Entry<Enchantment, Integer> l = leftEnch.entrySet().iterator().next();
            Map.Entry<Enchantment, Integer> r = rightEnch.entrySet().iterator().next();
            if (!l.getKey().equals(r.getKey()) || !l.getValue().equals(r.getValue())) {
                return;   // 必须同附魔、同级
            }
            Enchantment enchantment = l.getKey();
            int current = l.getValue();
            // 1.7.3:升到"创造物品栏/JEI 显示的最高级"(神化会把上限抬高)
            int max = maxLevel(enchantment);
            if (max <= current) {
                return;   // 理论上不会发生(原版书不会超上限)
            }
            ItemStack out = new ItemStack(Items.ENCHANTED_BOOK);
            ItemStack stored = new ItemStack(Items.ENCHANTED_BOOK);
            ListTag list = new ListTag();
            CompoundTag tag = new CompoundTag();
            tag.putString("id", BuiltInRegistries.ENCHANTMENT.getKey(enchantment).toString());
            tag.putShort("lvl", (short) max);
            list.add(tag);
            out.getOrCreateTag().put("StoredEnchantments", list);
            event.setOutput(out);
            event.setCost(1);
            event.setMaterialCost(1);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}