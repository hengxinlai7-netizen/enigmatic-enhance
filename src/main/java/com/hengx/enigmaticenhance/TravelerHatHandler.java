package com.hengx.enigmaticenhance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * 「旅行家帽子」(1.1.25c)的全部运行时行为。
 * <p>
 * 三个加成都**只对佩戴者本人**,用固定 UUID 的 {@link AttributeModifier} 挂在属性上,所以
 * "取下立即消失 / 重戴立即恢复"就是删/加三个修饰符,不需要每 tick 重算数值:
 * <ul>
 * <li>移动速度 {@code generic.movement_speed}:{@code MULTIPLY_TOTAL +0.5} = **+50%**(默认);
 *     配置 {@code travelerHat.speedMode="FLAT"} 时改成 {@code ADDITION +0.5}(直接加算);</li>
 * <li>生命上限 {@code generic.max_health}:{@code ADDITION +2 × 已探索群系数}(上限 maxBiomes=256);</li>
 * <li>攻击力 {@code generic.attack_damage}:{@code ADDITION +2 × 已去过维度数}(上限 maxDimensions=32)。</li>
 * </ul>
 * 数据采集:佩戴时每 20 tick 读一次当前群系/维度,新出现的追加进玩家的 {@code TravelerData}
 * (见 {@link TravelerHatData});取下后**停止记录**但数据保留(死亡重生由 {@code PlayerEvent.Clone} 复制)。
 * 所有日志走 {@link DebugLog}(默认 debugLog=false ⇒ 零输出)。
 */
public final class TravelerHatHandler {

    public static final String LOG_TAG = "[TravelerHat]";

    /** 固定 UUID:保证同一玩家身上永远只有一份帽子加成(重复添加 = 替换)。 */
    public static final UUID SPEED_UUID = UUID.fromString("b1f4c0de-2a51-4d3e-9c11-7a5e0d2b6a01");
    public static final UUID HEALTH_UUID = UUID.fromString("b1f4c0de-2a51-4d3e-9c11-7a5e0d2b6a02");
    public static final UUID ATTACK_UUID = UUID.fromString("b1f4c0de-2a51-4d3e-9c11-7a5e0d2b6a03");
    /** 给玩家补"头饰槽"用的固定 UUID(见 {@link #ensureHeadSlot})。 */
    public static final UUID HEAD_SLOT_UUID = UUID.fromString("b1f4c0de-2a51-4d3e-9c11-7a5e0d2b6a04");
    /** 1.2.13b 新效果:佩戴时**额外** +1 个头饰槽("头饰栏加一")。 */
    public static final UUID BONUS_SLOT_UUID = UUID.fromString("b1f4c0de-2a51-4d3e-9c11-7a5e0d2b6a05");

    /** 1.2.13e:哪些玩家的 head 槽是本模组用 growSlotType 补出来的(取下时要收)。 */
    private static final java.util.Set<UUID> GREW = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 最近一次通知客户端的计数(用于"只在变化时发包")。 */
    private static final Map<UUID, int[]> LAST_SENT = new ConcurrentHashMap<>();

    private TravelerHatHandler() {}

    // ------------------------------------------------------------------ 佩戴查询

    /** 玩家头饰槽(curios:head)里的旅行家帽子;没有/异常返回空堆。 */
    public static ItemStack equippedHat(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        Item hat = ModItems.travelerHatOrNull();
        if (hat == null) {
            return ItemStack.EMPTY;
        }
        try {
            return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(stack -> stack.getItem() == ModItems.travelerHatOrNull())
                    .map(SlotResult::stack)
                    .orElse(ItemStack.EMPTY))
                .orElse(ItemStack.EMPTY);
        }
        catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------ 槽位

