package com.hengx.enigmaticenhance;

import java.util.List;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 4.5.0:「未完成内容」的统一收口。
 *
 * <h2>这四项分别是什么(在代码里的真实身份)</h2>
 * <table border="1">
 *   <tr><th>中文名</th><th>类型</th><th>注册 id</th><th>实现类</th></tr>
 *   <tr><td>魔法剑气</td><td><b>附魔</b></td><td>{@code enigmaticenhance:magic_sword_aura}</td>
 *       <td>{@link MagicSwordAuraEnchantment} + {@code MagicSwordAuraEntity} + {@link MagicCombatHandler}</td></tr>
 *   <tr><td>虚空剑气</td><td><b>附魔</b></td><td>{@code enigmaticenhance:void_aura}</td>
 *       <td>{@link VoidAuraEnchantment}(效果落在 {@code MagicSwordAuraEntity} 的 voidAura 标记上)</td></tr>
 *   <tr><td>瓶装意识之海</td><td><b>自定义物品</b></td><td>{@code enigmaticenhance:sea_of_consciousness}</td>
 *       <td>{@link ConsciousnessSeaItem} + {@link ConsciousnessSeaHandler}</td></tr>
 *   <tr><td>执行之手</td><td><b>附魔</b></td><td>{@code enigmaticenhance:hand_of_execution}</td>
 *       <td>{@link HandOfExecutionEnchantment} + {@link HandOfExecutionHandler}</td></tr>
 * </table>
 *
 * <h2>名字后缀</h2>
 * <p>「（未完成）」/「(WIP)」<b>只写在语言文件里</b>
 * ({@code enchantment.enigmaticenhance.*} / {@code item.enigmaticenhance.sea_of_consciousness}),
 * 代码里没有任何硬编码字符串,服务端/客户端换语言都一致。</p>
 *
 * <h2>获得途径</h2>
 * <p>开关 {@code [unfinished] allowAcquisition}(默认 <b>false</b>)关闭时:</p>
 * <ul>
 *   <li><b>创造物品栏</b>:{@link CreativeTabContents} 里不再投递瓶装意识之海与执行之手附魔书;</li>
 *   <li><b>附魔台</b>:三个附魔的 {@code canApplyAtEnchantingTable} 一律 false(这是它们唯一的"制作"途径);</li>
 *   <li><b>战利品/随机附魔</b>:三个附魔的 {@code isDiscoverable} 一律 false
 *       (与「执行之手」原本"不出现在战利品里"用的是同一个开关);</li>
 *   <li><b>村民交易</b>:{@code isTradeable} 一律 false;</li>
 *   <li><b>游戏内获取</b>:手持大地之瓶睡觉不再产出瓶装意识之海。</li>
 * </ul>
 *
 * <p><b>已有存档里已经拿到的物品一律不回收</b>:本类不做任何"清理背包/摘附魔"的动作,
 * 关闭开关只影响"新获取",旧物品照常可用、照常显示(带「未完成」后缀)。</p>
 */
public final class UnfinishedContent {

    /** 魔法剑气。 */
    public static final ResourceLocation MAGIC_SWORD_AURA =
        new ResourceLocation(EnigmaticEnhance.MODID, "magic_sword_aura");

    /** 虚空剑气。 */
    public static final ResourceLocation VOID_AURA =
        new ResourceLocation(EnigmaticEnhance.MODID, "void_aura");

    /** 执行之手。 */
    public static final ResourceLocation HAND_OF_EXECUTION =
        new ResourceLocation(EnigmaticEnhance.MODID, "hand_of_execution");

    /** 瓶装意识之海。 */
    public static final ResourceLocation SEA_OF_CONSCIOUSNESS =
        new ResourceLocation(EnigmaticEnhance.MODID, "sea_of_consciousness");

    /** 三个未完成附魔的 id。 */
    public static final Set<ResourceLocation> UNFINISHED_ENCHANTMENTS =
        Set.of(MAGIC_SWORD_AURA, VOID_AURA, HAND_OF_EXECUTION);

    /** tooltip 提示行(灰)。 */
    public static final String HINT_KEY = "tooltip.enigmaticenhance.unfinished.hint";

    /** 三个未完成附魔的 id 文本(与 {@link #UNFINISHED_ENCHANTMENTS} 一一对应,类初始化时算好一次)。 */
    private static final String MAGIC_SWORD_AURA_TEXT = MAGIC_SWORD_AURA.toString();
    private static final String VOID_AURA_TEXT = VOID_AURA.toString();
    private static final String HAND_OF_EXECUTION_TEXT = HAND_OF_EXECUTION.toString();

    /** 瓶装意识之海的物品实例(懒解析;解析到就缓存,避免每帧反查注册表)。 */
    private static Item seaItem;

    private UnfinishedContent() {
    }

    // ------------------------------------------------------------------ 配置

