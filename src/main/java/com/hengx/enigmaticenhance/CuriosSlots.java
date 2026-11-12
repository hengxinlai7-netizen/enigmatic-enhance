package com.hengx.enigmaticenhance;

import java.util.Map;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;
import top.theillusivec4.curios.api.type.ISlotType;

/**
 * 4.7.0 修订:给玩家**内置注册** {@code head}(头饰)与 {@code hands}(手饰)两个 Curios 槽位。
 *
 * <h2>为什么要自己注册</h2>
 * <p>Curios 5.14.1(jar 内只有 {@code data/curios/curios/slots/*.json} = <b>槽位类型</b>)和
 * 神秘遗物 2.30.1(<b>一个</b> {@code curios/entities} 文件都没有)都<b>不会</b>把 {@code head}/{@code hands}
 * 挂到玩家身上。结果是只装「神秘遗物 + Curios」时玩家根本没有这两个槽:
 * {@code enigmaticenhance:demon_hand_bone} 与 {@code enigmaticenhance:traveler_hat} 虽然在自己的
 * {@code curios:hands} / {@code curios:head} 物品标签里,却<b>无处可放</b>。</p>
 *
 * <h2>为什么用 IMC 而不是 data/curios/curios/entities/*.json(方案 B 胜过方案 A)</h2>
 * <p>反编译 {@code CuriosEntityManager#apply}(curios-forge-5.14.1)可以确认两件事:</p>
 * <ol>
 *   <li>多个 entities 文件对同一实体是 <b>合并</b>({@code map.computeIfAbsent(type, k -> ImmutableMap.builder()).putAll(...)}),
 *       只有写了 {@code "replace": true} 才会把该实体已有的槽位整张表换掉 ——
 *       4.6.5 那个只写 {@code ["head;1"]} 的文件之所以出事,正是因为"想覆盖别人的表"这种做法本身脆弱
 *       (而且 {@code head;1} 不是合法槽位 id,Curios 直接报 "not a registered slot type")。</li>
 *   <li>同一个 {@code apply} 里有一段<b>专为 IMC 写死</b>的逻辑:
 *       {@code for (String s : LegacySlotManager.getImcBuilders().keySet()) {
 *            map.computeIfAbsent(EntityType.PLAYER, k -> ImmutableMap.builder()).put(s, slot); }}
 *       —— 也就是"用 IMC 注册的槽位类型,Curios 会<b>自动挂到玩家身上</b>",且是纯 {@code put},
 *       <b>只增不减</b>。神秘遗物本体的 {@code charm / ring / back}(以及 0 大小的 {@code spellstone / scroll})
 *       走的就是这条路({@code SuperpositionHandler#registerCurioType} →
 *       {@code InterModComms.sendTo("curios", "register_type", ...)})。</li>
 * </ol>
 * <p>所以走 IMC 等于"用神秘遗物自己那套办法再加两个槽":不写死任何别人的槽位清单、不碰别人的数据、
 * <b>原理上不可能</b>再把 charm/ring/back 弄丢(这正是硬性要求 1)。</p>
 *
 * <h2>还做了什么</h2>
 * <p>槽位类型的名字/顺序/图标不用我们填:Curios 的 {@code SlotTypePreset} 里
 * {@code head}=40、{@code hands}=140,图标分别是 {@code curios:slot/empty_head_slot} /
 * {@code empty_hands_slot};{@code LegacySlotManager} 会用 preset 自动补齐。
 * 打包器里已经存在的 {@code data/curios/curios/slots/head.json}、{@code hands.json}
 * 提供的大小/校验器({@code curios:tag})也会被 IMC 这层"沿用而不是覆盖"——
 * 只改我们显式给的那几个字段。</p>
 *
 * <p>另外在 {@link ServerStartedEvent}(数据包已加载完)做一次自检:
 * 玩家的真实槽位表里没有 {@code head}/{@code hands}(或大小为 0)就 <b>WARN</b>,
 * 免得又出现"物品在标签里但没槽可放"这种无提示故障。</p>
 *
 * <p>本类引用 Curios 的类型,只在 Curios 已加载时才会被注册(见 {@link EnigmaticEnhance})。</p>
 */
public final class CuriosSlots {

    /** 头饰槽(旅行家帽子用)。 */
    public static final String HEAD = "head";

    /** 手饰槽(恶魔的手骨用)。 */
    public static final String HANDS = "hands";