    /**
     * 保证玩家**至少有 1 个头饰槽**(1.1.25c)。
     * <p>
     * Curios 5.14 只是把 {@code head} 定义成"可以存在的槽",玩家初始并不带格数(本整合包里
     * {@code curios-common.toml} 的 {@code slots=[]},只有救赎之戒/扩展那类模组自己给玩家加
     * ring/charm/scroll 槽)。不给格子的话帽子根本戴不上,所以这里补 1 个:
     * <ul>
     * <li>属性方式:{@code curios:head} 槽位属性 +1(固定 UUID、ADDITION、幂等;登录/tick/重生时补);</li>
     * <li>运行时兜底:如果当前的 Curios 物品栏里还没有 head 这一格(能力在补属性之前就建好了),
     *     调用 {@code growSlotType("head", 1)} 补上,保证这一局就能戴。</li>
     * </ul>
     * 只在玩家**一个头饰槽都没有**时加,已经有(别的模组给的)就不动。
     */
    public static void ensureHeadSlot(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        try {
            Attribute headSlot = top.theillusivec4.curios.api.SlotAttribute.getOrCreate("head");
            AttributeInstance instance = player.getAttribute(headSlot);
            if (instance != null && instance.getValue() < 1.0D) {
                set(player, headSlot, HEAD_SLOT_UUID, new AttributeModifier(HEAD_SLOT_UUID,
                    "enigmaticenhance:traveler_hat_head_slot", 1.0D, AttributeModifier.Operation.ADDITION));
            }
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                var existing = handler.getCurios().get("head");
                if (existing == null || existing.getSlots() < 1) {
                    try {
                        handler.growSlotType("head", 1);
                    }
                    catch (Throwable ignored) {
                        // 旧 API 在某些版本会拒绝,属性方式下一局生效
                    }
                }
            });
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 补头饰槽失败", t);
        }
    }

    // ------------------------------------------------------------------ 属性加成

    /** 把三个修饰符按当前计数刷新(幂等:数值没变就不动)。 */
    public static void applyBonuses(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        try {
            boolean percent = RingConfig.travelerHatSpeedIsPercent();
            double speed = RingConfig.travelerHatSpeedValue();
            set(player, Attributes.MOVEMENT_SPEED, SPEED_UUID, new AttributeModifier(SPEED_UUID,
                "enigmaticenhance:traveler_hat_speed", speed,
                percent ? AttributeModifier.Operation.MULTIPLY_TOTAL : AttributeModifier.Operation.ADDITION));

            double health = Math.min(TravelerHatData.healthBonus(player), RingConfig.travelerHatMaxHealthBonus());
            if (health > 0.0D) {
                set(player, Attributes.MAX_HEALTH, HEALTH_UUID, new AttributeModifier(HEALTH_UUID,
                    "enigmaticenhance:traveler_hat_health", health, AttributeModifier.Operation.ADDITION));
            }
            else {
                remove(player, Attributes.MAX_HEALTH, HEALTH_UUID);
            }

            double attack = TravelerHatData.attackBonus(player);
            if (attack > 0.0D) {
                set(player, Attributes.ATTACK_DAMAGE, ATTACK_UUID, new AttributeModifier(ATTACK_UUID,
                    "enigmaticenhance:traveler_hat_attack", attack, AttributeModifier.Operation.ADDITION));
            }
            else {
                remove(player, Attributes.ATTACK_DAMAGE, ATTACK_UUID);
            }
            applyBonusHeadSlot(player);
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 应用属性加成失败", t);
        }
    }

    /** 取下/死亡/退出:三个加成一起摘掉(幂等)。 */
    public static void removeBonuses(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        remove(player, Attributes.MOVEMENT_SPEED, SPEED_UUID);
        remove(player, Attributes.MAX_HEALTH, HEALTH_UUID);
        remove(player, Attributes.ATTACK_DAMAGE, ATTACK_UUID);
        removeBonusHeadSlot(player);
    }

    /**
     * 1.2.13b 新效果:**头饰栏加一**。
     * <p>和 {@link #ensureHeadSlot} 那个"+1 让人戴得上帽子"的无条件底色不是一回事:
     * 这个只在戴着旅行家帽时额外再 +1,于是可以同时再戴一件别的头饰;
     * 取下帽子立刻收回(如果那一格上放着东西,Curios 会照它自己的规则处理)。</p>
     */
    public static void applyBonusHeadSlot(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        try {
            Attribute headSlot = top.theillusivec4.curios.api.SlotAttribute.getOrCreate("head");
            set(player, headSlot, BONUS_SLOT_UUID, new AttributeModifier(BONUS_SLOT_UUID,
                "enigmaticenhance:traveler_hat_bonus_head_slot", 1.0D, AttributeModifier.Operation.ADDITION));
            // 1.2.13e:Curios 5.14 的槽位数不会每次属性变化都重建,这里再走一次运行时兜底:
            // 只要当前 head 槽不到 2 格,就直接 growSlotType 补 1 格(记在 GREW 里,取下时收回)。
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                var head = handler.getCurios().get("head");
                int slots = head == null ? 0 : head.getSlots();
                if (slots < 2 && GREW.add(player.getUUID())) {
                    try {
                        handler.growSlotType("head", 1);
                        var after = handler.getCurios().get("head");
                        DebugLog.once("hat:slot:" + player.getUUID(),
                            "[TravelerHat] 头饰栏 +1: {} -> {}", slots, after == null ? -1 : after.getSlots());
                    }
                    catch (Throwable ignored) {
                        GREW.remove(player.getUUID());
                    }
                }
            });
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 应用额外头饰槽失败", t);
        }
    }

    /** 取下帽子:收回额外的那一格。 */
    public static void removeBonusHeadSlot(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        try {
            Attribute headSlot = top.theillusivec4.curios.api.SlotAttribute.getOrCreate("head");
            remove(player, headSlot, BONUS_SLOT_UUID);
            if (GREW.remove(player.getUUID())) {
                CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                    try {
                        handler.shrinkSlotType("head", 1);
                    }
                    catch (Throwable ignored) {
                        // 收不回来就留着,不影响使用
                    }
                });
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 收回额外头饰槽失败", t);
        }
    }

    private static void set(Player player, Attribute attribute, UUID uuid, AttributeModifier modifier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (existing != null && existing.getAmount() == modifier.getAmount()
            && existing.getOperation() == modifier.getOperation()) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(uuid);
        }
        instance.addTransientModifier(modifier);
        DebugLog.info("{} {} 帽子加成 {} = {}", LOG_TAG, player.getName().getString(),
            attribute.getDescriptionId(), modifier.getAmount());
    }

    private static void remove(Player player, Attribute attribute, UUID uuid) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.getModifier(uuid) != null) {
            instance.removeModifier(uuid);
            DebugLog.info("{} {} 移除帽子加成 {}", LOG_TAG, player.getName().getString(),
                attribute.getDescriptionId());
        }
    }

    // ------------------------------------------------------------------ 数据采集

    /** 记录玩家**当前**所在的群系与维度;返回是否有新增。 */
    public static boolean recordCurrent(Player player) {
        if (player == null || player.level().isClientSide) {
            return false;
        }
        boolean changed = TravelerHatData.visit(player, TravelerHatData.currentBiome(player),
            TravelerHatData.currentDimension(player));
        if (changed) {
            DebugLog.info("{} {} 新探索记录:群系 {} 个,维度 {} 个", LOG_TAG, player.getName().getString(),
                TravelerHatData.biomeCount(player), TravelerHatData.dimensionCount(player));
        }
        return changed;
    }

    /** 佩戴时每 tick 调用(由物品 curioTick 触发):每 20 tick 记一次,并刷新加成/同步。 */
    public static void onWornTick(Player player, ItemStack hat) {
        if (player == null || player.level().isClientSide || hat == null || hat.isEmpty()) {
            return;
        }
        try {
            if (player.tickCount % 20 == 0) {
                recordCurrent(player);
            }
            applyBonuses(player);
            syncIfChanged(player);
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 佩戴结算异常", t);
        }
    }

    // ------------------------------------------------------------------ 客户端同步

    /** 计数变化才发包(登录/换维度/新群系都会触发)。 */
    public static void syncIfChanged(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int biomes = TravelerHatData.biomeCount(player);
        int dimensions = TravelerHatData.dimensionCount(player);
        int[] last = LAST_SENT.get(serverPlayer.getUUID());
        if (last != null && last[0] == biomes && last[1] == dimensions) {
            return;
        }
        sendCounts(serverPlayer, biomes, dimensions);
    }

    private static void sendCounts(ServerPlayer player, int biomes, int dimensions) {
        LAST_SENT.put(player.getUUID(), new int[] { biomes, dimensions });
        try {
            EnigmaticNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                EnigmaticNetwork.TravelerDataPacket.of(biomes, dimensions));
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 发送探索数据失败", t);
        }
    }

    /** 自检:最近一次发给该玩家的计数(null = 还没发过)。 */
    public static int[] lastSent(Player player) {
        return player == null ? null : LAST_SENT.get(player.getUUID());
    }

    // ------------------------------------------------------------------ 事件

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        try {
            ensureHeadSlot(player);
            if (equippedHat(player).isEmpty()) {
                // 兜底:没戴帽子就一定没有这三个加成(别人/别的模组加的修饰符不碰,只删我们自己的 UUID)
                removeBonuses(player);
                return;
            }
            if (player.tickCount % 20 == 0) {
                recordCurrent(player);
            }
            applyBonuses(player);
            syncIfChanged(player);
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " tick 结算异常", t);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        LAST_SENT.remove(player.getUUID());
        ensureHeadSlot(player);
        if (!equippedHat(player).isEmpty()) {
            applyBonuses(player);
            syncIfChanged(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        removeBonuses(player);
        LAST_SENT.remove(player.getUUID());
        TravelerHatSync.reset();
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        try {
            if (!equippedHat(player).isEmpty()) {
                recordCurrent(player);
                applyBonuses(player);
            }
            LAST_SENT.remove(player.getUUID());
            syncIfChanged(player);
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 换维度结算异常", t);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            removeBonuses(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player created = event.getEntity();
        if (original == null || created == null) {
            return;
        }
        TravelerHatData.copy(original, created);
        LAST_SENT.remove(created.getUUID());
        ensureHeadSlot(created);
        DebugLog.info("{} {} 重生:已继承探索数据(群系 {} 个,维度 {} 个)", LOG_TAG,
            created.getName().getString(), TravelerHatData.biomeCount(created),
            TravelerHatData.dimensionCount(created));
    }

    /** 便捷:群系 id 列表(日志用)。 */
    public static String describe(Player player) {
        return TravelerHatData.biomeIds(player) + " / " + TravelerHatData.dimensionIds(player);
    }

    /** 便捷:当前群系 id(测试用)。 */
    public static ResourceLocation currentBiome(Player player) {
        return TravelerHatData.currentBiome(player);
    }
}
