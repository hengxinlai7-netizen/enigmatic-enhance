package com.hengx.enigmaticenhance;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.6.39:「宿命的修正」。
 *
 * <p>世界规则 {@code correctionOfFate} 打开后,所有生物(含玩家)的全部属性下调:</p>
 * <ul>
 *   <li>初始:**×0.2**(降低 80%);</li>
 *   <li>击杀凋零后:**×0.5**(降低 50%);</li>
 *   <li>击杀末影龙后:**×1.0**(恢复)。</li>
 * </ul>
 *
 * <p>进度存于 {@link FateSavedData}(跨重进世界保留)。交互距离两个属性在黑名单,不参与。</p>
 */
public final class FateCorrectionHandler {

    private FateCorrectionHandler() {
    }

    private static boolean enabled(Level level) {
        return level != null && !level.isClientSide
            && level.getGameRules().getBoolean(ModGameRules.CORRECTION_OF_FATE);
    }

    private static int stage(Level level) {
        if (level.getServer() == null) {
            return 0;
        }
        return FateSavedData.get(level.getServer());
    }

    private static float factor(int stage) {
        return stage >= 2 ? 1.0F : (stage == 1 ? 0.5F : 0.2F);
    }

    /** 每 1 秒兜底重算一次 —— 关键:gamerule 可能在世界加载后才被写入,此时实体早已加入世界。 */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        try {
            LivingEntity living = event.getEntity();
            if (living == null || living.level().isClientSide) {
                return;
            }
            if (living.tickCount % 20 != 0 || !enabled(living.level())) {
                return;
            }
            apply(living, factor(stage(living.level())));
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        try {
            net.minecraft.world.entity.Entity e = event.getEntity();
            if (e instanceof LivingEntity && enabled(e.level())) {
                LivingEntity living = (LivingEntity) e;
                apply(living, factor(stage(living.level())));
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            net.minecraft.world.entity.Entity e = event.getEntity();
            if (e instanceof LivingEntity && enabled(e.level())) {
                LivingEntity living = (LivingEntity) e;
                apply(living, factor(stage(living.level())));
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        try {
            LivingEntity dead = event.getEntity();
            if (dead == null || dead.level().isClientSide || !enabled(dead.level())) {
                return;
            }
            int old = stage(dead.level());
            int next = old;
            if (dead instanceof WitherBoss && old < 1) {
                next = 1;
            }
            else if (dead instanceof EnderDragon && old < 2) {
                next = 2;
            }
            if (next == old) {
                return;
            }
            FateSavedData.set(dead.level().getServer(), next);
            float f = factor(next);
            for (ServerLevel level : dead.level().getServer().getAllLevels()) {
                for (Entity e : level.getEntities().getAll()) {
                    if (e instanceof LivingEntity) {
                        apply((LivingEntity) e, f);
                    }
                }
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 遍历该实体的全部属性(黑名单除外),每个属性挂一条独立乘区。 */
    private static void apply(LivingEntity entity, float factor) {
        try {
            for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
                if (instance == null || blacklisted(instance)) {
                    continue;
                }
                UUID id = UUID.nameUUIDFromBytes(
                    ("enigmaticenhance:fate:" + instance.getAttribute().getDescriptionId())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                set(instance, id, factor);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 距离类属性不参与削减:实体/方块/攻击距离、交互距离、跟随范围等(1.20.1 Forge 里是 forge:entity_reach / forge:block_reach)。 */
    private static boolean blacklisted(AttributeInstance instance) {
        try {
            String desc = instance.getAttribute().getDescriptionId().toLowerCase(java.util.Locale.ROOT);
            if (containsDistanceWord(desc)) {
                return true;
            }
            net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getKey(instance.getAttribute());
            if (key != null) {
                String path = key.getPath().toLowerCase(java.util.Locale.ROOT);
                return containsDistanceWord(path);
            }
            return false;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** reach / range / distance / interaction 四类关键词,任意命中即视为"距离类"。 */
    private static boolean containsDistanceWord(String s) {
        return s.contains("reach") || s.contains("range") || s.contains("distance") || s.contains("interaction");
    }

    private static void set(AttributeInstance attr, UUID id, float factor) {
        if (attr == null) {
            return;
        }
        // 清掉该属性上**所有** fate 修饰符,再按当前阶段挂一条,避免历史遗留叠加
        for (AttributeModifier mod : new java.util.ArrayList<>(attr.getModifiers())) {
            if (mod.getName().startsWith("enigmaticenhance:fate")) {
                attr.removeModifier(mod.getId());
            }
        }
        double amount = factor - 1.0D;
        if (Math.abs(amount) < 1.0E-4D) {
            return;
        }
        attr.addPermanentModifier(new AttributeModifier(id, "enigmaticenhance:fate",
            amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
