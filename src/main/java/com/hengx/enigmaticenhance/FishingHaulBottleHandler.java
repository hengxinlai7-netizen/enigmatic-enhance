package com.hengx.enigmaticenhance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.7.2:「渔获瓶」。
 *
 * <p><b>4.8.2(用户实测「多倍渔获没生效」)后,效果拆成两条互不干扰的线:</b></p>
 * <ul>
 *   <li><b>数量(多倍渔获)</b>:{@link RingConfig#FISHING_HAUL_CATCH_MULTIPLIER 携带倍数}
 *       ×{@link RingConfig#FISHING_HAUL_BAG_MULTIPLIER 书袋额外倍数}。
 *       携带(背包/主手/副手)默认 ×2,<b>放进古旧书袋再 ×1.5(共 ×3)</b>。
 *       产出走两个挂钩点(见下),都只复制 {@code minecraft:fishes} 里的鱼。</li>
 *   <li><b>幸运(宝藏概率)</b>:4.8.1 起是真的 {@code generic.luck} 修饰符,书袋里 +2
 *       ({@link #BAG_LUCK})。它只影响战利品质量,和上面的数量倍数是两件事,不会重复计算。</li>
 * </ul>
 *
 * <p><b>数量为什么要两个挂钩点:</b>不同鱼竿走的路不一样。</p>
 * <ol>
 *   <li>{@link #onFished} —— Forge 的 {@code ItemFishedEvent}。原版鱼竿和
 *       Aquaculture(2.5.2 反编译确认 {@code AquaFishingBobberEntity.retrieve} 里有
 *       {@code new ItemFishedEvent(list,…)} + {@code EVENT_BUS.post} + {@code spawnLoot(list)})
 *       都会走它。这里读本竿产出、自己把多出来的鱼交给玩家,所以水产养殖的鱼不会变成原版鳕鱼。</li>
 *   <li>{@link #onItemSpawn} —— 兜底:任何鱼竿只要把鱼作为物品实体吐出来,
 *       就按"附近有没有该玩家的钓鱼浮标"判定这是钓上来的,再复制。不 post 事件的鱼竿也能生效。</li>
 * </ol>
 *
 * <p><b>4.8.2 的关键发现(以前为什么完全没效果):</b>Forge 的 {@code ItemFishedEvent}
 * 构造器会把调用方的 list <b>复制</b>进自己的 {@code NonNullList}({@code stacks.addAll(drops)}),
 * 而原版 {@code FishingHook.retrieve} 与 Aquaculture 的 {@code spawnLoot} 在 post 之后
 * <b>仍然用它们自己那份 list</b> 生成物品。也就是说 <b>往 {@code event.getDrops()} 里 add
 * 是彻底无效的</b> —— 旧实现(1.7.2~4.8.1)正是这么写的,所以"1024 幸运还是一条鱼"。
 * 现在事件通道改成"自己发鱼",不再指望任何鱼竿消费我们改过的列表。</p>
 *
 * <p><b>4.8.3(用户要求):多出来的渔获不再直接塞进背包</b>,而是像正常钓上来一样
 * 在<b>玩家脚下</b>生成 {@link ItemEntity}(带一点随机速度、10 tick 拾取延迟),
 * 玩家看得见、捡得到,也仍然遵守"背包满就留在地上"的常识。见 {@link #dropCatch}。</p>
 *
 * <p><b>两条线不会重复乘:</b>事件通道命中后会记一次 {@link #HANDLED_TICK}(同一 tick),
 * 生成通道看到这个记录就直接跳过 —— 所以一竿只会被乘一次,不会 4 倍。</p>
 */
public final class FishingHaulBottleHandler {

    /**
     * 书袋加成用的固定 UUID(4.8.1)。
     *
     * <p>必须是常量:修饰符靠 UUID 去重/撤回,随机 UUID 会每次叠加一条。</p>
     */
    static final UUID LUCK_MODIFIER_ID = UUID.fromString("7b0e1f2c-3a4d-4e5f-8a9b-0c1d2e3f4a5b");

    /** 修饰符名字:属性面板/调试里能看出是谁加的。 */
    private static final String LUCK_MODIFIER_NAME = "enigmaticenhance:fishing_haul_bottle";

    /** 书袋里那瓶给的幸运点数。 */
    public static final double BAG_LUCK = 2.0D;

    /** 每 10 tick(0.5 秒)维护一次属性:够快,又不必每 tick 扫背包/书袋。 */
    private static final int TICK_PERIOD = 10;

    /** 找"这根鱼竿的浮标"的半径:鱼是从浮标位置吐出来的,4 格足够(原版/水产养殖都是 0 格)。 */
    private static final double BOBBER_RADIUS = 4.0D;

    /** 事件通道命中后,生成通道在多少个 tick 内保持沉默(两条通路不重复乘)。 */
    private static final int HANDLED_WINDOW_TICKS = 1;

    /** 玩家 → 上一次由事件通道处理的 tick(只在钓鱼时写,写完即弃)。 */
    private static final Map<UUID, Long> HANDLED_TICK = new HashMap<>();

    /** 诊断日志:每名玩家只打一次(避免每竿刷日志),但**不受 debugLog 开关控制**(排查用)。 */
    private static final Set<UUID> DIAG_EVENT = new HashSet<>();
    private static final Set<UUID> DIAG_SPAWN = new HashSet<>();

    /** 我们自己在生成复制品时的重入闸:否则复制品会再触发一次生成事件,无限翻倍。 */
    private static boolean spawningCopy;

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

    /** 背包 / 主手 / 副手 / <b>古旧书袋</b> 都算"携带"(书袋里的瓶子同样触发多倍)。 */
    private static boolean carried(Player p, boolean inBag) {
        for (ItemStack s : p.getInventory().items) {
            if (isBottle(s)) {
                return true;
            }
        }
        return isBottle(p.getOffhandItem()) || isBottle(p.getMainHandItem()) || inBag;
    }

    // ------------------------------------------------------------------ 数量:倍数

    /**
     * 4.8.2:这一竿的渔获倍数(1.0 = 不增加)。
     *
     * <p>携带(背包/主手/副手)×{@link RingConfig#fishingHaulCatchMultiplier()};
     * 在书袋里再×{@link RingConfig#fishingHaulBagMultiplier()}(叠乘,默认 ×2 ×1.5 = ×3)。</p>
     *
     * <p>包内可见:自检 T57 直接调它验算。</p>
     */
    static double catchMultiplier(Player p, boolean inBag, boolean carried) {
        double mult = 1.0D;
        if (carried) {
            mult *= clamp(RingConfig.fishingHaulCatchMultiplier());
        }
        if (inBag) {
            mult *= clamp(RingConfig.fishingHaulBagMultiplier());
        }
        return mult;
    }

    /** 配置写坏(负数/0)时也不至于把渔获清空。 */
    private static double clamp(double v) {
        if (Double.isNaN(v) || v < 1.0D) {
            return 1.0D;
        }
        return Math.min(v, 64.0D);
    }

    /**
     * 4.8.2:倍数 → 额外份数。
     *
     * <p>倍数是**总量**倍数:{@code mult = 3.0} ⇒ 总共 3 份 ⇒ <b>额外 2 份</b>;
     * 小数部分按概率补一份(2.5 ⇒ 1 份 + 50% 再来 1 份)。{@code mult = 1.0} ⇒ 0 份
     * (所以"1.0 = 不增加"是成立的)。</p>
     *
     * <p>包内可见:自检 T57 直接调它验算。</p>
     */
    static int extraCatches(RandomSource random, double mult) {
        double extra = mult - 1.0D;
        if (extra <= 0.0D) {
            return 0;
        }
        int whole = (int) Math.floor(extra);
        double frac = extra - whole;
        if (frac > 0.0D && random.nextDouble() < frac) {
            whole++;
        }
        return whole;
    }

    // ------------------------------------------------------------------ 幸运:+2(4.8.1)

    /** 4.8.1:把"书袋里有渔获瓶 ⇒ {@code generic.luck} +2"落到<b>真实属性</b>上(幂等)。 */
    static boolean maintainBagLuck(Player p) {
        if (p == null) {
            return false;
        }
        return maintainBagLuck(p, inBag(p));
    }

    /** 已经算过 {@code inBag} 的重载(钓鱼时一次判定两用,少扫一遍背包)。 */
    private static boolean maintainBagLuck(Player p, boolean inBag) {
        AttributeInstance luck = p.getAttribute(Attributes.LUCK);
        if (luck == null) {
            return false;
        }
        boolean want = inBag && RingConfig.fishingHaulEnabled();
        boolean has = luck.getModifier(LUCK_MODIFIER_ID) != null;
        if (want == has) {
            return false;
        }
        if (want) {
            luck.addTransientModifier(new AttributeModifier(LUCK_MODIFIER_ID, LUCK_MODIFIER_NAME,
                BAG_LUCK, AttributeModifier.Operation.ADDITION));
        }
        else {
            luck.removeModifier(LUCK_MODIFIER_ID);
        }
        return true;
    }

    /** 4.8.1:书袋内容的增减由这里跟上(瓶子进出书袋、书袋换手、死亡重生、配置关掉)。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player == null) {
            return;
        }
        Player p = event.player;
        if (p.level().isClientSide) {
            return;
        }
        if (p.tickCount % TICK_PERIOD != 0) {
            return;
        }
        try {
            maintainBagLuck(p);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    // ------------------------------------------------------------------ 通道 1:ItemFishedEvent

    /**
     * 4.8.2:主通道。按**本竿真实产出**复制鱼(不会给水产养殖塞原版鱼)。
     *
     * <p><b>关键(4.8.2 才查清):往 {@code event.getDrops()} 里 add 是无效的。</b>
     * 反编译 Forge 的 {@code ItemFishedEvent} 构造器:
     * <pre>
     * this.stacks = NonNullList.create();
     * this.stacks.addAll(drops);      // ← 把调用方的 list **复制** 一份
     * ...
     * public NonNullList&lt;ItemStack&gt; getDrops() { return this.stacks; }
     * </pre>
     * 而原版 {@code FishingHook.retrieve} 与本整合包的 Aquaculture 2.5.2
     * ({@code AquaFishingBobberEntity.spawnLoot})在 post 事件之后,<b>仍然用它们自己那份 list</b>
     * 去生成物品实体,不读 {@code event.getDrops()}。所以以前"往事件里塞 102 条鱼"一条都出不来 ——
     * 这正是用户"给了 1024 幸运还是一条鱼"的真实原因(比"事件没收到"更根本)。</p>
     *
     * <p>因此这里改成:读事件里的产出 → 自己把多出来的鱼<b>掉在玩家脚下</b>
     * (4.8.3 起不再直接进背包,见 {@link #dropCatch})。不依赖任何鱼竿是否消费我们改过的列表。</p>
     */
    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (!RingConfig.fishingHaulEnabled()) {
            return;
        }
        try {
            Player p = event.getEntity();
            if (p == null || p.level().isClientSide) {
                return;
            }
            boolean inBag = inBag(p);
            boolean carried = carried(p, inBag);
            int luck = (int) Math.floor((double) p.getLuck());
            // 注意:这是 Forge 复制出来的一份,只读用;往里 add 不会被任何鱼竿采纳
            List<ItemStack> caught = event.getDrops();
            double mult = catchMultiplier(p, inBag, carried);
            int extra = 0;
            int given = 0;
            if (carried && caught != null && !caught.isEmpty()) {
                extra = extraCatches(p.level().getRandom(), mult);
                for (int i = 0; i < extra; i++) {
                    for (ItemStack s : caught) {
                        if (!s.isEmpty() && s.is(ItemTags.FISHES)) {
                            if (dropCatch(p, s)) {
                                given++;
                            }
                        }
                    }
                }
            }
            // 这一竿已由事件通道处理 ⇒ 生成通道同一 tick 内保持沉默(不重复乘)
            markHandled(p);
            diagEvent(p, luck, carried, inBag, mult, extra, given, caught == null ? 0 : caught.size());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    // ------------------------------------------------------------------ 通道 2:鱼生成(兜底)

    /**
     * 4.8.2:兜底通道。任何鱼竿只要把鱼作为物品实体吐出来,这里就能接住。
     *
     * <p>判定"是不是钓上来的"靠<b>附近有没有该玩家的钓鱼浮标</b>:
     * 鱼是在浮标位置生成的,所以 4 格内有浮标 ⇒ 钓鱼产物;
     * 手动丢鱼、打怪掉鱼、箱子里翻出来的鱼旁边不会有浮标 ⇒ 不处理。</p>
     */
    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (!RingConfig.fishingHaulEnabled() || spawningCopy) {
            return;
        }
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemEntity item)) {
            return;
        }
        // 先按物品过滤:绝大多数物品不是鱼,连 AABB 查询都不用做
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || !stack.is(ItemTags.FISHES)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            Player owner = fishingOwnerNear(level, item);
            if (owner == null || !spawnChannelActive(owner)) {
                return;
            }
            boolean inBag = inBag(owner);
            boolean carried = carried(owner, inBag);
            if (!carried) {
                return;
            }
            double mult = catchMultiplier(owner, inBag, carried);
            int extra = extraCatches(level.getRandom(), mult);
            if (extra <= 0) {
                return;
            }
            diagSpawn(owner, stack, carried, inBag, mult, extra);
            // 不在"实体加入世界"的回调里再塞实体:推到本 tick 末尾执行
            // 4.8.3:坐标用**玩家**的 —— 鱼实体虽然出现在浮标位置,但浮标随时可能被收回/消失,
            //        玩家位置一定是有效且"该把渔获给他"的位置。
            final double x = owner.getX();
            final double y = owner.getY();
            final double z = owner.getZ();
            final ItemStack template = stack.copy();
            final int count = extra;
            level.getServer().execute(() -> spawnCopies(level, x, y, z, template, count));
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /**
     * 在指定位置生成 {@code count} 份渔获(直接调用;事件里会先推到 tick 末尾再调)。
     *
     * <p>生成期间会竖起重入闸,复制品本身不会再触发一次复制。
     * 包内可见:自检 T57 直接调它验算"真的多出来 N 份、且不会递归翻倍"。</p>
     *
     * @return 实际生成份数
     */
    static int spawnCopies(ServerLevel level, double x, double y, double z, ItemStack template, int count) {
        if (level == null || template == null || template.isEmpty() || count <= 0) {
            return 0;
        }
        boolean previous = spawningCopy;
        spawningCopy = true;
        int made = 0;
        try {
            RandomSource random = level.getRandom();
            for (int i = 0; i < count; i++) {
                if (dropCatch(level, x, y, z, template, random)) {
                    made++;
                }
            }
        }
        finally {
            spawningCopy = previous;
        }
        return made;
    }

    /**
     * 4.8.3:把多出来的一份渔获<b>像正常钓上来一样</b>掉在玩家脚下。
     *
     * <p>为什么不再 {@code inventory.add()}:用户要的是"看得见的正常掉落物"。
     * 位置用玩家坐标(<b>不是浮标坐标</b> —— 浮标这时可能已经消失/被收回);
     * 给一点随机横向速度让几条鱼散开;{@code setPickUpDelay(10)} 让它先弹一下,
     * 不会生成当帧就被吸进背包。</p>
     *
     * <p>背包满/不满都走这条路,所以"背包满了就掉地上"这条行为天然保留。</p>
     *
     * @return 是否真的加进了世界
     */
    static boolean dropCatch(Player p, ItemStack stack) {
        if (p == null || stack == null || stack.isEmpty() || p.level().isClientSide) {
            return false;
        }
        return dropCatch(p.level(), p.getX(), p.getY(), p.getZ(), stack, p.level().getRandom());
    }

    /** 坐标化版本(生成通道用玩家坐标,不依赖任何实体是否还在)。 */
    private static boolean dropCatch(Level level, double x, double y, double z, ItemStack stack,
                                     RandomSource random) {
        if (level == null || stack == null || stack.isEmpty()) {
            return false;
        }
        // 重入闸:addFreshEntity 会同步派发 EntityJoinLevelEvent(生成通道的入口),
        // 不压住的话我们刚掉的这一条会被当成"新钓上来的鱼"再乘一次 ⇒ 指数级刷鱼。
        boolean previous = spawningCopy;
        spawningCopy = true;
        try {
            ItemEntity drop = new ItemEntity(level, x, y + 0.5D, z, stack.copy());
            drop.setDeltaMovement(
                (random.nextDouble() - 0.5D) * 0.1D,
                0.2D,
                (random.nextDouble() - 0.5D) * 0.1D);
            drop.setPickUpDelay(10);   // 短暂延迟,避免和玩家碰撞后立刻被吸走
            if (level.addFreshEntity(drop)) {
                DROPPED_CATCH++;
                return true;
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 生成失败不影响本竿原本的渔获
        }
        finally {
            spawningCopy = previous;
        }
        return false;
    }

    /**
     * 找"这根鱼竿的玩家":取 {@code around} 附近 4 格内的钓鱼浮标,返回它的主人。
     *
     * <p>原版 {@link FishingHook} 与 Aquaculture 的 {@code AquaFishingBobberEntity}
     * (2.5.2 反编译确认 {@code extends FishingHook})都走 {@code instanceof} 这一支;
     * 其它模组的浮标用类名({@code bobber/fishinghook})+ 反射 {@code getPlayerOwner()} 兜底,
     * 不引入任何硬依赖。</p>
     */
    static Player fishingOwnerNear(Level level, Entity around) {
        if (level == null || around == null) {
            return null;
        }
        try {
            List<Entity> near = level.getEntitiesOfClass(Entity.class,
                around.getBoundingBox().inflate(BOBBER_RADIUS), FishingHaulBottleHandler::isFishingBobber);
            for (Entity e : near) {
                Player owner = bobberOwner(e);
                if (owner != null) {
                    return owner;
                }
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
        return null;
    }

    /** 是不是钓鱼浮标(原版/水产养殖靠继承,其它模组靠类名)。 */
    private static boolean isFishingBobber(Entity e) {
        if (e == null) {
            return false;
        }
        if (e instanceof FishingHook) {
            return true;
        }
        String name = e.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("bobber") || name.contains("fishinghook")
            || (name.contains("fish") && name.contains("hook"));
    }

    /** 浮标的主人(原版/水产养殖直接拿;其它模组反射 getPlayerOwner/getOwner)。 */
    private static Player bobberOwner(Entity e) {
        if (e instanceof FishingHook hook) {
            return hook.getPlayerOwner();
        }
        try {
            Object owner = e.getClass().getMethod("getPlayerOwner").invoke(e);
            if (owner instanceof Player p) {
                return p;
            }
        }
        catch (Throwable ignored) {
            // 换下一种
        }
        try {
            Entity owner = e.getControllingPassenger();
            if (owner instanceof Player p) {
                return p;
            }
        }
        catch (Throwable ignored) {
            // 没有就算了
        }
        return null;
    }

    // ------------------------------------------------------------------ 防重复 / 诊断

    /** 自检用计数:4.8.3 起"额外渔获"全部以掉落物形式出现的份数(以前是"背包满才掉"的份数)。 */
    static int DROPPED_CATCH;

    private static void markHandled(Player p) {
        if (HANDLED_TICK.size() > 64) {
            HANDLED_TICK.clear();
        }
        HANDLED_TICK.put(p.getUUID(), p.level().getGameTime());
    }

    /** 生成通道是否应该处理这名玩家:事件通道刚处理过就不处理(一竿只乘一次)。 */
    static boolean spawnChannelActive(Player p) {
        return !handledRecently(p);
    }

    /** 自检用:清掉"这一竿已处理"的标记,免得影响后面的用例。 */
    static void resetHandled(Player p) {
        HANDLED_TICK.remove(p.getUUID());
    }

    private static boolean handledRecently(Player p) {
        Long stamp = HANDLED_TICK.get(p.getUUID());
        if (stamp == null) {
            return false;
        }
        return Math.abs(p.level().getGameTime() - stamp) <= HANDLED_WINDOW_TICKS;
    }

    /** 每名玩家一次:证明 {@code ItemFishedEvent} 到底有没有收到(用户排查用,不受开关控制)。 */
    private static void diagEvent(Player p, int luck, boolean carried, boolean inBag,
                                  double mult, int extra, int given, int totalCaught) {
        if (!DIAG_EVENT.add(p.getUUID())) {
            DebugLog.info("[渔获瓶] onFished 触发: 幸运={} 携带={} 书袋={} 倍数={} 额外={} 实发={}",
                luck, carried, inBag, mult, extra, given);
            return;
        }
        EnigmaticEnhance.LOGGER.warn(
            "[渔获瓶] onFished 触发: 鱼竿={} 幸运={} 携带={} 书袋={} 倍数={} 额外={} 本竿产物={} 实发={}",
            ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem()), luck, carried, inBag, mult, extra,
            totalCaught, given);
    }

    /** 每名玩家一次:证明"鱼生成"兜底通道真的接住了哪条鱼、附近是哪个浮标。 */
    private static void diagSpawn(Player p, ItemStack stack, boolean carried, boolean inBag,
                                  double mult, int extra) {
        if (!DIAG_SPAWN.add(p.getUUID())) {
            DebugLog.info("[渔获瓶] 鱼生成: 物品={} 倍数={} 额外={}",
                ForgeRegistries.ITEMS.getKey(stack.getItem()), mult, extra);
            return;
        }
        EnigmaticEnhance.LOGGER.warn("[渔获瓶] 鱼生成: 物品={} 玩家={} 携带={} 书袋={} 倍数={} 额外={}",
            ForgeRegistries.ITEMS.getKey(stack.getItem()), p.getName().getString(), carried, inBag, mult, extra);
    }
}
