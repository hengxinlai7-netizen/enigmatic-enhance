package com.hengx.enigmaticenhance;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/**
 * 「睡前读物」物品本体(1.1.28b)。
 * <p>
 * 一本普通的书:可堆叠 1、无耐久、没有 NBT、没有佩戴槽位。
 * 唯一作用由 {@link BedtimeReadingFix} 实现:把它<b>放进古旧书袋</b>或<b>主手/副手拿着</b>,
 * 同时戴着七咒之戒时,七咒之戒的**第七重咒(失眠)**失效,睡眠可以正常完成;
 * 其余六重咒、七咒之戒本身、书袋与书都一个字节都不碰。
 * <p>
 * 配方:{@code data/enigmaticenhance/recipes/bedtime_reading.json}(书 + 纯净之心,无序)。
 */
public class BedtimeReadingItem extends Item {

    public BedtimeReadingItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    /**
     * 4.6.2(内存):4 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static Component tip(String key, ChatFormatting style) {
        return Component.translatable("tooltip.enigmaticenhance.bedtime_reading." + key).withStyle(style);
    }

    private static final Component HEADER = tip("header", ChatFormatting.DARK_BLUE);
    private static final Component LORE = tip("lore", ChatFormatting.GRAY);
    private static final Component ABILITY = tip("ability", ChatFormatting.AQUA);
    private static final Component OTHERS = tip("others", ChatFormatting.DARK_GRAY);

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(HEADER);
        tooltip.add(LORE);
        tooltip.add(ABILITY);
        tooltip.add(OTHERS);
    }

    /** Patchouli 书 id(见 data/enigmaticenhance/patchouli_books/bedtime_reading)。 */
    private static final ResourceLocation PATCHOULI_BOOK = new ResourceLocation("enigmaticenhance", "bedtime_reading");

    /**
     * 右键翻开:交给 Patchouli 打开这本书(GUI 与内容由 Patchouli 数据驱动)。
     * <p>
     * Patchouli 是<b>软依赖</b>:工程没有它的编译期依赖,所以这里用反射调用
     * {@code vazkii.patchouli.api.PatchouliAPI.get().openBookGUI(ServerPlayer, ResourceLocation)},没有装 mod 时什么也不做。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            openPatchouliBook(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void openPatchouliBook(ServerPlayer player) {
        if (!ModList.get().isLoaded("patchouli")) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Object api = apiClass.getMethod("get").invoke(null);
            apiClass.getMethod("openBookGUI", ServerPlayer.class, ResourceLocation.class)
                .invoke(api, player, PATCHOULI_BOOK);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Patchouli 版本不匹配时静默降级:书依然能放进书袋并生效,只是翻不开
        }
    }
}
