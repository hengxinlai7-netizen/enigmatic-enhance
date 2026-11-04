package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
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
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object inv = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class)
                .invoke(null, player);
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
            // 没有 Curios 就跳过
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
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!SCROLL_ID.equals(id)) {
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