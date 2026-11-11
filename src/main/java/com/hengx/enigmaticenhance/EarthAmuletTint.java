package com.hengx.enigmaticenhance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.1.28j:大地护符的**动态染色**(核心层颜色 = 注入药水效果的颜色)。
 * <p>
 * 模型是两层 {@code item/generated}:
 * <pre>
 *   layer0 / tintindex 0 = 金色边框(按头颅类型换贴图,不染色)
 *   layer1 / tintindex 1 = 核心层(白色圆盘贴图,用 ItemColor 染色)
 * </pre>
 * 所以这里只有两个颜色:边框永远返回白色(保持贴图原色),核心返回"第一条保存效果的颜色";
 * 没有保存任何效果时返回白色 —— 白色 × 白色核心贴图 = **无色空芯**,也就是用户要的"无主护身符"常态。
 * <p>
 * 判定逻辑放在 common 里(不在客户端),这样专用服务端的自检可以直接断言返回值;
 * 客户端只负责把 {@link #colorFor} 包成 {@code ItemColor} 注册进去
 * (见 {@code client.EarthAmuletItemProperties})。
 */
public final class EarthAmuletTint {

    /** 不染色(保持贴图原色)。 */
    public static final int WHITE = 0xFFFFFF;
    /** 边框层 / 核心层的 tintindex(与 item/generated 的 layer 序号一致)。 */
    public static final int FRAME_LAYER = 0;
    public static final int CORE_LAYER = 1;

    private EarthAmuletTint() {}

    /**
     * 给 {@code ItemColor} 用:只对大地护符生效(tintindex 1 才染色),其它物品/层一律白色。
     */
    public static int colorFor(ItemStack stack, int tintIndex) {
        if (tintIndex != CORE_LAYER || !EarthAmuletData.isAmulet(stack)) {
            return WHITE;
        }
        return coreColor(stack);
    }

    /**
     * 核心层颜色:取**第一条**保存的药水效果的颜色({@code MobEffect#getColor()});
     * 没有效果、或者 id 已经不存在(整合包被拆)⇒ 白色(显示为无色空芯)。
     *
     * <p>4.6.3(帧率):本方法是 {@code ItemColor} 回调,<b>每帧 × 每层 × 每个在渲染的护符</b>都要跑
     * (背包/手上/物品展示框/创造物品栏都会走)。原来每次都:</p>
     * <ol>
     *   <li>调 {@code EarthAmuletData.effects(stack)} —— new 一个 ArrayList + 每条效果 new 一个 record;</li>
     *   <li>调 {@code effect(id)} —— {@code ResourceLocation.tryParse} <b>新建对象</b> + 注册表查找。</li>
     * </ol>
     * <p>现在直接遍历 NBT 列表(不建 List、不建 record),并把"效果 id → 颜色"缓存起来
     * ({@link #COLOR_CACHE},容量封顶 64 条,键就是 NBT 里原有的 String,不产生新对象)。
     * 判定与原来逐字一致:按顺序找<b>第一条能解析出的效果</b>,取它的颜色;一条都没有 ⇒ 白。</p>
     *
     * <p>唯一的行为差别只在"运行中把某个效果从注册表里删掉"这种极端情况:缓存会让它继续显示旧颜色
     * 直到重开游戏(纯外观、无逻辑影响)。</p>
     */
    public static int coreColor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return WHITE;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(EarthAmuletData.TAG_POTION_EFFECTS, Tag.TAG_LIST)) {
            return WHITE;
        }
        ListTag list = tag.getList(EarthAmuletData.TAG_POTION_EFFECTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getCompound(i).getString(EarthAmuletData.KEY_ID);
            if (id.isEmpty()) {
                continue;
            }
            int color = colorOf(id);
            if (color >= 0) {
                return color;
            }
        }
        return WHITE;
    }

    /** 效果 id → 核心层颜色(解析不到 = -1)。热路径上只是一次 HashMap 查找。 */
    private static final java.util.HashMap<String, Integer> COLOR_CACHE = new java.util.HashMap<>();

    private static int colorOf(String id) {
        Integer cached = COLOR_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        int color = -1;
        MobEffect effect = effect(id);
        if (effect != null) {
            color = effect.getColor() & 0xFFFFFF;
        }
        // 只缓存有限条:效果 id 是封闭集合,正常到不了上限;上限只防异常 NBT 把内存撑爆。
        if (COLOR_CACHE.size() < 64) {
            COLOR_CACHE.put(id, color);
        }
        return color;
    }

    /** 该物品栈的核心是否真的是"无色"(没有可显示的效果)。 */
    public static boolean isColorless(ItemStack stack) {
        return coreColor(stack) == WHITE;
    }

    private static MobEffect effect(String id) {
        try {
            ResourceLocation key = ResourceLocation.tryParse(id);
            return key == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(key);
        }
        catch (Throwable t) {
            return null;
        }
    }
}
