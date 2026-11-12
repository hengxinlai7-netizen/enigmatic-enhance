package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.2.6a:「瓶装灵界之光」的全部逻辑。
 *
 * <h2>1. 转化</h2>
 * 大地之瓶放在<b>副手</b>(仅副手,主手不算)时,按玩家实际走过的水平距离累计,
 * 满 {@value #TRANSFORM_DISTANCE} 格就在副手原地变成瓶装灵界之光。
 * 累计值存在瓶子 NBT({@value #MOVED_KEY});单 tick 位移超过 {@value #MAX_STEP} 格
 * 视为传送/换维度,只挪锚点不计距离。
 *
 * <h2>2. 额外掉落</h2>
 * 击杀者是玩家、且该玩家身上有灵界之光(背包/副手/古旧书袋)时,每一份掉落物都再补一份:
 * 数量取 {@code max(1, 原本数量 - 抢夺等级)} —— 抢夺放大的是"原本那一份",
 * 额外这一份<b>不吃抢夺</b>,也不重新过掉落概率(已经掉出来的东西必定再来一份)。
 *
 * <h2>3. 隐藏效果:必定触发「战利品大爆发」</h2>
 * 神化的 festive 词缀({@code apotheosis:sword/special/festive},中文"战利品大爆发")
 * 在 {@code FestiveAffix#drops} 里自己掷概率(2%~8%)。这里在它掷失败时<b>重复调用同一个方法</b>,
 * 直到掉落物集合真的发生变化(说明触发了),然后给实体打上它自己认的
 * {@code apoth.no_pinata} 标记防止重复触发。
 * 这样用的是神化自己的发奖代码,不需要另外模拟一套。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpiritLightHandler {

    /** 副手大地之瓶需要累计移动的水平距离(格)。 */
    public static final double TRANSFORM_DISTANCE = 2000.0D;

    /** 单个检查周期内允许计入的最大位移;更大的位移当作传送,不计距离。 */
    public static final double MAX_STEP = 32.0D;

    /** 累计距离的 NBT 键。 */
    public static final String MOVED_KEY = "SpiritMoved";

    /** 上一次检查位置的 NBT 键(存在瓶子上,所以把一个瓶子丢地上再捡起来也不重置)。 */
    public static final String ANCHOR_X = "SpiritAnchorX";
    public static final String ANCHOR_Z = "SpiritAnchorZ";
    public static final String ANCHOR_SET = "SpiritAnchorSet";

    /** 神化 festive 词缀的 id。 */
    public static final ResourceLocation FESTIVE_AFFIX = new ResourceLocation("apotheosis", "sword/special/festive");

    /** 神化用来"别再触发一次"的标记键。 */
    public static final String NO_PINATA = "apoth.no_pinata";

    /** 强制触发 festive 的最大尝试次数(2% 概率下 400 次失败的概率约 3e-4)。 */
    public static final int FESTIVE_MAX_TRIES = 400;

    /** 1.2.6f:灵界之光携带时经验获取倍率(+50%)。 */
    public static final float XP_MULTIPLIER = 1.5F;

    public static final ResourceLocation SPIRIT_LIGHT_ID =
        new ResourceLocation("enigmaticenhance", "spirit_light");

    private SpiritLightHandler() {
    }

    // ------------------------------------------------------------------ 事件

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.spiritLightEnabled()) {
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
            tickOffhandBottle(player);
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[SpiritLight] tick 异常", ex);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.spiritLightEnabled()) {
            return;
        }
        try {
            if (!(event.getSource().getEntity() instanceof Player player)) {
                return;
            }
            if (!hasSpiritLight(player)) {
                return;
            }
            addExtraDrops(event);
            forceFestive(player, event);
            // 1.2.12e:festive 只会复制"已经掉出来的东西",变不出没掉出来的稀有掉落(兔子脚)。
            // 所以这里再补掷战利品表,把"没掉出来的物品类型"补上。
            guaranteeRareDrops(player, event);
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[SpiritLight] 掉落结算异常", ex);
        }
    }

    // ------------------------------------------------------------------ 经验 +50%(1.2.6f)

    @SubscribeEvent
    public static void onXpChange(net.minecraftforge.event.entity.player.PlayerXpEvent.XpChange event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.spiritLightEnabled()) {
            return;
        }
        try {
            if (event.getEntity() == null || event.getAmount() <= 0) {
                return;
            }
            if (hasSpiritLight(event.getEntity())) {
                event.setAmount(xpGain(event.getAmount()));
            }
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[SpiritLight] 经验结算异常", ex);
        }
    }

    /** 经验获取量(纯函数:命中灵界之光就 ×1.5,至少 +1)。 */
    public static int xpGain(int amount) {
        if (amount <= 0) {
            return amount;
        }
        return Math.max(amount + 1, Math.round(amount * XP_MULTIPLIER));
    }


    // ------------------------------------------------------------------ 转化

    /** 副手大地之瓶按移动距离计时;满 2000 格转化。 */
    public static void tickOffhandBottle(Player player) {
        Item bottle = ModItems.earthBottleOrNull();
        Item spirit = ModItems.spiritLightOrNull();
        if (bottle == null || spirit == null || player == null) {
            return;
        }
        ItemStack stack = player.getOffhandItem();
        if (stack.isEmpty() || stack.getItem() != bottle) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        double x = player.getX();
        double z = player.getZ();
        if (!tag.getBoolean(ANCHOR_SET)) {
            tag.putBoolean(ANCHOR_SET, true);
            tag.putDouble(ANCHOR_X, x);
            tag.putDouble(ANCHOR_Z, z);
            return;
        }
        double dx = x - tag.getDouble(ANCHOR_X);
        double dz = z - tag.getDouble(ANCHOR_Z);
        double step = Math.sqrt(dx * dx + dz * dz);
        tag.putDouble(ANCHOR_X, x);
        tag.putDouble(ANCHOR_Z, z);
        if (step <= 0.0D || step > MAX_STEP) {
            return; // 没动 / 传送,不计
        }
        double moved = Math.min(tag.getDouble(MOVED_KEY) + step, TRANSFORM_DISTANCE);
        if (moved >= TRANSFORM_DISTANCE) {
            // 1.2.12d:只消耗副手 1 个大地之瓶
            BottleConversionUtil.convertOne(player, net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(spirit));
            DebugLog.info("[SpiritLight] {} 副手的大地之瓶移动 {} 格 -> 瓶装灵界之光",
                player.getName().getString(), (int) TRANSFORM_DISTANCE);
        }
        else {
            tag.putDouble(MOVED_KEY, moved);
        }
    }

    /** 已累计的距离(给自检/调试看)。 */
    public static double movedDistance(ItemStack bottle) {
        if (bottle == null || bottle.isEmpty() || !bottle.hasTag()) {
            return 0.0D;
        }
        return bottle.getTag().getDouble(MOVED_KEY);
    }

    // ------------------------------------------------------------------ 是否持有

    /** 玩家身上(背包/副手/古旧书袋)是否有瓶装灵界之光。 */
    public static boolean hasSpiritLight(Player player) {
        Item spirit = ModItems.spiritLightOrNull();
        if (spirit == null || player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot >= 36 && slot != 40) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == spirit) {
                return true;
            }
        }
        ItemStack inBag = BookBag.findBook(player, SPIRIT_LIGHT_ID);
        return !inBag.isEmpty() && inBag.getItem() == spirit;
    }

    // ------------------------------------------------------------------ 额外掉落

    /**
     * 额外掉落的数量:{@code max(1, 原本数量 - 抢夺等级)}。
     * 抢夺放大的是原本那一份,这里减掉它,等于"额外这份不吃抢夺"。
     */
    /** 每次击杀最多补几种新物品(防止把整张表刷爆)。 */
    private static final int RARE_MAX_TYPES = 6;

    /**
     * 1.2.12e:低概率掉落保底。
     * <p>神化的「战利品大爆发」只是把<b>已经掉出来的</b>战利品再复制几份,
     * 所以兔子脚没掷出来时,触发再多次也掉不出来。这里额外补掷受害者自己的战利品表,
     * 只把"这次还没出现过的物品类型"补进掉落(每种最多 1 份,总数封顶 {@value #RARE_MAX_TYPES})。</p>
     * <p>用玩家的抢夺等级作为 luck,与原版掉落一致;失败一律静默(不影响正常掉落)。</p>
     */
    public static void guaranteeRareDrops(Player player, LivingDropsEvent event) {
        // 1.2.28:**不碰掉落表**,只把这次真实掉出来的物品再复制一份。
        // 这样"硬编码掉落"的 Boss(凋零的下界之星、末影龙、模组 Boss)也能吃到,
        // 而掉落表里没有的东西也不会被凭空塞进来。
        try {
            net.minecraft.world.entity.LivingEntity victim = event.getEntity();
            if (victim == null || victim.level().isClientSide) {
                return;
            }
            java.util.List<ItemEntity> extras = new java.util.ArrayList<>();
            for (ItemEntity drop : event.getDrops()) {
                if (drop == null || drop.getItem().isEmpty()) {
                    continue;
                }
                ItemStack original = drop.getItem();
                if (original.is(ModItems.SPIRIT_LIGHT.get())) {
                    continue;   // 不复制灵界之光本身
                }
                ItemStack copy = original.copy();
                ItemEntity entity = new ItemEntity(victim.level(),
                    drop.getX(), drop.getY(), drop.getZ(), copy);
                entity.setDeltaMovement(drop.getDeltaMovement());
                extras.add(entity);
            }
            if (extras.isEmpty()) {
                return;
            }
            event.getDrops().addAll(extras);
            DebugLog.once("spiritCopy:" + victim.getType(),
                "[SpiritLight] 额外复制掉落:{} 共 {} 份", victim.getType(), extras.size());
        }
        catch (RuntimeException | LinkageError ex) {
            // 复制失败不影响正常掉落
        }
    }

    public static int extraCount(int originalCount, int lootingLevel) {
        int loot = Math.max(0, lootingLevel);
        return Math.max(1, originalCount - loot);
    }

    /** 给每一份掉落物再补一份(单独生成 ItemEntity,避免改动原本那堆)。 */
    public static void addExtraDrops(LivingDropsEvent event) {
        List<ItemEntity> extras = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            if (drop == null || drop.getItem().isEmpty()) {
                continue;
            }
            ItemStack original = drop.getItem();
            ItemStack extra = original.copy();
            extra.setCount(extraCount(original.getCount(), event.getLootingLevel()));
            ItemEntity entity = new ItemEntity(drop.level(), drop.getX(), drop.getY(), drop.getZ(), extra);
            entity.setDefaultPickUpDelay();
            extras.add(entity);
        }
        event.getDrops().addAll(extras);
    }

    // ------------------------------------------------------------------ 隐藏效果

    /** 神化是否存在的缓存标志(纯反射探测;缺失时下面的联动全部静默跳过)。 */
    private static volatile Boolean apotheosisLoaded;

    /** 神化(联动模组)是否存在。绝不抛出、绝不写日志。 */
    public static boolean apotheosisLoaded() {
        Boolean cached = apotheosisLoaded;
        if (cached == null) {
            try {
                Class.forName("dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper", false,
                    SpiritLightHandler.class.getClassLoader());
                cached = Boolean.TRUE;
            }
            catch (Throwable t) {
                cached = Boolean.FALSE;
            }
            apotheosisLoaded = cached;
        }
        return cached.booleanValue();
    }

    /**
     * 玩家主手武器上的 festive 词缀实例(没有/神化缺失 → null)。
     * <p>返回类型故意是 {@link Object}:一旦签名里出现神化类型,Forge 注册本类时
     * {@code Class#getMethods()} 会去解析签名 → 没装神化就 {@code NoClassDefFoundError}。
     * 这里全部用精确查找的反射,且吞掉 {@code Throwable}。</p>
     */
    @Nullable
    static Object festiveInstance(Player player) {
        if (!apotheosisLoaded()) {
            return null;
        }
        try {
            ItemStack weapon = player.getMainHandItem();
            if (weapon.isEmpty()) {
                return null;
            }
            ClassLoader cl = SpiritLightHandler.class.getClassLoader();
            Class<?> helper = Class.forName("dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper", false, cl);
            Object map = helper.getMethod("getAffixes", ItemStack.class).invoke(null, weapon);
            if (!(map instanceof java.util.Map<?, ?> affixes)) {
                return null;
            }
            Class<?> holders = Class.forName("dev.shadowsoffire.apotheosis.Apoth$Affixes", false, cl);
            Object festiveHolder = holders.getField("FESTIVE").get(null);
            Object instance = affixes.get(festiveHolder);
            if (instance == null) {
                return null;
            }
            Object valid = instance.getClass().getMethod("isValid").invoke(instance);
            return Boolean.TRUE.equals(valid) ? instance : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    /**
     * 强制触发 festive:重复调用神化自己的 {@code FestiveAffix#drops},
     * 一旦掉落集合发生变化就停,并打上 {@code apoth.no_pinata} 防止再次触发。
     * 返回是否触发成功。神化缺失/版本不符时静默返回 false。
     */
    public static boolean forceFestive(Player player, LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.getPersistentData().getBoolean(NO_PINATA)) {
            return false;
        }
        Object instance = festiveInstance(player);
        if (instance == null) {
            return false;
        }
        try {
            Object holder = instance.getClass().getMethod("affix").invoke(instance);
            Object affix = holder.getClass().getMethod("get").invoke(holder);
            if (affix == null) {
                return false;
            }
            // 精确查找 drops(LivingDropsEvent) —— 不做 getMethods() 批量扫描
            java.lang.reflect.Method drops = affix.getClass().getMethod("drops", LivingDropsEvent.class);
            String before = dropsSignature(event);
            for (int i = 0; i < FESTIVE_MAX_TRIES; i++) {
                drops.invoke(affix, event);
                if (!before.equals(dropsSignature(event))) {
                    victim.getPersistentData().putBoolean(NO_PINATA, true);
                    DebugLog.debug("[SpiritLight] 强制触发战利品大爆发(尝试 {} 次)", i + 1);
                    return true;
                }
            }
        }
        catch (Throwable t) {
            DebugLog.debug("[SpiritLight] 强制触发 festive 失败(神化联动已跳过): {}", t.toString());
        }
        return false;
    }

    /** 掉落集合指纹:条数 + 每一条的物品 id 与数量(顺序无关)。 */
    static String dropsSignature(LivingDropsEvent event) {
        long hash = 17L;
        for (ItemEntity drop : event.getDrops()) {
            if (drop == null || drop.getItem().isEmpty()) {
                continue;
            }
            ItemStack stack = drop.getItem();
            hash = hash * 31L + stack.getItem().hashCode();
            hash = hash * 31L + stack.getCount();
        }
        return event.getDrops().size() + ":" + hash;
    }

}
