package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 掉落管线:把"玩家挖掘时算出来的掉落表"里的每个堆按普通熔炉配方替换掉。
 * <p>
 * <b>接入点</b>:{@code Block#getDrops(BlockState, ServerLevel, BlockPos, BlockEntity, Entity, ItemStack)}
 * 的 RETURN(见 {@code mixin.BlockGetDropsMixin})。为什么不按调研报告里的
 * "捕获 ItemEntity + 延迟结算":
 * <ul>
 * <li>{@code Block#dropResources} 就是 {@code getDrops(...).forEach(popResource)} ——
 *     在这里替换,<b>原版只会生成替换后的那一个</b>,我们既不取消事件也不自己 spawn;</li>
 * <li>与其它掉落管线(奇妙附魔的物品收集球、任何自动熔炼模组)天然<b>只替换一次</b>:
 *     别人看到/收走的已经是产物,不存在"两边各生成一份";</li>
 * <li>不需要跨 tick 的收集窗口,也就没有崩溃/停服时的残留物品要兜底。</li>
 * </ul>
 * <b>只对玩家挖掘生效</b>:{@code dropResources} 由 {@code Block#playerDestroy} 调用(玩家挖掘的正常路径),
 * 它把挖掘者与挖掘用的工具原样传进来;{@code entity} 不是玩家(爆炸/活塞/命令的
 * {@code Level#destroyBlock})时这里直接返回原表。
 * <p>
 * 1.1.22:所有日志改成<b>纯 ASCII/英文</b>(中文在这套环境里会变成乱码),并给出固定格式的判定链:
 * {@code [AutoSmelt] decision player=... tool=... ourLevel=... theirLevel=... theirConfig=...
 * sneaking=... drops=... action=SMELT|YIELD|SKIP ...} —— 只看这一行就能定位"为什么没熔"。
 */
public final class AutoSmeltPipeline {

    /** 日志前缀(ASCII;判定细节只在 debugLog=true 输出,启动时另有一条 ready 行)。 */
    public static final String TAG = "[AutoSmelt]";

    private AutoSmeltPipeline() {}

    /**
     * 处理一次挖掘的掉落表。
     *
     * @param level  服务端世界
     * @param pos    被破坏方块的坐标(账本作用域的一部分)
     * @param miner  挖掘者(不是玩家 ⇒ 原样返回)
     * @param tool   挖掘时手里的工具(附魔从这里读,不受挖掘后损坏/换手影响)
     * @param drops  {@code Block#getDrops} 算出来的原版掉落(可能已经过时运/精准采集)
     * @return 替换后的新表;一个都没变时<b>返回原表实例</b>,调用方据此完全不动原版逻辑
     */
    public static List<ItemStack> process(ServerLevel level, BlockPos pos, @Nullable Entity miner,
        ItemStack tool, @Nullable List<ItemStack> drops) {
        if (level == null || pos == null || drops == null || drops.isEmpty()) {
            return drops;
        }
        if (!(miner instanceof Player player)) {
            // Only player mining: explosions/pistons/commands call Level#destroyBlock(entity=null).
            return drops;
        }

        // Fixed-format decision line (ASCII): a single line explains everything.
        ItemStack effective = AutoSmeltHandler.effectiveTool(player, tool);
        int ourLevel = AutoSmeltHandler.levelOf(effective, ModEnchantments.autoSmeltOrNull());
        int theirLevel = AutoSmeltCompat.wonderLevel(effective);
        boolean theirConfig = AutoSmeltCompat.wonderFeatureEnabled();
        boolean sneaking = AutoSmeltHandler.suppressedBySneaking(player);
        AutoSmeltHandler.Action action = AutoSmeltHandler.decide(player, tool);
        if (RingConfig.debugLog()) {
            DebugLog.info(
                "{} decision player={} tool={} ourLevel={} theirLevel={} theirConfig={} sneaking={} drops={} action={} pos={}",
                TAG, player.getGameProfile().getName(), idOf(effective), ourLevel, theirLevel, theirConfig,
                sneaking, drops.size(), action, pos);
        }
        if (action != AutoSmeltHandler.Action.SMELT) {
            return drops;
        }

        AutoSmeltLedger.Session session = AutoSmeltLedger.session(level, pos);
        List<ItemStack> out = null;
        int replacedStacks = 0;
        int producedItems = 0;

        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            List<ItemStack> smelted = null;
            if (drop != null && !drop.isEmpty()) {
                // Guard 1: the same stack instance is only replaced once per mining tick.
                if (session.alreadySeen(drop)) {
                    if (RingConfig.debugLog()) {
                        DebugLog.info("{} skip stack (already handled in this mining) {}x{} at {}",
                            TAG, drop.getCount(), idOf(drop), pos);
                    }
                }
                // Guard 2: never smelt something we just produced in this mining.
                else if (session.isOurProduct(drop)) {
                    if (RingConfig.debugLog()) {
                        DebugLog.info("{} skip stack (produced by us in this mining) {}x{} at {}",
                            TAG, drop.getCount(), idOf(drop), pos);
                    }
                }
                else {
                    List<ItemStack> result = AutoSmeltHandler.smeltOne(level, drop);
                    if (!(result.size() == 1 && result.get(0) == drop)) {
                        smelted = result;
                    }
                }
            }

            if (smelted == null) {
                if (out != null) {
                    out.add(drop);
                }
                continue;
            }

            if (out == null) {
                out = new ArrayList<>(drops.size() + 2);
                out.addAll(drops.subList(0, i));
            }
            for (ItemStack produced : smelted) {
                session.recordProduced(produced);
                out.add(produced);
                producedItems += produced.getCount();
            }
            ++replacedStacks;
            if (RingConfig.debugLog()) {
                String producedId = smelted.isEmpty() ? "none" : idOf(smelted.get(0));
                DebugLog.info("{} replace {}x{} -> {} item(s) of {} ({} stack(s)) at {}", TAG,
                    drop.getCount(), idOf(drop), producedItems, producedId, smelted.size(), pos);
            }
        }

        if (out == null) {
            if (RingConfig.debugLog()) {
                DebugLog.info(
                    "{} fired(via=getDrops) player={} tool={} ench={} lvl={} dropsBefore={} smeltLookup ok=false "
                        + "written=false dropsAfter={} pos={}",
                    TAG, player.getGameProfile().getName(), idOf(effective), AutoSmeltHandler.hasEnchant(effective),
                    ourLevel, describe(drops), describe(drops), pos);
            }
            return drops;
        }
        if (RingConfig.debugLog()) {
            DebugLog.info(
                "{} fired(via=getDrops) player={} tool={} ench={} lvl={} dropsBefore={} smeltLookup ok=true "
                    + "written=true dropsAfter={} pos={} (replaced={} stacks / produced={} items)",
                TAG, player.getGameProfile().getName(), idOf(effective), AutoSmeltHandler.hasEnchant(effective),
                ourLevel, describe(drops), describe(out), pos, replacedStacks, producedItems);
        }
        return out;
    }

    /** 掉落表摘要(ASCII 日志用),例如 {@code [raw_iron x1]}. */
    public static String describe(@Nullable List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (ItemStack stack : stacks) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(idOf(stack)).append(" x").append(stack.getCount());
        }
        return builder.append(']').toString();
    }

    /** 自检用:直接走生产入口,不经过 mixin(参数与 {@link #process} 完全一致)。 */
    public static List<ItemStack> processForTest(ServerLevel level, BlockPos pos, Player miner, ItemStack tool,
        List<ItemStack> drops) {
        return process(level, pos, miner, tool, drops);
    }

    /** ASCII 物品 id(日志用)。 */
    public static String idOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? String.valueOf(stack.getItem()) : id.toString();
    }
}
