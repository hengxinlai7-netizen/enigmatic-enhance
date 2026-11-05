package com.hengx.enigmaticenhance;

import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 3.8.6:「磁吸戒指」与「超级磁吸戒指」的吸取范围改到 36 格。
 *
 * <h2>目标</h2>
 * <p>神秘遗物的两个戒指各自持有一个 Omniconfig 参数:</p>
 * <pre>
 *   com.aizistral.enigmaticlegacy.items.MagnetRing.range       (IntParameter)
 *   com.aizistral.enigmaticlegacy.items.SuperMagnetRing.range  (IntParameter)
 * </pre>
 *
 * <p>本模组不改它们的代码,只是在这两个参数被配置系统初始化之后,
 * 直接把运行时值改成 {@value #TARGET_RANGE}(必要时同时抬高上限)。</p>
 *
 * <h2>为什么用反射</h2>
 * <p>没装神秘遗物时类不存在,直接引用会在类加载阶段抛 {@link NoClassDefFoundError}。
 * 反射 + 全部异常吞掉,保证"没有神秘遗物就什么都不做"。</p>
 *
 * <h2>时机</h2>
 * <ul>
 *   <li>{@link FMLCommonSetupEvent}:模组构造与配置文件读取之后(主入口);</li>
 *   <li>{@link ServerAboutToStartEvent}:进世界前再确认一次(配置被外部工具改写过的情况)。</li>
 * </ul>
 */
public final class MagnetRangeHandler {

    /** 目标吸取范围(格)。 */
    public static final int TARGET_RANGE = 36;

    /** 参数上限至少抬到这个值,否则 setValue(36) 会被夹回旧上限。 */
    private static final int MIN_MAX = 64;

    private static final String[] PARAM_FIELDS = {
        "com.aizistral.enigmaticlegacy.items.MagnetRing",
        "com.aizistral.enigmaticlegacy.items.SuperMagnetRing"
    };

    private MagnetRangeHandler() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MagnetRangeHandler::apply);
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        apply();
    }

    /** 把两个参数的实际值改成 {@link #TARGET_RANGE}。 */
    public static void apply() {
        for (String className : PARAM_FIELDS) {
            try {
                Class<?> type = Class.forName(className, false, MagnetRangeHandler.class.getClassLoader());
                Object param = type.getField("range").get(null);
                if (param == null) {
                    continue;
                }
                int oldMax = (int) invoke(param, "getMaxValue", -1);
                if (oldMax > 0 && oldMax < TARGET_RANGE) {
                    invoke(param, "setMaxValue", MIN_MAX);
                }
                int before = (int) invoke(param, "getValue", -1);
                invoke(param, "setValue", TARGET_RANGE);
                int after = (int) invoke(param, "getValue", -1);
                DebugLog.info("[Magnet] {} 吸取范围 {} -> {} 格", simpleName(className), before, after);
            }
            catch (ClassNotFoundException ignored) {
                // 没装神秘遗物
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                DebugLog.warn("[Magnet] 修改 {} 的 range 失败: {}", simpleName(className), e.toString());
            }
        }
    }

    private static Object invoke(Object target, String method, int arg) throws ReflectiveOperationException {
        if (arg < 0) {
            return target.getClass().getMethod(method).invoke(target);
        }
        Class<?>[] types = {int.class};
        return target.getClass().getMethod(method, types).invoke(target, arg);
    }

    private static String simpleName(String className) {
        int i = className.lastIndexOf('.');
        return i < 0 ? className : className.substring(i + 1);
    }
}
