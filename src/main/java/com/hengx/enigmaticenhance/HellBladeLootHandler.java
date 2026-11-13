package com.hengx.enigmaticenhance;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 地狱刃片:把下界地牢箱子里的出现概率提上去,但<b>严格保持「每名玩家一辈子只有一枚」</b>。
 *
 * <h2>原版(神秘遗物扩展)的唯一性是怎么实现的</h2>
 * <p>反编译 {@code auviotre.enigmatic.addon.contents.objects.SpecialLootModifier#doApply} 可见,
 * 扩展是<b>在战利品生成的那一刻</b>做判定的(不是拾取时):</p>
 * <pre>
 *   if (下界地牢表 &amp;&amp; player.getRandom().nextInt(3) == 0
 *       &amp;&amp; !SuperpositionHandler.hasPersistentTag(player, "LootedHellCharm")
 *       &amp;&amp; 战利品里有 earth_heart_fragment) {
 *       把 earth_heart_fragment 换成 hell_blade_charm;
 *       SuperpositionHandler.setPersistentBoolean(player, "LootedHellCharm", true);   // ← 唯一性标记
 *   }
 * </pre>
 * <p>那个标记存放在 {@code player.getPersistentData()} 的 {@code PlayerPersisted} 子标签里
 * (见 {@code SuperpositionHandler.hasPersistentTag/setPersistentTag} 的字节码),
 * 跟着玩家 NBT 永久存盘 —— 这就是「全流程只能获得一个」的唯一判定点。<b>它只认这一个键</b>,
 * 所以任何其它途径塞进来的刃片都能绕过它。</p>
 *
 * <h2>4.8.2 修的是什么</h2>
 * <p>4.0.1 起本模组给 {@code nether_bridge} / 四张堡垒表 / {@code ruined_portal} 各追加了一个
 * {@code enigmaticenhance_hell_blade} 池(按 {@code hellBladeChance} 出现)。那个池<b>完全不看</b>
 * 上面那个标记,于是「提高权重」实际变成了「无限刷」:同一名玩家反复开下界箱子就能拿到很多枚。
 * (4.0.2 曾试过在 {@code ItemPickupEvent} 里拦,但箱子里拿东西是容器之间直接搬格子、
 * 根本不会生成 ItemEntity,所以那个拦截对开箱路径完全无效。)</p>
 *
 * <p>现在改成<b>与扩展同一口径</b>:池子上挂一道 {@link #UNIQUENESS} 条件,
 * 战利品生成时(战利品上下文里带着开箱玩家 {@link LootContextParams#THIS_ENTITY}):</p>
 * <ol>
 *   <li>没有玩家上下文(指令 /loot、漏斗搬运等)→ <b>不产出</b>,宁可不发也不复制;</li>
 *   <li>玩家已经有 {@code LootedHellCharm} 标记(不管是谁给的)→ 不产出;</li>
 *   <li>玩家身上/饰品栏里已经有一枚(老存档没打过标记)→ 补打标记,不产出;</li>
 *   <li>否则 → 产出,并<b>当场写下扩展那个同名标记</b>,扩展自己的 1/3 换刃片逻辑也就此停用。</li>
 * </ol>
 * <p>标记写在 {@link #PERSISTED_ROOT}.{@link #ADDON_MARK},与扩展字节级一致,所以两边共用同一条唯一性。</p>
 *
 * <p>另外保留两条兜底:每 32 tick 扫一次背包(把 4.8.1 时代「有刃片但没标记」的存档补齐),
 * 以及拾取掉落物时拦重复(只有玩家手上确实已经有一枚时才会移除,绝不误删最后一枚)。</p>
 *
 * <p>开关:{@code [lootGuarantee] hellBladeGuaranteed}(默认 true;关掉 = 本类完全不介入,
 * 刃片回到扩展原本的 1/3 换碎片逻辑)。</p>
 */
public final class HellBladeLootHandler {

    /** 唯一的刃片 id(神秘遗物本体没有同名物品,已核对两边 jar 的 assets/lang)。 */
    private static final String HELL_BLADE_ID = "enigmaticaddons:hell_blade_charm";

    private static final String POOL_NAME = "enigmaticenhance_hell_blade";

    private static final java.util.Set<String> TABLES = java.util.Set.of(
        "minecraft:chests/nether_bridge",
        "minecraft:chests/bastion_treasure",
        "minecraft:chests/bastion_other",
        "minecraft:chests/bastion_bridge",
        "minecraft:chests/bastion_hoglin_stable",
        "minecraft:chests/ruined_portal");

    // ---------------------------------------------------------------- 唯一性标记(与扩展共用)

    /** 神秘遗物/扩展把玩家持久标记放在这个子标签里(SuperpositionHandler.PersistentTag)。 */
    private static final String PERSISTED_ROOT = "PlayerPersisted";
    /** 扩展自己的「这辈子拿过刃片」标记键(SpecialLootModifier 里那个字符串)。 */
    private static final String ADDON_MARK = "LootedHellCharm";
    /** 4.8.1 及更早版本本模组自己写的标记,只用于兼容老存档。 */
    private static final String LEGACY_MARK = "enigmaticenhance:lootedHellBlade";

    /** 兜底扫描间隔(tick)。 */
    private static final int SCAN_INTERVAL = 32;

    private HellBladeLootHandler() {
    }

    // ---------------------------------------------------------------- 注入池子

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        try {
            if (!RingConfig.hellBladeGuaranteed()) {
                return;
            }
            ResourceLocation name = event.getName();
            if (name == null || !TABLES.contains(name.toString())) {
                return;
            }
            Item blade = bladeItem();
            if (blade == null) {
                DebugLog.once("hellblade:missing", "[HellBlade] 找不到物品 {},跳过", HELL_BLADE_ID);
                return;
            }
            List<LootPool> pools = poolsOf(event.getTable());
            if (pools == null) {
                DebugLog.warn("[HellBlade] 无法访问 {} 的 pools,跳过", name);
                return;
            }
            for (LootPool p : pools) {
                if (POOL_NAME.equals(p.getName())) {
                    return;
                }
            }
            // 4.0.1:改成按概率出现。一个堡垒遗迹有多张表、几十个箱子,
            // 原来的 100% 会让一趟刷出十几个刃片。
            LootPool.Builder poolBuilder = LootPool.lootPool()
                .name(POOL_NAME)
                .setRolls(ConstantValue.exactly(1.0F));
            double chance = RingConfig.hellBladeChance();
            if (chance < 1.0D) {
                poolBuilder.when(LootItemRandomChanceCondition.randomChance((float) chance));
            }
            // 4.8.2:唯一性闸门。必须排在概率条件之后 —— 概率没过时不该动玩家的标记。
            poolBuilder.when(() -> UNIQUENESS);
            poolBuilder.add(LootItem.lootTableItem(blade).setWeight(1));
            LootPool pool = poolBuilder.build();
            if (!unfreeze(event.getTable())) {
                DebugLog.warn("[HellBlade] {} 的表处于冻结状态,无法追加池子", name);
                return;
            }
            pools.add(pool);
            DebugLog.once("hellblade:added:" + name,
                "[HellBlade] {} 的刃片出现概率 = {}(每名玩家最多一枚)",
                name, RingConfig.hellBladeChance());
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[HellBlade] 注入 {} 失败: {}", event.getName(), e.toString());
        }
    }

    // ---------------------------------------------------------------- 唯一性闸门

    /**
     * 复用一个真实存在的 {@link LootItemConditionType} 实例。
     *
     * <p>战利品条件只有<b>写回 json</b> 时才用得到 {@code getType()},而我们的池子是在代码里直接
     * 构造、永远不会被序列化,所以这里不需要注册自定义类型(注册反而会引入加载顺序问题)。
     * 从原版条件反查一个实例,拿到的必然是已注册的合法对象。</p>
     *
     * <p>做成惰性:连原版条件类都等到真的有人问 {@code getType()} 时才加载,
     * 避免模组构造期就提前触碰 {@code BuiltInRegistries} 里那批条件类型。</p>
     */
    private static LootItemConditionType reusedType;

    private static synchronized LootItemConditionType reusedType() {
        if (reusedType == null) {
            reusedType = LootItemRandomChanceCondition.randomChance(1.0F).build().getType();
        }
        return reusedType;
    }

    /**
     * 4.8.2:唯一性闸门 —— 与神秘遗物扩展 {@code LootedHellCharm} 标记同一口径。
     *
     * <p>作为<b>池级条件</b>挂上去,所以每次「这张表为该玩家掷一次」只会判定一次;
     * 通过即代表这枚刃片真的会生成,此时立刻打标记(与扩展做法一致:
     * 扩展也是在把刃片塞进战利品列表的同一刻打标记,而不是等玩家捡起来)。</p>
     */
    private static final LootItemCondition UNIQUENESS = new LootItemCondition() {

        @Override
        public LootItemConditionType getType() {
            return reusedType();
        }

        @Override
        public boolean test(LootContext context) {
            try {
                Player player = playerOf(context);
                if (player == null) {
                    // 没有玩家上下文:无法判定唯一性。宁可这次不发,也不制造第二枚。
                    DebugLog.once("hellblade:nocontext",
                        "[HellBlade] 战利品生成时没有玩家上下文,本次不产出刃片");
                    return false;
                }
                if (hasLooted(player)) {
                    DebugLog.once("hellblade:gate:" + player.getUUID(),
                        "[HellBlade] {} 已经拥有过刃片,本次不再产出", player.getName().getString());
                    return false;
                }
                if (ownsBlade(player)) {
                    // 老存档 / 别的模组给过一枚,但没打标记 → 补上,这次不出。
                    markLooted(player);
                    DebugLog.once("hellblade:adopt:" + player.getUUID(),
                        "[HellBlade] {} 身上已有刃片却没有标记,已补打标记,本次不再产出",
                        player.getName().getString());
                    return false;
                }
                markLooted(player);
                DebugLog.once("hellblade:grant:" + player.getUUID(),
                    "[HellBlade] {} 获得唯一一枚刃片,已写下 LootedHellCharm 标记",
                    player.getName().getString());
                return true;
            }
            catch (RuntimeException | LinkageError e) {
                // 判定本身出问题就当作「不能发」,避免复制
                return false;
            }
        }
    };

    /** 从战利品上下文里取出开箱玩家(没有就返回 null)。 */
    private static Player playerOf(LootContext context) {
        try {
            if (context == null || !context.hasParam(LootContextParams.THIS_ENTITY)) {
                return null;
            }
            Entity entity = context.getParam(LootContextParams.THIS_ENTITY);
            return entity instanceof Player player ? player : null;
        }
        catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 标记读写

    /** 玩家是否已经拿过刃片(扩展的标记 或 本模组老标记)。 */
    static boolean hasLooted(Player player) {
        try {
            if (player == null) {
                return false;
            }
            CompoundTag root = player.getPersistentData();
            if (root.contains(PERSISTED_ROOT, Tag.TAG_COMPOUND)
                && root.getCompound(PERSISTED_ROOT).contains(ADDON_MARK)) {
                return true;
            }
            return root.getBoolean(LEGACY_MARK);
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** 打标记:写扩展同名标记(两边共用唯一性)+ 本模组老标记(仅兼容)。 */
    private static void markLooted(Player player) {
        try {
            if (player == null) {
                return;
            }
            CompoundTag root = player.getPersistentData();
            CompoundTag persisted = root.contains(PERSISTED_ROOT, Tag.TAG_COMPOUND)
                ? root.getCompound(PERSISTED_ROOT)
                : new CompoundTag();
            persisted.putBoolean(ADDON_MARK, true);
            root.put(PERSISTED_ROOT, persisted);
            root.putBoolean(LEGACY_MARK, true);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯记账,失败不影响发物品
        }
    }

    /**
     * 撤销标记。
     *
     * <p>只给 {@link BastionTreasureHandler} 用:扭曲之心抽堡垒藏宝室时,刃片虽然生成了,
     * 但如果箱子塞不下就等于没给,这时要把标记退回去,不能让玩家白丢一次机会。</p>
     */
    static void revokeLooted(Player player) {
        try {
            if (player == null || ownsBlade(player)) {
                return;
            }
            CompoundTag root = player.getPersistentData();
            if (root.contains(PERSISTED_ROOT, Tag.TAG_COMPOUND)) {
                CompoundTag persisted = root.getCompound(PERSISTED_ROOT);
                persisted.remove(ADDON_MARK);
                root.put(PERSISTED_ROOT, persisted);
            }
            root.remove(LEGACY_MARK);
            DebugLog.once("hellblade:revoke:" + player.getUUID(),
                "[HellBlade] {} 的刃片没有真正到手,唯一性标记已退回",
                player.getName().getString());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    // ---------------------------------------------------------------- 兜底

    /**
     * 4.8.2 兜底:每 {@value #SCAN_INTERVAL} tick 扫一次背包/饰品栏。
     *
     * <p>盖住所有「不经过我们池子」的获得途径(老存档 4.8.1 刷出来的、别的模组给的、创造模式拿的),
     * 让这些玩家也打上标记,从而不再从箱子里产出第二枚。<b>只打标记,不删任何物品。</b></p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Player player = event.player;
            if (player == null || player.level().isClientSide || player.tickCount % SCAN_INTERVAL != 0) {
                return;
            }
            if (!RingConfig.hellBladeGuaranteed() || hasLooted(player)) {
                return;
            }
            if (!ownsBlade(player)) {
                return;
            }
            markLooted(player);
            DebugLog.once("hellblade:scan:" + player.getUUID(),
                "[HellBlade] {} 身上已有刃片,补打唯一性标记(老存档对齐)",
                player.getName().getString());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯兜底,失败就算了
        }
    }

    /**
     * 拾取掉落物时的兜底:只有玩家<b>手上/背包/饰品栏里确实已经有一枚</b>时,
     * 才把多出来的那枚移除 —— 绝不会把玩家最后那一枚删掉(4.8.1 的旧逻辑会)。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickup(PlayerEvent.ItemPickupEvent event) {
        try {
            if (!RingConfig.hellBladeGuaranteed()) {
                return;
            }
            Player player = event.getEntity();
            ItemStack stack = event.getStack();
            if (player == null || stack == null || stack.isEmpty() || !isHellBlade(stack)) {
                return;
            }
            if (!ownsBlade(player)) {
                markLooted(player);
                return;
            }
            if (event.getOriginalEntity() != null && !event.getOriginalEntity().level().isClientSide) {
                event.getOriginalEntity().discard();
            }
            DebugLog.once("hellblade:dup:" + player.getUUID(),
                "[HellBlade] {} 手上已经有一枚刃片,本次拾取的多余一枚已拦截",
                player.getName().getString());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯判定逻辑,失败就放行
        }
    }

    // ---------------------------------------------------------------- 工具

    private static Item bladeItem() {
        try {
            return ForgeRegistries.ITEMS.getValue(new ResourceLocation(HELL_BLADE_ID));
        }
        catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static boolean isHellBlade(ItemStack stack) {
        try {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return id != null && HELL_BLADE_ID.equals(id.toString());
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 玩家身上(背包 + 双手 + 饰品栏)是否已经有一枚刃片。 */
    private static boolean ownsBlade(Player player) {
        try {
            Item blade = bladeItem();
            return blade != null && player != null && Superposition.hasItem(player, blade);
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<LootPool> poolsOf(net.minecraft.world.level.storage.loot.LootTable table) {
        for (String name : new String[]{"pools", "f_79109_"}) {
            try {
                Field f = net.minecraft.world.level.storage.loot.LootTable.class.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(table);
                if (v instanceof List<?> list) {
                    return (List<LootPool>) list;
                }
            }
            catch (ReflectiveOperationException | RuntimeException ignored) {
                // 试下一个名字
            }
        }
        return null;
    }

    private static boolean unfreeze(net.minecraft.world.level.storage.loot.LootTable table) {
        try {
            Field f = net.minecraft.world.level.storage.loot.LootTable.class.getDeclaredField("isFrozen");
            f.setAccessible(true);
            f.setBoolean(table, false);
            return true;
        }
        catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    public static List<String> tables() {
        return new ArrayList<>(TABLES);
    }
}
