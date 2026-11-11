package com.hengx.enigmaticenhance;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 「大地护符」物品本体(1.1.24)。
 * <p>
 * 无耐久、不可堆叠、走 Curios 的 {@code curios:charm} 槽(槽位由
 * {@code data/curios/tags/items/charm.json} 声明,Curios 5.x 的标准做法)。
 * 初始没有任何效果,NBT 结构见 {@link EarthAmuletData}。
 * <p>
 * 佩戴效果全部委托给 {@link EarthAmuletHandler}(事件优先,仅 3 处 mixin)。
 */
public class EarthAmuletItem extends Item implements ICurioItem {

    public EarthAmuletItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    // ------------------------------------------------------------------ 佩戴

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            EarthAmuletHandler.onWornTick(player, stack);
        }
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack previousStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            EarthAmuletHandler.onWornTick(player, stack);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            EarthAmuletHandler.revokeFlight(player);
        }
    }

    /**
     * 1.2.13g:大地护符**只能佩戴一个**。
     * <p>Curios 本身只按"槽位格子"判空,魅力槽被别的模组加宽后能同时塞两枚,
     * 两枚的效果会叠着走(回血/头颅强化重复触发)。这里在装备入口直接拦掉第二枚。</p>
     */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        try {
            if (!(slotContext.entity() instanceof Player player)) {
                return true;
            }
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(
                    worn -> worn.getItem() == this && worn != stack).isEmpty())
                .orElse(true);
        }
        catch (Throwable t) {
            return true;
        }
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    // ------------------------------------------------------------------ tooltip

    /**
     * 4.6.2(内存):"纯常量"的 tooltip 行只建一次(理由见 {@link DemonHandBoneItem})。
     *
     * <p>头颅行虽然 key 带参数,但可能出现的 lang path 只有有限几个,所以按 path 缓存
     * ({@link #HEAD_LINES});{@code potion} 行的内容是 NBT 里所有效果拼出来的,保持每帧重建。</p>
     */
    private static final Component HEADER =
        Component.translatable("tooltip.enigmaticenhance.earth_amulet.header").withStyle(ChatFormatting.GOLD);
    private static final Component EMPOWERED =
        Component.translatable("tooltip.enigmaticenhance.earth_amulet.empowered")
            .withStyle(ChatFormatting.LIGHT_PURPLE);
    private static final Component EMPTY =
        Component.translatable("tooltip.enigmaticenhance.earth_amulet.empty")
            .withStyle(ChatFormatting.DARK_GRAY);

    /** 头颅 id → 已建好的头颅行(lang path 是有限集合,缓存不会无限长)。 */
    private static final java.util.Map<String, Component> HEAD_LINES = new java.util.concurrent.ConcurrentHashMap<>();

    private static Component headLine(String head) {
        String path = EarthAmuletData.headLangPath(head);
        Component cached = HEAD_LINES.get(path);
        if (cached != null) {
            return cached;
        }
        Component built = Component.translatable("tooltip.enigmaticenhance.earth_amulet.head." + path)
            .withStyle(ChatFormatting.YELLOW);
        HEAD_LINES.put(path, built);
        return built;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER);
        boolean any = false;

        // 4.6.3(帧率):药水效果那一行按"NBT 内容指纹 + 语言实例"缓存。
        // 本方法在鼠标悬停护符期间**每帧**被调用,而原来每帧都要:
        //   effects()(new ArrayList + 每条一个 record)→ 每条 effectName().getString()
        //   (翻译查找 + 建 String)→ StringBuilder → 新的 TranslatableComponent。
        // 内容指纹只读 NBT(id/lvl/heads/empowered),没有分配,所以缓存命中时这一段近乎免费。
        long key = contentKey(stack);
        Language language = Language.getInstance();
        if (key != cachedPotionKey || language != cachedPotionLanguage) {
            cachedPotionLine = buildPotionLine(stack);
            cachedPotionKey = key;
            cachedPotionLanguage = language;
        }
        if (cachedPotionLine != null) {
            tooltip.add(cachedPotionLine);
            any = true;
        }

        // 头颅行:直接遍历 NBT(不再 new 一个 List<String>),每行按 lang path 缓存(见 headLine)。
        CompoundTag tag = stack == null ? null : stack.getTag();
        if (tag != null && tag.contains(EarthAmuletData.TAG_HEADS, Tag.TAG_LIST)) {
            ListTag heads = tag.getList(EarthAmuletData.TAG_HEADS, Tag.TAG_STRING);
            for (int i = 0; i < heads.size(); i++) {
                String head = heads.getString(i);
                if (head.isEmpty()) {
                    continue;
                }
                tooltip.add(headLine(head));
                any = true;
            }
        }
        if (EarthAmuletData.isEmpowered(stack)) {
            tooltip.add(EMPOWERED);
            any = true;
        }
        if (!any) {
            tooltip.add(EMPTY);
        }
    }

    // ------------------------------------------------------------------ 药水效果行的内容缓存(4.6.3)

    /** 上次建"药水效果行"时的内容指纹。 */
    private static long cachedPotionKey = Long.MIN_VALUE;

    /** 上次建行时的语言实例(资源包/语言重载会换实例 ⇒ 自动重建)。 */
    private static Language cachedPotionLanguage;

    /** 缓存的"药水效果行";{@code null} = 这叠护符没有任何可显示的效果。 */
    private static Component cachedPotionLine;

    /** 与 tooltip 内容一一对应的 NBT 指纹(只读,不分配)。 */
    private static long contentKey(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        if (tag == null) {
            return 0L;
        }
        long h = 1125899906842597L;
        if (tag.getBoolean(EarthAmuletData.TAG_EMPOWERED)) {
            h = h * 31L + 1L;
        }
        if (tag.contains(EarthAmuletData.TAG_POTION_EFFECTS, Tag.TAG_LIST)) {
            ListTag effects = tag.getList(EarthAmuletData.TAG_POTION_EFFECTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < effects.size(); i++) {
                CompoundTag one = effects.getCompound(i);
                h = h * 31L + one.getString(EarthAmuletData.KEY_ID).hashCode();
                h = h * 31L + one.getInt(EarthAmuletData.KEY_LVL);
            }
        }
        h = h * 31L + 0x9E3779B97F4A7C15L;   // 分隔,避免 effects 与 heads 的序列混淆
        if (tag.contains(EarthAmuletData.TAG_HEADS, Tag.TAG_LIST)) {
            ListTag heads = tag.getList(EarthAmuletData.TAG_HEADS, Tag.TAG_STRING);
            for (int i = 0; i < heads.size(); i++) {
                h = h * 31L + heads.getString(i).hashCode();
            }
        }
        return h;
    }

    /** 真正建"药水效果行"(只在内容/语言变化时调用一次);没有效果返回 {@code null}。 */
    private static Component buildPotionLine(ItemStack stack) {
        List<EarthAmuletData.StoredEffect> effects = EarthAmuletData.effects(stack);
        if (effects.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (EarthAmuletData.StoredEffect stored : effects) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(effectName(stored.id()).getString()).append(' ').append(roman(stored.level()));
        }
        return Component.translatable("tooltip.enigmaticenhance.earth_amulet.potion", text.toString())
            .withStyle(ChatFormatting.AQUA);
    }

    /** 药水效果显示名(本地化;取不到就退回注册表 id)。 */
    private static Component effectName(String effectId) {
        MobEffect effect = EarthAmuletData.effect(effectId);
        return effect == null ? Component.literal(effectId) : effect.getDisplayName();
    }

    /** 等级 → 罗马数字(I..X,再往上直接用数字)。 */
    private static String roman(int level) {
        String[] numerals = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        if (level >= 1 && level <= numerals.length) {
            return numerals[level - 1];
        }
        return Integer.toString(level);
    }
}
