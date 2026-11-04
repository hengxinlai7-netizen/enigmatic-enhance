package com.hengx.enigmaticenhance;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class RingSelfTest {
    private static final String TAG = "[书袋Tooltip自检]";
    private RingSelfTest() {}
    public static void register() { MinecraftForge.EVENT_BUS.register(RingSelfTest.class); DebugLog.info(TAG + " registered"); }
    static void setBool(net.minecraftforge.common.ForgeConfigSpec.BooleanValue v, boolean e) {
        for (int i = 0; i < 10; i++) { try { v.set(e); if (v.get() == e) { Thread.sleep(120L); return; } } catch (Throwable ignored) {} try { Thread.sleep(200L); } catch (InterruptedException x) { return; } }
    }
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            List<String> problems = ModIds.problems();
            log(" F0 ModIds.problems()=" + problems + " [必须空]");
            ItemStack bag = new ItemStack(RingConfig.antiqueBagItem());
            log(" F0-b 书袋 id=" + RingConfig.antiqueBagItem() + " 目标 id 清单=" + BagHudEffects.bookIds());
            // 1) 未同步
            BagHudSync.resetClientCache();
            setBool(RingConfig.BAG_TOOLTIP_ENABLED, true);
            int unsynced = lines(bag);
            log(" F1 未同步(缓存空)-> tooltip 行数=" + unsynced + " [应 >原始行数,含未同步行]");
            // 2) 已同步:一条
            BagHudSync.accept(List.of(new BagHudEffects.Entry("enigmaticlegacy:the_twist", "baghud.effect.twist", "10%", true)));
            int synced = lines(bag);
            log(" F2 已同步 1 条 -> tooltip 行数=" + synced + " [应比 F1 多一条效果行]");
            // 3) 关闭
            setBool(RingConfig.BAG_TOOLTIP_ENABLED, false);
            int off = lines(bag);
            setBool(RingConfig.BAG_TOOLTIP_ENABLED, true);
            log(" F3 bagTooltipEnabled=false -> 行数=" + off + " [应等于原始行数]");
            int raw = lines(new ItemStack(net.minecraft.world.item.Items.STONE));
            int rawBag = rawOf(bag);
            boolean f = problems.isEmpty() && unsynced > rawBag && synced > unsynced && off == rawBag;
            log(" F4 原始行数(空袋 tooltip 基础)=" + rawBag + " 石头=" + raw);
            log(" ALL-RESULT " + (f ? "PASS" : "FAIL"));
        }
        catch (Throwable t) { EnigmaticEnhance.LOGGER.error(TAG + " ABORTED", t); }
        finally {
            setBool(RingConfig.BAG_TOOLTIP_ENABLED, true);
            setBool(RingConfig.DEBUG_LOG, false);
            RingConfig.SPEC.save();
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "stop"));
        }
    }
    private static int lines(ItemStack stack) {
        java.util.List<net.minecraft.network.chat.Component> tooltipList = new java.util.ArrayList<>();
        ItemTooltipEvent e = new ItemTooltipEvent(stack, null, tooltipList, TooltipFlag.Default.NORMAL);
        ItemTooltips.onItemTooltip(e);
        return e.getToolTip().size();
    }
    private static int rawOf(ItemStack stack) {
        setBool(RingConfig.BAG_TOOLTIP_ENABLED, false);
        int n = lines(stack);
        setBool(RingConfig.BAG_TOOLTIP_ENABLED, true);
        return n;
    }
    private static void log(String m) { DebugLog.info(TAG + m); }
}