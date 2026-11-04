package com.hengx.enigmaticenhance;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 1.6.6:按创建世界界面的勾选,写入两个 gamerule。 */
public final class FateWorldApplier {

    private FateWorldApplier() {
    }

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        apply(event.getServer());
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        apply(event.getServer());
    }

    private static void apply(MinecraftServer server) {
        try {
            GameRules rules = server.getGameRules();
            // 1.6.38:只要界面是"开"就写入(不再依赖 touched 标记,避免状态丢失)
            if (FateOptions.correctionTouched || FateOptions.correction) {
                rules.getRule(ModGameRules.CORRECTION_OF_FATE).set(FateOptions.correction, server);
                FateOptions.correctionTouched = false;
            }
            DebugLog.info("[Fate] 开世界:correction={}(touched={}) trial={}(touched={})",
                FateOptions.correction, FateOptions.correctionTouched,
                FateOptions.trial, FateOptions.trialTouched);
            if (FateOptions.trialTouched || FateOptions.trial) {
                // 2.7.3:宿命的试炼已拆到 enhancetrial,主模组不再写这条规则
                FateOptions.trialTouched = false;
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}