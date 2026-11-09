package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.7.2:「渔获瓶」效果:每点幸运值 +10% 额外钓鱼产物,超过 100% 允许多次结算;
 * 放入古旧书袋({@code enigmaticaddons:antique_bag})时额外 +2 幸运。
 */
public final class FishingHaulBottleHandler {

    private FishingHaulBottleHandler() {
    }

    private static boolean isBottle(ItemStack s) {
        return s != null && !s.isEmpty()
            && ModItems.FISHING_HAUL_BOTTLE.isPresent()
            && s.getItem() == ModItems.FISHING_HAUL_BOTTLE.get();
    }

    private static boolean inBag(Player p) {
        ItemStack found = BookBag.findBook(p,
            new ResourceLocation(EnigmaticEnhance.MODID, ModIds.FISHING_HAUL_BOTTLE_PATH));
        return found != null && !found.isEmpty();
    }

    private static boolean carried(Player p) {
        for (ItemStack s : p.getInventory().items) {
            if (isBottle(s)) {
                return true;
            }
        }
        return isBottle(p.getOffhandItem()) || isBottle(p.getMainHandItem()) || inBag(p);
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.fishingHaulEnabled()) {
            return;
        }
        try {
            Player p = event.getEntity();
            if (p == null || p.level().isClientSide || !carried(p)) {
                return;
            }
            int luck = (int) Math.floor((double) p.getLuck());
            if (inBag(p)) {
                luck += 2;
            }
            if (luck <= 0) {
                return;
            }
            int extra = luck / 10;
            float frac = (luck % 10) / 10.0F;
            if (p.level().random.nextFloat() < frac) {
                extra++;
            }
            if (extra <= 0) {
                return;
            }
            ServerLevel sl = (ServerLevel) p.level();
            LootTable table = sl.getServer().getLootData().getLootTable(new ResourceLocation("gameplay/fishing"));
            LootParams params = new LootParams.Builder(sl)
                .withParameter(LootContextParams.ORIGIN, p.position())
                .withParameter(LootContextParams.TOOL, p.getMainHandItem())
                .withParameter(LootContextParams.THIS_ENTITY, p)
                .withLuck(p.getLuck())
                .create(LootContextParamSets.FISHING);
            for (int i = 0; i < extra; i++) {
                table.getRandomItems(params, sl.random.nextLong(), event.getDrops()::add);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}