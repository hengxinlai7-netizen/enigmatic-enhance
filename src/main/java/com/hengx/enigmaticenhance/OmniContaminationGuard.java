package com.hengx.enigmaticenhance;

import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.6.23:阻止"生物生成时被塞入全属性(omni)"。
 *
 * <ul>
 *   <li>只处理**非玩家**实体;</li>
 *   <li>移除 {@code enigmaticenhance:omni} 上**不属于宿命的修正**的修饰符;</li>
 *   <li>宿命的修正自己挂的修饰符(UUID 前缀固定)会被保留,不影响其效果。</li>
 * </ul>
 */
public final class OmniContaminationGuard {

    private OmniContaminationGuard() {
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        try {
            net.minecraft.world.entity.Entity e = event.getEntity();
            if (!(e instanceof LivingEntity) || e.level().isClientSide) {
                return;
            }
            LivingEntity living = (LivingEntity) e;
            if (e instanceof Player) {
                return;
            }
            if (living.tickCount % 40 != 0) {
                return;
            }
            if (!OmniAttribute.OMNI.isPresent()) {
                return;
            }
            AttributeInstance omni = living.getAttribute(OmniAttribute.OMNI.get());
            if (omni == null) {
                return;
            }
            // 4.6.0(内存):只在真的发现了"要删的修饰符"时才建集合。
            // 原来无条件 new ArrayList(每个非玩家生物每 40 tick 一个),而绝大多数生物身上
            // 压根没有可删的修饰符 —— 那些 ArrayList 全是纯垃圾。
            java.util.List<UUID> remove = null;
            java.util.Collection<net.minecraft.world.entity.ai.attributes.AttributeModifier> mods = omni.getModifiers();
            for (net.minecraft.world.entity.ai.attributes.AttributeModifier mod : mods) {
                if (!mod.getName().startsWith("enigmaticenhance:fate")) {
                    if (remove == null) {
                        remove = new java.util.ArrayList<>(2);
                    }
                    remove.add(mod.getId());
                }
            }
            if (remove != null) {
                for (UUID id : remove) {
                    omni.removeModifier(id);
                }
            }
            // 基础值也不允许被抬高
            if (omni.getBaseValue() != 0.0D) {
                omni.setBaseValue(0.0D);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}