package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 大地护符的**模型状态**(1.1.25b):同一件物品按 NBT 换贴图。
 * <p>
 * 状态优先级(高 → 低),返回值就是 item model 的 predicate 值:
 * <pre>
 *   3  EMPOWERED   Empowered:1b 存在            → earth_amulet_empowered.png(暗红核心 + 金色余烬)
 *   2  HEADS       Heads 非空(至少一颗头颅)     → earth_amulet_heads.png(骨白核心)
 *   1  POTION      PotionEffects 非空            → earth_amulet_potion.png(紫色核心 + 液体高光)
 *   0  EMPTY       以上都没有                    → earth_amulet.png(土黄核心)
 * </pre>
 * 判定函数放这里(而不是只写在客户端):服务端自检可以直接断言每种 NBT 组合的返回值,
 * 客户端只负责把 {@link #PROPERTY} 注册成 {@code ItemProperties} 谓词
 * (见 {@code client.EarthAmuletItemProperties})。
 * <p>
 * 客户端如何及时换图:模型谓词每次渲染前都会读**客户端手里的那个 ItemStack 的 NBT**;
 * 护符在饰品栏 / 背包 / 手上的 NBT 由 Curios 与容器的常规同步送到客户端,所以合成、注入、
 * 下界之星强化、重登、切维度之后都会自动按新 NBT 选模型,不需要额外发同步包。
 */
public final class EarthAmuletState {

    /** 模型谓词名(enigmaticenhance:amulet_state)。 */
    public static final ResourceLocation PROPERTY =
        new ResourceLocation(EnigmaticEnhance.MODID, "amulet_state");

    public static final int EMPTY = 0;
    public static final int POTION = 1;
    public static final int HEADS = 2;
    public static final int EMPOWERED = 3;

    private EarthAmuletState() {}

    /** 状态索引(0..3),不是护符时也返回 0。 */
    public static int state(ItemStack stack) {
        if (!EarthAmuletData.isAmulet(stack)) {
            return EMPTY;
        }
        if (EarthAmuletData.isEmpowered(stack)) {
            return EMPOWERED;
        }
        if (!EarthAmuletData.heads(stack).isEmpty()) {
            return HEADS;
        }
        if (!EarthAmuletData.effects(stack).isEmpty()) {
            return POTION;
        }
        return EMPTY;
    }

    /** 1.1.28j:边框谓词名(enigmaticenhance:amulet_head),0 = 没有头颅(素金环),1..6 = 六种头颅。 */
    public static final ResourceLocation HEAD_PROPERTY =
        new ResourceLocation(EnigmaticEnhance.MODID, "amulet_head");

    /** 六种头颅在 {@link EarthAmuletData#HEADS} 里的顺序,决定边框贴图与 override 值。 */
    public static final java.util.List<String> HEAD_FRAMES = java.util.List.of(
        "zombie_head", "skeleton_skull", "creeper_head", "wither_skeleton_skull",
        "piglin_head", "dragon_head");

    /**
     * 边框索引:0 = 没有头颅(素金环),1..6 = 六种头颅(取**第一个**已解锁的头颅)。
     * 与模型 override({@code amulet_head})、贴图 {@code earth_amulet_frame_<名字>.png} 一一对应。
     */
    public static int headIndex(ItemStack stack) {
        if (!EarthAmuletData.isAmulet(stack)) {
            return 0;
        }
        for (String id : EarthAmuletData.heads(stack)) {
            int idx = EarthAmuletData.HEADS.indexOf(id);
            if (idx >= 0 && idx < HEAD_FRAMES.size()) {
                return idx + 1;
            }
        }
        return 0;
    }

    /** 该索引对应的子模型名(不含命名空间与 .json);0 = 素金框。 */
    public static String headModelName(int index) {
        switch (index) {
            case 1: return "earth_amulet_zombie_head";
            case 2: return "earth_amulet_skeleton_skull";
            case 3: return "earth_amulet_creeper_head";
            case 4: return "earth_amulet_wither_skeleton_skull";
            case 5: return "earth_amulet_piglin_head";
            case 6: return "earth_amulet_dragon_head";
            default: return "earth_amulet_none";
        }
    }

    /** 该索引对应的边框贴图短名(none / zombie / skeleton / ...),越界返回 none。 */
    public static String headFrameName(int index) {
        if (index <= 0 || index > HEAD_FRAMES.size()) {
            return "none";
        }
        return HEAD_FRAMES.get(index - 1);
    }

    /** 给 ItemProperties 用的 float 值(0.0 / 1.0 / 2.0 / 3.0)。 */
    public static float modelState(ItemStack stack) {
        return state(stack);
    }

    /** 状态对应的模型文件名(不含 .json),报告/自检用。 */
    public static String modelName(int state) {
        switch (state) {
            case POTION:
                return "earth_amulet_potion";
            case HEADS:
                return "earth_amulet_heads";
            case EMPOWERED:
                return "earth_amulet_empowered";
            default:
                return "earth_amulet";
        }
    }
}
