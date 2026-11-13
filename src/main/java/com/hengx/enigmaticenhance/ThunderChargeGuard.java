package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 4.8.2:雷击关掉以后,把场上<b>残留的电荷</b>清掉。
 *
 * <h2>为什么需要这一步</h2>
 * <p>「万钧之护卷轴」的雷击是靠目标身上一个持久 NBT 计数 {@code Electric} 驱动的:
 * 扩展的 {@code AddonEventHandler#onEntityHurt} 每次命中往目标身上加 {@code 60 + rand(80) + 伤害*10},
 * 然后 {@code AddonEventHandler#onTick} 在 {@code Electric} 涨过阈值时劈一道带 {@code HarmlessThunder}
 * 标签的雷({@code EntityType.LIGHTNING_BOLT},伤害 600)。</p>
 *
 * <p>我们关掉雷击的做法是"不再累积"(见 {@link ScrollHandler#allowLightningCharge} 与
 * {@code AddonThunderLightningMixin}),但<b>已经攒在目标身上的电荷不会自己消失</b>:
 * 玩家在开关还开着的时候打了半管,关掉开关后发现目标身上还挂着 {@code Electric},
 * 下一 tick 的 {@code onTick} 照样落雷 —— 看起来就是「关了开关还在劈」。</p>
 *
 * <p>所以这里每 20 tick 扫一遍:只要两个全局开关有一项是关的,就把所有实体的 {@code Electric}
 * 直接清掉。全项目只有扩展这一处在写这个键(已逐个 class 反编译确认),所以清掉不会影响别的功能。</p>
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThunderChargeGuard {

    /** 扩展用的电荷键名。 */
    private static final String ELECTRIC = "Electric";

    /** 扫描间隔(tick)。 */
    private static final int INTERVAL = 20;

    private ThunderChargeGuard() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) {
                return;
            }
            if (entity.tickCount % INTERVAL != 0) {
                return;
            }
            // 开关开着:一个字节都不碰,完全保持扩展原行为。
            if (ScrollHandler.lightningSwitchesOn()) {
                return;
            }
            CompoundTag tag = entity.getPersistentData();
            if (!tag.contains(ELECTRIC)) {
                return;
            }
            tag.remove(ELECTRIC);
            DebugLog.once("thunder:charge-cleared",
                "[卷轴雷击] 雷击开关已关闭,场上残留的 Electric 电荷已清除");
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯清理,失败不影响游戏
        }
    }
}
