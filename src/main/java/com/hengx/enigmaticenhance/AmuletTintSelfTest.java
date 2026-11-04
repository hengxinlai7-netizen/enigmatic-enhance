package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.1.28j 自检 T32:大地护符的**动态染色 + 分头颅边框**。
 * <ul>
 * <li>T32a 染色口径:{@code tintindex 0}(金框)= 白;{@code tintindex 1}(核心)在无药水时 = 白(无色空芯)、
 *     注入治疗药水 = 治疗效果的 {@code getColor()},注入速度药水 = 速度的颜色,且两者不同;</li>
 * <li>T32b 六个头颅:每种头颅解析出各自的 headIndex(1..6)、互不相同,
 *     对应边框贴图 {@code earth_amulet_frame_<名字>.png} 与模型
 *     {@code earth_amulet_head_<名字>.json} 都存在且引用了正确的贴图;</li>
 * <li>T32c 模型接线:基础模型 {@code earth_amulet.json} 有两层(layer0 边框 / layer1 核心)
 *     且 overrides 覆盖 6 个头颅 + 强化形态;强化模型自己只有一层(不会被误染色)。</li>
 * </ul>
 * 客户端真正的 {@code ItemColor} 只是把 {@link EarthAmuletTint#colorFor} 包了一层,
 * 所以这里直接断言那个纯函数即可在专用服务端跑。
 */
final class AmuletTintSelfTest {

    private static final String TAG = "[AmuletTintSelfTest]";

    private AmuletTintSelfTest() {}

    static boolean run(ServerLevel level, Player player) {
        boolean a = testColors(player);
        boolean b = testHeads(player);
        boolean c = testModels();
        boolean all = a && b && c;
        log(" 大地护符染色小结 T32a(染色)=" + a + " T32b(六头颅)=" + b + " T32c(模型)=" + c
            + " => " + (all ? "PASS" : "FAIL"));
        return all;
    }

    // ------------------------------------------------------------------ a 染色

    private static boolean testColors(Player player) {
        log(" --- T32a 核心层染色(效果颜色)");
        ItemStack amulet = newAmulet();
        if (amulet.isEmpty()) {
            check("T32a", false);
            return false;
        }
        boolean ok = true;
        int frame = EarthAmuletTint.colorFor(amulet, EarthAmuletTint.FRAME_LAYER);
        int emptyCore = EarthAmuletTint.colorFor(amulet, EarthAmuletTint.CORE_LAYER);
        log(" T32a 无药水:边框层=" + hex(frame) + " [应 FFFFFF];核心层=" + hex(emptyCore) + " [应 FFFFFF]");
        ok &= frame == EarthAmuletTint.WHITE && emptyCore == EarthAmuletTint.WHITE;

        int healColor = MobEffects.HEAL.getColor() & 0xFFFFFF;
        int speedColor = MobEffects.MOVEMENT_SPEED.getColor() & 0xFFFFFF;
        ItemStack healing = withEffect(amulet, "minecraft:instant_health");
        ItemStack speedy = withEffect(amulet, "minecraft:speed");
        int heal = EarthAmuletTint.colorFor(healing, EarthAmuletTint.CORE_LAYER);
        int speed = EarthAmuletTint.colorFor(speedy, EarthAmuletTint.CORE_LAYER);
        log(" T32a 治疗药水:核心=" + hex(heal) + " [应 " + hex(healColor) + "]");
        log(" T32a 速度药水:核心=" + hex(speed) + " [应 " + hex(speedColor) + "]");
        log(" T32a 两者不同=" + (heal != speed) + " [应 true]");
        ok &= heal == healColor && speed == speedColor && heal != speed;

        // 多条效果:取第一条(与实现一致)
        ItemStack both = withEffects(amulet, List.of("minecraft:speed", "minecraft:regeneration"));
        int first = EarthAmuletTint.colorFor(both, EarthAmuletTint.CORE_LAYER);
        log(" T32a 多效果取第一条:核心=" + hex(first) + " [应 速度 " + hex(speedColor) + "]");
        ok &= first == speedColor;

        // 非护符 / 边框层 ===> 永远白
        ItemStack sword = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
        log(" T32a 非护符:核心=" + hex(EarthAmuletTint.colorFor(sword, 1)) + " [应 FFFFFF]");
        ok &= EarthAmuletTint.colorFor(sword, 1) == EarthAmuletTint.WHITE;
        check("T32a", ok);
        return ok;
    }

    // ------------------------------------------------------------------ b 六头颅

    private static boolean testHeads(Player player) {
        log(" --- T32b 六种头颅各自解析到独立边框");
        boolean ok = true;
        List<Integer> indices = new ArrayList<>();
        String[] frames = { "zombie_head", "skeleton_skull", "creeper_head", "wither_skeleton_skull",
                            "piglin_head", "dragon_head" };
        for (int i = 0; i < EarthAmuletData.HEADS.size(); i++) {
            String head = EarthAmuletData.HEADS.get(i);
            ItemStack stack = EarthAmuletData.withHead(newAmulet(), head);
            int index = EarthAmuletState.headIndex(stack);
            String frameName = EarthAmuletState.headFrameName(index);
            boolean framePng = texture(frameName, true);
            String modelName = EarthAmuletState.headModelName(index);
            boolean model = model(modelName, "earth_amulet_frame_" + frameName);
            log(" T32b " + head + " -> headIndex=" + index + " [应 " + (i + 1) + "];贴图 "
                + "earth_amulet_frame_" + frameName + ".png=" + framePng + ";模型 " + modelName + "=" + model);
            ok &= index == i + 1 && frameName.equals(frames[i]) && framePng && model;
            indices.add(index);
        }
        boolean distinct = indices.stream().distinct().count() == indices.size() && !indices.contains(0);
        log(" T32b 6 个索引互不相同且都不为 0=" + distinct + " " + indices);
        ok &= distinct;

        // 常态:head = 0 -> earth_amulet_none + frame_none
        String noneModel = EarthAmuletState.headModelName(0);
        boolean none = noneModel.equals("earth_amulet_none") && model(noneModel, "earth_amulet_frame_none")
            && EarthAmuletState.headFrameName(0).equals("none");
        log(" T32b 常态(无头颅):headModelName(0)=" + noneModel + ";模型+金框贴图=" + none);
        ok &= none;

        // 6 个模型名互不相同
        java.util.Set<String> models = new java.util.HashSet<>();
        for (int i = 0; i <= EarthAmuletData.HEADS.size(); i++) {
            models.add(EarthAmuletState.headModelName(i));
        }
        log(" T32b 7 个子模型名互不相同=" + (models.size() == 7) + " " + models);
        ok &= models.size() == 7;
        check("T32b", ok);
        return ok;
    }

    // ------------------------------------------------------------------ c 模型接线

    private static boolean testModels() {
        log(" --- T32c 模型两层 / overrides");
        boolean ok = true;
        String base = read("earth_amulet");
        boolean twoLayers = base.contains("earth_amulet_frame_none") && base.contains("earth_amulet_core");
        boolean sixOverrides = true;
        for (int i = 1; i <= 6; i++) {
            sixOverrides &= base.contains(EarthAmuletState.headModelName(i));
        }
        boolean empoweredOverride = base.contains("earth_amulet_empowered");
        log(" T32c 基础模型两层(layer0 边框 / layer1 核心)=" + twoLayers + ";6 个头颅 override="
            + sixOverrides + ";强化 override=" + empoweredOverride);
        ok &= twoLayers && sixOverrides && empoweredOverride;

        // 核心层是独立贴图,且是白色系(供染色)
        boolean core = texture("core", false);
        log(" T32c 核心贴图 earth_amulet_core.png=" + core);
        ok &= core;

        // 强化模型不能有 layer1(否则会被染成药水色)
        String empowered = read("earth_amulet_empowered");
        boolean single = empowered.contains("earth_amulet_empowered") && !empowered.contains("layer1");
        log(" T32c 强化模型只有一层(不被染色)=" + single);
        ok &= single;

        // 强化护符的 tint 断言:边框层白色;模型没有 layer1 ⇒ 永远不会被核心染色
        ItemStack emp = EarthAmuletData.empowered(newAmulet());
        int empFrame = EarthAmuletTint.colorFor(emp, EarthAmuletTint.FRAME_LAYER);
        boolean empTint = empFrame == EarthAmuletTint.WHITE && !read("earth_amulet_empowered").contains("layer1");
        log(" T32c 强化护符 tintIndex0=" + String.format("%06X", empFrame) + " [应 FFFFFF];"
            + "无 layer1 ⇒ 不被误染=" + empTint);
        ok &= empTint;

        // potion / heads 两个旧模型仍是单层、且基础模型不再引用它们
        boolean oldSingles = !read("earth_amulet_potion").contains("layer1")
            && !read("earth_amulet_heads").contains("layer1");
        boolean baseDrops = !base.contains("earth_amulet_potion") && !base.contains("earth_amulet_heads");
        log(" T32c 旧 potion/heads 模型仍单层=" + oldSingles + ";基础模型不再引用它们=" + baseDrops);
        ok &= oldSingles && baseDrops;
        check("T32c", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 工具

    private static ItemStack newAmulet() {
        net.minecraft.world.item.Item item = ModItems.earthAmuletOrNull();
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack withEffect(ItemStack amulet, String id) {
        return withEffects(amulet, List.of(id));
    }

    private static ItemStack withEffects(ItemStack amulet, List<String> ids) {
        ItemStack copy = amulet.copy();
        List<EarthAmuletData.StoredEffect> list = new ArrayList<>();
        for (String id : ids) {
            list.add(new EarthAmuletData.StoredEffect(id, 1));
        }
        EarthAmuletData.setEffects(copy, list);
        return copy;
    }

    private static boolean texture(String name, boolean frame) {
        String path = (frame ? "earth_amulet_frame_" : "earth_amulet_") + name;
        try (var in = AmuletTintSelfTest.class
            .getResourceAsStream("/assets/enigmaticenhance/textures/item/" + path + ".png")) {
            return in != null;
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static boolean model(String modelName, String frameTexture) {
        String json = read(modelName);
        return !json.isEmpty() && json.contains(frameTexture) && json.contains("earth_amulet_core");
    }

    private static String read(String modelName) {
        try (var in = AmuletTintSelfTest.class
            .getResourceAsStream("/assets/enigmaticenhance/models/item/" + modelName + ".json")) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (Throwable t) {
            return "";
        }
    }

    private static String hex(int color) {
        return String.format("%06X", color & 0xFFFFFF);
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
