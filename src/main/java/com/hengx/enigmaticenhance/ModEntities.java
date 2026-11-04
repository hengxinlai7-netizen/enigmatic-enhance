package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的实体类型注册表(1.1.28c 起)。
 * <ul>
 * <li>{@code enigmaticenhance:pure_rune} —— 纯净符文的投掷物({@link PureRuneProjectile},MISC);</li>
 * <li>{@code enigmaticenhance:pure_rune_flash} —— 落点那道半径 3 的白光视觉体
 *     ({@link PureRuneFlash},纯客户端渲染,零存档数据、零碰撞)。</li>
 * </ul>
 * 注册名依旧走 {@link ModIds} 的裸 path,由 {@code DeferredRegister} 拼命名空间(与物品/附魔同一套约定)。
 * 两个实体都不需要属性表(不是 LivingEntity),所以没有 {@code RegisterEntityAttributes}。
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EnigmaticEnhance.MODID);

    /** 投掷物:小尺寸、近距离跟踪就够(飞 3 秒 / 命中即结束)。 */
    public static final RegistryObject<EntityType<PureRuneProjectile>> PURE_RUNE =
        ENTITY_TYPES.register(ModIds.PURE_RUNE_PATH, () -> EntityType.Builder
            .<PureRuneProjectile>of(PureRuneProjectile::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .clientTrackingRange(6)
            .updateInterval(10)
            .build(EnigmaticEnhance.MODID + ":" + ModIds.PURE_RUNE_PATH));

    /** 白光视觉体:只活 1 秒,给足跟踪范围与更新频率,免得客户端看不到。 */
    public static final RegistryObject<EntityType<PureRuneFlash>> PURE_RUNE_FLASH =
        ENTITY_TYPES.register(ModIds.PURE_RUNE_FLASH_PATH, () -> EntityType.Builder
            .<PureRuneFlash>of(PureRuneFlash::new, MobCategory.MISC)
            .sized(0.1F, 0.1F)
            .clientTrackingRange(8)
            .updateInterval(1)
            .build(EnigmaticEnhance.MODID + ":" + ModIds.PURE_RUNE_FLASH_PATH));

    private ModEntities() {}

    /** 1.1.29a:魔法剑气。 */
    public static final RegistryObject<EntityType<MagicSwordAuraEntity>> MAGIC_SWORD_AURA =
        ENTITY_TYPES.register("magic_sword_aura",
            () -> EntityType.Builder.<MagicSwordAuraEntity>of(MagicSwordAuraEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F).clientTrackingRange(64).build("enigmaticenhance:magic_sword_aura"));

    /** 剑气实体类型(未注册时抛异常,仅服务端生成时用)。 */
    public static EntityType<MagicSwordAuraEntity> magicSwordAuraOrNull() {
        return MAGIC_SWORD_AURA.get();
    }
}
