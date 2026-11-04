package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.1.28f 自检 T29:**分类表**,逐一列出所有已注册 {@code MobEffect} 在佩戴恶魔血杯时的状态:
 * {@code effect id | 类型 | 是否封印 | hasEffect | 是否有实际效果}。
 *
 * <h2>1.1.28f 的口径(用户拍板)</h2>
 * <ul>
 * <li>{@code hasEffect} **一律为 true**(不再有"检测不到"的效果 —— 这是硬断言);</li>
 * <li>每 tick 型 / 属性型:被 {@code MobEffectInstance#applyEffect} 与
 *     {@code onEffectAdded/onEffectUpdated} 两处注入封印 ⇒ 无实际效果;</li>
 * <li>"读取型"那 16 个原版效果(防火/水下呼吸/潮涌/隐身/发光/夜视/失明/黑暗/反胃/飘浮/缓降/
 *     海豚/跳跃/抗性/不祥之兆/村庄英雄):原版在别处直接读 hasEffect 生效,
 *     既然 hasEffect 保持 true,它们**会正常生效** —— 这是明确接受的取舍,T29 如实列出。</li>
 * </ul>
 *
 * <h2>怎么判"是否封印"</h2>
 * 干净状态下记录全属性值 + 血量/饱食/氧气/着火/吸收/坐标,施加效果并推进 40 tick 后再比:
 * 完全没变化 ⇒ 封印;有变化 ⇒ 仍生效。瞬时型直接调用三次 {@code applyInstantenousEffect} 后比血量/饱食。
 * 读取型效果本身不会改这些数值(它们改的是原版别的判定),所以按分类直接标"仍生效(读取型)"。
 */
final class EffectMatrixSelfTest {

    private static final String TAG = "[EffectMatrix]";
    private static final int DURATION = 600;
    private static final int TICKS = 40;
    private static final double EPS = 1.0E-6D;

    private EffectMatrixSelfTest() {}

    static boolean run(ServerLevel level, Player player) {
        log(" --- T29 分类表:所有已注册 MobEffect 的封印状态(1.1.28f:hasEffect 一律 true)");
        boolean sanity = sanityCheck(level, player);
        Result result = sweep(level, player);

        List<String> stillActive = result.rows.stream().filter(Row::effective).map(Row::id).toList();
        int sealed = (int) result.rows.stream().filter(row -> !row.effective()).count();
        boolean allHasEffect = result.rows.stream().allMatch(Row::hasEffect);
        boolean sealedMatches = result.rows.stream()
            .filter(row -> !row.mechanical())
            .allMatch(row -> !row.effective());
        boolean ok = sanity && allHasEffect && sealedMatches;

        log(" T29 分类表(共 " + result.rows.size() + " 个效果):");
        for (Row row : result.rows) {
            log(" T29 | " + row.id() + " | " + row.type() + " | 封印=" + (!row.effective())
                + " | hasEffect=" + row.hasEffect() + " | 有实际效果=" + row.effective());
        }
        log(" T29 汇总:封印=" + sealed + " / 仍生效=" + stillActive.size() + " " + stillActive);
        log(" T29 断言:全部 hasEffect=true=" + allHasEffect + "(必须 true);"
            + "非读取型全部封印=" + sealedMatches + ";探针灵敏度=" + sanity);
        check("T29", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 探针灵敏度

    private static boolean sanityCheck(ServerLevel level, Player player) {
        log(" T29s 探针灵敏度(不戴杯子,同样的探针必须能看到变化)");
        boolean ok = true;
        unequip(player);
        ok &= detects(player, net.minecraft.world.effect.MobEffects.REGENERATION, "再生");
        ok &= detects(player, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, "缓慢");
        ok &= detects(player, net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, "速度");
        ok &= detects(player, net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, "力量");
        ok &= detects(player, net.minecraft.world.effect.MobEffects.HEALTH_BOOST, "生命提升");
        log(" T29s 探针灵敏度=" + ok + " [必须 true,否则整张表不可信]");
        reset(player);
        return ok;
    }

    private static boolean detects(Player player, MobEffect effect, String name) {
        reset(player);
        player.setHealth(player.getMaxHealth() * 0.5F);
        double health0 = player.getHealth();
        float absorption0 = player.getAbsorptionAmount();
        Map<String, Double> attributes0 = attributes(player);
        player.addEffect(new MobEffectInstance(effect, DURATION, 2));
        MobEffectInstance instance = player.getEffect(effect);
        if (instance != null) {
            for (int i = 0; i < TICKS; i++) {
                instance.tick(player, () -> { });
            }
        }
        boolean changed = Math.abs(player.getHealth() - health0) > EPS
            || Math.abs(player.getAbsorptionAmount() - absorption0) > EPS
            || !attributes0.equals(attributes(player));
        log(" T29s " + name + "(" + id(effect) + "):观测到变化=" + changed);
        player.removeAllEffects();
        return changed;
    }

    // ------------------------------------------------------------------ 分类表

    private record Row(String id, String type, boolean mechanical, boolean hasEffect, boolean effective) {}

    private record Result(List<Row> rows) {}

    private static Result sweep(ServerLevel level, Player player) {
        List<Row> rows = new ArrayList<>();
        if (!equip(player)) {
            log(" T29 无法佩戴恶魔血杯,分类表中止");
            return new Result(rows);
        }
        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS) {
            if (effect == null) {
                continue;
            }
            String type = type(effect);
            boolean mechanical = DemonBloodChaliceHandler.isMechanical(effect);
            boolean hasEffect;
            boolean effective;
            try {
                reset(player);
                Map<String, Double> attributes0 = attributes(player);
                double health0 = player.getHealth();
                int food0 = player.getFoodData().getFoodLevel();
                int air0 = player.getAirSupply();
                int fire0 = player.getRemainingFireTicks();
                float absorption0 = player.getAbsorptionAmount();
                double x0 = player.getX();
                double y0 = player.getY();
                double z0 = player.getZ();

                if (effect.isInstantenous()) {
                    effect.applyInstantenousEffect(player, player, player, 1, 1.0D);
                    effect.applyInstantenousEffect(player, player, player, 2, 1.0D);
                    effect.applyInstantenousEffect(player, player, player, 0, 1.0D);
                    hasEffect = true; // 瞬时型本来就不进效果列表
                    effective = Math.abs(player.getHealth() - health0) > EPS
                        || player.getFoodData().getFoodLevel() != food0
                        || Math.abs(player.getAbsorptionAmount() - absorption0) > EPS;
                }
                else {
                    player.addEffect(new MobEffectInstance(effect, DURATION, 1));
                    hasEffect = player.hasEffect(effect) && player.getEffect(effect) != null;
                    MobEffectInstance instance = player.getEffect(effect);
                    if (instance != null) {
                        for (int i = 0; i < TICKS; i++) {
                            instance.tick(player, () -> { });
                        }
                    }
                    boolean attributesChanged = !attributes0.equals(attributes(player));
                    boolean healthChanged = Math.abs(player.getHealth() - health0) > EPS;
                    boolean foodChanged = player.getFoodData().getFoodLevel() != food0;
                    boolean airChanged = player.getAirSupply() != air0;
                    boolean fireChanged = player.getRemainingFireTicks() != fire0;
                    boolean absorptionChanged = Math.abs(player.getAbsorptionAmount() - absorption0) > EPS;
                    boolean moved = Math.abs(player.getX() - x0) > EPS || Math.abs(player.getY() - y0) > EPS
                        || Math.abs(player.getZ() - z0) > EPS;
                    effective = attributesChanged || healthChanged || foodChanged || airChanged
                        || fireChanged || absorptionChanged || moved;
                    if (mechanical) {
                        // 读取型:原版在别处读 hasEffect 生效;探针观测不到,按设计标"仍生效"
                        effective = true;
                    }
                }
            }
            catch (Throwable t) {
                log(" T29 " + id(effect) + " 施加/推进异常: " + t);
                hasEffect = false;
                effective = false;
            }
            rows.add(new Row(id(effect).toString(), type, mechanical, hasEffect, effective));
            reset(player);
        }
        unequip(player);
        return new Result(rows);
    }

    private static String type(MobEffect effect) {
        if (effect.isInstantenous()) {
            return "instant";
        }
        if (DemonBloodChaliceHandler.isMechanical(effect)) {
            return "读取型(hasEffect)";
        }
        if (!effect.getAttributeModifiers().isEmpty()) {
            return "attribute";
        }
        if (effect.getClass() != MobEffect.class) {
            return "tick(自定义)";
        }
        return "tick(原版)";
    }

    private static ResourceLocation id(MobEffect effect) {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        return id == null ? new ResourceLocation("unknown", "unknown") : id;
    }

    // ------------------------------------------------------------------ 工具

    private static Map<String, Double> attributes(Player player) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(instance.getAttribute());
            map.put(String.valueOf(id), Math.round(instance.getValue() * 10000.0D) / 10000.0D);
        }
        return map;
    }

    private static void reset(Player player) {
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.setAirSupply(300);
        player.clearFire();
        player.setAbsorptionAmount(0.0F);
        DemonBloodChaliceHandler.invalidate(player);
    }

    private static boolean equip(Player player) {
        Item chalice = ModItems.demonBloodChaliceOrNull();
        if (chalice == null) {
            return false;
        }
        try {
            boolean placed = CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    var slot = handler.getCurios().get(ModIds.CURIO_SLOT_CHARM);
                    if (slot == null || slot.getSlots() < 1) {
                        return false;
                    }
                    slot.getStacks().setStackInSlot(0, new ItemStack(chalice));
                    return true;
                })
                .orElse(false);
            DemonBloodChaliceHandler.onEquipped(player);
            return placed && DemonBloodChaliceHandler.isSealed(player);
        }
        catch (Throwable t) {
            log(" T29 佩戴异常: " + t);
            return false;
        }
    }

    private static void unequip(Player player) {
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                var slot = handler.getCurios().get(ModIds.CURIO_SLOT_CHARM);
                if (slot != null && slot.getSlots() > 0) {
                    slot.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                }
            });
        }
        catch (Throwable ignored) {
            // 清理
        }
        DemonBloodChaliceHandler.onUnequipped(player);
        DemonBloodChaliceHandler.invalidate(player);
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
