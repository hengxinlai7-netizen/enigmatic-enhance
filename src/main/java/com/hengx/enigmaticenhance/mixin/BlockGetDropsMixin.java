package com.hengx.enigmaticenhance.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.AutoSmeltPipeline;
import com.hengx.enigmaticenhance.EnigmaticEnhance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 【自动熔炼】的唯一接入点:{@code Block#getDrops}(6 参:BlockState/ServerLevel/BlockPos/BlockEntity/Entity/ItemStack)
 * 的 RETURN。SRG {@code m_49874_}(1.20.1 生产运行期用的名字;本项目的 mixin 一律按 SRG + {@code remap = false} 写,
 * 与 {@code EnchantmentHelperSweepMixin} 同一套约定)。
 * <p>
 * 为什么是这个方法(而不是捕获 {@code ItemEntity} 或 {@code Block#popResource}):
 * <ul>
 * <li>{@code Block#dropResources} 的实现就是 {@code getDrops(...).forEach(popResource)} ——
 *     在这里换成熔炼后的表,原版接下来<b>只会生成替换后的物品</b>;</li>
 * <li>本模组<b>不取消任何事件、不自己 spawn 物品</b>,所以其它模组的掉落管线
 *     (奇妙附魔的物品收集球、别的自动熔炼模组)看到的就是产物 ⇒ 同一条掉落只可能被替换一次;</li>
 * <li>参数里直接带着挖掘者与挖掘时用的工具,不需要 ThreadLocal 上下文,
 *     也天然满足"只对玩家挖掘掉落生效"({@code entity} 不是玩家时下游直接返回原表)。</li>
 * </ul>
 * 任何异常都在这里就地吞掉并保留原版掉落(绝不吞物品)。
 */
@Mixin(Block.class)
public abstract class BlockGetDropsMixin {

    @Inject(
        method = "m_49874_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false)
    private static void enigmaticenhance$autoSmeltDrops(BlockState state, ServerLevel level, BlockPos pos,
        BlockEntity blockEntity, Entity miner, ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> drops = cir.getReturnValue();
        try {
            List<ItemStack> replaced = AutoSmeltPipeline.process(level, pos, miner, tool, drops);
            if (replaced != drops) {
                cir.setReturnValue(replaced);
            }
        }
        catch (Throwable t) {
            // 任何意外都不许影响原版掉落:原表已经在 cir 里,什么都不做就是"保持原样"
            EnigmaticEnhance.LOGGER.error("[自动熔炼] 掉落管线异常,已保留原版掉落", t);
        }
    }
}
