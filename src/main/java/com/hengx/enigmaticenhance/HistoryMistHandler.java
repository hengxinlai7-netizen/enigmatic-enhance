package com.hengx.enigmaticenhance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「瓶装历史迷雾」({@code enigmaticenhance:history_mist}) 的两件事:
 *
 * <ol>
 *   <li><b>转化</b>:{@code 大地之瓶}({@code earth_bottle})在玩家背包里累计 {@value #TRANSFORM_TICKS} tick
 *       (0.5 小时 = 30 分钟)后,原地变成瓶装历史迷雾。计时存在物品 NBT 的 {@code MistTicks} 里,
 *       只在主物品栏(0..35)与副手(40)里推进 —— 放进箱子/背包模组的槽位不会计时。</li>
 *   <li><b>记录与生效</b>:右键一个活体实体,把它<b>所有已有的数值属性</b>按
 *       {@code min(目标该属性 × 0.1, 玩家自身该属性 × 0.5)} 记进瓶子 NBT
 *       ({@code HistoryRecords});之后只要这个瓶子在背包里,就用这些值给玩家挂属性修饰符
 *       (临时修饰符,每秒同步一次,丢了瓶子就没了)。每个瓶子可以反复记录,但<b>只保留最新一次的生物</b>。</li>
 * </ol>
 *
 * <p>为什么用"携带生效"而不是"一次性永久加成":属性修饰符跨死亡/重登要自己持久化,
 * 而瓶子本身是可掉落的物品 —— 让加成跟着物品走,语义最干净,也免得玩家一死就得重来。
 * 修饰符名固定为 {@value #MODIFIER_NAME},每秒先摘掉自己挂的全部再按 NBT 重挂,天然幂等。</p>
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HistoryMistHandler {

    /** 0.5 小时 = 30 分钟 = 1800 秒 = 36000 tick。 */
    public static final int TRANSFORM_TICKS = 0.5 * 3600 * 20 > 0 ? 36000 : 36000;

    /** 记录比例:目标属性的 10%。 */
    public static final double RECORD_RATIO = 0.1D;

    /** 上限比例:不超过玩家自身该属性的 50%。 */
    public static final double CAP_RATIO = 0.5D;

    /** 转化计时的 NBT 键(累计 tick)。 */
    public static final String TIMER_KEY = "MistTicks";

    /** 已记录属性列表的 NBT 键:{@code [{Attribute:"id", Amount:1.2d}, ...]}。 */
    public static final String RECORDS_KEY = "HistoryRecords";

    /** 是否已经记录过(仅作 UI 提示用;记录本身可重复,每次覆盖上一次)。 */
    public static final String USED_KEY = "HistoryUsed";

    /** 我们挂的修饰符的固定名字(用来识别/清理)。 */
    public static final String MODIFIER_NAME = "enigmaticenhance:history_mist";


    /** 本物品 id(书袋里也认,见 {@link #findRecordedMist})。 */
    public static final ResourceLocation HISTORY_MIST_ID = new ResourceLocation("enigmaticenhance", "history_mist");
    private HistoryMistHandler() {
    }

    // ------------------------------------------------------------------ 每 20 tick 做一次

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        try {
            tickTransformation(player);
            syncModifiers(player);
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[HistoryMist] tick 异常", ex);
        }
    }

    /** 下线时把自己挂的修饰符摘掉(下次上线会按瓶子 NBT 重挂)。 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            clearModifiers(event.getEntity());
        }
    }

    // ------------------------------------------------------------------ 转化

    /** 推进背包里所有 大地之瓶 的计时,到点原地换成 瓶装历史迷雾。 */
    public static void tickTransformation(Player player) {
        Item bottle = ModItems.earthBottleOrNull();
        Item mist = ModItems.historyMistOrNull();
        if (bottle == null || mist == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot >= 36 && slot != 40) {
                continue; // 只认主物品栏 0..35 和副手 40,护甲槽不参与
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != bottle) {
                continue;
            }
            int ticks = stack.getOrCreateTag().getInt(TIMER_KEY) + 20;
            if (ticks >= TRANSFORM_TICKS) {
                ItemStack converted = new ItemStack(mist, Math.max(1, stack.getCount()));
                inventory.setItem(slot, converted);
                DebugLog.info("[HistoryMist] {} 的大地之瓶在槽位 {} 转化为瓶装历史迷雾", player.getName().getString(), slot);
            }
            else {
                stack.getOrCreateTag().putInt(TIMER_KEY, ticks);
            }
        }
    }

    // ------------------------------------------------------------------ 记录

    /** 右键实体时调用:把目标的所有数值属性按比例记进瓶子(覆盖上一次的记录)。返回是否记录成功。 */
    public static boolean record(Player player, LivingEntity target, ItemStack stack) {
        if (player == null || target == null || stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag();
        Map<ResourceLocation, Double> recorded = computeRecords(player, target);
        if (recorded.isEmpty()) {
            // 2.7.8:记录失败时打诊断 —— 之前这里静默 return,完全查不出原因。
            if (RingConfig.debugLog()) {
                int targetAttrs = 0;
                int shared = 0;
                int zero = 0;
                for (Attribute attribute : ForgeRegistries.ATTRIBUTES) {
                    AttributeInstance instance = target.getAttribute(attribute);
                    if (instance == null) {
                        continue;
                    }
                    targetAttrs++;
                    if (!player.getAttributes().hasAttribute(attribute)) {
                        continue;
                    }
                    shared++;
                    if (recordedAmount(instance.getValue(), player.getAttributeValue(attribute)) <= 0.0D) {
                        zero++;
                    }
                }
                DebugLog.warn("[HistoryMist] 记录 {} 失败:目标属性 {} 条,玩家共有 {} 条,算出<=0 {} 条,玩家属性总数 {}",
                    target.getName().getString(), targetAttrs, shared, zero,
                    player.getAttributes().getSyncableAttributes().size());
            }
            return false;
        }
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, Double> entry : recorded.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("Attribute", entry.getKey().toString());
            one.putDouble("Amount", entry.getValue());
            list.add(one);
        }
        tag.put(RECORDS_KEY, list);
        tag.putBoolean(USED_KEY, true);
        DebugLog.info("[HistoryMist] {} 记录了 {} 的 {} 项属性", player.getName().getString(),
            target.getName().getString(), recorded.size());
        if (player instanceof ServerPlayer serverPlayer) {
            syncModifiers(serverPlayer);
        }
        return true;
    }

    /**
     * 记录表:{@code min(目标 × 0.1, 自身 × 0.5)},跳过目标没有值(≤ 0)或自身为 0 的属性。
     * 纯函数(不碰物品),自检可直接断言。
     */
    /**
     * 两重限制的纯函数:记录值 = min(目标值 × 0.1, 自身值 × 0.5),负数按 0 处理。
     * 自检直接断言这两个限制各自生效的情形。
     */
    public static double recordedAmount(double targetValue, double ownValue) {
        if (targetValue <= 0.0D || ownValue <= 0.0D) {
            return 0.0D;
        }
        return Math.min(targetValue * RECORD_RATIO, ownValue * CAP_RATIO);
    }


    public static Map<ResourceLocation, Double> computeRecords(Player player, LivingEntity target) {
        Map<ResourceLocation, Double> out = new LinkedHashMap<>();
        if (player == null || target == null) {
            return out;
        }
        // 注意:不能用 target.getAttributes().getSyncableAttributes() —— 实测生物(牛)返回 0 项,
        // 玩家的 AttributeMap 也只报 4 项。遍历属性注册表 + getAttribute(attr) 才是"实体真正拥有的属性"。
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            if (id == null) {
                continue;
            }
            double targetValue = instance.getValue();
            if (!player.getAttributes().hasAttribute(attribute)) {
                continue;
            }
            double ownValue = player.getAttributeValue(attribute);
            double amount = recordedAmount(targetValue, ownValue);
            if (amount <= 0.0D) {
                continue;
            }
            out.put(id, amount);
        }
        return out;
    }

    /** 读瓶子上的记录表(属性 id → 数值)。 */
    public static Map<ResourceLocation, Double> readRecords(ItemStack stack) {
        Map<ResourceLocation, Double> out = new LinkedHashMap<>();
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return out;
        }
        ListTag list = stack.getTag().getList(RECORDS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(one.getString("Attribute"));
            if (id != null) {
                out.put(id, one.getDouble("Amount"));
            }
        }
        return out;
    }

    /**
     * 已记录的瓶装历史迷雾:先找背包/副手,再找古旧书袋(1.2.5d:瓶子可以放进书袋,袋里同样生效)。
     * 找不到返回 null。
     */
    @Nullable
    public static ItemStack findRecordedMist(Player player) {
        Item mist = ModItems.historyMistOrNull();
        if (mist == null || player == null) {
            return null;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot >= 36 && slot != 40) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == mist && !readRecords(stack).isEmpty()) {
                return stack;
            }
        }
        // 书袋(需要 enigmaticaddons;缺失时 BookBag 安全返回空)
        ItemStack inBag = BookBag.findBook(player, HISTORY_MIST_ID);
        if (!inBag.isEmpty() && !readRecords(inBag).isEmpty()) {
            return inBag;
        }
        return null;
    }

    // ------------------------------------------------------------------ 属性同步

    /** 先摘掉自己挂的全部修饰符,再按当前携带的瓶子重挂(幂等,丢了瓶子效果就消失)。 */
    public static void syncModifiers(Player player) {
        clearModifiers(player);
        ItemStack mist = findRecordedMist(player);
        if (mist == null) {
            return;
        }
        for (Map.Entry<ResourceLocation, Double> entry : readRecords(mist).entrySet()) {
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(entry.getKey());
            if (attribute == null) {
                continue;
            }
            if (!player.getAttributes().hasAttribute(attribute)) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.addTransientModifier(new AttributeModifier(modifierId(entry.getKey()), MODIFIER_NAME,
                mistModifierValue(entry.getKey(), entry.getValue()), AttributeModifier.Operation.ADDITION));
        }
    }
    /** 1.2.10a:生命上限的记录值封顶(默认 40 = 20 心),其它属性原样。 */
    static double mistModifierValue(ResourceLocation attributeId, double value) {
        if (attributeId != null && "minecraft:generic.max_health".equals(attributeId.toString())) {
            return Math.min(value, 40.0D);
        }
        return value;
    }



    /** 摘掉所有名字为 {@value #MODIFIER_NAME} 的修饰符。 */
    public static void clearModifiers(Player player) {
        if (player == null) {
            return;
        }
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES) {
            if (!player.getAttributes().hasAttribute(attribute)) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            List<UUID> mine = new ArrayList<>();
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (MODIFIER_NAME.equals(modifier.getName())) {
                    mine.add(modifier.getId());
                }
            }
            for (UUID id : mine) {
                instance.removeModifier(id);
            }
        }
    }

    /** 每个属性一个稳定 UUID(同一属性不会叠出两条)。 */
    public static UUID modifierId(ResourceLocation attributeId) {
        return UUID.nameUUIDFromBytes((MODIFIER_NAME + ":" + attributeId).getBytes(StandardCharsets.UTF_8));
    }
}
