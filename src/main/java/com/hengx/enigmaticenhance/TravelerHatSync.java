package com.hengx.enigmaticenhance;

/**
 * 「旅行家帽子」tooltip 用的**客户端计数缓存**(1.1.25c)。
 * <p>
 * 玩家的持久数据({@link TravelerHatData})只在服务端;tooltip 在客户端渲染,所以服务端在计数
 * 变化/登录/换维度时发 S2C {@code TravelerDataPacket},客户端把它记在这里,tooltip 直接读。
 * 这个类是双端共用的普通类(没有 Minecraft 引用),专用服务端只会让它保持 0,不会加载客户端类。
 */
public final class TravelerHatSync {

    private static volatile int biomes;
    private static volatile int dimensions;
    private static volatile boolean synced;

    private TravelerHatSync() {}

    /** 客户端收到同步包时调用(自检里也直接调用来模拟客户端)。 */
    public static void accept(int biomeCount, int dimensionCount) {
        biomes = Math.max(0, biomeCount);
        dimensions = Math.max(0, dimensionCount);
        synced = true;
    }

    public static int biomes() {
        return biomes;
    }

    public static int dimensions() {
        return dimensions;
    }

    public static boolean synced() {
        return synced;
    }

    /** 退出/切角色时清空,避免把上一个角色的数字显示给下一个。 */
    public static void reset() {
        biomes = 0;
        dimensions = 0;
        synced = false;
    }
}
