package com.hengx.enigmaticenhance;

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
     */
    public static int coreColor(ItemStack stack) {
        // 4.6.1(帧率):这个方法由 ItemColor 回调在**每次渲染前**调用。没有 NBT 就一定没有效果,
        // 直接返回"无色",省掉每帧一个 ArrayList + 每条记录一个 record(见 EarthAmuletData#hasEffects)。
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return WHITE;
        }
        for (EarthAmuletData.StoredEffect stored : EarthAmuletData.effects(stack)) {
            MobEffect effect = effect(stored.id());
            if (effect != null) {
                return effect.getColor() & 0xFFFFFF;
            }
        }
        return WHITE;
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
