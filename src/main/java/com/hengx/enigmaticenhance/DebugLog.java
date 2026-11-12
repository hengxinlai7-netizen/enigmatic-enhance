package com.hengx.enigmaticenhance;

import org.apache.logging.log4j.Logger;

/**
 * 本模组<b>所有自定义日志</b>的唯一出口(1.1.23)。
 * <p>
 * 规则(4.7.0 起:正式版正常游玩时只留数据自检那一行):
 * <ul>
 *   <li>{@link #info}/ {@link #debug}/ {@link #once}/ <b>{@link #warn}</b> —— 全部受 {@code debugLog} 门控,
 *       默认 false ⇒ <b>零输出</b>。任何"正常运行时会刷屏"的日志都必须走这里
 *       (4.7.0 把原先不门控的 {@code warn}/{@code once} 一并收进开关下:它们的量太大,正常游玩时会写进
 *       latest.log,想让它们出现只要 {@code debug.logging.debugLog = true});</li>
 *   <li>{@link #startup} —— 单独受 {@code logStartupLine} 门控,<b>默认 false</b>(用户要求日志彻底关闭);
 *       想保留"已加载"那一行的人把它设 true 即可;</li>
 *   <li>{@link #error} —— <b>不门控</b>,只在真正出错时触发(异常被兜住的那种:
 *       掉管线异常、反射失败、网络包处理失败等)。正常游玩时不会产生任何一条。</li>
 * </ul>
 * 读配置时全部带 try/catch:配置还没加载(构造期/极早期)时按"关"处理,绝不因为读日志开关而抛异常。
 */
public final class DebugLog {

    private static final Logger LOGGER = EnigmaticEnhance.LOGGER;

    private DebugLog() {}

    /** 受 {@code debugLog} 门控的普通日志(默认不输出)。 */
    public static void info(String format, Object... args) {
        if (!debugEnabled()) {
            return;
        }
        LOGGER.info(format, args);
    }

    /** {@link #info} 的别名,语义更明确。 */
    public static void debug(String format, Object... args) {
        info(format, args);
    }

    /** 启动那一行;受 {@code logStartupLine} 门控(默认 false)。 */
    public static void startup(String format, Object... args) {
        if (!startupEnabled()) {
            return;
        }
        LOGGER.info(format, args);
    }

    /**
     * 1.2.12b:关键诊断,每类只打前 8 次。
     * <p>4.7.0:同样受 {@code debugLog} 门控 —— 这类"看一眼就够"的日志在正常游玩时也是噪音
     * (挖方块、开枪、破盾都会触发),定位问题时把 {@code debugLog} 打开即可。</p>
     */
    private static final java.util.Set<String> ONCE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void once(String key, String format, Object... args) {
        try {
            if (!debugEnabled() || ONCE.size() >= 32 || !ONCE.add(key)) {
                return;
            }
            LOGGER.info(format, args);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 日志失败不影响游戏
        }
    }

    /**
     * 4.6.0(内存):{@link #once} 的<b>廉价预检</b>,给"每 tick 都要拼 key"的热路径用。
     *
     * <p>{@code once(key, ...)} 的第一个参数常常是这样拼的:
     * {@code "noclip:off:" + uuid + ":" + flying} —— 字符串在<b>调用前</b>就已经建好了,即使
     * {@code once} 因为 key 已经记过而立刻返回,这个 String(几十字符 + UUID.toString 的 36 字符)
     * 也已经白白分配了一次。这个预检只读一个 int,返回 false 时调用方可以整段跳过,
     * 分配与 {@code once} 的语义完全一致(记录表满了 / key 已存在 ⇒ 本来也不会输出)。</p>
     */
    public static boolean oncePending() {
        return debugEnabled() && ONCE.size() < 32;
    }

    /**
     * 4.7.0:原先的"不门控警告"现在也走 {@code debugLog}。
     * <p>保持 WARN 级别(而不是降级成 INFO):打开 {@code debugLog} 时,这类行仍然以 WARN 出现在日志里,
     * 便于和真正的 {@link #error} 区分;关着的时候一条都不输出。</p>
     */
    public static void warn(String format, Object... args) {
        if (!debugEnabled()) {
            return;
        }
        LOGGER.warn(format, args);
    }

    /** 真正的错误:不门控。 */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    /** 真正的错误(无异常):不门控。 */
    public static void error(String format, Object... args) {
        LOGGER.error(format, args);
    }

    /** {@code debugLog} 当前是否打开(读不到配置 ⇒ false)。 */
    public static boolean debugEnabled() {
        try {
            return RingConfig.debugLog();
        }
        catch (Throwable t) {
            return false;
        }
    }

    /** {@code logStartupLine} 当前是否打开(读不到配置 ⇒ false)。 */
    public static boolean startupEnabled() {
        try {
            return RingConfig.logStartupLine();
        }
        catch (Throwable t) {
            return false;
        }
    }
}
