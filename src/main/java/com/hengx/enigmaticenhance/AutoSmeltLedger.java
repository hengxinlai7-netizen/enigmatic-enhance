package com.hengx.enigmaticenhance;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 「本次挖掘」的一次性标记 + 产物去重双保险。
 * <p>
 * 作用域是 <b>(维度, 方块坐标, gameTick)</b> —— 也就是"同一个方块在同一 tick 里的这一次破坏"。
 * 只活一个 tick,下一次调用就整表清掉,不会泄漏、也不会影响别处的正常掉落。
 * <ul>
 * <li><b>一次性标记</b>({@code seenInputs}):同一个 {@link ItemStack} <b>实例</b>只会被替换一次。
 *     若同一段代码/另一个模组把同一个堆再喂进管线一次,第二次原样返回 ⇒ 不会出现"熔炼两次"。</li>
 * <li><b>产物去重</b>({@code producedInstances} + {@code producedKeys}):本模组这次挖掘已经产出的东西,
 *     又被当成输入喂回来时不再熔炼(键 = 物品 id + 数量,按实例和按值各记一份)。
 *     防止 A→B→C 的连锁(例如砂砾/圆石这种"产物本身还能再熔炼"的配方被连熔两次)。</li>
 * </ul>
 */
public final class AutoSmeltLedger {

    private static final Map<String, Session> SESSIONS = new HashMap<>();

    private AutoSmeltLedger() {}

    /** 取(或建立)当前 tick、当前方块的会话;顺手把过期的会话丢掉。 */
    public static Session session(Level level, BlockPos pos) {
        long tick = level == null ? 0L : level.getGameTime();
        ResourceLocation dimension = dimensionOf(level);
        SESSIONS.values().removeIf(session -> session.tick != tick);
        String key = dimension + "@" + pos.asLong() + "#" + tick;
        return SESSIONS.computeIfAbsent(key, ignored -> new Session(tick));
    }

    /** 自检用:清空(测完不留下状态)。 */
    public static void reset() {
        SESSIONS.clear();
    }

    private static ResourceLocation dimensionOf(Level level) {
        if (level == null) {
            return new ResourceLocation("minecraft", "overworld");
        }
        try {
            return level.dimension().location();
        }
        catch (Throwable t) {
            // 极端早期:退回对象身份,保证不同维度的会话不会互相串
            return new ResourceLocation("enigmaticenhance", "unknown_" + System.identityHashCode(level));
        }
    }

    /** 一个方块一次破坏的账本。 */
    public static final class Session {

        private final long tick;
        private final Set<ItemStack> seenInputs = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<ItemStack> producedInstances = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> producedKeys = new HashSet<>();

        Session(long tick) {
            this.tick = tick;
        }

        /** 这个输入实例是不是已经处理过了(第一次调用返回 false 并登记)。 */
        public boolean alreadySeen(ItemStack input) {
            return !this.seenInputs.add(input);
        }

        /** 这个输入是不是本模组这次挖掘刚产出的东西(是 ⇒ 不许再熔一次)。 */
        public boolean isOurProduct(ItemStack input) {
            if (input == null || input.isEmpty()) {
                return false;
            }
            return this.producedInstances.contains(input) || this.producedKeys.contains(keyOf(input));
        }

        /** 登记产出的堆。 */
        public void recordProduced(ItemStack produced) {
            if (produced == null || produced.isEmpty()) {
                return;
            }
            this.producedInstances.add(produced);
            this.producedKeys.add(keyOf(produced));
        }

        private static String keyOf(ItemStack stack) {
            ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            return (id == null ? "unknown" : id.toString()) + "|" + stack.getCount();
        }
    }
}
