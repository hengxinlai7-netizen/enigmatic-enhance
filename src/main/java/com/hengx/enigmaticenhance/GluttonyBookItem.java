package com.hengx.enigmaticenhance;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * 4.1.0:贪食之书。
 *
 * <p><b>获取</b>:扭曲之心 + 书,无序合成。</p>
 *
 * <p><b>效果</b>:使用时给使用者挂一条<b>上限为负的血条</b>
 * (往 {@code MAX_HEALTH} 上追加一个极大的负值修饰符),然后强制走死亡流程把他杀死。</p>
 *
 * <p><b>复活清理</b>:负上限修饰符必须在复活时彻底移除,否则新角色会带着一条负血条
 * —— 那就直接出问题了。清理点两处:</p>
 * <ul>
 *   <li>{@link PlayerEvent.Clone}(死亡复活时触发,新实体带着旧实体的持久数据);</li>
 *   <li>{@link PlayerEvent.PlayerLoggedInEvent}(极端情况:死亡瞬间退出游戏)。</li>
 * </ul>
 *
 * <p>修饰符用固定 UUID 标识,清理时按 UUID 精确移除,不会误删别的模组/装备给的生命上限。</p>
 */
public class GluttonyBookItem extends Item {

    /** 负上限修饰符的固定 UUID(清理时按它精确移除)。 */
    public static final UUID NEGATIVE_MAX_HEALTH_ID =
        UUID.fromString("8f2d4c1a-6b3e-4a97-9c85-2e7d10b4a651");

    private static final String NEGATIVE_MAX_HEALTH_NAME = "enigmaticenhance:gluttony_book";

    /** 追加的负值:足够把任何玩家的上限压到 0 以下。 */
    private static final double NEGATIVE_AMOUNT = -1.0E6D;

    private static final String MARKER = "enigmaticenhance:gluttony_book_used";

