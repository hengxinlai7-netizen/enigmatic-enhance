package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** 1.6.22:「宿命的修正」的阶段存档(0=80%,1=50%,2=正常),重进世界不再丢失。 */
public class FateSavedData extends SavedData {

    private static final String NAME = "enigmaticenhance_fate";
    private int stage;

    public static FateSavedData load(CompoundTag tag) {
        FateSavedData data = new FateSavedData();
        data.stage = tag.getInt("stage");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("stage", this.stage);
        return tag;
    }

    public static int get(MinecraftServer server) {
        try {
            FateSavedData data = server.overworld().getDataStorage()
                .computeIfAbsent(FateSavedData::load, FateSavedData::new, NAME);
            return data.stage;
        }
        catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public static void set(MinecraftServer server, int stage) {
        try {
            FateSavedData data = server.overworld().getDataStorage()
                .computeIfAbsent(FateSavedData::load, FateSavedData::new, NAME);
            data.stage = stage;
            data.setDirty();
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}