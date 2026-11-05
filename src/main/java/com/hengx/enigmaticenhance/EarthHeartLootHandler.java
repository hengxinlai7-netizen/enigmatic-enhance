package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 大地之心「战利品记录」。
 *
 * <h2>玩法</h2>
 * <ul>
 *   <li><b>潜行 + 右键</b>箱子（或任何带战利品表的容器）→ <b>记录它的战利品表</b>，
 *       同时消耗 1 个大地之心；如果这个容器<b>没有战利品表</b>，则不消耗、什么也不做（等于返还）。</li>
 *   <li>记录之后，<b>每次关闭箱子时如果箱子里是空的</b> → 用记下的战利品表<b>重新投一次</b>，
 *       并且<b>消耗 1 条记录</b>。</li>
 *   <li><b>记录相加原则</b>：同一箱子可以反复记录，次数<b>累加</b>——
 *       大地之心 = +1 次，寰宇之心 = +10 次；多次记录不会覆盖、只会相加。</li>
 * </ul>
 *
 * <h2>兼容</h2>
 * <p>只依赖 {@link RandomizableContainerBlockEntity}（原版箱子/木桶/潜影盒、以及 <b>Lootr</b> 的战利品箱
 * 都是它的子类），所以 Lootr 的"每人一份战利品"逻辑照旧：重新投递走的还是 Lootr 自己覆写的
 * {@code unpackLootTable}，我们只负责把表重新挂回去。</p>
 *
 * <p>记录用 {@link Data}（SavedData）存，跟着存档走；按"维度 + 坐标"索引，
 * 所以容器被替换/破坏后记录自然失效，不会串到别的箱子上。</p>
 */
public final class EarthHeartLootHandler {

    private static final String LOG_TAG = "[EarthHeartLoot]";

    /** persist key：记录表 */
    private static final String DATA_NAME = "enigmaticenhance_earthheart_loot";

    private EarthHeartLootHandler() {
    }

    /** 末地城宝箱表：只认寰宇之心，且一颗只算 1 次。 */
    public static final String END_CITY_TABLE = "minecraft:chests/end_city_treasure";

    /** 是否只是「大地之心」（用来给出拒绝提示）。 */
    private static boolean isEarthOnly(ItemStack stack) {
        Item earth = RingConfig.earthHeartItem();
        return earth != null && stack != null && !stack.isEmpty() && stack.is(earth);
    }

    /** 是否为「寰宇之心」一类（宇宙之心 / 全属性之心 / 通用之心）。 */
    private static boolean isCosmicHeart(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return path.contains("cosmic_heart") || path.contains("omni_heart") || path.contains("universal_heart");
    }

