package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;

/** 1.6.1:末地传送封锁判定 —— 用 ServerLevel 的龙列表(反射取,避免泛型差异),不做超大 AABB 扫描。 */
public final class EnigmaticDragonGate {

    private EnigmaticDragonGate() {
    }

    public static boolean dragonAliveInEnd(Entity entity) {
        return entity != null && dragonAliveIn(entity.level());
    }

    private static long cachedTick = -1000L;
    private static boolean cachedAlive;

    public static boolean dragonAliveIn(Level level) {
        try {
            if (level instanceof ServerLevel sl) {
                long now = sl.getGameTime();
                if (now - cachedTick < 20L) {
                    return cachedAlive && Level.END.equals(level.dimension());
                }
                cachedTick = now;
                cachedAlive = computeAlive(sl);
                return cachedAlive && Level.END.equals(level.dimension());
            }
            return false;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean computeAlive(ServerLevel level) {
        try {
            if (!Level.END.equals(level.dimension())) {
                return false;
            }
            Object dragons = invokeDragons(level);
            if (!(dragons instanceof List<?> list)) {
                return false;
            }
            for (Object o : list) {
                if (o instanceof EnderDragon dragon && dragon.isAlive()) {
                    return true;
                }
            }
            return false;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static final java.lang.invoke.MethodHandle SERVER_LEVEL_DRAGONS = findDragons();

    private static java.lang.invoke.MethodHandle findDragons() {
        for (Class<?> cls = ServerLevel.class; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (String name : new String[] { "getDragons", "m_6292_" }) {
                try {
                    java.lang.reflect.Method m = cls.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return java.lang.invoke.MethodHandles.lookup().unreflect(m);
                }
                catch (Throwable ignored) {
                    // 下一个
                }
            }
        }
        return null;
    }

    private static Object invokeDragons(ServerLevel level) {
        try {
            return SERVER_LEVEL_DRAGONS == null ? null : SERVER_LEVEL_DRAGONS.invoke(level);
        }
        catch (Throwable ignored) {
            return null;
        }
    }
}