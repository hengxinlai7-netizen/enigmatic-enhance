package com.hengx.enigmaticenhance;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「血战沙场之证」({@code enigmaticlegacy:berserk_charm},类 {@code com.aizistral.enigmaticlegacy.items.BerserkEmblem})的
 * <b>额外效果:假血条</b>。
 *
 * <h2>设计(1.7.9)</h2>
 * <ul>
 *   <li><b>假血条</b>:玩家实际参与"死亡/饰品加成/扣血"的血量,默认 = 锁值(1),只受**外部扣血**下降,
 *       <b>回血一律无效</b>;</li>
 *   <li><b>真血条</b>:只负责"生命上限"(属性)与"生命值加成",当前血量每 tick 被同步成假血条的值,
 *       所以界面看到的就是假血条;</li>
 *   <li>扣血走假血条:受伤不扣真血,而是扣假血;假血 ≤ 0 时才真正死亡;</li>
 *   <li>生命上限(来自饰品/药水等属性加成)自动反映到假血条(假血条上限 = 当前最大生命)。</li>
 * </ul>
 *
 * <p>这样绝大多数回血(再生、吸血、瞬间治疗、其它模组直接改血)都会被吃掉,而"血战沙场"的
 * 1 血玻璃大炮手感保留。</p>
 *
 * <h2>Ctrl+左键 开关</h2>
 * 逐件、状态在物品 NBT {@value #NBT_LOCK_DISABLED} 上(<b>键不存在 = 开 = 默认行为</b>),
 * 走 1.1.15 抽出来的通用 {@link ItemToggleHandler}(与万钧之护卷轴共用同一条网络通道与服务端校验)。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BloodbathHandler {

    /** 物品 NBT 键:true = 玩家用 Ctrl+左键 关掉了这个锁血;键不存在 = 开。 */
    public static final String NBT_LOCK_DISABLED = "enigmaticenhance:BloodbathLockDisabled";

    /** 假血条 NBT 键。 */
    private static final String TAG_FAKE = "bloodbath_fake";

    /** 锁血允许的最小值(绝不写 0/负数)。 */
    public static final float MIN_LOCK_VALUE = 0.5F;

    /** 自检用:真正执行过写入的次数。 */
    private static int healthWrites;

    /** 1.1.15:交给通用的 Ctrl+左键 开关设施。 */
    public static final ItemToggleHandler.Target TOGGLE_TARGET = new ItemToggleHandler.Target() {

        @Override
        public boolean matches(ItemStack stack) {
            return isTargetCharm(stack);
        }

        @Override
        public String nbtKey() {
            return NBT_LOCK_DISABLED;
        }

        @Override
        public String displayName() {
            return "血战沙场之证·锁血";
        }

        @Override
        public boolean toggleAllowed() {
            return RingConfig.bloodbathAllowToggle();
        }
    };

    private BloodbathHandler() {}

    // ------------------------------------------------------------------ 目标判定 / NBT

    public static boolean isTargetCharm(ItemStack stack) {
        return stack != null && !stack.isEmpty() && RingConfig.isBerserkCharm(stack.getItem());
    }

    /** 该勋章是否被玩家关掉了锁血(键不存在 = 开)。 */
    public static boolean isLockDisabled(ItemStack stack) {
        return ItemToggleHandler.isDisabled(stack, NBT_LOCK_DISABLED);
    }

    public static void setLockDisabled(ItemStack stack, boolean disabled) {
        ItemToggleHandler.setDisabled(stack, NBT_LOCK_DISABLED, disabled);
    }

    public static int healthWrites() {
        return healthWrites;
    }

    // ------------------------------------------------------------------ 生效条件 / 锁定值

    /** 锁定到多少点生命值(配置下限 0.5,这里再兜一次)。 */
    public static float lockValue() {
        return Math.max(MIN_LOCK_VALUE, (float) RingConfig.bloodbathLockValue());
    }

    /** 完整生效条件:总开关 + 佩戴该勋章 + 该件的锁血没被关掉。 */
    public static boolean lockActive(Player player) {
        if (player == null || !RingConfig.bloodbathLockEnabled()) {
            return false;
        }
        Item charm = RingConfig.berserkCharmItem();
        if (charm == null || !Superposition.hasCurio(player, charm)) {
            return false;
        }
        ItemStack worn = Superposition.getCurioStack(player, charm);
        return worn.isEmpty() || !isLockDisabled(worn);
    }

    // ------------------------------------------------------------------ 假血条

    /** 假血条当前血量(默认 = 锁值)。 */
    public static float fakeHealth(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag tag = player.getPersistentData();
        if (!tag.contains(TAG_FAKE)) {
            tag.putFloat(TAG_FAKE, lockValue());
        }
        return tag.getFloat(TAG_FAKE);
    }

    public static void setFakeHealth(ServerPlayer player, float value) {
        player.getPersistentData().putFloat(TAG_FAKE, Math.max(0.0F, value));
    }

    /** 把假血条重置为锁值(重新佩戴/开关切换时用)。 */
    public static void resetFakeHealth(ServerPlayer player) {
        player.getPersistentData().putFloat(TAG_FAKE, lockValue());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isAlive() || !lockActive(player)) {
            return;
        }
        float fake = fakeHealth(player);
        if (fake <= 0.0F) {
            killLocked(player);
            return;
        }
        // 真血条只负责显示假血条的值(生命上限由属性决定)
        if (Math.abs(player.getHealth() - fake) > 1.0E-3F) {
            player.setHealth(fake);
            healthWrites++;
        }
    }

    /** 外部扣血 → 扣假血条(真血不动)。 */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.isAlive() || !lockActive(player)) {
                return;
            }
            float fake = fakeHealth(player);
            float amount = event.getAmount();
            event.setAmount(0.0F);
            event.setCanceled(true);
            setFakeHealth(player, fake - amount);
            if (RingConfig.debugLog()) {
                DebugLog.info("[血战沙场] 假血条:{} {} -> {}", player.getGameProfile().getName(), fake, fake - amount);
            }
            if (fake - amount <= 0.0F) {
                killLocked(player);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 回血一律无效(假血条不接受治疗)。 */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isAlive() || !lockActive(player)) {
            return;
        }
        event.setAmount(0.0F);
    }

    private static void killLocked(ServerPlayer player) {
        try {
            player.setHealth(0.0F);
            player.die(player.damageSources().genericKill());
            // 2.9.7:死亡后必须重置假血条,否则复活后 tick 检测到 fake<=0 会立刻再死 —— 无限死亡。
            resetFakeHealth(player);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /** 2.9.7:复活时兜底重置假血条。 */
    @SubscribeEvent
    public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resetFakeHealth(player);
        }
    }

    /** 兼容旧自检:把真血夹到假血条。 */
    public static boolean clampHealth(ServerPlayer player) {
        if (player == null || !player.isAlive() || !lockActive(player)) {
            return false;
        }
        float fake = fakeHealth(player);
        if (player.getHealth() > fake) {
            player.setHealth(fake);
            healthWrites++;
            return true;
        }
        return false;
    }

}
