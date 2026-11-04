package com.hengx.enigmaticenhance;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 2.7.0:「地狱刃片护符」保底。
 *
 * <p>打开战利品箱时,若该箱子的战利品表里<b>含有</b> {@code enigmaticaddons:hell_blade_charm},
 * 则给这名玩家追加一份(每名玩家仅一次,持久化在玩家 PersistentData)。</p>
 *
 * <p>配置:{@code [lootGuarantee] hellBladeGuaranteed = true}。</p>
 */
public final class HellBladeLootHandler {

    /** 目标物品 id。 */
    private static final String HELL_BLADE_ID = "enigmaticaddons:hell_blade_charm";

    /** 已确认"表里有地狱刃片"的战利品表 id。 */
    private static final Set<ResourceLocation> TABLES = ConcurrentHashMap.newKeySet();

    /** 玩家 PersistentData 键:已经保底过。 */
    private static final String TAG_DONE = "enigmaticenhance:hell_blade_granted";

    private HellBladeLootHandler() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        try {
            if (!RingConfig.hellBladeGuaranteed()) {
                return;
            }
            if (event.getName() == null || TABLES.contains(event.getName())) {
                return;
            }
            if (tableContains(event.getTable())) {
                TABLES.add(event.getName());
                DebugLog.info("[HellBlade] 记录含地狱刃片的战利品表 {}", event.getName());
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        try {
            if (!RingConfig.hellBladeGuaranteed()) {
                return;
            }
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            // 2.7.2:不再用一次性标记 —— 只要玩家身上还没有地狱刃片,保底就一直有效;
            // 真正拿到之后(背包/饰品栏里有)才失效。
            if (playerHasBlade(player)) {
                return;
            }
            Container menuContainer = containerOf(event.getContainer());
            ResourceLocation table = menuContainer == null ? null : lootTableOf(menuContainer);
            if (table == null || !TABLES.contains(table)) {
                return;
            }
            Item blade = ForgeRegistries.ITEMS.getValue(new ResourceLocation(HELL_BLADE_ID));
            if (blade == null) {
                return;
            }
            ItemStack stack = new ItemStack(blade);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            DebugLog.info("[HellBlade] {} 从 {} 获得保底地狱刃片护符", player.getName().getString(), table);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    // ------------------------------------------------------------------ 内部

    /** 玩家背包 / 副手 / 饰品栏里是否已经有地狱刃片护符。 */
    private static boolean playerHasBlade(net.minecraft.world.entity.player.Player player) {
        try {
            for (ItemStack st : player.getInventory().items) {
                if (isBlade(st)) {
                    return true;
                }
            }
            if (isBlade(player.getOffhandItem())) {
                return true;
            }
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .map(h -> h.findFirstCurio(HellBladeLootHandler::isBlade).isPresent())
                .orElse(false);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isBlade(ItemStack st) {
        if (st == null || st.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        return id != null && HELL_BLADE_ID.equals(id.toString());
    }


    /** 反射扫 LootTable.pools → LootPool.entries,递归找目标物品。 */
    private static boolean tableContains(net.minecraft.world.level.storage.loot.LootTable table) {
        try {
            Field poolsField = net.minecraft.world.level.storage.loot.LootTable.class.getDeclaredField("pools");
            poolsField.setAccessible(true);
            Object pools = poolsField.get(table);
            if (pools instanceof List<?> list) {
                for (Object pool : list) {
                    if (pool instanceof LootPool lp && poolContains(lp)) {
                        return true;
                    }
                }
            }
        }
        catch (Throwable ignored) {
            // 反射失败:当作不含
        }
        return false;
    }

    private static boolean poolContains(LootPool pool) {
        try {
            Field f = LootPool.class.getDeclaredField("entries");
            f.setAccessible(true);
            Object entries = f.get(pool);
            return entries != null && scanObject(entries, 0);
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    /** 递归扫任意对象里的字段,找 Item / 物品 id 字符串。 */
    private static boolean scanObject(Object obj, int depth) {
        if (obj == null || depth > 6) {
            return false;
        }
        if (obj instanceof Item item) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            return id != null && HELL_BLADE_ID.equals(id.toString());
        }
        if (obj instanceof net.minecraft.world.item.ItemStack st) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
            return id != null && HELL_BLADE_ID.equals(id.toString());
        }
        if (obj instanceof List<?> list) {
            for (Object o : list) {
                if (scanObject(o, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (obj instanceof ItemStack[] arr) {
            for (Object o : arr) {
                if (scanObject(o, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().isPrimitive()) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v != null && v != obj && scanObject(v, depth + 1)) {
                        return true;
                    }
                }
                catch (Throwable ignored) {
                    // 下一字段
                }
            }
        }
        return false;
    }

    /** 从菜单里取出真正的容器(ChestMenu 直接给,其它反射兜底)。 */
    private static Container containerOf(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (menu == null) {
            return null;
        }
        if (menu instanceof net.minecraft.world.inventory.ChestMenu chest) {
            return chest.getContainer();
        }
        for (String name : new String[] {"getContainer", "getInventory", "getTileInventory"}) {
            try {
                Object v = menu.getClass().getMethod(name).invoke(menu);
                if (v instanceof Container c) {
                    return c;
                }
            }
            catch (ReflectiveOperationException ignored) {
                // 换下一个
            }
        }
        return null;
    }

    /** 从打开的容器反查它所属方块实体的战利品表。 */
    private static ResourceLocation lootTableOf(Container container) {
        try {
            if (container instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                String s = be.saveWithoutMetadata().getString("LootTable");
                return s.isEmpty() ? null : ResourceLocation.tryParse(s);
            }
            // Lootr / 包装容器:反射找 BlockEntity 字段
            for (Class<?> c = container.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!net.minecraft.world.level.block.entity.BlockEntity.class.isAssignableFrom(f.getType())) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object v = f.get(container);
                    if (v instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                        String s = be.saveWithoutMetadata().getString("LootTable");
                        if (!s.isEmpty()) {
                            return ResourceLocation.tryParse(s);
                        }
                    }
                }
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
        return null;
    }
}
