package com.hengx.enigmaticenhance;

import net.minecraft.world.level.GameRules;

/** 1.6.4:「宿命的修正」世界规则(创建世界/存档可用 /gamerule correctionOfFate true 开启)。 */
public final class ModGameRules {

    public static final GameRules.Key<GameRules.BooleanValue> CORRECTION_OF_FATE =
        GameRules.register("correctionOfFate", GameRules.Category.MISC, GameRules.BooleanValue.create(false));

    public static final GameRules.Key<GameRules.BooleanValue> TRIAL_OF_FATE =
        GameRules.register("trialOfFate", GameRules.Category.MISC, GameRules.BooleanValue.create(false));

    private ModGameRules() {
    }

    public static void bootstrap() {
        // 触发类加载即可完成注册
    }
}