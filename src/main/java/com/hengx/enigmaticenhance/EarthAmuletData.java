package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * 「大地护符」(1.1.24)的 NBT 结构与只读查询。
 * <p>
 * NBT 结构(全部挂在物品根 tag 下,缺省 = 没有):
 * <pre>
 * {
 *   PotionEffects: [ { id: "minecraft:strength", lvl: 2 } ],  // 只记录 id + 等级,不记录时长;只保留最新一次合成
 *   Heads: [ "minecraft:zombie_head", "minecraft:dragon_head" ], // 已解锁的头颅能力,可叠加
 *   Empowered: 1b                                             // 下界之星强化(免疫亡灵生物造成的伤害)
 * }
 * </pre>
 * 说明:
 * <ul>
 * <li>{@code lvl} 是**人读等级**(1 = I 级),对应 {@link MobEffectInstance#getAmplifier()} + 1;</li>
 * <li>药水效果 id 存字符串而不是数字 id —— 数字 id 会随注册顺序变化,存档会错乱;</li>
 * <li>头颅 id 同样存字符串( {@code minecraft:zombie_head} ),配方/事件都按注册表 id 判定。</li>
 * </ul>
 * 本类只做数据读写与"佩戴查询",任何异常(例如没装 Curios)都吞掉并返回"没有"。
 */
public final class EarthAmuletData {

    /** 药水效果列表。 */
    public static final String TAG_POTION_EFFECTS = "PotionEffects";
    /** 已解锁头颅列表。 */
    public static final String TAG_HEADS = "Heads";
    /** 下界之星强化标记。 */
    public static final String TAG_EMPOWERED = "Empowered";
    /** 单条药水效果的字段名。 */
    public static final String KEY_ID = "id";
    /** 单条药水效果的等级字段名。 */
    public static final String KEY_LVL = "lvl";

    public static final String HEAD_ZOMBIE = "minecraft:zombie_head";
    public static final String HEAD_SKELETON = "minecraft:skeleton_skull";
    public static final String HEAD_CREEPER = "minecraft:creeper_head";
    public static final String HEAD_WITHER_SKELETON = "minecraft:wither_skeleton_skull";
    public static final String HEAD_PIGLIN = "minecraft:piglin_head";
    public static final String HEAD_DRAGON = "minecraft:dragon_head";

    /** 允许注入的 6 种头颅(顺序与 tooltip 一致)。 */
    public static final List<String> HEADS = List.of(
        HEAD_ZOMBIE, HEAD_SKELETON, HEAD_CREEPER, HEAD_WITHER_SKELETON, HEAD_PIGLIN, HEAD_DRAGON);

    private EarthAmuletData() {}

    // ------------------------------------------------------------------ 物品判定

    /** 这个堆是不是大地护符(物品实例比对,不看 NBT)。 */
    public static boolean isAmulet(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ModItems.earthAmuletOrNull();
    }

    /** 这个堆如果是 6 种头颅之一,返回它的注册表 id;否则返回空串。 */
    public static String headIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return "";
        }
        String key = id.toString();
        return HEADS.contains(key) ? key : "";
    }

    /** 头颅 id 对应的 tooltip 语言键 path(zombie_head / skeleton_skull / ...)。 */
    public static String headLangPath(String headId) {
        int colon = headId == null ? -1 : headId.indexOf(':');
        return colon < 0 ? "unknown" : headId.substring(colon + 1);
    }

    // ------------------------------------------------------------------ 药水效果

    /** 一条被保存的药水效果(id + 人读等级)。 */
    public record StoredEffect(String id, int level) {}

    /** 读出保存的药水效果(没有则空表,永远不返回 null)。 */
    public static List<StoredEffect> effects(ItemStack stack) {
        List<StoredEffect> out = new ArrayList<>();
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return out;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return out;
        }
        ListTag list = tag.getList(TAG_POTION_EFFECTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString(KEY_ID);
            if (id.isEmpty()) {
                continue;
            }
            int lvl = entry.getInt(KEY_LVL);
            out.add(new StoredEffect(id, Math.max(1, lvl)));
        }
        return out;
    }

    /** 覆盖写入药水效果列表(只保留最新一次合成结果)。 */
    /**
     * 4.3.8:把护符上【已经记录】的非法效果清掉。
     *
     * <p>黑名单是后加的,老存档里的护符可能早就把抗性/金心之类记进去了。
     * 这里在每次应用前扫一遍,发现内置黑名单、或在配置黑名单里、或是被禁的瞬时效果,
     * 直接从记录里删掉并写回 NBT。</p>
     *
     * @return 清理掉的条数(0 = 没变)
     */
    public static int sanitize(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) {
                return 0;
            }
            List<StoredEffect> stored = effects(stack);
            if (stored.isEmpty()) {
                return 0;
            }
            List<StoredEffect> keep = new ArrayList<>(stored.size());
            for (StoredEffect e : stored) {
                if (e == null || e.id() == null) {
                    continue;
                }
                String id = e.id().toLowerCase(java.util.Locale.ROOT);
                if (ItemConfig.isEffectBlacklisted(id)) {
                    continue;   // 内置 + 配置黑名单(内置白名单逻辑在 isEffectBlacklisted 里)
                }
                if (!ItemConfig.earthAmuletAllowInstantEffects()) {
                    var effect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                        .getValue(net.minecraft.resources.ResourceLocation.tryParse(e.id()));
                    if (effect != null && effect.isInstantenous()) {
                        continue;
                    }
                }
                keep.add(e);
            }
            if (keep.size() == stored.size()) {
                return 0;
            }
            setEffects(stack, keep);
            int removed = stored.size() - keep.size();
            DebugLog.warn("[EarthAmulet] 清理了 {} 条已记录的非法效果", removed);
            return removed;
        }
        catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public static void setEffects(ItemStack stack, List<StoredEffect> effects) {
        // 2.5.0:一个护符最多记多少条(0 = 无上限)
        int maxItems = ItemConfig.earthAmuletMaxCapturedItems();
        if (maxItems > 0 && effects != null && effects.size() > maxItems) {
            effects = new ArrayList<>(effects.subList(0, maxItems));
        }
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = new ListTag();
        for (StoredEffect effect : effects) {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_ID, effect.id());
            entry.putInt(KEY_LVL, Math.max(1, effect.level()));
            list.add(entry);
        }
        if (list.isEmpty()) {
            tag.remove(TAG_POTION_EFFECTS);
        }
        else {
            tag.put(TAG_POTION_EFFECTS, list);
        }
    }

    /** 从一件"带 buff 的物品"里读出全部效果:原版药水 NBT + 神秘遗物的 AdvancedPotion。 */
    public static List<MobEffectInstance> allEffects(ItemStack stack) {
        List<MobEffectInstance> out = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return out;
        }
        try {
            out.addAll(PotionUtils.getMobEffects(stack));
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
        // 神秘遗物系(AdvancedPotion,如 enigmaticaddons:common_potion 冻结之心药水)
        try {
            Class<?> helper = Class.forName("com.aizistral.enigmaticlegacy.helpers.PotionHelper");
            java.lang.reflect.Method m = helper.getMethod("getEffects", ItemStack.class);
            Object r = m.invoke(null, stack);
            if (r instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof MobEffectInstance inst && !out.contains(inst)) {
                        out.add(inst);
                    }
                }
            }
        }
        catch (Throwable ignored) {
            // 没有神秘遗物就跳过
        }
        // 神秘遗物扩展(enigmaticaddons)自己的药水读取:common_potion / ultimate_potion 用的是它自己的 AdvancedPotion 格式
        if (out.isEmpty()) {
            try {
                Class<?> helper = Class.forName("auviotre.enigmatic.addon.helpers.PotionAddonHelper");
                java.lang.reflect.Method m = helper.getMethod("getEffects", ItemStack.class);
                Object r = m.invoke(null, stack);
                if (r instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof MobEffectInstance inst && !out.contains(inst)) {
                            out.add(inst);
                        }
                    }
                }
            }
            catch (Throwable ignored) {
                // 没有扩展就跳过
            }
        }
        if (RingConfig.debugLog()) {
            DebugLog.info("[灌注] 读取:{} -> {} 个效果",
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()), out.size());
        }
                // 2.4.2:铁魔法(Iron's Spells)的精灵药 / 特殊药水 —— 不是原版 PotionContents,
        // 用反射从物品类里把效果读出来,这样大地护符也能灌注它们。
        try {
            IronElixirCompat.addEffects(stack, out);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 铁魔法不在 / 结构变了 → 当作没有效果
        }
        // 2.5.2:buff 黑名单 —— 配置里点名的效果一律不记录
        var blacklist = ItemConfig.earthAmuletBlacklistedEffects();
        if (!blacklist.isEmpty()) {
            int before = out.size();
            out.removeIf(inst -> {
                try {
                    var key = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                        .getKey(inst.getEffect());
                    return key != null && blacklist.contains(
                        key.toString().toLowerCase(java.util.Locale.ROOT));
                }
                catch (RuntimeException | LinkageError e) {
                    return false;
                }
            });
            if (out.size() != before) {
                DebugLog.once("amulet:blacklist",
                    "[EarthAmulet] buff 黑名单挡掉 {} 个效果", before - out.size());
            }
        }
        // 2.5.0:一瓶药水最多记多少种(0 = 无上限)
        // 4.3.7:默认只记 1 种。开启 unlimitedPotionTypes 才恢复"有多少记多少"。
        if (!ItemConfig.earthAmuletUnlimitedPotionTypes()) {
            int maxTypes = ItemConfig.earthAmuletMaxPotionTypes();
            if (maxTypes <= 0) {
                maxTypes = 1;   // 旧配置里是 0(旧默认 = 无上限),按 1 处理
            }
            if (out.size() > maxTypes) {
                out = new ArrayList<>(out.subList(0, maxTypes));
            }
        }
