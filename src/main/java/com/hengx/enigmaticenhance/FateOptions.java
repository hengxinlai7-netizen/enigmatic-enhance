package com.hengx.enigmaticenhance;

/** 1.6.14:创建世界界面的两个选项(客户端勾选 → 开世界时写入 gamerule)。 */
public final class FateOptions {

    public static volatile boolean correction;
    public static volatile boolean trial;

    /** 用户是否手动点过(只有点过才在开世界时覆盖 gamerule,避免把手动 /gamerule 冲掉)。 */
    public static volatile boolean correctionTouched;
    public static volatile boolean trialTouched;

    private FateOptions() {
    }
}