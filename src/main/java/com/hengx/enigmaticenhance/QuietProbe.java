package com.hengx.enigmaticenhance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;

/**
 * **仅用于发版验证**的静默探针(1.1.26 起)。
 * <p>
 * 只有当 JVM 参数 {@code -Denigmaticenhance.quietprobe=<输出文件>} 存在时才动作;生产环境永远不会设置它,
 * 因此对玩家/存档没有任何影响,也不会打印任何日志。
 * <p>
 * 它做的事:放方块→挖掉若干方块(触发挖掘类事件)、给若干物品各取一次 tooltip(触发说明追加)、
 * 触发一次"打开容器界面"事件、给旅行家帽子跑一次佩戴 tick。跑完只写一个**结果文件**(给发版验证看),
 * 用来证明"挖方块 + 开界面"这些操作不会产生 {@code [enigmaticenhance/]} 日志行。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuietProbe {

    public static final String PROPERTY = "enigmaticenhance.quietprobe";

    private QuietProbe() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String out = System.getProperty(PROPERTY);
        if (out == null || out.isBlank()) {
            return;
        }
        try {
            run(event.getServer(), Path.of(out));
        }
        catch (Throwable t) {
            // 探针失败也不往日志里写(发版验证只看结果文件与日志行数)
            try {
                Files.writeString(Path.of(out + ".error"), String.valueOf(t), StandardCharsets.UTF_8);
            }
            catch (Throwable ignored) {
                // 忽略
            }
        }
    }

    private static void run(MinecraftServer server, Path out) throws Exception {
        ServerLevel level = server.overworld();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
            UUID.nameUUIDFromBytes("enigmaticenhance_quiet_probe".getBytes(StandardCharsets.UTF_8)),
            "ee_quiet_probe"));
        player.setPos(0.5D, 100.0D, 0.5D);

        int broken = 0;
        for (int i = 0; i < 8; i++) {
            BlockPos pos = new BlockPos(i, 120, 0);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            if (level.destroyBlock(pos, false, player)) {
                ++broken;
            }
        }

        int tooltips = 0;
        List<String> ids = List.of(
            "enigmaticenhance:earth_amulet", "enigmaticenhance:demon_pact", "enigmaticenhance:traveler_hat",
            "enigmaticaddons:bless_ring", "enigmaticlegacy:cursed_ring", "enigmaticlegacy:berserk_charm",
            "enigmaticaddons:thunder_scroll", "enigmaticlegacy:enchanter_pearl", "enigmaticaddons:lost_engine",
            "enigmaticaddons:adventure_charm", "enigmaticaddons:despair_insignia", "enigmaticlegacy:the_twist");
        for (String id : ids) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                net.minecraft.resources.ResourceLocation.tryParse(id));
            if (item == null) {
                continue;
            }
            List<Component> tooltip = new ArrayList<>();
            ItemTooltips.onItemTooltip(new ItemTooltipEvent(new ItemStack(item), player, tooltip,
                TooltipFlag.Default.NORMAL));
            ++tooltips;
        }

        // 模拟"打开一次界面"
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, player.inventoryMenu));

        // 模拟佩戴一次旅行家帽子(走 tick 路径)
        TravelerHatHandler.onWornTick(player, ModItems.travelerHatOrNull() == null
            ? ItemStack.EMPTY
            : new ItemStack(ModItems.travelerHatOrNull()));

        String report = "{\"blocksBroken\":" + broken
            + ",\"tooltipsBuilt\":" + tooltips
            + ",\"containerOpened\":true"
            + ",\"hatTick\":true}";
        Files.writeString(out, report, StandardCharsets.UTF_8);
    }
}
