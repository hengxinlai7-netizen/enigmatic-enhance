package com.hengx.enigmaticenhance;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * 【自动熔炼】enigmaticenhance:auto_smelt(中文「自动熔炼」)。
 * <p>
 * 语义(与奇妙附魔 wonderenchant:auto_smelt 完全一致,见 {@link AutoSmeltHandler}):
 * 玩家挖掘产生的掉落,逐个按<b>普通熔炉配方</b>(只查 {@code RecipeType.SMELTING})熔炼后照常掉落;
 * 查不到配方、或产物与输入是同一种物品 ⇒ 原样保留(绝不因为熔炼让物品消失)。
 * <p>
 * 属性(1.20.1 没有数据驱动附魔,这里是"数据驱动字段"的代码等价物;同样的字段也写在
 * {@code data/enigmaticenhance/enchantment/auto_smelt.json} 里作为声明式说明/向前兼容):
 * <ul>
 * <li>{@code max_level = 1}、UNCOMMON(权重 5)、{@code slots = [mainhand]} —— 见构造器与 {@link #getMaxLevel()};</li>
 * <li>{@code supported_items / primary_items} = 镐/斧/锹({@link #isAllowedTool(ItemStack)};类目仍是
 *     {@link EnchantmentCategory#DIGGER},但 1.2.5a 起<b>排除锄</b>,只认镐/斧/锹),即 {@link #canEnchant(ItemStack)} 只认这三类;</li>
 * <li>获取途径(1.2.2b 起与 1.2.1 说明对齐,不再只有战利品):
 *     附魔台随机池({@code isDiscoverable = true} + {@link #canApplyAtEnchantingTable(ItemStack)} = true)、
 *     铁砧({@code isAllowedOnBooks = true})、村民交易({@code isTradeable = true})、
 *     以及原来的战利品附魔书({@link AutoSmeltLoot})。<b>物品类型只限镐/斧/锹</b> ——
 *     那由 {@link #canEnchant(ItemStack)} 管,和"能不能拿到"是两件事;</li>
 * <li>想退回旧版"只有战利品"的行为:配置 {@code autoSmelt.lootOnlyAcquisition = true};</li>
 * <li>{@code anvil_cost = 2}、{@code weight = 5} 在 1.20.1 由 {@link Enchantment.Rarity#UNCOMMON} 与
 *     Forge 的附魔逻辑体现(1.20.1 的 {@code Enchantment} 没有 {@code getAnvilCost()});</li>
 * <li>{@code exclusive_set}:只与"明显无关"的附魔互斥({@link #checkCompatibility}),
 *     与时运/精准采集/效率等挖掘相关附魔<b>全部兼容</b>。</li>
 * </ul>
 */
public class AutoSmeltEnchantment extends Enchantment {

    /**
     * 只与"明显无关"的附魔互斥:三叉戟/弓/弩/钓竿/靴子专属这些,永远不可能出现在挖掘工具上。
     * 挖掘相关的(效率、时运、精准采集、耐久、经验修补)一个都不排除。
     */
    private static final Enchantment[] OBVIOUSLY_UNRELATED = {
        Enchantments.LOYALTY,
        Enchantments.CHANNELING,
        Enchantments.RIPTIDE,
        Enchantments.IMPALING,
        Enchantments.POWER_ARROWS,
        Enchantments.PUNCH_ARROWS,
        Enchantments.FLAMING_ARROWS,
        Enchantments.INFINITY_ARROWS,
        Enchantments.MULTISHOT,
        Enchantments.QUICK_CHARGE,
        Enchantments.PIERCING,
        Enchantments.FISHING_LUCK,
        Enchantments.FISHING_SPEED,
        Enchantments.FROST_WALKER,
        Enchantments.SOUL_SPEED,
        Enchantments.SWIFT_SNEAK
    };

    public AutoSmeltEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.DIGGER, new EquipmentSlot[] { EquipmentSlot.MAINHAND });
    }

    /** max_level = 1(命令/NBT 塞进来更高等级也一律按 1 级处理,见 {@link AutoSmeltHandler#hasEnchant})。 */
    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    /** 附魔台不会用到(见 canApplyAtEnchantingTable),这两个值只影响铁砧/命令的"等级成本"显示。 */
    @Override
    public int getMinCost(int level) {
        return 1;
    }

    @Override
    public int getMaxCost(int level) {
        return 41;
    }

    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    /** 附魔台候选列表按它过滤;1.2.2b 起改为可发现(与 1.2.1 说明「已放开获取限制」对齐)。 */
    @Override
    public boolean isDiscoverable() {
        return acquisitionOpen();
    }

    /**
     * 第 2 道门:与 {@link #canEnchant(ItemStack)} 用同一个判定(镐/斧/锹),不能直接调 canEnchant ——
     * Forge 把 {@code Enchantment#canEnchant} 改成会回调本方法,互相调就是无限递归。
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return acquisitionOpen() && isAllowedTool(stack);
    }

    /** 村民是否可能卖;1.2.2b 起可以。 */
    @Override
    public boolean isTradeable() {
        return acquisitionOpen();
    }

    /**
     * 获取途径是否放开(附魔台 / 铁砧 / 村民 / 发现)。
     * <p>
     * 配置 {@code autoSmelt.lootOnlyAcquisition = true} 时整体退回旧版"只有战利品附魔书"的行为 ——
     * 三个方法的判定都走这里,不会出现"村民卖了但附魔台不给"这种半开状态。
     */
    public static boolean acquisitionOpen() {
        try {
            return !RingConfig.autoSmeltLootOnly();
        }
        catch (Throwable t) {
            return true;
        }
    }

    /** 战利品拿到的是附魔书,必须允许在铁砧上用。 */
    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    /**
     * 只认<b>镐 / 斧 / 锹</b>(1.2.5a 起把锄排除掉)。
     *
     * <p>判定同时看原版物品标签({@code minecraft:pickaxes / axes / shovels},模组工具一般挂在这三个标签上)
     * 和 {@code PickaxeItem/AxeItem/ShovelItem} 类型,命中任一即可 —— 前者覆盖"标签对但没继承原版类"的模组工具,
     * 后者覆盖"没写标签但是原版工具类"的情况。</p>
     */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return isAllowedTool(stack);
    }

    /** 物品是否是自动熔炼允许的工具(镐 / 斧 / 锹,不含锄)。纯函数,自检可直接断言。 */
    public static boolean isAllowedTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            if (stack.is(net.minecraft.tags.ItemTags.PICKAXES) || stack.is(net.minecraft.tags.ItemTags.AXES)
                || stack.is(net.minecraft.tags.ItemTags.SHOVELS)) {
                return true;
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 标签未加载(极早期调用):退回类型判定
        }
        net.minecraft.world.item.Item item = stack.getItem();
        return item instanceof net.minecraft.world.item.PickaxeItem
            || item instanceof net.minecraft.world.item.AxeItem
            || item instanceof net.minecraft.world.item.ShovelItem;
    }

    /**
     * 只与明显无关的附魔互斥。{@link Enchantment#isCompatibleWith(Enchantment)} 在 1.20.1 是 final,
     * 所以真正可覆写的是这个钩子。
     */
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        if (!super.checkCompatibility(other)) {
            return false;
        }
        for (Enchantment unrelated : OBVIOUSLY_UNRELATED) {
            if (unrelated == other) {
                return false;
            }
        }
        return true;
    }

    /** 显示恒定 1 级(直接调 super,避免回调自己造成递归)。 */
    @Override
    public Component getFullname(int level) {
        return super.getFullname(1);
    }
}
