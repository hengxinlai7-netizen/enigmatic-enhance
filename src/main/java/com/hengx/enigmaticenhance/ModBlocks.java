package com.hengx.enigmaticenhance;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 1.2.24:本模组的方块注册表。 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, EnigmaticEnhance.MODID);

    /** 1.2.29:enigmaticenhance:war_banner —— 战争之旗(无碰撞、可拆、范围施加血色之灾)。 */
    public static final RegistryObject<Block> WAR_BANNER =
        BLOCKS.register("war_banner", WarBannerBlock::new);

    private ModBlocks() {
    }
}