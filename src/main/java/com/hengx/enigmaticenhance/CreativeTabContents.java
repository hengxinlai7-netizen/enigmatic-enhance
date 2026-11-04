package com.hengx.enigmaticenhance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 把本模组注册的物品挂进**创造模式物品栏**(1.1.25a 修复:之前只注册了物品,没有挂载标签页,
 * 所以创造模式里搜不到)。
 * <p>
 * 为什么挂在原版 {@code TOOLS_AND_UTILITIES}(工具与实用物品)而不是自建标签页:
 * <ul>
 * <li>本模组一共只有 2 件物品(见 {@link #items()}),为 2 件饰品单开一个标签页反而更难找,
 *     而且会多一个只有 2 格的标签页;自建页通常留给内容量足够大的模组;</li>
 * <li>原版没有"饰品/护符"分类;Curios 类饰品在整合包里普遍落在 {@code TOOLS_AND_UTILITIES}
 *     (工具与实用物品),和"实用小件"的语义最接近;{@code COMBAT} 只适合纯战斗装备;</li>
 * <li>用 {@code BuildCreativeModeTabContentsEvent} 追加而不是新建标签页 ⇒ 与其它模组共存、
 *     不改变原版页顺序,也不会影响任何既有 id/数据。</li>
 * </ul>
 * 该方法在**模组事件总线**上触发(见 {@link EnigmaticEnhance} 的 {@code modBus.register}),
 * 且只对上面这一个标签页生效;其它标签页原样不动。
 */
public final class CreativeTabContents {

    /** 目标标签页:工具与实用物品。 */
    private static final ResourceKey<CreativeModeTab> TARGET_TAB = CreativeModeTabs.TOOLS_AND_UTILITIES;

    /** 把本模组的三个效果做成 药水/喷溅/滞留 放进创造栏。 */
    private static void addPotions(java.util.List<Item> items) {
        if (ModPotions.MIRROR_SHATTER.isPresent()) {
            addPotionItems(items);
        }
        if (ModPotions.BLOOD_CALAMITY.isPresent()) {
            addPotionItems(items);
        }
        if (ModPotions.MERCURY_SERPENT.isPresent()) {
            addPotionItems(items);
        }
    }

    private static void addPotionItems(java.util.List<Item> items) {
        items.add(net.minecraft.world.item.Items.POTION);
        items.add(net.minecraft.world.item.Items.SPLASH_POTION);
        items.add(net.minecraft.world.item.Items.LINGERING_POTION);
    }
    private CreativeTabContents() {}

    /** 是否是我们挂载的目标标签页(自检直接断言)。 */
    public static boolean isTargetTab(ResourceKey<CreativeModeTab> key) {
        return TARGET_TAB.equals(key);
    }

    /** 本模组**全部**已注册物品(1.2.5c 起 9 件:大地护符、恶魔契约、旅行家帽子、恶魔血杯、睡前读物、纯净符文、恶魔的手骨、大地之瓶、瓶装历史迷雾)。 */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        Item amulet = ModItems.earthAmuletOrNull();
        Item pact = ModItems.demonPactOrNull();
        Item hat = ModItems.travelerHatOrNull();
        Item chalice = ModItems.demonBloodChaliceOrNull();
        Item bedtime = ModItems.bedtimeReadingOrNull();
        Item rune = ModItems.pureRuneOrNull();
        Item ledger = ModItems.fakeLedgerOrNull();
        Item bottle = ModItems.earthBottleOrNull();
        Item mist = ModItems.historyMistOrNull();
        Item spirit = ModItems.spiritLightOrNull();
        Item styx = ModItems.styxWaterOrNull();
        Item fog = ModItems.warFogOrNull();
        Item sun = ModItems.sunBottleOrNull();
        Item fog2 = ModItems.uncertainFogOrNull();
        Item mirror = ModItems.mirrorShadowOrNull();
        Item bone = ModItems.demonHandBoneOrNull();
        Item meter = ModItems.lifeMeterOrNull();
        Item charm = ModItems.sharpshooterCharmOrNull();
        Item banner = ModItems.WAR_BANNER.get();
        Item sea = ModItems.SEA_OF_CONSCIOUSNESS.get();
        Item editor = ModItems.VALUE_EDITOR.get();
        Item fishing = ModItems.FISHING_HAUL_BOTTLE.get();
        Item leafStone = ModItems.revivalLeafStoneOrNull();
        if (amulet != null) {
            items.add(amulet);
        }
        if (pact != null) {
            items.add(pact);
        }
        if (hat != null) {
            items.add(hat);
        }
        if (chalice != null) {
            items.add(chalice);
        }
        if (bedtime != null) {
            items.add(bedtime);
        }
        if (rune != null) {
            items.add(rune);
        }
        if (ledger != null) {

            items.add(ledger);

        }
        if (bone != null) {
            items.add(bone);
        }
        if (meter != null) {
            items.add(meter);
        }
        if (charm != null) {
            items.add(charm);
        }
        if (banner != null) {
            items.add(banner);
        }
        if (editor != null) {
            items.add(editor);
            items.add(sea);
        }
        // 1.3.4:自定义效果药水(普通/喷溅/滞留)都放进创造栏
        addPotions(items);
        if (bottle != null) {
            items.add(bottle);
        }
        if (mist != null) {
            items.add(mist);
        }
        if (spirit != null) {
            items.add(spirit);
        }
        if (styx != null) {
            items.add(styx);
        }
        if (fog != null) {
            items.add(fog);
        }
        if (sun != null) {
            items.add(sun);
        }
        if (fog2 != null) {
            items.add(fog2);
        }
        if (mirror != null) {
            items.add(mirror);
        }
        if (fishing != null) {
            items.add(fishing);
        }
        if (leafStone != null) {
            items.add(leafStone);
        }
        // 2.5.0:隐藏被禁用的物品
        if (ItemConfig.hideDisabledFromTab()) {
            var disabled = ItemConfig.disabledItemIds();
            items.removeIf(it -> {
                var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it);
                return id != null && disabled.contains(id.toString());
            });
        }

        return items;
    }

    /** 把物品追加进目标标签页(父页 + 搜索都可见)。 */
    static void addTo(CreativeModeTab.Output output) {
        for (Item item : items()) {
            output.accept(new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        // 2.9.0:「执行之手」—— 附魔书形式,只能靠创造模式取用
        try {
            HandOfExecutionEnchantment ench = ModEnchantments.handOfExecutionOrNull();
            if (ench != null) {
                ItemStack book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
                book.enchant(ench, 1);
                output.accept(book, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                DebugLog.info("[CreativeTab] 已挂载 执行之手 附魔书");
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }

    /** 条目里是否已经有某个物品(自检用)。 */
    static boolean contains(ResourceKey<CreativeModeTab> key, Item item,
        Map<ItemStack, CreativeModeTab.TabVisibility> entries) {
        if (!isTargetTab(key)) {
            return false;
        }
        for (ItemStack stack : entries.keySet()) {
            if (stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        try {
            if (!isTargetTab(event.getTabKey())) {
                return;
            }
            addTo(event);
            DebugLog.info("{} 创造标签页 {}:已挂载 {}", "[CreativeTab]", event.getTabKey().location(),
                items().size());
        }
        catch (Throwable t) {
            DebugLog.error("[CreativeTab] 挂载创造标签页异常", t);
        }
    }
}