    public GluttonyBookItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        try {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.pass(stack);
            }
            consume(serverPlayer);
            if (!serverPlayer.isCreative()) {
                stack.shrink(1);
            }
            kill(serverPlayer);
            return InteractionResultHolder.consume(stack);
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[GluttonyBook] 使用失败: {}", e.toString());
            return InteractionResultHolder.pass(stack);
        }
    }

    /** 挂上负上限。 */
    private static void consume(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        instance.removeModifier(NEGATIVE_MAX_HEALTH_ID);
        instance.addTransientModifier(new AttributeModifier(
            NEGATIVE_MAX_HEALTH_ID, NEGATIVE_MAX_HEALTH_NAME, NEGATIVE_AMOUNT,
            AttributeModifier.Operation.ADDITION));
        player.getPersistentData().putBoolean(MARKER, true);
    }

    /**
     * 强制死亡(4.1.2:三档升级,最后一档不经过死亡流程)。
     *
     * <h2>为什么需要三档</h2>
     * <p>1.20.1 里"免死"层次很多:不死图腾拦 {@code hurt}、某些模组拦 {@code LivingDeathEvent}
     * 让 {@code die()} 直接返回、还有的直接把血量锁在 1。所以这里逐级加码:</p>
     *
     * <ol>
     *   <li><b>tick 0</b>:{@code hurt(genericKill, MAX)} —— 正常受伤流程,图腾/护甲都按原版处理;</li>
     *   <li><b>tick 1</b>:还活着 ⇒ {@code setHealth(0)} + {@code die()} —— 绕过伤害管线与图腾;</li>
     *   <li><b>tick 3</b>:仍活着 ⇒ <b>{@code PlayerList#respawn}</b> —— 直接让玩家名单重建这个玩家,
     *       完全不经过 {@code hurt}/{@code die},任何"免死"都没有插手的点。</li>
     * </ol>
     *
     * <p>注意:整个过程<b>绝不</b>调用 {@link #cleanup}。血条复原交给
     * {@code GluttonyBookCleanup} 在 Clone / Respawn / Login 时处理。</p>
     */
    private static void kill(ServerPlayer player) {
        var server = player.getServer();
        // ① 立刻:正常受伤流程
        try {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 继续
        }
        if (server == null) {
            fallbackKill(player);
            return;
        }
        // ② 下一 tick:绕过伤害管线
        server.execute(() -> {
            try {
                if (stillAlive(player)) {
                    fallbackKill(player);
                }
            }
            catch (RuntimeException | LinkageError ignored) {
                // 忽略
            }
        });
        // ③ 第 3 tick:强制重生,谁也别想拦
        server.execute(() -> server.execute(() -> server.execute(() -> {
            try {
                if (stillAlive(player)) {
                    forceRespawn(player);
                }
            }
            catch (RuntimeException | LinkageError ignored) {
                // 忽略
            }
        })));
    }

    private static boolean stillAlive(ServerPlayer player) {
        try {
            return player != null && !player.isRemoved() && player.isAlive() && !player.isDeadOrDying();
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 第二档:直接改血量再走 die()。 */
    private static void fallbackKill(ServerPlayer player) {
        try {
            player.setHealth(0.0F);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 继续
        }
        try {
            player.die(player.damageSources().genericKill());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 继续
        }
    }

    /**
     * 第三档:摘掉所有"免死"来源,然后重新走一次正常死亡结算。
     *
     * <p>4.1.3:上一版这一档用的是 PlayerList#respawn(强制重生),
     * 会跳过死亡信息/掉落/死亡界面 —— 不是"正常结算",所以换掉。</p>
     *
     * <p>正常死亡被拦通常只有两种原因:不死图腾、吸收伤害(金心)。这里各处理一遍后再 die(),
     * 死亡的结算流程完全正常:死亡信息、掉经验、按规则掉落、死亡界面,一个都不少。</p>
     */
    private static void forceRespawn(ServerPlayer player) {
        try {
            var src = player.damageSources().genericKill();
            // ① 先让原版正常结算(能过就最干净:掉落/经验/死亡信息/界面全都由它负责)
            try {
                player.die(src);
            }
            catch (RuntimeException | LinkageError ignored) {
                // 继续
            }
            if (player.isDeadOrDying()) {
                return;
            }

            // ② 被拦住了 —— 学永爱之刃,不经过 setHealth/kill/remove 这些可被 Mixin 拦截的入口,
            //    直接写状态 + 自己结算 + 自己发包。
            DebugLog.warn("[GluttonyBook] die() 被拦截,改用直接状态写入");
            manualSettle(player, src);
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.warn("[GluttonyBook] 手工结算失败: {}", e.toString());
        }
    }

    /**
     * 手工结算死亡。
     *
     * <p>照搬「永爱之刃」的思路:不调 {@code hurt}/{@code kill},而是直接</p>
     * <ol>
     *   <li>补掉落与经验({@code dropAllDeathLoot});</li>
     *   <li>{@code setHealth(0)} + 直写 {@code deathTime = 20};</li>
     *   <li>反射写同步血量 {@code DATA_HEALTH_ID};</li>
     *   <li>服务端直接发 {@link net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket},
     *       客户端必定弹原版死亡界面。</li>
     * </ol>
     */
    private static void manualSettle(ServerPlayer player, net.minecraft.world.damagesource.DamageSource src) {
        // ① 掉落 + 经验:dropAllDeathLoot 内部已包含 Player.dropEquipment(),
        //    它会按 keepInventory 规则处理背包 —— 所以这里【不能】再调 inventory.dropAll()
        try {
            DropHelper.dropAllDeathLoot(player, src);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 继续
        }
        // ② 死亡统计
        try {
            player.awardStat(net.minecraft.stats.Stats.DEATHS);
            player.resetStat(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_DEATH));
        }
        catch (RuntimeException | LinkageError ignored) {
            // 继续
        }
        // ③ 血量 + 死亡计时(直写字段,不走 setHealth/kill,绕开 Mixin 拦截)
        try {
            player.setHealth(0.0F);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 继续
        }
        try {
            player.deathTime = 20;
        }
        catch (RuntimeException | LinkageError ignored) {
            // 继续
        }
        // ④ 同步血量给客户端
        try {
            java.lang.reflect.Field field =
                net.minecraft.world.entity.LivingEntity.class.getDeclaredField("DATA_HEALTH_ID");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            net.minecraft.network.syncher.EntityDataAccessor<Float> accessor =
                (net.minecraft.network.syncher.EntityDataAccessor<Float>) field.get(null);
            player.getEntityData().set(accessor, 0.0F);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // 继续
        }
        // ⑤ 生成真实死亡信息并发包(客户端弹原版死亡界面)
        net.minecraft.network.chat.Component deathMessage;
        try {
            player.getCombatTracker().recheckStatus();
            deathMessage = player.getCombatTracker().getDeathMessage();
        }
        catch (RuntimeException | LinkageError ignored) {
            deathMessage = net.minecraft.network.chat.Component.translatable("death.attack.generic",
                player.getDisplayName());
        }
        try {
            if (player.connection != null) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket(
                    player.getId(), deathMessage));
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
        DebugLog.warn("[GluttonyBook] 手工结算完成:血量={} deathTime={} 死亡信息={}",
            player.getHealth(), player.deathTime, deathMessage.getString());
    }

    /** 该玩家是否正处在「贪食之书致死」流程中。 */
    public static boolean isMarked(Player player) {
        try {
            return player != null && player.getPersistentData().getBoolean(MARKER);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static void cleanup(Player player) {
        try {
            if (player == null) {
                return;
            }
            AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
            if (instance != null) {
                instance.removeModifier(NEGATIVE_MAX_HEALTH_ID);
            }
            player.getPersistentData().remove(MARKER);
            // 只在"活着但血是 0"时补满;正在死亡流程里(dead=true)绝不干预
            if (!player.isDeadOrDying()) {
                float max = player.getMaxHealth();
                if (max > 0.0F && player.getHealth() <= 0.0F) {
                    player.setHealth(max);
                }
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 清理失败不影响游戏
        }
    }

    /**
     * 4.6.2(内存):2 行 tooltip 全是常量 ⇒ 只建一次,不再每帧重建(理由见 {@link DemonHandBoneItem})。
     */
    private static final Component TOOLTIP_LORE =
        Component.translatable("tooltip.enigmaticenhance.gluttony_book.lore").withStyle(ChatFormatting.GRAY);
    private static final Component TOOLTIP_EFFECT =
        Component.translatable("tooltip.enigmaticenhance.gluttony_book.effect").withStyle(ChatFormatting.RED);

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(TOOLTIP_LORE);
        tooltip.add(TOOLTIP_EFFECT);
    }
}
