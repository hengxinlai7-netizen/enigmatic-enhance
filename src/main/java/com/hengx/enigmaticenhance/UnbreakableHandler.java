package com.hengx.enigmaticenhance;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.2.9e:「无法破坏」的运行时。
 *
 * <p>这个附魔本身没有"持续效果" —— 它一落到物品上就把物品变成原版的
 * {@code Unbreakable:1b},然后<b>把自己从物品上抹掉</b>。所以它只能生效一次,也不会占附魔位。</p>
 *
 * <p>为什么用"每秒扫背包"而不是只在附魔台结算时处理:附魔来源不止一个(附魔台、铁砧、
 * 创造模式、命令、战利品、别的模组的注入),扫背包能覆盖全部路径,最迟 1 秒内完成转化。</p>
 */
public final class UnbreakableHandler {

    /** 原版"无法破坏"的 NBT 键。 */
    public static final String UNBREAKABLE_TAG = "Unbreakable";

    private UnbreakableHandler() {
    }

    /** 把物品变成无法破坏并清掉这个附魔;返回是否真的动了物品。 */
    public static boolean convert(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Enchantment enchantment = ModEnchantments.unbreakableOrNull();
        if (enchantment == null) {
            return false;
        }
        Map<Enchantment, Integer> enchantments;
        try {
            enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(stack));
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        if (!enchantments.containsKey(enchantment)) {
            return false;
        }
        // 1) 打上原版无法破坏标记
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(UNBREAKABLE_TAG, true);
        // 2) 抹掉自己
        enchantments.remove(enchantment);
        EnchantmentHelper.setEnchantments(enchantments, stack);
        return true;
    }

    /** 扫一遍玩家背包(主栏 + 盔甲 + 副手),返回转化了几件。 */
    public static int sweep(Player player) {
        if (player == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int changed = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && convert(stack)) {
                ++changed;
            }
        }
        return changed;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.unbreakableEnchantEnabled()) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player == null || player.level().isClientSide() || player.tickCount % 20 != 0) {
            return;
        }
        try {
            int changed = sweep(player);
            if (changed > 0) {
                DebugLog.info("[Unbreakable] {} 的 {} 件物品已变为无法破坏(附魔已移除)",
                    player.getName().getString(), changed);
            }
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[Unbreakable] 扫描异常", ex);
        }
    }
}
