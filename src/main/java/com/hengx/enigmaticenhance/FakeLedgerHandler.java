package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;

/**
 * 2.4.0:假账生成器的服务端逻辑（改统计）。
 *
 * <p>所有改动都要求 {@code player.isCreative()}，并且只作用于**发送者自己**，
 * 所以这东西离开创造模式就完全没用 —— 不会变成生存玩法里的作弊器。</p>
 */
public final class FakeLedgerHandler {

    /** 数值上限（原版统计是 int，这里给个安全范围）。 */
    private static final int MAX_VALUE = 2_000_000_000;

    private FakeLedgerHandler() {
    }

    /** 供网络包调用：mode 0 = 加/减，1 = 直接设成 amount。 */
    public static void apply(ServerPlayer player, String statId, int mode, int amount) {
        if (player == null || statId == null) {
            return;
        }
        if (!player.isCreative()) {
            DebugLog.warn("[FakeLedger] {} 非创造模式，拒绝改统计", player.getName().getString());
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(statId);
        if (id == null) {
            return;
        }
        Stat<ResourceLocation> stat;
        try {
            stat = Stats.CUSTOM.get(id);
        }
        catch (RuntimeException | LinkageError e) {
            return;   // 这个 id 不是合法统计
        }
        int current = player.getStats().getValue(stat);
        int target;
        if (mode == 1) {
            target = amount;
        }
        else {
            target = current + amount;
        }
        target = Math.max(0, Math.min(MAX_VALUE, target));
        player.getStats().setValue(player, stat, target);
        DebugLog.info("[FakeLedger] {} 统计 {} : {} → {}", player.getName().getString(), id, current, target);
    }
}
