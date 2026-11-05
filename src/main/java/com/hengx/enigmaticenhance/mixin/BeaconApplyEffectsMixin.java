package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.BeaconEnhanceHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 2.11.15:信标加成<b>无视距离</b>。
 *
 * <p>原版 {@code applyEffects} 只对"距信标 N 格内"的玩家生效;这里在 HEAD 接管:
 * 只要信标所在区块仍然加载着,就给<b>同维度所有玩家</b>施加同样的效果,
 * 然后取消原逻辑(避免叠加)。</p>
 */
@Mixin(BeaconBlockEntity.class)
public class BeaconApplyEffectsMixin {

    @Inject(method = "m_155097_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$applyGlobal(Level level, BlockPos pos, int levels,
        MobEffect primary, MobEffect secondary, CallbackInfo ci) {
        try {
            if (level == null || pos == null || (primary == null && secondary == null)) {
                return;
            }
            if (level.isClientSide) {
                return;
            }
            if (!level.isLoaded(pos)) {
                return;   // 信标所在区块没加载 → 交给原版(原版也不会做)
            }
            if (level.getBlockEntity(pos) instanceof BeaconBlockEntity be) {
                BeaconEnhanceHandler.ensureValid(be);
            }
            int duration = (9 + levels * 2) * 20;
            int amp = levels >= 4 ? 1 : 0;
            // 3.4.0:跨维度生效 —— 不再只看信标所在维度,而是遍历服务器全部在线玩家
            java.util.List<? extends Player> targets;
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && serverLevel.getServer() != null) {
                targets = serverLevel.getServer().getPlayerList().getPlayers();
            }
            else {
                targets = level.players();   // 没有服务器引用的兜底
            }
            int applied = 0;
            for (Player player : targets) {
                if (player == null || player.isSpectator()) {
                    continue;
                }
                if (primary != null) {
                    player.addEffect(new MobEffectInstance(primary, duration,
                        BeaconEnhanceHandler.amplifierFor(primary, amp), true, true));
                }
                if (secondary != null) {
                    player.addEffect(new MobEffectInstance(secondary, duration,
                        BeaconEnhanceHandler.amplifierFor(secondary, amp), true, true));
                }
                applied++;
            }
            com.hengx.enigmaticenhance.DebugLog.once(
                "beacon:apply:" + pos.asLong(),
                "[Beacon] {} 施加信标效果:主={} 副={} 等级={} 跨维度命中 {} 人",
                pos, primary, secondary, levels, applied);
            ci.cancel();
        }
        catch (RuntimeException | LinkageError ignored) {
            // 回退到原版
        }
    }
}
