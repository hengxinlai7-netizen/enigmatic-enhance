package com.hengx.enigmaticenhance;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.1.25c 自检:【旅行家帽子】(-D 开关,由 {@link EarthAmuletSelfTest} 串起来跑)。
 * <ul>
 * <li>T15 定义:id / 不可堆叠 / 无耐久 / curios:head 槽 / 在创造标签页 / **没有任何配方产出它**;</li>
 * <li>T16 加成:空数据只有 +0.5 速度;3 群系 ⇒ 生命上限 +6,第 4 个 ⇒ +8,重复群系不叠加;2 维度 ⇒ 攻击 +4;</li>
 * <li>T17 取下立即回落到原值、重戴恢复;Clone 复制数据;换维度数据仍在;死亡只掉加成不掉数据;</li>
 * <li>T18 tooltip 数字与计数一致;debugLog 门控;ModIds.problems() 为空。</li>
 * </ul>
 * 断言用**差值**(其它模组也可能给玩家挂属性修饰符,绝对值不可靠)。
 */
final class TravelerHatSelfTest {

    private static final String TAG = "[TravelerHatSelfTest]";
    private static final String PROBE = TravelerHatHandler.LOG_TAG;
    private static final String ROOT = TravelerHatData.ROOT;

    /** 未戴帽子时的基线(其它模组的影响被一起减掉)。 */
    private static String wornSlot = "-";
    private static double preHealth;
    private static double preAttack;
    private static double preSpeed;

    private TravelerHatSelfTest() {}

    static boolean run(ServerLevel level, FakePlayer player) {
        boolean t15 = testDefinition(level, player);
        boolean t16 = testBonuses(level, player);
        boolean t17 = testUnequipCloneDimension(level, player);
        boolean t18 = testTooltipAndLogs(level, player);
        boolean all = t15 && t16 && t17 && t18;
        log(" 旅行家帽子小结 T15=" + t15 + " T16=" + t16 + " T17=" + t17 + " T18=" + t18 + " => "
            + (all ? "PASS" : "FAIL"));
        return all;
    }

    // ------------------------------------------------------------------ T15

    private static boolean testDefinition(ServerLevel level, FakePlayer player) {
        log(" --- T15 旅行家帽子定义 / 槽位 / 无配方");
        Item item = ModItems.travelerHatOrNull();
        ResourceLocation id = item == null ? null : ForgeRegistries.ITEMS.getKey(item);
        log(" T15 物品 id=" + id + " [应为 enigmaticenhance:traveler_hat]");
        boolean ok = "enigmaticenhance:traveler_hat".equals(String.valueOf(id));
        if (item == null) {
            check("T15", false);
            return false;
        }
        ItemStack stack = new ItemStack(item);
        boolean head = false;
        try {
            head = CuriosApi.getItemStackSlots(stack, level).containsKey("head");
        }
        catch (Throwable t) {
            log(" T15 Curios 槽位查询异常: " + t);
        }
        log(" T15 最大堆叠=" + stack.getMaxStackSize() + " [应 1];最大耐久=" + stack.getMaxDamage()
            + " [应 0];curios:head=" + head + " [应 true]");
        ok &= stack.getMaxStackSize() == 1 && stack.getMaxDamage() == 0 && head;

        boolean inTab = CreativeTabContents.items().contains(item);
        log(" T15 在创造标签页清单里=" + inTab + " [应 true];本模组物品总数="
            + CreativeTabContents.items().size());
        ok &= inTab;

        // 1.2.2c 起有配方了(注册配方 enigmaticenhance:heart_shaped,heart="any"):只断言"只有我们自己那一条"
        List<String> sources = new java.util.ArrayList<>();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            try {
                if (recipe.getResultItem(level.registryAccess()).is(item)) {
                    sources.add(recipe.getId().toString());
                }
            }
            catch (Throwable ignored) {
                // 与我们无关的配方取产物失败
            }
        }
        boolean onlyOurs = sources.size() == 1 && "enigmaticenhance:traveler_hat".equals(sources.get(0));
        log(" T15 产出旅行家帽子的配方=" + sources + " [应只有 enigmaticenhance:traveler_hat]");
        ok &= onlyOurs;

