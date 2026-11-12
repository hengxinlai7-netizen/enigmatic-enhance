package com.hengx.enigmaticenhance;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.1.27a 自检:【附魔师的珍珠】× <b>远古重铸台</b>({@code ancientreforging})。
 * <p>
 * 全部用**真实对象**跑,反射只用于"远古重铸台"这个可能缺席的第三方类:
 * <ol>
 * <li>在服务端放一个 {@code ancientreforging:ancient_reforging_table},用 FakePlayer 的背包
 *     构造真正的 {@code AncientReforgingMenu};</li>
 * <li>槽位按模组自己的定义填:0=待重铸物品({@code itemInv})、1=珍宝材料({@code tile.inv[0]})、
 *     2=新生符石({@code tile.inv[1]});然后走 {@code slotsChanged},让 {@code costs} 由
 *     数据包里的 {@code ancientreforging:reforging/ancient} 配方填充(sigil 10 / mat 3 / level 125);</li>
 * <li>未佩戴珍珠:断言 {@code getSigilCost(0/1/2) = 10/20/30}、{@code mayPickup} 门槛生效、
 *     {@code onTake} 真的把新生符石从 12 扣到 2(材料 5 → 2);</li>
 * <li>戴上七咒之戒 + 珍珠:{@code EnchanterPearl#isPresent} 为真,断言 {@code getSigilCost} 全 0、
 *     符石只有 2 个(不够 10 个)时 {@code mayPickup} 仍为真、{@code onTake} 后符石仍是 2、材料 5 → 2
 *     —— 即"符石不扣、其它照扣";</li>
 * <li>把珍珠摘下(只剩七咒之戒)与把配置 {@code pearl.freeReforge} 关掉,分别断言成本回到 10/20/30
 *     —— 即"未佩戴 / 效果关闭时行为与原来完全一致,不会白送"。</li>
 * </ol>
 * 缺 {@code ancientreforging} 时整项跳过(返回 PASS),不制造假失败。
 */
final class PearlReforgeSelfTest {

    private static final String TAG = "[PearlReforgeSelfTest]";

    /** 目标菜单类(第三方,可能缺席;只用反射,不在编译期引用)。 */
    private static final String MENU_CLASS = "com.ianm1647.ancientreforging.screen.AncientReforgingMenu";
    private static final String TABLE_ID = "ancientreforging:ancient_reforging_table";
    private static final String SIGIL_ID = "apotheosis:sigil_of_rebirth";
    private static final String MAT_ID = "apotheosis:ancient_material";

    /** 数据包 {@code data/ancientreforging/recipes/reforging/ancient.json}:sigil 10 / mat 3 / level 125。 */
    private static final int EXPECT_SIGIL = 10;
    private static final int EXPECT_MAT = 3;
    private static final int EXPECT_LEVEL = 125;

    private static final int Y = 110;

    /** 菜单私有字段/方法(输入槽走模组自己的 InternalItemHandler,避免触发候选刷新)。 */
    private static Field itemInvField;
    private static Method setStackInSlot;

    private PearlReforgeSelfTest() {}

    static boolean run(ServerLevel level) {
        log(" --- T25 附魔师的珍珠 × 远古重铸台(ancientreforging)");
        if (!ModList.get().isLoaded("ancientreforging")) {
            log(" T25 ancientreforging 未安装:跳过(不影响其它自检)");
            return true;
        }

        Method getSigilCost;
        Method getMatCost;
        Method getLevelCost;
        Method slotsChanged;
        Field costsField;
        Constructor<?> ctor;
        try {
            Class<?> menuClass = Class.forName(MENU_CLASS);
            getSigilCost = menuClass.getMethod("getSigilCost", int.class);
            getMatCost = menuClass.getMethod("getMatCost", int.class);
            getLevelCost = menuClass.getMethod("getLevelCost", int.class);
            slotsChanged = findSlotsChanged(menuClass);
            costsField = menuClass.getDeclaredField("costs");
            costsField.setAccessible(true);
            itemInvField = menuClass.getDeclaredField("itemInv");
            itemInvField.setAccessible(true);
            ctor = menuClass.getConstructor(int.class, Inventory.class, BlockPos.class);
        }
        catch (Throwable t) {
            log(" T25 反射解析 " + MENU_CLASS + " 失败: " + t);
            check("T25", false);
            return false;
        }

        Item sigilItem = item(SIGIL_ID);
        Item matItem = item(MAT_ID);
        Block table = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(TABLE_ID));
        log(" T25 依赖: 新生符石=" + (sigilItem == null ? "缺失" : SIGIL_ID)
            + "; 珍宝材料=" + (matItem == null ? "缺失" : MAT_ID)
            + "; 方块=" + (table == null ? "缺失" : TABLE_ID));
        if (sigilItem == null || matItem == null || table == null || slotsChanged == null) {
            log(" T25 远古重铸台的注册内容不完整,无法构造真实菜单");
            check("T25", false);
            return false;
        }

        boolean ok = true;
        boolean initialFree = RingConfig.pearlFreeReforge();
        log(" T25 起始配置 pearl.freeReforge=" + initialFree + " [默认 true]");
        if (!initialFree && !setFreeReforge(true)) {
            log(" T25 无法把 pearl.freeReforge 置为 true,跳过");
            return false;
        }
        try {
            BlockPos pos = new BlockPos(20, Y, 20);
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            clearArea(level, pos);
            level.setBlockAndUpdate(pos, table.defaultBlockState());
            Object tile = level.getBlockEntity(pos);
            log(" T25 方块实体=" + (tile == null ? "null" : tile.getClass().getName()));
            ok &= tile != null;

            FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.randomUUID(), "ee_pearl_reforge"));
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(pos.getX() + 0.5D, Y, pos.getZ() + 2.5D, 0.0F, 0.0F);
            player.getInventory().clearContent();

            Object menu = ctor.newInstance(0, player.getInventory(), pos);
            AbstractContainerMenu container = (AbstractContainerMenu) menu;
            log(" T25 菜单=" + menu.getClass().getName() + ";槽位=" + container.slots.size()
                + "(0 输入 / 1 材料 / 2 符石 / 3-5 产物)");

            // 诊断:可选配置里的 mixin 是否真的合进了这个类;以及它能看到的 player 是不是我们这一个
            boolean injected = false;
            try {
                for (Method method : menu.getClass().getDeclaredMethods()) {
                    if (method.getName().contains("freeAncientSigilReforge")) {
                        injected = true;
                    }
                }
            }
            catch (Throwable ignored) {
                // 签名里有缺失的类(远古重铸台没装)→ 当作未注入
            }
            Object menuPlayer = null;
            try {
                Field playerField = menu.getClass().getDeclaredField("player");
                playerField.setAccessible(true);
                menuPlayer = playerField.get(menu);
            }
            catch (Throwable ignored) {
                // 诊断失败不致命
            }
            log(" T25 诊断: mixin已注入=" + injected + "; menu.player==本玩家=" + (menuPlayer == player)
                + "; freeReforge配置=" + RingConfig.pearlFreeReforge());
            ok &= injected;

            // ---------------- A. 未佩戴珍珠:正常扣费
            levelSet(player, 500);
            fill(container, sigilItem, matItem, 5, 12);
            // 输入槽先留空再刷新成本:菜单会照配方填好 costs,但不会去生成 3 个候选产物
            // (空输入时它只把候选槽清空),这样自检不会触发 Apotheosis 的战利品规则日志。
            boolean recipe = refresh(container, player.getInventory(), slotsChanged, costsField);
            setInput(container, new ItemStack(Items.DIAMOND_SWORD));
            setResult(container);
            int[] costs = readCosts(costsField, menu);
            log(" T25 配方命中=" + recipe + ";costs=[" + costs[0] + "," + costs[1] + "," + costs[2] + "]"
                + " [期望 10,3,125]");
            int base0 = cost(getSigilCost, menu, 0);
            int base1 = cost(getSigilCost, menu, 1);
            int base2 = cost(getSigilCost, menu, 2);
            int sigilBefore = count(container, 2);
            boolean mayBase = mayPickup(container, player);
            container.getSlot(3).onTake(player, ItemStack.EMPTY);
            int sigilAfter = count(container, 2);
            int matAfter = count(container, 1);
            log(" T25[A 未佩戴] 珍珠在场=" + PearlHandler.pearlPresent(player)
                + "; getSigilCost=" + base0 + "/" + base1 + "/" + base2 + " [期望 10/20/30]"
                + "; mayPickup=" + mayBase + " [期望 true]"
                + "; 符石 " + sigilBefore + " -> " + sigilAfter + " [期望 12 -> 2]"
                + "; 材料 5 -> " + matAfter + " [期望 2]");
            ok &= !PearlHandler.pearlPresent(player);
            ok &= base0 == EXPECT_SIGIL && base1 == EXPECT_SIGIL * 2 && base2 == EXPECT_SIGIL * 3;
            if (recipe) {
                ok &= mayBase;
            }
            ok &= sigilBefore == 12 && sigilAfter == 12 - EXPECT_SIGIL && matAfter == 5 - EXPECT_MAT;

            // ---------------- B. 佩戴珍珠:符石不扣,其它照扣
            boolean ring = equip(player, "ring", "enigmaticlegacy:cursed_ring");
            boolean pearl = ring && equip(player, "charm", "enigmaticlegacy:enchanter_pearl");
            boolean present = PearlHandler.pearlPresent(player);
            log(" T25[B 佩戴] 七咒之戒=" + ring + "; 珍珠=" + pearl + "; isPresent=" + present + " [期望 true]");
            ok &= ring && pearl && present;

            levelSet(player, 500);
            fill(container, sigilItem, matItem, 5, 2); // 只有 2 个符石,远不够 10 个
            int free0 = cost(getSigilCost, menu, 0);
            int free1 = cost(getSigilCost, menu, 1);
            int free2 = cost(getSigilCost, menu, 2);
            setResult(container);
            boolean mayFree = mayPickup(container, player);
            int sigilBeforeFree = count(container, 2);
            container.getSlot(3).onTake(player, ItemStack.EMPTY);
            int sigilAfterFree = count(container, 2);
            int matAfterFree = count(container, 1);
            log(" T25[B 佩戴] getSigilCost=" + free0 + "/" + free1 + "/" + free2 + " [期望 0/0/0]"
                + "; mayPickup=" + mayFree + " [期望 true:2 个符石也够]"
                + "; 符石 " + sigilBeforeFree + " -> " + sigilAfterFree + " [期望 2 -> 2]"
                + "; 材料 5 -> " + matAfterFree + " [期望 2:材料照扣]"
                + "; 经验等级=" + player.experienceLevel);
            ok &= free0 == 0 && free1 == 0 && free2 == 0;
            if (recipe) {
                ok &= mayFree;
            }
            ok &= sigilBeforeFree == 2 && sigilAfterFree == 2 && matAfterFree == 5 - EXPECT_MAT;

            // ---------------- C. 摘下珍珠(效果本身关闭):成本立刻回到原样
            boolean removed = unequip(player, "charm");
            int afterUnequip = cost(getSigilCost, menu, 0);
            log(" T25[C 摘下珍珠] 卸载=" + removed + "; isPresent=" + PearlHandler.pearlPresent(player)
                + "; getSigilCost(0)=" + afterUnequip + " [期望 10]");
            ok &= removed && !PearlHandler.pearlPresent(player) && afterUnequip == EXPECT_SIGIL;

            // ---------------- D. 配置 pearl.freeReforge=false(珍珠仍在):同样不白送
            equip(player, "charm", "enigmaticlegacy:enchanter_pearl");
            levelSet(player, 500);
            fill(container, sigilItem, matItem, 5, 2);
            boolean config = setFreeReforge(false);
            int whenOff = cost(getSigilCost, menu, 0);
            setResult(container);
            boolean mayWhenOff = mayPickup(container, player);
            log(" T25[D 效果关闭] 配置写入=" + config + "; isPresent=" + PearlHandler.pearlPresent(player)
                + "; getSigilCost(0)=" + whenOff + " [期望 10]"
                + "; mayPickup=" + mayWhenOff + " [期望 false:只有 2 个符石]");
            ok &= config && whenOff == EXPECT_SIGIL && (!recipe || !mayWhenOff);
            boolean restored = setFreeReforge(true);
            log(" T25[恢复] freeReforge=" + RingConfig.pearlFreeReforge() + " [期望 true];恢复成功=" + restored);
            ok &= restored && RingConfig.pearlFreeReforge();

            // ---------------- E. 公式抽查:getMatCost / getLevelCost 未被本改动影响
            int matCost = invoke(getMatCost, menu, 0);
            int levelCost = invoke(getLevelCost, menu, 0);
            log(" T25[E 其它成本] getMatCost(0)=" + matCost + " [期望 3]; getLevelCost(0)=" + levelCost
                + " [期望 125]");
            ok &= (!recipe || (matCost == EXPECT_MAT && levelCost == EXPECT_LEVEL));

            clean(level, pos);
        }
        catch (Throwable t) {
            log(" T25 执行异常: " + t);
            t.printStackTrace();
            ok = false;
        }
        check("T25", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 菜单操作

    /** {@code slotsChanged}(SRG {@code m_6199_})在运行时/开发环境名字不同,按签名找。 */
    private static Method findSlotsChanged(Class<?> menuClass) {
        try {
            for (Method method : menuClass.getMethods()) {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == Container.class) {
                    return method;
                }
            }
        }
        catch (Throwable ignored) {
            // 签名里有缺失的类 → 当作没找到
        }
        return null;
    }

    /** 填 0/1/2 三个槽,再让菜单自己按材料算一遍 costs;返回"数据包配方是否真的命中"。 */
    private static boolean refresh(AbstractContainerMenu menu, Container container, Method slotsChanged,
            Field costsField) throws Exception {
        setInput(menu, ItemStack.EMPTY);
        slotsChanged.invoke(menu, container);
        int[] costs = readCosts(costsField, menu);
        boolean recipe = costs[0] > 0;
        if (!recipe) {
            // 数据包配方理论上一定在(ancientreforging 自带);万一没有,按它自己的数值注入,
            // 保证"消耗路径"本身仍可被验证(此时跳过依赖配方的 mayPickup 断言)。
            costs[0] = EXPECT_SIGIL;
            costs[1] = EXPECT_MAT;
            costs[2] = EXPECT_LEVEL;
            log(" T25 数据包配方未命中,按 10/3/125 注入 costs 继续测消耗路径");
        }
        return recipe;
    }

    private static void fill(AbstractContainerMenu menu, Item sigil, Item mat, int mats, int sigils)
            throws Exception {
        // 顺序与写法都有讲究:1/2 是模组自己的 UpdatingSlot,set 会回调 slotsChanged;
        // 0 也是 UpdatingSlot(它的 setChanged 同样会触发候选刷新),所以直接写模组的
        // InternalItemHandler,不触发 slotsChanged。这样"输入还空着"时算出来的 costs
        // 依然是真实配方值,又不会去生成 3 个候选产物 —— 自检不会触发 Apotheosis 的战利品规则日志。
        menu.getSlot(1).set(mat == null ? ItemStack.EMPTY : new ItemStack(mat, mats));
        menu.getSlot(2).set(sigil == null ? ItemStack.EMPTY : new ItemStack(sigil, sigils));
        setInput(menu, new ItemStack(Items.DIAMOND_SWORD));
    }

    /** 直接写输入槽的 {@code InternalItemHandler}(绕开 {@code Slot#setChanged} 触发的候选刷新)。 */
    private static void setInput(AbstractContainerMenu menu, ItemStack stack) throws Exception {
        Object inv = itemInvField.get(menu);
        if (setStackInSlot == null) {
            setStackInSlot = inv.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
        }
        setStackInSlot.invoke(inv, 0, stack);
    }

    /**
     * 给"产物槽"放一个占位物品。真实游戏里这三个格子由菜单生成词缀产物;
     * {@code SlotItemHandler#mayPickup} 会先看这一格是否为空,所以不摆一个的话门槛永远是 false。
     */
    private static void setResult(AbstractContainerMenu menu) {
        menu.getSlot(3).set(new ItemStack(Items.DIAMOND_SWORD));
    }

    private static boolean mayPickup(AbstractContainerMenu menu, FakePlayer player) {
        return menu.getSlot(3).mayPickup(player);
    }

    private static int[] readCosts(Field costsField, Object menu) throws Exception {
        return (int[]) costsField.get(menu);
    }

    private static int cost(Method getSigilCost, Object menu, int slot) throws Exception {
        return invoke(getSigilCost, menu, slot);
    }

    private static int invoke(Method method, Object menu, int slot) throws Exception {
        Object value = method.invoke(menu, slot);
        return value instanceof Integer ? (Integer) value : -1;
    }

    private static int count(AbstractContainerMenu menu, int slot) {
        return menu.getSlot(slot).getItem().getCount();
    }

    private static void levelSet(FakePlayer player, int level) {
        player.experienceLevel = level;
        player.experienceProgress = 0.0F;
    }

    // ------------------------------------------------------------------ 珍珠 / 配置

    /** 把指定物品放进 curios 的某个槽(第 0 格)。 */
    private static boolean equip(FakePlayer player, String slot, String itemId) {
        Item item = item(itemId);
        if (item == null) {
            log(" T25 " + itemId + " 不存在");
            return false;
        }
        try {
            return CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> {
                    var curio = handler.getCurios().get(slot);
                    if (curio == null || curio.getStacks().getSlots() <= 0) {
                        return false;
                    }
                    curio.getStacks().setStackInSlot(0, new ItemStack(item));
                    return !curio.getStacks().getStackInSlot(0).isEmpty();
                })
                .orElse(false);
        }
        catch (Throwable t) {
            log(" T25 装备 " + itemId + " 失败: " + t);
            return false;
        }
    }

    private static boolean unequip(FakePlayer player, String slot) {
        try {
            return CuriosApi.getCuriosInventory(player).resolve()
                .map(handler -> {
                    var curio = handler.getCurios().get(slot);
                    if (curio == null) {
                        return false;
                    }
                    curio.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                    return curio.getStacks().getStackInSlot(0).isEmpty();
                })
                .orElse(false);
        }
        catch (Throwable t) {
            log(" T25 卸下 " + slot + " 失败: " + t);
            return false;
        }
    }

    private static boolean setFreeReforge(boolean value) {
        for (int i = 0; i < 10; i++) {
            try {
                RingConfig.PEARL_FREE_REFORGE.set(value);
                if (RingConfig.pearlFreeReforge() == value) {
                    return true;
                }
            }
            catch (Throwable ignored) {
                // 配置还没加载完:等一会儿再试
            }
            EarthAmuletSelfTest.hold(100L);
        }
        return RingConfig.pearlFreeReforge() == value;
    }

    // ------------------------------------------------------------------ 环境

    private static void clearArea(ServerLevel level, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlockAndUpdate(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
    }

    private static void clean(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
    }

    private static void check(String test, boolean ok) {
        log(" " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        DebugLog.info(TAG + message);
    }
}
