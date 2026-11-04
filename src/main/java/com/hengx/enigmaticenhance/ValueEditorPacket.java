package com.hengx.enigmaticenhance;

import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.network.NetworkEvent;

/** 1.3.1:C2S —— 数值编辑器设置某个属性的加值。 */
public final class ValueEditorPacket {

    private final int entityId;
    private final ResourceLocation attributeId;
    private final double bonus;

    public ValueEditorPacket(int entityId, ResourceLocation attributeId, double bonus) {
        this.entityId = entityId;
        this.attributeId = attributeId;
        this.bonus = bonus;
    }

    public static void encode(ValueEditorPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeResourceLocation(msg.attributeId);
        buf.writeDouble(msg.bonus);
    }

    public static ValueEditorPacket decode(FriendlyByteBuf buf) {
        return new ValueEditorPacket(buf.readVarInt(), buf.readResourceLocation(), buf.readDouble());
    }

    public static void handle(ValueEditorPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            try {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                Entity target = player.level().getEntity(msg.entityId);
                if (!(target instanceof LivingEntity living)) {
                    return;
                }
                // 哨兵:切换 AI(非玩家生物)
                if ("enigmaticenhance:no_ai".equals(msg.attributeId.toString())) {
                    if (living instanceof net.minecraft.world.entity.Mob mob) {
                        mob.setNoAi(!mob.isNoAi());
                        DebugLog.info("[ValueEditor] {} AI -> {}", mob.getName().getString(), !mob.isNoAi());
                    }
                    return;
                }
                Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(msg.attributeId);
                if (attribute == null) {
                    return;
                }
                // 只允许改自己,或改"准星所指"的实体(防止作弊改别人)
                if (living != player && player.distanceToSqr(living) > 64.0D) {
                    return;
                }
                ValueEditorUtil.setBonus(living, attribute, msg.bonus);
            }
            catch (Throwable ignored) {
                // 忽略
            }
        });
        context.setPacketHandled(true);
    }
}