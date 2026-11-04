package com.hengx.enigmaticenhance;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 死亡时把「冒险者之证」转化成「绝望者证章」——纯 Forge 事件,没有任何注入。
 * <p>
 * 为什么用 {@code LivingDeathEvent} 而不是 {@code LivingDropsEvent}:Forge 打补丁后的
 * {@code ServerPlayer#die} 第一件事就是 {@code ForgeHooks.onLivingDeath}(反编译
 * forge-1.20.1-47.2.20-srg.jar 可见:偏移 9),**早于** {@code dropAllDeathLoot}/keepInventory 判定,
 * 所以在这里改完背包,转化出的证章会照常走原版的掉落 / 保留(keepInventory)流程。
 * <p>
 * 口径(用户拍板):
 * <ul>
 * <li>只算"手里"的:{@link RingConfig#convertHandsOnly()} 为 true(默认)时只看主手/副手;</li>
 * <li>哪里拿着就从哪个手消耗 1 个,并把 1 个绝望者证章放回同一只手;手里还有剩(数量&gt;1)或者
 *     放不回去时按原版逻辑进背包,背包也满就掉出 —— 数量永远守恒(1 换 1);</li>
 * <li>已经拥有绝望者证章(饰品栏 / 双手 / 背包任一位置)→ 跳过,不复制;</li>
 * <li>事件已被取消(复活类道具/模组)→ 不转化;</li>
 * <li>扩展自己那套"装备瞬间转化"(灵魂水晶碎满 / 困难模式)在 {@code AdventureCharm#onEquip},
 *     与本类互不干涉,两者并存。</li>
 * </ul>
 * 日志一律先判 {@link RingConfig#debugLog()},关闭时零输出。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeathConversion {

    private DeathConversion() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!RingConfig.convertOnDeath() || event.isCanceled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        // 只在服务端:客户端拿不到 ServerPlayer,服务端玩家死亡才会有掉落判定
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        convert(player);
    }

    /** 真正的转化逻辑(自检直接调它)。 */
    public static void convert(ServerPlayer player) {
        Item charm = RingConfig.adventureCharmItem();
        Item insignia = RingConfig.despairInsigniaItem();
        if (charm == null || insignia == null) {
            return; // 扩展不在场 / 物品缺失
        }
        if (Superposition.hasItem(player, insignia)) {
            debug("玩家 {} 已拥有绝望者证章,不转化", player);
            return; // 已有 -> 不复制
        }
        if (convertFromHands(player, charm, insignia)) {
            return;
        }
        if (!RingConfig.convertHandsOnly()) {
            if (convertFromInventory(player, charm, insignia)) {
                return;
            }
            if (ModList.get().isLoaded("curios")) {
                try {
                    if (CurioConversionHelper.convertFirst(player, charm, insignia)) {
                        notifyPlayer(player, insignia);
                    }
                }
                catch (Throwable t) {
                    debug("Curios 转化失败: {}", t.toString());
                }
            }
        }
    }

    /** 主手 → 副手。 */
    private static boolean convertFromHands(ServerPlayer player, Item charm, Item insignia) {
        for (InteractionHand hand : new InteractionHand[] { InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND }) {
            ItemStack inHand = player.getItemInHand(hand);
            if (inHand.isEmpty() || !inHand.is(charm)) {
                continue;
            }
            inHand.shrink(1);                                   // 消耗 1 个冒险者之证
            ItemStack result = new ItemStack(insignia);          // 产出 1 个绝望者证章
            if (inHand.isEmpty()) {
                player.setItemInHand(hand, result);              // 同一只手放回
            }
            else {
                // 手里还有剩余的冒险者之证(count>1),同一格放不下另一个物品 -> 走原版逻辑
                give(player, result);
            }
            notifyPlayer(player, insignia);
            debug("玩家 {} 的 {} 手里的冒险者之证已转化为绝望者证章", player, hand);
            return true;
        }
        return false;
    }

    /** convertHandsOnly=false 时:背包(不碰饰品栏,那部分交给 Curios)里的第一枚。 */
    private static boolean convertFromInventory(ServerPlayer player, Item charm, Item insignia) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(charm)) {
                continue;
            }
            stack.shrink(1);
            give(player, new ItemStack(insignia));
            notifyPlayer(player, insignia);
            debug("玩家 {} 背包第 {} 格的冒险者之证已转化(convertHandsOnly=false)", player, i);
            return true;
        }
        return false;
    }

    /** 先入背包;背包满则按原版逻辑掉在脚下。 */
    static void give(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void notifyPlayer(ServerPlayer player, Item insignia) {
        if (RingConfig.convertNotify()) {
            player.displayClientMessage(Component.translatable("message.enigmaticenhance.convertOnDeath",
                Component.translatable(insignia.getDescriptionId())), false);
        }
    }

    private static void debug(String format, Object... args) {
        if (RingConfig.debugLog()) {
            DebugLog.info("[死亡转化] " + format, args);
        }
    }
}