    /** 我们希望玩家身上一定存在的槽位。 */
    private static final String[] REQUIRED = { HEAD, HANDS };

    /** 只用于日志展示:玩家身上还可能有哪些槽(神秘遗物本体给的)。 */
    private static final String[] EXPECTED_FROM_EL = { "charm", "ring", "back", "spellstone", "scroll" };

    private CuriosSlots() {
    }

    // ------------------------------------------------------------------ 1. 内置注册

    /**
     * 模组总线上的处理器:{@link InterModEnqueueEvent} 早于 Curios 的 {@code InterModProcessEvent},
     * 是 Forge 规定的 IMC 投递窗口。
     * <p>单独拆一个内部类,是为了让 {@link CuriosSlots} 本身只挂在 Forge 总线上
     * (同一个类同时注册到两条总线虽然无害,但语义上不干净)。</p>
     */
    public static final class IMC {

        private IMC() {
        }

        @SubscribeEvent
        public static void onInterModEnqueue(InterModEnqueueEvent event) {
            try {
                if (!ModList.get().isLoaded("curios")) {
                    return;   // 硬前置,理论上不会走到;保守起见还是判一下
                }
                registerSlotType(HEAD);
                registerSlotType(HANDS);
            }
            catch (RuntimeException | LinkageError ex) {
                // 注册失败不能让启动失败:后面还有自检会 WARN
                EnigmaticEnhance.LOGGER.warn("[神秘遗物增强] Curios 槽位注册失败(head/hands): {}", String.valueOf(ex));
            }
        }

        /**
         * 用"神秘遗物同款"的老接口把槽位类型登记给 Curios。
         * <p>{@link SlotTypeMessage} 在 Curios 5.x 上被标了 {@code forRemoval}(计划 1.22 移除),
         * 但 1.20.1 分支仍是<b>唯一</b>能在运行期把槽位挂到玩家身上的入口
         * ({@code LegacySlotManager} → {@code CuriosEntityManager#apply} 的 IMC 段),
         * 所以这里刻意继续用它;换成 assets/data 就得写死全量槽位清单(方案 A),反而更脆。</p>
         */
        private static void registerSlotType(String identifier) {
            SlotTypeMessage.Builder builder = new SlotTypeMessage.Builder(identifier).size(1);
            InterModComms.sendTo("curios", SlotTypeMessage.REGISTER_TYPE, builder::build);
        }
    }

    // ------------------------------------------------------------------ 2. 启动自检

    /**
     * 4.7.0 修订:数据包(含 {@code curios/entities}、IMC 槽位)都已加载完毕后,
     * 检查玩家身上是否真的有 {@code head}/{@code hands},没有就 WARN。
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            checkPlayerSlots();
        }
        catch (RuntimeException | LinkageError ex) {
            // 自检自己出错绝不能影响服务器
            EnigmaticEnhance.LOGGER.warn("[神秘遗物增强] Curios 槽位自检跳过(内部错误): {}", String.valueOf(ex));
        }
    }

    private static void checkPlayerSlots() {
        Map<String, ISlotType> slots = CuriosApi.getPlayerSlots(false);
        StringBuilder present = new StringBuilder();
        for (String id : slots.keySet()) {
            if (present.length() > 0) {
                present.append(", ");
            }
            present.append(id);
        }

        StringBuilder missing = new StringBuilder();
        for (String id : REQUIRED) {
            ISlotType type = slots.get(id);
            if (type == null) {
                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(id).append("(不存在)");
            }
            else if (type.getSize() <= 0) {
                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(id).append("(大小为 0)");
            }
        }

        if (missing.length() > 0) {
            EnigmaticEnhance.LOGGER.warn(
                "[神秘遗物增强] Curios 槽位自检失败: 玩家缺少 {} —— 恶魔的手骨(hands)/旅行家帽子(head)将无法装备。"
                    + " 当前玩家槽位: [{}]",
                missing, present);
            return;
        }

        // 正常时保持一行(与 DataSelfCheck 的风格一致);debugLog 打开时顺便列出神秘遗物给的槽位
        EnigmaticEnhance.LOGGER.info("[神秘遗物增强] Curios 槽位自检通过: head/hands 存在, 玩家槽位 [{}]", present);
        for (String id : EXPECTED_FROM_EL) {
            if (!slots.containsKey(id)) {
                DebugLog.info("[CuriosSlots] 玩家没有 {} 槽(非必需,神秘遗物本体/其它模组未提供)", id);
            }
        }
    }
}