return out;
    }

    /** 从一件"带 buff 的物品"里读出"全部效果"(基础效果 + 自定义效果),只保留 id 与等级,丢弃时长。 */
    public static List<StoredEffect> storedEffects(ItemStack potion) {
        List<StoredEffect> out = new ArrayList<>();
        if (potion == null || potion.isEmpty()) {
            return out;
        }
        for (MobEffectInstance instance : allEffects(potion)) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect());
            if (id == null) {
                continue;
            }
            // 4.3.0:瞬时效果按配置过滤;等级按 maxEffectLevel 夹紧(0 = 无上限)
            if (!ItemConfig.earthAmuletAllowInstantEffects()
                && instance.getEffect().isInstantenous()) {
                continue;
            }
            int level = instance.getAmplifier() + 1;
            int maxLevel = ItemConfig.earthAmuletMaxEffectLevel();
            if (maxLevel > 0 && level > maxLevel) {
                level = maxLevel;
            }
            out.add(new StoredEffect(id.toString(), level));
        }
        return out;
    }

    // ------------------------------------------------------------------ 头颅

    /** 已解锁头颅 id 列表(没有则空表)。 */
    public static List<String> heads(ItemStack stack) {        List<String> out = new ArrayList<>();
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return out;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return out;
        }
        ListTag list = tag.getList(TAG_HEADS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getString(i);
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    public static boolean hasHead(ItemStack stack, String headId) {
        return heads(stack).contains(headId);
    }

    /**
     * 4.6.1(帧率):"有没有记录过药水效果"的<b>零分配</b>版本(不建 List、不建 StoredEffect)。
     *
     * <p>大地护符的两个物品模型谓词({@code amulet_state} / {@code amulet_head})和染色回调
     * {@link EarthAmuletTint#colorFor} 都是<b>每次渲染前</b>被调用的({@code ItemProperties} /
     * {@code ItemColor});它们只需要"空/非空",却各自调用了一次 {@link #effects(ItemStack)} ——
     * 每次都要 {@code new ArrayList} 并给每条记录建一个 {@link StoredEffect} record。
     * 护符在背包/手上/物品展示框里时会持续产生这些短期垃圾(60~200 帧/秒 × 多个实例)。</p>
     *
     * <p>判定与 {@code !effects(stack).isEmpty()} 严格等价:读取同一个
     * {@value #TAG_POTION_EFFECTS} 列表,只看"有没有一条 id 非空"。</p>
     */
    public static boolean hasEffects(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_POTION_EFFECTS, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = tag.getList(TAG_POTION_EFFECTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (!list.getCompound(i).getString(KEY_ID).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 4.6.1(帧率):"有没有解锁过头颅"的零分配版本(判定与 {@code !heads(stack).isEmpty()} 等价)。 */
    public static boolean hasHeads(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_HEADS, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = tag.getList(TAG_HEADS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (!list.getString(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 返回"加了这颗头颅"的副本(输入堆不动)。 */
    public static ItemStack withHead(ItemStack amulet, String headId) {
        ItemStack out = amulet.copy();
        out.setCount(1);
        if (!hasHead(out, headId)) {
            ListTag list = new ListTag();
            for (String existing : heads(out)) {
                list.add(net.minecraft.nbt.StringTag.valueOf(existing));
            }
            list.add(net.minecraft.nbt.StringTag.valueOf(headId));
            out.getOrCreateTag().put(TAG_HEADS, list);
        }
        return out;
    }

    // ------------------------------------------------------------------ 强化

    public static boolean isEmpowered(ItemStack stack) {
        return stack != null && stack.hasTag() && stack.getTag() != null && stack.getTag().getBoolean(TAG_EMPOWERED);
    }

    /** 返回"已强化"的副本(输入堆不动)。 */
    public static ItemStack empowered(ItemStack amulet) {
        ItemStack out = amulet.copy();
        out.setCount(1);
        out.getOrCreateTag().putBoolean(TAG_EMPOWERED, true);
        return out;
    }

    /** 强化前置:必须已经注入**凋灵骷髅头**(用户 1.1.24 明确要求)。 */
    public static boolean canBeEmpowered(ItemStack amulet) {
        return isAmulet(amulet) && hasHead(amulet, HEAD_WITHER_SKELETON) && !isEmpowered(amulet);
    }

    // ------------------------------------------------------------------ 佩戴查询

    /** 玩家饰品栏(curios:charm)里的大地护符;没有/没装 Curios/出错都返回空堆。 */
    public static ItemStack equippedAmulet(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        try {
            // 4.6.2(内存):原来走 .map(...).orElse(...) —— LazyOptional#map 每次都要新建一个
            // LazyOptional + 一个 lambda 实例(这条路径被伤害结算/效果判定/每帧 tooltip 调用)。
            // orElse(null) 直接取内部引用(没有就是 null),不分配;内层 Optional 也一次取空。
            var handler = CuriosApi.getCuriosInventory(player).orElse(null);
            if (handler == null) {
                return ItemStack.EMPTY;
            }
            SlotResult found = handler.findFirstCurio(EarthAmuletData::isAmulet).orElse(null);
            return found == null ? ItemStack.EMPTY : found.stack();
        }
        catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 是否戴着"含某个头颅能力"的护符。 */
    public static boolean equippedWithHead(Player player, String headId) {
        ItemStack amulet = equippedAmulet(player);
        return !amulet.isEmpty() && hasHead(amulet, headId);
    }

    /** 头颅 id 对应的物品(缺失返回 null)。 */
    public static Item headItem(String headId) {
        ResourceLocation id = ResourceLocation.tryParse(headId);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? null : item;
    }

    /** 药水效果 id 对应的效果(缺失返回 null)。 */
    public static MobEffect effect(String effectId) {
        ResourceLocation id = ResourceLocation.tryParse(effectId);
        if (id == null || !ForgeRegistries.MOB_EFFECTS.containsKey(id)) {
            return null;
        }
        return ForgeRegistries.MOB_EFFECTS.getValue(id);
    }
}
