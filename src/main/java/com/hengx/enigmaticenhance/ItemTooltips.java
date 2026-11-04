package com.hengx.enigmaticenhance;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 给被本模组改动过的**原版物品**追加说明(1.1.25d 起精简,1.1.25f 起带来源名与状态)。
 * <ul>
 * <li>每行统一前缀 {@code · [神秘遗物增强] }(英文 {@code [Enigmatic Legacy Enhance]}),一眼看出是本模组加的;</li>
 * <li>每个物品最多 2 行,只写"改了什么",不写原因/机制;</li>
 * <li>**逐件开关**(状态存在物品 NBT 上、Ctrl+左键 切换)的条目,行尾必须带实时状态
 *     {@code 当前:开启(Ctrl+左键 切换)} / {@code 当前:关闭(Ctrl+左键 切换)};</li>
 * <li>没有逐件开关的条目(纯全局配置)不加状态;古旧书袋的 {@code baghud.*} 说明保持原样。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemTooltips {

    private static final ChatFormatting BODY = ChatFormatting.GRAY;

    private ItemTooltips() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        List<Component> lines = event.getToolTip();

        if (isRing(item)) {
            List<Component> parts = new ArrayList<>();
            if (RingConfig.ringUnbreakable()) {
                parts.add(line("add.ring.unbreakable"));
            }
            if (RingConfig.allowRingWithCursedRing()) {
                parts.add(line("add.ring.withCursed"));
            }
            if (RingConfig.hideBetrayalBar()) {
                parts.add(line("add.ring.hideBar"));
            }
            add(lines, join(parts, ";"));
        }
        else if (isCursedRing(item)) {
            List<Component> parts = new ArrayList<>();
            if (RingConfig.allowRingWithCursedRing()) {
                parts.add(line("add.cursed.withBless"));
            }
            if (RingConfig.fourthCurseFixEnabled()) {
                parts.add(body("add.cursed.fourthCurse", FourthCurseFix.residualPercent()));
            }
            add(lines, join(parts, ";"));
        }
        else if (isEngine(item)) {
            if (RingConfig.disableEngineLightningItemClear()) {
                add(lines, line("add.engine.lightning"));
            }
        }
        else if (RingConfig.isGluttonyCharm(item)) {
            if (RingConfig.gluttonyEnabled()) {
                add(lines, body("add.gluttony", RingConfig.gluttonyLockedFoodLevel(),
                    trim(RingConfig.gluttonyLockedSaturation())));
            }
        }
        else if (RingConfig.isEnchanterPearl(item)) {
            // 1.1.25g:最多 2 行 —— 第一行写效果(附魔台/重铸),第二行写前置条件。
            List<Component> parts = new ArrayList<>();
            if (RingConfig.pearlFixEnchantingTable()) {
                net.minecraft.network.chat.MutableComponent enchant = line("add.pearl.enchant").copy();
                enchant.append(Component.translatable("tooltip.enigmaticenhance.add.pearl.apotheosis"));
                parts.add(enchant);
            }
            if (RingConfig.pearlFreeReforge()) {
                parts.add(line("add.pearl.reforge"));
            }
            Component effects = join(parts, ";");
            if (effects != null) {
                add(lines, effects);
                add(lines, line("add.pearl.condition"));
            }
        }
        else if (RingConfig.isAdventureCharm(item)) {
            List<Component> parts = new ArrayList<>();
            if (RingConfig.adventureCharmAllowMining() && RingConfig.miningTooltip()) {
                parts.add(line("add.mining"));
            }
            if (RingConfig.convertOnDeath()) {
                parts.add(line("add.adventure.convert"));
            }
            add(lines, join(parts, ";"));
        }
        else if (RingConfig.isDespairInsignia(item)) {
            if (RingConfig.despairInsigniaAllowMining() && RingConfig.miningTooltip()) {
                add(lines, line("add.mining"));
            }
        }
        else if (RingConfig.isThunderScroll(item)) {
            if (RingConfig.scaleEnabled()) {
                // 扩展那句"横扫攻击造成 100% 伤害"已经被我们改掉了,先从提示里去掉,免得自相矛盾。
                String stale = Component.translatable("tooltip.enigmaticaddons.thunderScroll1").getString();
                lines.removeIf(line -> stale.equals(line.getString()));
                add(lines, body("add.thunder", percent(RingConfig.scaleAttackDamagePercent() / 100.0D),
                    state(!ScrollHandler.isLightningDisabled(stack))));
                if (RingConfig.scaleChargeSpread()) {
                    add(lines, line("add.thunder.spread"));
                }
            }
        }
        else if (RingConfig.isAntiqueBag(item)) {
            // 1.1.25d 起:古旧书袋的说明保持原样(用户明确要求不要动),连分隔空行都不变。
            if (RingConfig.bagTooltipEnabled()) {
                lines.add(Component.empty());
                lines.add(Component.translatable("baghud.title").withStyle(ChatFormatting.AQUA));
                if (!BagHudSync.synced()) {
                    lines.add(Component.translatable("baghud.unsynced").withStyle(BODY));
                }
                else if (BagHudSync.cache().isEmpty()) {
                    lines.add(Component.translatable("baghud.empty").withStyle(BODY));
                }
                else {
                    for (BagHudEffects.Entry entry : BagHudSync.cache()) {
                        lines.add(BagHudEffects.display(entry)
                            .withStyle(entry.active() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
                    }
                    lines.add(Component.translatable("baghud.summary",
                        String.valueOf(BagHudEffects.damageBonusSum(BagHudSync.cache())),
                        String.valueOf(BagHudEffects.activeCount(BagHudSync.cache())))
                        .withStyle(ChatFormatting.GOLD));
                }
            }
        }
        else if (RingConfig.isBerserkCharm(item)) {
            if (RingConfig.bloodbathLockEnabled()) {
                add(lines, body("add.berserk", healthText(BloodbathHandler.lockValue()),
                    state(!BloodbathHandler.isLockDisabled(stack))));
            }
        }
        else if (BooksHandler.isTargetBook(stack)) {
            if (RingConfig.booksEnabled()) {
                if (BooksHandler.isDisabled(stack)) {
                    add(lines, line("add.books.disabled"));
                    lines.set(0, lines.get(0).copy().withStyle(BODY));
                }
                else if (RingConfig.booksToggleEnabled()) {
                    add(lines, line("add.books.toggle"));
                }
            }
        }
        else if (RingConfig.isEarthHeart(item)) {
            // 1.2.2:深渊侵蚀态是叠在「大地之心」这个物品上的一个标记,所以说明也写在这个物品上
            // (与本体「受污染的大地之心」只多一行说明的做法一致,不新增物品、不改名字)。
            if (RingConfig.abyssalEarthHeartEnabled() && AbyssalEarthHeart.isAbyssalTainted(stack)) {
                add(lines, line("add.earthHeart.abyssal"));
            }
        }
    }

    /** 追加一行 {@code · [神秘遗物增强] …}(空内容直接跳过,绝不产生空行)。 */
    private static void add(List<Component> lines, Component body) {
        if (body == null) {
            return;
        }
        String text = body.getString();
        if (text == null || text.isBlank()) {
            return;
        }
        lines.add(Component.translatable("tooltip.enigmaticenhance.added", body).withStyle(BODY));
    }

    /** 一条"改了什么"的正文(不带前缀)。 */
    private static Component line(String key) {
        return Component.translatable("tooltip.enigmaticenhance." + key);
    }

    /** 带参数的正文。 */
    private static Component body(String key, Object... args) {
        return Component.translatable("tooltip.enigmaticenhance." + key, args);
    }

    /** 逐件开关的实时状态。 */
    private static Component state(boolean on) {
        return Component.translatable(on
            ? "tooltip.enigmaticenhance.state.on"
            : "tooltip.enigmaticenhance.state.off");
    }

    /** 把若干片段拼成一段。 */
    private static Component join(List<Component> parts, String separator) {
        if (parts.isEmpty()) {
            return null;
        }
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(separator));
            }
            out.append(parts.get(i));
        }
        return out;
    }

    private static String percent(double value) {
        double pct = value * 100.0D;
        if (pct == Math.floor(pct)) {
            return String.format(Locale.ROOT, "%d", (long) pct);
        }
        return String.format(Locale.ROOT, "%.1f", pct);
    }

    /** 生命值/饱和度的短文本(1.0 → "1",1.5 → "1.5")。 */
    private static String healthText(float value) {
        if (value == Math.floor(value)) {
            return String.format(Locale.ROOT, "%d", (long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String trim(float value) {
        return healthText(value);
    }

    private static boolean isRing(Item item) {
        Item ring = RingConfig.ringItem();
        return ring != null && item == ring;
    }

    private static boolean isCursedRing(Item item) {
        Item cursed = RingConfig.cursedRingItem();
        return cursed != null && item == cursed;
    }

    private static boolean isEngine(Item item) {
        Item engine = RingConfig.engineItem();
        return engine != null && item == engine;
    }
}