        // 诊断:Curios 注册表里的槽位(含 size)与"该玩家身上实际有的槽位"
        try {
            StringBuilder registry = new StringBuilder();
            for (var e : CuriosApi.getSlots(level).entrySet()) {
                registry.append(e.getKey()).append('=').append(e.getValue().getSize()).append(' ');
            }
            log(" T15 Curios 槽位注册表: " + registry.toString().trim());
            StringBuilder playerSlots = new StringBuilder();
            for (var e : CuriosApi.getPlayerSlots(player).entrySet()) {
                playerSlots.append(e.getKey()).append('=').append(e.getValue().getSize()).append(' ');
            }
            log(" T15 getPlayerSlots: " + playerSlots.toString().trim());
        }
        catch (Throwable t) {
            log(" T15 注册表诊断异常: " + t);
        }
        // 诊断:这个玩家身上每个饰品槽的实际格数(槽位是否启用由 Curios 决定)
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                StringBuilder slots = new StringBuilder();
                for (var entry : handler.getCurios().entrySet()) {
                    slots.append(entry.getKey()).append('=').append(entry.getValue().getSlots()).append(' ');
                }
                log(" T15 玩家饰品槽: " + slots.toString().trim());
            });
        }
        catch (Throwable t) {
            log(" T15 槽位诊断异常: " + t);
        }
        check("T15", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T16 加成

    private static boolean testBonuses(ServerLevel level, FakePlayer player) {
        log(" --- T16 加成:速度 / 群系→生命上限 / 维度→攻击力");
        resetPlayer(player);
        preHealth = value(player, Attributes.MAX_HEALTH);
        preAttack = value(player, Attributes.ATTACK_DAMAGE);
        preSpeed = value(player, Attributes.MOVEMENT_SPEED);

        boolean equipped = equip(player, new ItemStack(ModItems.TRAVELER_HAT.get()));
        TravelerHatHandler.onWornTick(player, TravelerHatHandler.equippedHat(player));
        EarthAmuletSelfTest.hold(50L);
        boolean ok = equipped && RingConfig.travelerHatSpeedIsPercent();
        log(" T16 戴上帽子=" + equipped + " [应 true];槽位=" + wornSlot + ";speedMode="
            + (RingConfig.travelerHatSpeedIsPercent() ? "PERCENT" : "FLAT"));

        AttributeModifier speed = modifier(player, Attributes.MOVEMENT_SPEED, TravelerHatHandler.SPEED_UUID);
        int biomes0 = TravelerHatData.biomeCount(player);
        int dims0 = TravelerHatData.dimensionCount(player);
        log(" T16 首次佩戴:速度=" + value(player, Attributes.MOVEMENT_SPEED) + " [基线 " + preSpeed
            + "];速度修饰符=" + describe(speed) + " [应 +0.5 MULTIPLY_TOTAL];当前群系=" + biomes0
            + " 个(生命上限 +" + (value(player, Attributes.MAX_HEALTH) - preHealth) + ");当前维度=" + dims0
            + " 个(攻击力 +" + (value(player, Attributes.ATTACK_DAMAGE) - preAttack) + ")");
        ok &= speed != null && speed.getAmount() == 0.5D
            && speed.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL
            && biomes0 >= 1 && dims0 >= 1
            && value(player, Attributes.MAX_HEALTH) - preHealth == 2.0D * biomes0
            && value(player, Attributes.ATTACK_DAMAGE) - preAttack == 2.0D * dims0;

        // 3 个新群系
        TravelerHatData.visit(player, id("minecraft:plains"), null);
        TravelerHatData.visit(player, id("minecraft:desert"), null);
        TravelerHatData.visit(player, id("minecraft:taiga"), null);
        TravelerHatHandler.onWornTick(player, TravelerHatHandler.equippedHat(player));
        EarthAmuletSelfTest.hold(50L);
        int biomes3 = TravelerHatData.biomeCount(player);
        double health3 = value(player, Attributes.MAX_HEALTH);
        log(" T16 又走过 3 个群系:群系总数=" + biomes3 + " [应 " + (biomes0 + 3) + "];生命上限=" + health3
            + " [应 = 基线 + " + (2.0D * biomes3) + "]");
        ok &= biomes3 == biomes0 + 3 && health3 - preHealth == 2.0D * biomes3;

        // 第 4 个新群系
        TravelerHatData.visit(player, id("minecraft:swamp"), null);
        TravelerHatHandler.onWornTick(player, TravelerHatHandler.equippedHat(player));
        EarthAmuletSelfTest.hold(50L);
        int biomes4 = TravelerHatData.biomeCount(player);
        double health4 = value(player, Attributes.MAX_HEALTH);
        log(" T16 第 4 个新群系:群系总数=" + biomes4 + " [应 " + (biomes0 + 4) + "];生命上限=" + health4
            + " [应 = 基线 + " + (2.0D * biomes4) + "]");
        ok &= biomes4 == biomes0 + 4 && health4 - preHealth == 2.0D * biomes4;

        // 重复经过同一个群系不叠加
        boolean dup = TravelerHatData.visit(player, id("minecraft:plains"), null);
        TravelerHatHandler.onWornTick(player, TravelerHatHandler.equippedHat(player));
        EarthAmuletSelfTest.hold(50L);
        log(" T16 重复经过 plains:有新增=" + dup + " [应 false];群系总数=" + TravelerHatData.biomeCount(player)
            + " [应 " + biomes4 + "];生命上限=" + value(player, Attributes.MAX_HEALTH) + " [应仍 " + health4 + "]");
        ok &= !dup && TravelerHatData.biomeCount(player) == biomes4
            && value(player, Attributes.MAX_HEALTH) == health4;

        // 2 个维度:当前(overworld)已在,再记 nether ⇒ 维度 +1,攻击力 +2
        TravelerHatData.visit(player, null, id("minecraft:overworld"));
        TravelerHatData.visit(player, null, id("minecraft:the_nether"));
        TravelerHatHandler.onWornTick(player, TravelerHatHandler.equippedHat(player));
        EarthAmuletSelfTest.hold(50L);
        int dims = TravelerHatData.dimensionCount(player);
        double attack = value(player, Attributes.ATTACK_DAMAGE);
        log(" T16 去过维度=" + dims + " 个(含 overworld 与 the_nether):攻击力=" + attack + " [应 = 基线 + "
            + (2.0D * dims) + "]");
        ok &= dims >= 2 && dims == dims0 + 1 && attack - preAttack == 2.0D * dims;

        // 真实检测路径:读当前群系/维度
        TravelerHatHandler.recordCurrent(player);
        int biomesNow = TravelerHatData.biomeCount(player);
        log(" T16 真实检测:当前群系=" + TravelerHatData.currentBiome(player) + ",当前维度="
            + TravelerHatData.currentDimension(player) + ";群系总数=" + biomesNow);
        ok &= biomesNow >= biomes4;

        int[] sent = TravelerHatHandler.lastSent(player);
        log(" T16 已同步客户端的计数=" + (sent == null ? "无" : (sent[0] + "/" + sent[1])) + " [应 = "
            + biomesNow + "/" + TravelerHatData.dimensionCount(player) + "]");
        ok &= sent != null && sent[0] == biomesNow && sent[1] == TravelerHatData.dimensionCount(player);
        check("T16", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T17 取下 / 重戴 / 克隆 / 维度 / 死亡

    private static boolean testUnequipCloneDimension(ServerLevel level, FakePlayer player) {
        log(" --- T17 取下回落 / 重戴恢复 / Clone / 换维度 / 死亡");
        boolean ok = true;
        int biomes = TravelerHatData.biomeCount(player);
        int dims = TravelerHatData.dimensionCount(player);

        unequip(player);
        TravelerHatHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        EarthAmuletSelfTest.hold(50L);
        log(" T17 取下后:生命上限=" + value(player, Attributes.MAX_HEALTH) + " [应回落 " + preHealth
            + "];攻击力=" + value(player, Attributes.ATTACK_DAMAGE) + " [应回落 " + preAttack + "];速度修饰符="
            + describe(modifier(player, Attributes.MOVEMENT_SPEED, TravelerHatHandler.SPEED_UUID)) + " [应 null]");
        ok &= value(player, Attributes.MAX_HEALTH) == preHealth
            && value(player, Attributes.ATTACK_DAMAGE) == preAttack
            && modifier(player, Attributes.MOVEMENT_SPEED, TravelerHatHandler.SPEED_UUID) == null;

        equip(player, new ItemStack(ModItems.TRAVELER_HAT.get()));
        TravelerHatHandler.onWornTick(player, TravelerHatHandler.equippedHat(player));
        EarthAmuletSelfTest.hold(50L);
        log(" T17 重新戴上:生命上限=" + value(player, Attributes.MAX_HEALTH) + " [应 = " + preHealth + " + 2×"
            + biomes + "];攻击力=" + value(player, Attributes.ATTACK_DAMAGE) + " [应 = " + preAttack + " + 2×"
            + dims + "]");
        ok &= value(player, Attributes.MAX_HEALTH) - preHealth == 2.0D * biomes
            && value(player, Attributes.ATTACK_DAMAGE) - preAttack == 2.0D * dims;

        FakePlayer reborn = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ea_hat_reborn"));
        reborn.setGameMode(GameType.SURVIVAL);
        reborn.moveTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        log(" T17 Clone 前旧角色数据=" + TravelerHatData.biomeIds(player));
        TravelerHatHandler.onClone(new PlayerEvent.Clone(reborn, player, true));
        int newBiomes = TravelerHatData.biomeCount(reborn);
        int newDims = TravelerHatData.dimensionCount(reborn);
        log(" T17 Clone 后新角色:群系=" + newBiomes + " [应 " + biomes + "];维度=" + newDims + " [应 " + dims
            + "];数据=" + TravelerHatData.biomeIds(reborn));
        ok &= newBiomes == biomes && newDims == dims;

        TravelerHatHandler.onChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD,
            Level.NETHER));
        EarthAmuletSelfTest.hold(50L);
        log(" T17 换维度后:群系=" + TravelerHatData.biomeCount(player) + " [应 >= " + biomes + "];生命上限="
            + value(player, Attributes.MAX_HEALTH) + " [应仍 = " + preHealth + " + 2×"
            + TravelerHatData.biomeCount(player) + "]");
        ok &= TravelerHatData.biomeCount(player) >= biomes
            && value(player, Attributes.MAX_HEALTH) - preHealth == 2.0D * TravelerHatData.biomeCount(player);

        TravelerHatHandler.onDeath(new LivingDeathEvent(player, level.damageSources().generic()));
        EarthAmuletSelfTest.hold(50L);
        log(" T17 死亡事件后:速度修饰符="
            + describe(modifier(player, Attributes.MOVEMENT_SPEED, TravelerHatHandler.SPEED_UUID))
            + " [应 null];数据仍在=" + (TravelerHatData.biomeCount(player) == TravelerHatData.biomeCount(reborn)));
        ok &= modifier(player, Attributes.MOVEMENT_SPEED, TravelerHatHandler.SPEED_UUID) == null
            && TravelerHatData.biomeCount(player) > 0;
        reborn.discard();
        check("T17", ok);
        return ok;
    }
    // ------------------------------------------------------------------ T18 tooltip / 日志

    private static boolean testTooltipAndLogs(ServerLevel level, FakePlayer player) {
        log(" --- T18 tooltip 数字 / debugLog 门控 / ModIds");
        boolean ok = true;
        equip(player, new ItemStack(ModItems.TRAVELER_HAT.get()));
        EarthAmuletSelfTest.hold(50L);
        ItemStack hat = new ItemStack(ModItems.TRAVELER_HAT.get());
        List<Component> tooltip = new java.util.ArrayList<>();
        ((TravelerHatItem) ModItems.TRAVELER_HAT.get()).appendHoverText(hat, level, tooltip,
            TooltipFlag.Default.NORMAL);
        StringBuilder text = new StringBuilder();
        for (Component line : tooltip) {
            text.append(line.getString()).append(" | ");
        }
        int effectLines = Math.max(0, tooltip.size() - 1);
        log(" T18 tooltip=" + text + " [效果行数=" + effectLines + ",应 4(含头饰栏 +1)]");
        ok &= effectLines == 4 && text.indexOf("%s") < 0
            && (text.indexOf("头饰栏") >= 0 || text.indexOf("head slot") >= 0);

        List<String> problems = ModIds.problems();
        log(" T18 ModIds.problems()=" + problems + " [必须空]");
        ok &= problems.isEmpty();

        EarthAmuletSelfTest.setDebugLog(false);
        EarthAmuletSelfTest.hold(300L);
        int quietBefore = EarthAmuletSelfTest.logHits(PROBE);
        unequip(player);
        TravelerHatHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        equip(player, new ItemStack(ModItems.TRAVELER_HAT.get()));
        TravelerHatData.visit(player, id("minecraft:jungle"), id("minecraft:the_end"));
        TravelerHatHandler.onWornTick(player, TravelerHatHandler.equippedHat(player));
        unequip(player);
        EarthAmuletSelfTest.hold(450L);
        int quietAfter = EarthAmuletSelfTest.logHits(PROBE);
        log(" T18 debugLog=false 同动作新增日志行=" + (quietAfter - quietBefore) + " [应为 0]");
        ok &= quietBefore >= 0 && quietAfter >= 0 && quietAfter - quietBefore == 0;

        EarthAmuletSelfTest.setDebugLog(true);
        EarthAmuletSelfTest.hold(300L);
        int loudBefore = EarthAmuletSelfTest.logHits(PROBE);
        TravelerHatHandler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        equip(player, new ItemStack(ModItems.TRAVELER_HAT.get()));
        TravelerHatHandler.onWornTick(player, TravelerHatHandler.equippedHat(player));
        unequip(player);
        EarthAmuletSelfTest.hold(450L);
        int loudAfter = EarthAmuletSelfTest.logHits(PROBE);
        log(" T18 debugLog=true 同动作新增日志行=" + (loudAfter - loudBefore) + " [应 >=1]");
        ok &= loudBefore >= 0 && loudAfter >= 0 && loudAfter - loudBefore >= 1;
        check("T18", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 工具

    private static void resetPlayer(Player player) {
        TravelerHatHandler.ensureHeadSlot(player);
        player.getPersistentData().remove(ROOT);
        unequip(player);
        TravelerHatHandler.removeBonuses(player);
        TravelerHatSync.reset();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }

    /** 试戴到第一个可用的槽(head 优先,没有 head 槽时退回 charm;两头都登记了这件物品)。 */
    private static boolean equip(Player player, ItemStack hat) {
        try {
            return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    for (String slot : new String[] { "head", "charm" }) {
                        var stacks = handler.getCurios().get(slot);
                        if (stacks == null || stacks.getSlots() < 1) {
                            continue;
                        }
                        stacks.getStacks().setStackInSlot(0, hat.copy());
                        if (!TravelerHatHandler.equippedHat(player).isEmpty()) {
                            wornSlot = slot;
                            return true;
                        }
                        stacks.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                    }
                    return false;
                })
                .orElse(false);
        }
        catch (Throwable t) {
            log(" 装备旅行家帽子失败: " + t);
            return false;
        }
    }

    private static void unequip(Player player) {
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                for (String slot : new String[] { "head", "charm" }) {
                    var stacks = handler.getCurios().get(slot);
                    if (stacks != null && stacks.getSlots() > 0) {
                        stacks.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                    }
                }
            });
        }
        catch (Throwable ignored) {
            // 清理用
        }
    }

    private static AttributeInstance instance(Player player, Attribute attribute) {
        return player.getAttribute(attribute);
    }

    private static double value(Player player, Attribute attribute) {
        AttributeInstance instance = instance(player, attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    private static AttributeModifier modifier(Player player, Attribute attribute, UUID uuid) {
        AttributeInstance instance = instance(player, attribute);
        return instance == null ? null : instance.getModifier(uuid);
    }

    private static String describe(AttributeModifier modifier) {
        if (modifier == null) {
            return "null";
        }
        return "+" + modifier.getAmount() + " " + modifier.getOperation();
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
