package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.6.20:经验溢出保护(两层)。
 *
 * <ul>
 *   <li><b>玩家经验</b>:总经验/等级夹在 {@code Integer.MAX_VALUE - 1} 以内,负数归零;</li>
 *   <li><b>无知诅咒卷轴</b>({@code enigmaticaddons:cursed_xp_scroll},NBT {@code XPStored}):
 *       夹在 {@code Integer.MAX_VALUE - 1};已经溢出成负数的,直接拉回上限。</li>
 * </ul>
 */
public final class ExperienceOverflowGuard {

    private static final int CAP = Integer.MAX_VALUE - 1;
    /** 1.6.27:卷轴自身还会计数,所以留出安全余量,避免夹住后又被加爆。 */
    private static final int SCROLL_CAP = 1_000_000_000;
    private static final String SCROLL_ID = "enigmaticaddons:cursed_xp_scroll";
    private static final String NBT_KEY = "XPStored";

    private ExperienceOverflowGuard() {
    }

    /**
     * 4.6.0(内存/CPU):诅咒卷轴的 Item 只解析一次。
     *
     * <p>原来 {@link #clampScroll} 每个槽位都做
     * {@code BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()} 再和字符串比 —— 玩家背包 36 格
     * + 副手 + 每个饰品槽,<b>每秒每玩家都会新建 40 个左右的 String</b>(≈ 2 KB/s/玩家),而且只是为了和
     * 一个常量比较。改成拿注册表里的 {@link Item} 实例做 == 比较:零分配,语义相同
     * (物品不在 ⇒ 谁都匹配不上 ⇒ 和原来一样直接返回)。</p>
     */
    private static Item scrollItem;
    private static boolean scrollResolved;

    private static Item scroll() {
        if (scrollResolved) {
            return scrollItem;
        }
        scrollResolved = true;
        try {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(SCROLL_ID);
            Item found = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            scrollItem = found == net.minecraft.world.item.Items.AIR ? null : found;
        }
        catch (RuntimeException | LinkageError ignored) {
            scrollItem = null;
        }
        return scrollItem;
    }

    /**
     * 4.6.0(内存/CPU):Curios 的反射入口只解析一次。
     *
     * <p>原来每秒(每玩家)都要 {@code Class.forName("...CuriosApi")} + {@code getMethod(...)};
     * 类加载器查找虽然不分配堆,但每次都要走 JNI + 类名解析,20 tps × 玩家数 下来纯属浪费。
     * 解析失败(没装 Curios)时保持 null,{@link #clampScrolls} 直接跳过 —— 与原来 catch 住一样。</p>
     */
    private static java.lang.reflect.Method curiosInventoryMethod;
    private static boolean curiosResolved;

    private static java.lang.reflect.Method curiosInventory() {
        if (curiosResolved) {
            return curiosInventoryMethod;
        }
        curiosResolved = true;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            curiosInventoryMethod = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
        }
        catch (Throwable ignored) {
            curiosInventoryMethod = null;
        }
        return curiosInventoryMethod;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
                return;
            }
            if (player.tickCount % 20 != 0) {
                return;
            }
            if (player.totalExperience < 0) {
                player.totalExperience = 0;
            }
            else if (player.totalExperience > CAP) {
                player.totalExperience = CAP;
            }
            if (player.experienceLevel < 0) {
                player.experienceLevel = 0;
                player.experienceProgress = 0.0F;
            }
            else if (player.experienceLevel > CAP) {
                player.experienceLevel = CAP;
            }
            clampScrolls(player);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    private static void clampScrolls(Player player) {
        // 1.6.22:再用反射扫一遍 Curios 饰品槽(编译期不依赖 Curios 泛型签名)
        java.lang.reflect.Method inventoryOf = curiosInventory();
        if (inventoryOf != null) {
            try {
                Object inv = inventoryOf.invoke(null, player);
                if (inv instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object handler = opt.get();
                    Object curios = handler.getClass().getMethod("getCurios").invoke(handler);
                    if (curios instanceof java.util.Map<?, ?> map) {
                        for (Object value : map.values()) {
                            clampCurioStacks(value);
                        }
                    }
                }
            }
            catch (Throwable ignored) {
                // 没有 Curios / 结构不一样就跳过
            }
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            clampScroll(player.getInventory().getItem(i));
        }
        clampScroll(player.getOffhandItem());
    }

    private static void clampCurioStacks(Object stacks) {
        try {
            Object stackHandler = stacks.getClass().getMethod("getStacksHandler").invoke(stacks);
            Object inv = stackHandler.getClass().getMethod("getStacks").invoke(stackHandler);
            int slots = (int) inv.getClass().getMethod("getSlots").invoke(inv);
            for (int i = 0; i < slots; i++) {
                clampScroll((ItemStack) inv.getClass().getMethod("getStackInSlot", int.class).invoke(inv, i));
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }

    private static void clampScroll(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            // 4.6.0(内存):原来是 `BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()` 再比字符串,
            // 每个槽位都会新建一个 String。改成拿缓存好的 Item 实例做身份比较,零分配。
            Item scroll = scroll();
            if (scroll == null || stack.getItem() != scroll) {
                return;
            }
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(NBT_KEY)) {
                return;
            }
            int stored = tag.getInt(NBT_KEY);
            if (stored > SCROLL_CAP) {
                tag.putInt(NBT_KEY, SCROLL_CAP);
            }
            else if (stored < 0) {
                tag.putInt(NBT_KEY, SCROLL_CAP);   // 已溢出 → 拉回安全上限
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}