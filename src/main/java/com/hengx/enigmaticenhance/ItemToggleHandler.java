package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 「某物品的 Ctrl+左键 开关」的**通用实现**(1.1.15 从 1.1.12 那份"只写给万钧之护卷轴"的代码里抽出来,两处共用)。
 *
 * <h2>约定(与书籍开关、卷轴开关完全一致)</h2>
 * <ul>
 * <li>状态写在**物品自己的 NBT** 上:<b>键不存在 = 开(默认行为)</b>,键存在且为 true = 用户主动关闭;</li>
 * <li>关闭时不写任何"多余"的 NBT(删键后若 NBT 空了就整个清掉,避免给物品挂空 CompoundTag);</li>
 * <li>客户端只发"我在哪个菜单的哪个槽位按了 Ctrl+左键"({@link EnigmaticNetwork.ItemTogglePacket}),
 *     <b>服务端重新校验</b>:必须是自己当前打开的菜单、槽位在范围内、槽里确实是该物品,任何一条不满足都静默忽略
 *     → 伪造请求无效;</li>
 * <li>改完 {@code slot.setChanged() + menu.broadcastChanges()},客户端立即看到新状态;</li>
 * <li>日志一律先判 {@code debugLog()}。</li>
 * </ul>
 * 复用的是 1.1.12 就有的那一条网络通道({@code enigmaticenhance:main},message id 1),**没有另起通道**。
 */
public final class ItemToggleHandler {

    /** 一个支持 Ctrl+左键 开关的物品。 */
    public interface Target {

        /** 这个 ItemStack 是不是我管的物品。 */
        boolean matches(ItemStack stack);

        /** 物品 NBT 里表示"已关闭"的键。 */
        String nbtKey();

        /** 日志里用的名字(中文)。 */
        String displayName();

        /** 配置是否允许用 Ctrl+左键 切换(不允许时服务端也会拒绝)。 */
        boolean toggleAllowed();
    }

    private static List<Target> targets;

    private ItemToggleHandler() {}

    /** 所有支持开关的物品(延迟构建,避免类初始化顺序问题)。 */
    public static synchronized List<Target> targets() {
        if (targets == null) {
            targets = List.of(ScrollHandler.TOGGLE_TARGET, BloodbathHandler.TOGGLE_TARGET, RevivalLeafStoneHandler.TOGGLE_TARGET);
        }
        return targets;
    }

    /** 找一个能管这个 ItemStack 的 Target(没有则 null)。 */
    public static Target targetFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (Target target : targets()) {
            if (target.matches(stack)) {
                return target;
            }
        }
        return null;
    }

    /** 客户端用:这个物品现在能不能被 Ctrl+左键 切换。 */
    public static boolean canToggle(ItemStack stack) {
        Target target = targetFor(stack);
        return target != null && target.toggleAllowed();
    }

    // ------------------------------------------------------------------ 通用 NBT(缺省 = 开)

    /** 通用读取:键不存在(或没有 NBT)= 未关闭。 */
    public static boolean isDisabled(ItemStack stack, String key) {
        if (stack == null || !stack.hasTag()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(key);
    }

    /** 通用写入/清除:关闭时写 true;打开时删键,删完空了就清掉整个 CompoundTag。 */
    public static void setDisabled(ItemStack stack, String key, boolean disabled) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (disabled) {
            stack.getOrCreateTag().putBoolean(key, true);
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(key);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    /** 切换某个 Target 管辖的物品,返回"切换后是否仍然启用"。 */
    public static boolean toggle(Target target, ItemStack stack) {
        boolean disabled = isDisabled(stack, target.nbtKey());
        if (target == null || !target.matches(stack) || !target.toggleAllowed()) {
            return !disabled;
        }
        setDisabled(stack, target.nbtKey(), !disabled);
        return disabled;
    }

    // ------------------------------------------------------------------ 服务端:处理 Ctrl+左键 请求

    /**
     * 服务端校验并执行一次"切换"。客户端只发请求(容器 id + 槽位号),这里**完全重新校验**:
     * 必须是自己当前打开的菜单、槽位合法、槽里确实是某个支持开关的物品、且配置允许切换。
     */
    public static void handleToggleRequest(ServerPlayer sender, int containerId, int slotIndex) {
        if (sender == null) {
            return;
        }
        AbstractContainerMenu menu = sender.containerMenu;
        if (menu == null || menu.containerId != containerId) {
            return;
        }
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.getSlot(slotIndex);
        ItemStack stack = slot.getItem();
        Target target = targetFor(stack);
        if (target == null || !target.toggleAllowed()) {
            return;
        }
        boolean enabled = toggle(target, stack);
        slot.setChanged();
        menu.broadcastChanges();
        if (RingConfig.debugLog()) {
            DebugLog.info("[物品开关] 玩家 {} 槽 {} -> {}({}) 禁用标记={}", sender.getGameProfile().getName(),
                slotIndex, target.displayName(), enabled ? "已启用" : "已关闭", isDisabled(stack, target.nbtKey()));
        }
    }
}
