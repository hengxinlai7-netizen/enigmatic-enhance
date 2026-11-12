package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 2.11.15:信标增强。
 *
 * <ul>
 *   <li>把神秘遗物 / 扩展 / 本模组的效果塞进信标可选列表(按等级);</li>
 *   <li>信标加成<b>无视距离</b>,只要信标所在区块还加载着就生效;</li>
 *   <li>纯化抗性<b>只能 4 级信标出,且只能 I 级</b>。</li>
 * </ul>
 *
 * <p>实现要点:{@code BeaconBlockEntity.BEACON_EFFECTS} 是公开的 {@code MobEffect[][]},
 * 直接往对应等级的数组里追加即可(客户端/服务端共用同一张表,所以菜单自动可见);
 * 另需把它们加进私有的 {@code VALID_EFFECTS} 白名单,否则服务端会拒绝。</p>
 */
public final class BeaconEnhanceHandler {

    /** 等级 → 效果 id(1 级索引 0,4 级索引 3)。 */
    private static final String[][] TABLE = {
        // 1 级
        {"enigmaticlegacy:growing_hunger", "enigmaticlegacy:growing_bloodlust"},
        // 2 级
        {"enigmaticlegacy:blazing_strength", "enigmaticlegacy:molten_heart", "enigmaticaddons:dragon_breath"},
        // 3 级
        {"enigmaticaddons:ichor_corrosion", "enigmaticaddons:frozen_heart",
            "enigmaticenhance:mirror_shatter", "enigmaticenhance:blood_calamity"},
        // 4 级
        {"enigmaticenhance:calamity_origin", "enigmaticaddons:pure_resistance"},
    };

    /** 纯化抗性:只能 4 级,且只给 I 级。 */
    public static final String PURE_RESISTANCE = "enigmaticaddons:pure_resistance";

    private static boolean installed;
    private static final List<MobEffect> KNOWN = new ArrayList<>();
    private static boolean validFilled;

    private BeaconEnhanceHandler() {
    }

    /** 启动完成后把效果塞进信标表(此时注册表已就绪)。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 4.5.0:配置开关(默认开启;关闭后不再往信标表里塞增强效果)
        if (!RingConfig.beaconEnhanceEnabled()) {
            return;
        }
        install();
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            MobEffect[][] table = BeaconBlockEntity.BEACON_EFFECTS;
            List<MobEffect> all = new ArrayList<>();
            for (int lvl = 0; lvl < table.length && lvl < TABLE.length; lvl++) {
                List<MobEffect> list = new ArrayList<>(List.of(table[lvl]));
                for (String id : TABLE[lvl]) {
                    MobEffect effect = effectOf(id);
                    if (effect != null && !list.contains(effect)) {
                        list.add(effect);
                        all.add(effect);
                    }
                }
                table[lvl] = list.toArray(new MobEffect[0]);
            }
            KNOWN.addAll(all);
            // 2.11.20:VALID_EFFECTS 是 static,反射 get(null) 就能拿到,不需要信标实例,
            // 也不再依赖 applyEffects 的 Mixin(它在生产环境可能匹配不上)。
            Set<MobEffect> valid = validEffectsSet();
            if (valid != null) {
                valid.addAll(all);
                validFilled = true;
                DebugLog.info("[Beacon] 已把 {} 个自定义效果加入白名单", all.size());
            }
            else {
                DebugLog.warn("[Beacon] 未能定位 VALID_EFFECTS 字段,自定义效果可能被服务端拒绝");
            }
            installed = true;
            DebugLog.info("[Beacon] 已扩展信标效果表,新增 {} 个自定义效果", all.size());
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[Beacon] 扩展信标效果表失败: {}", e.toString());
        }
    }

    /** 2.11.16:把自定义效果补进 VALID_EFFECTS(需要真实信标实例才能拿到那个私有集合)。 */
    public static void ensureValid(net.minecraft.world.level.block.entity.BeaconBlockEntity be) {
        if (validFilled || be == null || KNOWN.isEmpty()) {
            return;
        }
        try {
            Set<MobEffect> valid = validEffectsSet();
            if (valid != null) {
                valid.addAll(KNOWN);
                validFilled = true;
                DebugLog.info("[Beacon] 已把 {} 个自定义效果加入白名单", KNOWN.size());
            }
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[Beacon] 白名单写入失败: {}", e.toString());
        }
    }

    /** 2.11.17:反射拿 VALID_EFFECTS(生产环境 SRG 名 f_58647_,开发环境 VALID_EFFECTS)。 */
    @SuppressWarnings("unchecked")
    private static Set<MobEffect> validEffectsSet() {
        for (String name : new String[]{"VALID_EFFECTS", "f_58647_"}) {
            try {
                java.lang.reflect.Field fld = BeaconBlockEntity.class.getDeclaredField(name);
                fld.setAccessible(true);
                Object v = fld.get(null);
                if (v instanceof Set<?> set) {
                    return (Set<MobEffect>) set;
                }
            }
            catch (ReflectiveOperationException | RuntimeException ignored) {
                // 下一个名字
            }
        }
        return null;
    }

    private static MobEffect effectOf(String id) {
        try {
            return ForgeRegistries.MOB_EFFECTS.getValue(
                net.minecraft.resources.ResourceLocation.tryParse(id));
        }
        catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** 纯化抗性的特殊处理:只给 I 级。 */
    public static int amplifierFor(MobEffect effect, int vanillaAmp) {
        net.minecraft.resources.ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (id != null && PURE_RESISTANCE.equals(id.toString())) {
            return 0;
        }
        return vanillaAmp;
    }
}