    /**
     * 这一叠物品对<b>指定战利品表</b>能提供几次记录。
     *
     * <ul>
     *   <li>普通表：大地之心 1 次，寰宇之心 10 次；</li>
     *   <li><b>末地城宝箱表</b>：<b>只认寰宇之心</b>，而且<b>一颗只算 1 次</b>（大地之心无效）。</li>
     * </ul>
     *
     * @return 0 表示这件物品对该表无效
     */
    private static int recordsFor(ItemStack stack, String table) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        boolean endCity = END_CITY_TABLE.equals(table);
        Item earth = RingConfig.earthHeartItem();
        if (earth != null && stack.is(earth)) {
            // 末地城：大地之心不可用
            return endCity ? 0 : 1;
        }
        if (isCosmicHeart(stack)) {
            // 末地城：一颗寰宇之心只刷新一次
            return endCity ? 1 : 10;
        }
        return 0;
    }

    // ------------------------------------------------------------------ 记录

    /**
     * 2.5.1:改成 LOWEST + receiveCanceled。
     * <p>别的模组(Lootr、饰品、搬运类)常在更高优先级把 RightClickBlock 取消掉，
     * 默认优先级的处理器会被直接跳过 —— 之前"完全没反应、连日志都没有"就是这个原因。</p>
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST,
        receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (event.getLevel().isClientSide() || !event.getEntity().isShiftKeyDown()) {
                return;   // 只在服务端处理；必须潜行，避免影响正常开箱
            }
            ItemStack held = event.getItemStack();
            BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
            if (!(be instanceof RandomizableContainerBlockEntity container)) {
                return;   // 不是带战利品表的容器 → 不管
            }
            ResourceLocation table = lootTableOf(be);
            // 3.3.0:次数必须先看是「哪张表」——末地城表只认寰宇之心,且一颗只算 1 次
            int add = recordsFor(held, table == null ? null : table.toString());
            if (add <= 0) {
                if (table != null && END_CITY_TABLE.equals(table.toString()) && isEarthOnly(held)) {
                    event.getEntity().displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§c末地城的宝箱只能用§b寰宇之心§c刷新，且一颗只刷新一次"), true);
                    DebugLog.info("{} {} 试图用大地之心刷新末地城宝箱，已拒绝",
                        LOG_TAG, event.getEntity().getName().getString());
                }
                return;
            }
            DebugLog.once("ehl:enter:" + event.getPos().asLong(),
                "[EarthHeartLoot] 进入记录流程 pos={} item={} add={} table={}",
                event.getPos(), net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getKey(held.getItem()), add, table);
            if (table == null) {
                // 没有战利品表 ⇒ 不消耗、什么也不做（返还）
                DebugLog.info("{} {} 对 {} 使用大地之心：该容器没有战利品表，不消耗",
                    LOG_TAG, event.getEntity().getName().getString(), event.getPos());
                return;
            }
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }

            Data data = data(level);
            int total = data.add(level, event.getPos(), table, add);
            data.setDirty();

            // 消耗一份（寰宇之心也是一次性消耗掉）
            if (!event.getEntity().isCreative()) {
                held.shrink(1);
            }

            DebugLog.info("{} {} 记录 {} 的战利品表 {} → 累计 {} 次（本次 +{}）",
                LOG_TAG, event.getEntity().getName().getString(), event.getPos(), table, total, add);
            event.setCanceled(true);   // 只记录，不开箱
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.error(LOG_TAG + " 记录战利品表失败", e);
        }
    }

    // ------------------------------------------------------------------ 重投

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        try {
            if (!(event.getEntity().level() instanceof ServerLevel level)) {
                return;
            }
            Container container = containerOf(event.getContainer());
            // 3.5.0:战利品矿车(箱子矿车等)——不是方块实体,走单独路径
            if (container instanceof net.minecraft.world.entity.vehicle.AbstractMinecartContainer cart) {
                handleCartClose(event.getEntity(), cart, level);
                return;
            }
            BlockPos pos = container == null ? null : posOf(container);
            if (pos == null) {
                return;
            }
            Data data = data(level);
            String table = data.lootTable(level, pos);
            if (table == null) {
                return;   // 这个箱子没被记录过
            }
            if (!isEmpty(container)) {
                return;   // 还有东西 ⇒ 不重投、也不消耗记录
            }
            int records = data.records(level, pos);
            if (records <= 0) {
                return;
            }

            BlockEntity be = level.getBlockEntity(pos);
            // ① Lootr 箱子:走它自己的 ILootBlockEntity.unpackLootTable(player, container, table, seed),
            //    这样才会按"每人一份"重新投递。
            if (refillLootr(be, container, event.getEntity(), ResourceLocation.tryParse(table))) {
                int leftLootr = data.consume(level, pos, 1);
                data.setDirty();
                DebugLog.info("{} {} 关闭空箱(Lootr) {} → 重投 {}，剩余 {} 次",
                    LOG_TAG, event.getEntity().getName().getString(), pos, table, leftLootr);
                return;
            }
            if (!(be instanceof RandomizableContainerBlockEntity chest)) {
                return;
            }
            // 重新挂表 + 重新投递（Lootr 会走它自己覆写的 unpackLootTable，每人一份）
            chest.setLootTable(new ResourceLocation(table), level.random.nextLong());
            chest.unpackLootTable(event.getEntity());
            chest.setChanged();
            level.sendBlockUpdated(pos, chest.getBlockState(), chest.getBlockState(), 3);

            int left = data.consume(level, pos, 1);
            data.setDirty();
            DebugLog.info("{} {} 关闭空箱 {} → 重投 {}，剩余 {} 次",
                LOG_TAG, event.getEntity().getName().getString(), pos, table, left);
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.error(LOG_TAG + " 重投战利品失败", e);
        }
    }

    /**
     * 3.5.0:战利品矿车(箱子矿车 / 漏斗矿车等)关闭时的重投。
     *
     * <p>矿车不是方块实体,`AbstractMinecartContainer` 自己持有 {@code lootTable} 字段。
     * 战利品由 {@code startOpen(Player)} 在首次打开时生成,所以这里:</p>
     * <ol>
     *   <li>先把表重新挂回去;</li>
     *   <li>再调一次 {@code startOpen(player)} 触发生成;</li>
     *   <li>消耗一次记录。</li>
     * </ol>
     */
    private static void handleCartClose(Player player,
        net.minecraft.world.entity.vehicle.AbstractMinecartContainer cart,
        ServerLevel level) {
        try {
            BlockPos pos = cart.blockPosition();
            Data data = data(level);
            String table = data.lootTable(level, pos);
            if (table == null) {
                return;   // 这辆矿车没被记录过
            }
            if (!isEmpty(cart)) {
                return;   // 还有东西 ⇒ 不重投
            }
            int records = data.records(level, pos);
            if (records <= 0) {
                return;
            }
            ResourceLocation rl = ResourceLocation.tryParse(table);
            if (rl == null) {
                return;
            }
            long seed = level.random.nextLong();
            cart.setLootTable(rl, seed);
            cart.startOpen(player);   // 触发生成
            int left = data.consume(level, pos, 1);
            data.setDirty();
            DebugLog.info("{} {} 关闭空矿车 {} → 重投 {}，剩余 {} 次",
                LOG_TAG, player.getName().getString(), pos, table, left);
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn(LOG_TAG + " 矿车重投失败: {}", e.toString());
        }
    }

    /**
     * 3.5.0:对着战利品矿车潜行 + 右键 → 记录刷新次数。
     *
     * <p>矿车不是方块,{@code RightClickBlock} 不会触发,所以这里单独接 {@code EntityInteract}。</p>
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST,
        receiveCanceled = true)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        try {
            if (event.getLevel().isClientSide() || !event.getEntity().isShiftKeyDown()) {
                return;
            }
            if (!(event.getTarget() instanceof net.minecraft.world.entity.vehicle.AbstractMinecartContainer cart)) {
                return;
            }
            ItemStack held = event.getItemStack();
            ResourceLocation table = cart.getLootTable();
            if (table == null) {
                return;   // 没有战利品表 ⇒ 普通矿车,不管
            }
            int add = recordsFor(held, table.toString());
            if (add <= 0) {
                return;
            }
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            BlockPos pos = cart.blockPosition();
            Data data = data(level);
            int total = data.add(level, pos, table, add);
            data.setDirty();
            if (!event.getEntity().isCreative()) {
                held.shrink(1);
            }
            DebugLog.info("{} {} 记录矿车 {} 的战利品表 {} → 累计 {} 次（本次 +{}）",
                LOG_TAG, event.getEntity().getName().getString(), pos, table, total, add);
            event.setCanceled(true);   // 只记录,不开箱
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.error(LOG_TAG + " 记录矿车战利品表失败", e);
        }
    }

    /** 从方块实体的 NBT 里读战利品表（原版与 Lootr 都存在 "LootTable" 键）。 */
    private static ResourceLocation lootTableOf(BlockEntity be) {
        // ① 原版 / 大多数模组：方块实体 NBT 里的 LootTable
        try {
            String s = be.saveWithoutMetadata().getString("LootTable");
            if (!s.isEmpty()) {
                return ResourceLocation.tryParse(s);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 继续往下试
        }
        // ② Lootr：表存在它自己的 ILootrInfo 里，不在 NBT
        String lootr = lootTableFromLootr(be);
        if (lootr != null) {
            DebugLog.once("ehl:lootr:" + be.getBlockPos().asLong(),
                "[EarthHeartLoot] 从 Lootr 信息里读到战利品表 {} @{}", lootr, be.getBlockPos());
        }
        return lootr == null ? null : ResourceLocation.tryParse(lootr);
    }

    /** Lootr 兼容：getInfo().getLootTable() / getTable() / lootTable 字段，全用反射。 */
    private static String lootTableFromLootr(BlockEntity be) {
        try {
            Object info;
            try {
                info = be.getClass().getMethod("getInfo").invoke(be);
            }
            catch (ReflectiveOperationException e) {
                info = be;   // 老版本可能是实体自己实现 ILootrInfo
            }
            if (info == null) {
                return null;
            }
            for (String name : new String[] {"getLootTable", "getTable"}) {
                try {
                    Object v = info.getClass().getMethod(name).invoke(info);
                    if (v instanceof ResourceLocation rl) {
                        return rl.toString();
                    }
                    if (v instanceof String s && !s.isEmpty()) {
                        return s;
                    }
                }
                catch (ReflectiveOperationException ignored) {
                    // 换下一个
                }
            }
            for (String field : new String[] {"lootTable", "table"}) {
                try {
                    java.lang.reflect.Field f = info.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    Object v = f.get(info);
                    if (v instanceof ResourceLocation rl) {
                        return rl.toString();
                    }
                    if (v instanceof String s && !s.isEmpty()) {
                        return s;
                    }
                }
                catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 换下一个
                }
            }
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[EarthHeartLoot] 读 Lootr 战利品表失败: {}", e.toString());
        }
        return null;
    }

    /**
     * Lootr 兼容:用 {@code ILootBlockEntity.unpackLootTable(player, container, table, seed)} 重新投递。
     * <p>不同实例的箱子在原版 {@code setLootTable} 下会被 Lootr 视为"已开过",所以这里走它自己的入口。</p>
     *
     * @return true 表示这次已经由 Lootr 路径处理完,调用方不要再走原版逻辑
     */
    private static boolean refillLootr(BlockEntity be, Container playerInv,
        net.minecraft.world.entity.player.Player player, ResourceLocation table) {
        if (be == null || table == null || player == null) {
            return false;
        }
        try {
            Class<?> iface = Class.forName("noobanidus.mods.lootr.api.blockentity.ILootBlockEntity");
            if (!iface.isInstance(be)) {
                return false;
            }
            // 关键:必须用"该玩家专属的 Lootr 库存"(菜单里的容器),不能传方块实体本身
            Container target = playerInv != null ? playerInv : (be instanceof Container c ? c : null);
            if (target == null) {
                return false;
            }
            java.lang.reflect.Method m = iface.getMethod("unpackLootTable",
                net.minecraft.world.entity.player.Player.class, Container.class, ResourceLocation.class, long.class);
            m.invoke(be, player, target, table, player.level().random.nextLong());
            be.setChanged();
            if (be.getLevel() != null) {
                be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
            }
            return true;
        }
        catch (Throwable t) {
            DebugLog.warn("[EarthHeartLoot] Lootr 重投失败: {}", t.toString());
            return false;
        }
    }

    /** 从打开的菜单里取出真正的容器（箱子菜单直接给；Lootr 之类用反射兜底）。 */
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

    private static boolean isEmpty(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 找容器对应的方块坐标（原版容器直接用；Lootr 的每人物品栏用反射取坐标）。 */
    private static BlockPos posOf(Container container) {
        if (container instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity base) {
            return base.getBlockPos();
        }
        for (String name : new String[] {"getBlockPos", "getTilePos", "getPos"}) {
            try {
                Object v = container.getClass().getMethod(name).invoke(container);
                if (v instanceof BlockPos p) {
                    return p;
                }
            }
            catch (ReflectiveOperationException ignored) {
                // 换下一个名字
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 存档数据

    private static Data data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
            .computeIfAbsent(Data::load, Data::new, DATA_NAME);
    }

    /** 每个容器的记录：战利品表 id + 剩余次数（累加，不覆盖）。 */
    public static final class Data extends SavedData {

        private final Map<String, Integer> records = new HashMap<>();
        private final Map<String, String> tables = new HashMap<>();

        private static String key(ServerLevel level, BlockPos pos) {
            return level.dimension().location() + "|" + pos.asLong();
        }

        public static Data load(CompoundTag tag) {
            Data data = new Data();
            ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                String k = e.getString("Key");
                data.records.put(k, e.getInt("Records"));
                data.tables.put(k, e.getString("Table"));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Map.Entry<String, Integer> e : records.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putString("Key", e.getKey());
                c.putInt("Records", e.getValue());
                c.putString("Table", tables.getOrDefault(e.getKey(), ""));
                list.add(c);
            }
            tag.put("Entries", list);
            return tag;
        }

        /** 累加记录（记录相加原则），返回累加后的总数。 */
        int add(ServerLevel level, BlockPos pos, ResourceLocation table, int amount) {
            String k = key(level, pos);
            int total = records.getOrDefault(k, 0) + amount;
            records.put(k, total);
            tables.put(k, table.toString());
            return total;
        }

        String lootTable(ServerLevel level, BlockPos pos) {
            String t = tables.get(key(level, pos));
            return t == null || t.isEmpty() ? null : t;
        }

        int records(ServerLevel level, BlockPos pos) {
            return records.getOrDefault(key(level, pos), 0);
        }

        /** 消耗若干次记录；归零时清掉条目（箱子恢复成普通箱子）。 */
        int consume(ServerLevel level, BlockPos pos, int amount) {
            String k = key(level, pos);
            int left = Math.max(0, records.getOrDefault(k, 0) - amount);
            if (left == 0) {
                records.remove(k);
                tables.remove(k);
            }
            else {
                records.put(k, left);
            }
            return left;
        }

        /** 自检/调试用。 */
        public List<String> dump() {
            List<String> out = new ArrayList<>();
            records.forEach((k, v) -> out.add(k + " -> " + v + " (" + tables.get(k) + ")"));
            return out;
        }
    }
}
