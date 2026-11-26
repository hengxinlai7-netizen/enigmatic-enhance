package com.hengx.enigmaticenhance.test;

import com.hengx.enigmaticenhance.BloodbathHandler;
import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.ModEnchantments;
import com.hengx.enigmaticenhance.RingConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

/**
 * 4.17.0 自检：本版新增内容的模拟验证。
 *
 * <ul>
 *   <li>C1-C3：血战沙场锁血的 FIXED / PERCENT 两种模式</li>
 *   <li>C4-C5：执行之手的抗封印（NBT 里存在、helper 返回 0 时补成 1）</li>
 *   <li>C6：获取途径显示的配置默认值</li>
 * </ul>
 */
public final class BloodbathUnsealSelfTest {

    private static final String TAG = "[NewContentSelfTest]";
    private static boolean allPass = true;
    private static MinecraftServer server;

    private BloodbathUnsealSelfTest() {
    }

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(BloodbathUnsealSelfTest.class);
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        run(event.getServer());
    }

    public static void run(MinecraftServer srv) {
        server = srv;
        try {
            ServerLevel level = srv.overworld();
            level.getChunk(0, 0);
            ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ea_newcontent"));
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
            player.setHealth(20.0F);

            testBloodbath(player);
            testUnseal(level);
            testAcquisitionToggle();

            log(" summary => " + (allPass ? "PASS" : "FAIL"));
            log(" ALL-RESULT " + (allPass ? "PASS" : "FAIL"));
        }
        catch (Throwable t) {
            log(" ABORTED " + t);
            allPass = false;
        }
        finally {
            stop(server);
        }
    }

    private static void testBloodbath(ServerPlayer player) {
        try {
            // C1: FIXED 3.0
            float fixed = BloodbathHandler.lockValueForTest(player, false, 3.0D, 1.0D);
            pass("C1", "FIXED(3.0) -> " + fixed, Math.abs(fixed - 3.0F) < 1.0E-4F);

            // C2: PERCENT 10% of 20 = 2
            float percent = BloodbathHandler.lockValueForTest(player, true, 3.0D, 10.0D);
            pass("C2", "PERCENT(10% of 20) -> " + percent, Math.abs(percent - 2.0F) < 1.0E-3F);

            // C3: PERCENT 100% = maxHealth, 且不低于下限 0.5
            float full = BloodbathHandler.lockValueForTest(player, true, 3.0D, 100.0D);
            pass("C3", "PERCENT(100% of 20) -> " + full, Math.abs(full - 20.0F) < 1.0E-3F);

            // C3b: 极小值夹到下限
            float floor = BloodbathHandler.lockValueForTest(player, true, 3.0D, 0.1D);
            pass("C3b", "PERCENT(0.1% of 20) -> " + floor, Math.abs(floor - 0.5F) < 1.0E-3F);
        }
        catch (Throwable t) {
            pass("C1-C3b", "异常: " + t, false);
        }
    }

    private static void testUnseal(ServerLevel level) {
        try {
            var enchant = ModEnchantments.handOfExecutionOrNull();
            if (enchant == null) {
                pass("C4", "执行之手附魔未注册", false);
                return;
            }

            // C4: NBT 里有附魔, 但先清掉 helper 的缓存路径 -> 期望仍能读出 1
            ItemStack sealed = new ItemStack(Items.DIAMOND_SWORD);
            var list = new net.minecraft.nbt.ListTag();
            var entry = new net.minecraft.nbt.CompoundTag();
            entry.putString("id", EnigmaticEnhance.MODID + ":hand_of_execution");
            entry.putShort("lvl", (short) 1);
            list.add(entry);
            sealed.getOrCreateTag().put("Enchantments", list);

            int lvl = EnchantmentHelper.getItemEnchantmentLevel(enchant, sealed);
            pass("C4", "NBT 有附魔(被封印) -> level=" + lvl, lvl >= 1);

            // C5: 干净物品 -> 0(不能凭空给附魔)
            ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
            int none = EnchantmentHelper.getItemEnchantmentLevel(enchant, plain);
            pass("C5", "干净物品 -> level=" + none, none == 0);
        }
        catch (Throwable t) {
            pass("C4-C5", "异常: " + t, false);
        }
    }

    private static void testAcquisitionToggle() {
        try {
            boolean shown = RingConfig.showAcquisitionPaths();
            pass("C6", "showAcquisitionPaths 默认值=" + shown, !shown);
        }
        catch (Throwable t) {
            pass("C6", "异常: " + t, false);
        }
    }

    private static void pass(String name, String detail, boolean ok) {
        if (!ok) {
            allPass = false;
        }
        log(" " + name + " " + detail + (ok ? "  [PASS]" : "  [FAIL]"));
        log(" " + name + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }

    private static void log(String message) {
        com.mojang.logging.LogUtils.getLogger().info(TAG + message);
    }

    private static void stop(MinecraftServer srv) {
        if (srv != null) {
            srv.halt(false);
        }
    }
}
