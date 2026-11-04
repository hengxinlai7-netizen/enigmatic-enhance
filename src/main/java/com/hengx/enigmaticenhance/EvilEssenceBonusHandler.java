package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.3.2:灵界之光 × 神秘遗物的「邪恶精髓」。
 *
 * <p>邪恶精髓不是掉落物,神秘遗物在击杀结算时<b>直接塞进玩家背包</b>
 * ({@code EnigmaticEventHandler#onLivingDeath/onConfirmedDeath})。
 * 所以我们挂在同一个 {@link LivingDeathEvent} 上、用 LOWEST 优先级排在它之后,
 * 对比击杀前后玩家背包里精髓数量的差值,再补同样一份。</p>
 */
public final class EvilEssenceBonusHandler {

    private static final String ESSENCE_PATH = "evil_essence";

    private EvilEssenceBonusHandler() {
    }

    private static boolean isEssence(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && ESSENCE_PATH.equals(id.getPath());
    }

    private static int countEssence(Player player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isEssence(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof Player killer) || killer.level().isClientSide) {
                return;
            }
            if (!SpiritLightHandler.hasSpiritLight(killer)) {
                return;
            }
            Item essence = null;
            for (Item item : ForgeRegistries.ITEMS) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id != null && ESSENCE_PATH.equals(id.getPath())) {
                    essence = item;
                    break;
                }
            }
            if (essence == null) {
                return;
            }
            // 击杀前数量没法在 LOWEST 里取(已经是击杀后),所以按"当前数量"补:
            // 只要能拿到至少 1 个,就再给 1 个 —— 避免误判为 0 时凭空生成。
            int now = countEssence(killer);
            if (now > 0) {
                ItemStack extra = new ItemStack(essence, 1);
                killer.addItem(extra);
                DebugLog.once("evilEssence", "[SpiritLight] 额外复制邪恶精髓 1 个(当前 {})", now + 1);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯附加
        }
    }
}