    /** 是否允许获得这些未完成内容(默认 false)。 */
    public static boolean allowAcquisition() {
        try {
            return ItemConfig.unfinishedAllowAcquisition();
        }
        catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 判定

    /** 这个附魔是不是未完成内容。 */
    public static boolean isUnfinished(Enchantment enchantment) {
        if (enchantment == null) {
            return false;
        }
        try {
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
            return id != null && UNFINISHED_ENCHANTMENTS.contains(id);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * 这叠物品是不是瓶装意识之海。
     *
     * <p>4.6.1(帧率):原来是每次都 {@code ForgeRegistries.ITEMS.getKey(stack.getItem())} 反查注册表 +
     * {@link ResourceLocation#equals}{@code (id)}。本方法在 {@link #onTooltip} 里对<b>每一个</b>
     * tooltip 都会被调用(= 悬停在任意物品上时每帧一次),反查注册表是纯浪费。现在物品实例懒解析一次并缓存,
     * 之后只做一次引用比较;解析不到时退回原来的注册表反查(语义完全一致)。</p>
     */
    public static boolean isUnfinishedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            Item item = stack.getItem();
            Item cached = seaItem;
            if (cached != null) {
                return item == cached;
            }
            cached = ModItems.seaOfConsciousnessOrNull();
            if (cached != null) {
                seaItem = cached;
                return item == cached;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            return SEA_OF_CONSCIOUSNESS.equals(id);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * 4.6.1(帧率):判断这叠物品是否带着三个"未完成"附魔之一 —— <b>不建 Map、不查注册表、零分配</b>。
     *
     * <p>原来 {@link #onTooltip} 对每个被悬停的物品都调 {@link #hasUnfinishedEnchant},而它内部的
     * {@code EnchantmentHelper.getEnchantments(stack)} 会:读 NBT → 新建一个 {@code HashMap}
     * → 逐条反查附魔注册表 → 再对每个附魔反查 id。物品一旦被悬停,这段代码就<b>每帧</b>跑一次
     * (60~200 次/秒),绝大多数物品根本没有附魔,等于每秒几十~几百个纯垃圾对象。
     *
     * <p>这里直接读 {@code Enchantments} / {@code StoredEnchantments} 这两个原版键(与
     * {@code EnchantmentHelper#getEnchantments} 读取的是同一对键),逐条把 {@code id} 字符串和
     * 三个目标 id 直接比较:没有这两个键时是一次 {@code contains} 判断(不分配);有键时只做字符串比较。
     * 判定结果与"反序列化后 {@code keySet()} 里是否有目标附魔"严格等价。</p>
     */
    public static boolean hasUnfinishedEnchantRaw(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        // 与 ItemStack#getEnchantmentTags 的取值顺序一致:有 Enchantments 就只看它,
        // 否则才看 StoredEnchantments(附魔书走后者)。
        if (tag.contains("Enchantments", Tag.TAG_LIST)) {
            return listHasUnfinished(tag, "Enchantments");
        }
        return listHasUnfinished(tag, "StoredEnchantments");
    }

    /** 读一个附魔列表键,看里面有没有三个目标 id 之一(不建中间集合)。 */
    private static boolean listHasUnfinished(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return false;
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getCompound(i).getString("id");
            if (MAGIC_SWORD_AURA_TEXT.equals(id) || VOID_AURA_TEXT.equals(id)
                || HAND_OF_EXECUTION_TEXT.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** 这叠物品上是否带着任意一个未完成附魔(剑 / 附魔书 / 任意被塞进去的物品)。 */
    public static boolean hasUnfinishedEnchant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
                if (isUnfinished(enchantment)) {
                    return true;
                }
            }
            return false;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------ tooltip

    /**
     * 给这四类内容追加一行灰色提示「· 该内容尚未完成，仅供测试」。
     * <p>只加在<b>这四项自己</b>的说明里:瓶装意识之海本体、以及带着这三个附魔的物品
     * (附魔书也走这条路径,因为附魔名字本来就在书自己的 tooltip 里)。</p>
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        try {
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty()) {
                return;
            }
            // 4.6.1(帧率):先做两次**零分配**判断,再决定要不要往下走。
            // 原来这里对任何被悬停的物品都会跑一遍 EnchantmentHelper.getEnchantments(stack)
            // (新建 HashMap + 反查注册表),而本事件在悬停期间**每帧**触发。
            if (!isUnfinishedItem(stack) && !hasUnfinishedEnchantRaw(stack)) {
                return;
            }
            List<Component> lines = event.getToolTip();
            Component hint = Component.translatable(HINT_KEY).withStyle(ChatFormatting.GRAY);
            String text = hint.getString();
            for (Component line : lines) {
                if (line != null && text.equals(line.getString())) {
                    return;   // 只加一次(防止多个 tooltip 事件叠加)
                }
            }
            lines.add(hint);
        }
        catch (RuntimeException | LinkageError ignored) {
            // tooltip 失败不影响物品
        }
    }
}
