package com.hengx.enigmaticenhance;

import org.apache.logging.log4j.Logger;

/**
 * 本模组<b>所有自定义日志</b>的唯一出口(1.1.23)。
 * <p>
 * 规则:
 * <ul>
 *   <li>{@link #info}/ {@link #debug} —— 全部受 {@code debugLog} 门控,默认 false ⇒ <b>零输出</b>。
 *       任何"正常运行时会刷屏"的判定日志都必须走这里;</li>
 *   <li>{@link #startup} —— 单独受 {@code logStartupLine} 门控,<b>默认 false</b>(用户要求日志彻底关闭);
 *       想保留"已加载"那一行的人把它设 true 即可;</li>
 *   <li>{@link #warn}/{@link #error} —— <b>不门控</b>,只在真正出错时触发(配置非法 id、反射失败、
 *       注入失败、管线异常等)。这类信息如果被门控掉,出问题时就彻底看不见了。</li>
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
     * 1.2.12b:关键诊断,**每次都输出**,但每类只打前 8 次(不需要开 debugLog)。
     * 用于"功能不生效"这种只能靠日志定位的问题。
     */
    private static final java.util.Set<String> ONCE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void once(String key, String format, Object... args) {
        try {
            if (ONCE.size() >= 32 || !ONCE.add(key)) {
                return;
            }
            LOGGER.info(format, args);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 日志失败不影响游戏
        }
    }

    /** 真正的警告:不门控。 */
    public static void warn(String format, Object... args) {
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
