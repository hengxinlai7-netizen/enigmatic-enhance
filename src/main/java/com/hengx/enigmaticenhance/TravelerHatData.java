package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 「旅行家帽子」(1.1.25c)的**每玩家持久数据**:存在玩家 NBT 的 {@code ForgeData} 里,
 * 由 Forge 随存档保存;死亡重生由 {@code PlayerEvent.Clone} 复制(见 {@link TravelerHatHandler})。
 * <pre>
 * TravelerData: {
 *   Biomes:     [ "minecraft:plains", "minecraft:desert", ... ]   // 戴帽时走过的群系 id
 *   Dimensions: [ "minecraft:overworld", "minecraft:the_nether" ] // 去过的维度 id
 * }
 * </pre>
 * 上限由配置 {@code travelerHat.maxBiomes}(256)/{@code maxDimensions}(32) 控制,超出不再追加
 * (不会把属性叠到离谱);重复经过同一个 id 不会重复追加;列表按加入顺序保存。
 */
public final class TravelerHatData {

    public static final String ROOT = "TravelerData";
    public static final String BIOMES = "Biomes";
    public static final String DIMENSIONS = "Dimensions";

    private TravelerHatData() {}

    private static CompoundTag root(Player player) {
        return player.getPersistentData().getCompound(ROOT);
    }

    private static List<String> read(Player player, String key) {
        List<String> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        ListTag tag = root(player).getList(key, Tag.TAG_STRING);
        for (int i = 0; i < tag.size(); i++) {
            String value = tag.getString(i);
            if (!value.isEmpty() && !out.contains(value)) {
                out.add(value);
            }
        }
        return out;
    }

    private static void write(Player player, String key, List<String> values) {
        CompoundTag root = root(player);
        ListTag tag = new ListTag();
        for (String value : values) {
            tag.add(StringTag.valueOf(value));
        }
        root.put(key, tag);
        player.getPersistentData().put(ROOT, root);
    }

    public static List<String> biomeIds(Player player) {
        return read(player, BIOMES);
    }

    public static List<String> dimensionIds(Player player) {
        return read(player, DIMENSIONS);
    }

    public static int biomeCount(Player player) {
        return biomeIds(player).size();
    }

    public static int dimensionCount(Player player) {
        return dimensionIds(player).size();
    }

    /** 追加一个群系(已存在 / 超出上限返回 false)。 */
    public static boolean addBiome(Player player, ResourceLocation biome) {
        return add(player, BIOMES, biome, RingConfig.travelerHatMaxBiomes());
    }

    /** 追加一个维度(已存在 / 超出上限返回 false)。 */
    public static boolean addDimension(Player player, ResourceLocation dimension) {
        return add(player, DIMENSIONS, dimension, RingConfig.travelerHatMaxDimensions());
    }

    private static boolean add(Player player, String key, ResourceLocation id, int cap) {
        if (player == null || id == null) {
            return false;
        }
        List<String> values = read(player, key);
        if (values.size() >= Math.max(0, cap)) {
            return false;
        }
        String text = id.toString();
        if (values.contains(text)) {
            return false;
        }
        values.add(text);
        write(player, key, values);
        return true;
    }

    /** 一次记一对(自检用;任一为 null 就跳过那一项)。返回是否有新增。 */
    public static boolean visit(Player player, ResourceLocation biome, ResourceLocation dimension) {
        boolean changed = false;
        if (biome != null) {
            changed |= addBiome(player, biome);
        }
        if (dimension != null) {
            changed |= addDimension(player, dimension);
        }
        return changed;
    }

    /** 当前所在群系 id(取不到返回 null)。 */
    public static ResourceLocation currentBiome(Player player) {
        if (player == null || player.level() == null) {
            return null;
        }
        var holder = player.level().getBiome(player.blockPosition());
        return holder.unwrapKey().map(key -> key.location()).orElse(null);
    }

    /** 当前所在维度 id。 */
    public static ResourceLocation currentDimension(Player player) {
        return player == null || player.level() == null ? null : player.level().dimension().location();
    }

    /** 死亡重生:把旧角色的数据整体搬到新角色(覆盖式、幂等)。 */
    public static void copy(Player from, Player to) {
        if (from == null || to == null) {
            return;
        }
        CompoundTag source = from.getPersistentData().getCompound(ROOT);
        if (source.isEmpty()) {
            return;
        }
        to.getPersistentData().put(ROOT, source.copy());
    }

    /** 该玩家是否戴着旅行家帽子(饰品栏 curios:head)。 */
    public static boolean isWearing(Player player) {
        return !TravelerHatHandler.equippedHat(player).isEmpty();
    }

    /** 群系数量对应的生命上限加成。 */
    public static double healthBonus(Player player) {
        return RingConfig.travelerHatHealthPerBiome() * biomeCount(player);
    }

    /** 维度数量对应的攻击力加成。 */
    public static double attackBonus(Player player) {
        return RingConfig.travelerHatAttackPerDimension() * dimensionCount(player);
    }
}
