package com.hengx.enigmaticenhance;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「大地护符」的全部运行时行为(1.1.24)。
 * <p>
 * 设计原则:**能用 Forge 事件就不用 mixin**。这里只有 3 个注入点(见
 * {@code enigmaticenhance.mixins.json}):
 * <ul>
 * <li>{@code LivingEntity#m_6336_}(getMobType)—— 僵尸头让玩家被视为亡灵生物;</li>
 * <li>{@code LivingEntity#m_7301_}(canBeAffected)—— 凋灵骷髅头免疫凋零效果;</li>
 * <li>{@code Player#m_6298_}(getProjectile)—— 骷髅头在背包没有箭时给一支"虚拟箭"。</li>
 * </ul>
 * 其余全在本类:
 * <ul>
 * <li>{@link LivingChangeTargetEvent} —— 亡灵/下界生物不把佩戴者设为目标(只改目标,不动实体数据);</li>
 * <li>{@link LivingAttackEvent} —— 免疫爆炸伤害 / 强化后免疫亡灵生物伤害(只对佩戴者);</li>
 * <li>{@link LivingHurtEvent} —— 凋灵骷髅头让攻击附带凋零;</li>
 * <li>{@link TickEvent.PlayerTickEvent} —— 创造飞行的授予/回收兜底、凋零清除;</li>
 * <li>死亡 / 切换维度 / 退出游戏 —— 回收飞行,防止"永久飞行"漏洞。</li>
 * </ul>
 * 所有日志走 {@link DebugLog}(默认 debugLog=false ⇒ 零输出)。
 */
public final class EarthAmuletHandler {

    /** 日志/自检共用的稳定 ASCII 标记。 */
    public static final String LOG_TAG = "[EarthAmulet]";

    /** 药水效果每次刷新的时长(短时长,避免显示成 ∞);每 20 tick 由 curioTick 刷新一次。 */
    public static final int EFFECT_DURATION_TICKS = 2400;
    /** 凋灵骷髅头命中时给目标上的凋零:5 秒、II 级。 */
    public static final int WITHER_DURATION_TICKS = 100;
    public static final int WITHER_AMPLIFIER = 1;

    /** "下界生物"清单(变异/敌对型;凋灵骷髅头同时属于亡灵,两条判定都成立)。 */
    private static final Set<EntityType<?>> NETHER_MOBS = Set.<EntityType<?>>of(
        EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN, EntityType.HOGLIN,
        EntityType.ZOGLIN, EntityType.BLAZE, EntityType.GHAST, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON);

    /** 由本模组授予过创造飞行的玩家(取下/死亡/切维度/退游戏时按这个清单回收)。 */
    private static final Set<UUID> FLIGHT_GRANTED = ConcurrentHashMap.newKeySet();

    /** 1.1.25b:由本模组打开了 {@code noPhysics}(穿方块)的玩家。只回收自己打开的那一份。 */
    private static final Set<UUID> NOCLIP = ConcurrentHashMap.newKeySet();

    /** 2.8.0:每名玩家的"允许龙首穿墙"开关(按键切换,默认开)。 */
    /** 2.9.9:改为"显式开启"集合 —— 默认不穿墙,必须按键开启。 */
    private static final Set<UUID> NOCLIP_ENABLED = ConcurrentHashMap.newKeySet();
    /** 最近一次发给客户端的 noPhysics 值(自检断言用;没有真实客户端也记录)。 */
    private static final java.util.Map<UUID, Boolean> NOCLIP_SENT = new ConcurrentHashMap<>();

    /**
     * 2.1.1:每个玩家**最后一次确认在飞行**时的 tickCount。
     *
     * <p>原来 {@code want} 要求"当前正在 flying"，一落地、一被别的模组清掉飞行能力，
     * 穿方块就立刻关闭 —— 玩家体感是"飞着飞着突然撞墙、横向过不去"。
     * 现在改成"最近 {@link #NOCLIP_GRACE_TICKS} tick 内飞过"也算，起落瞬间不再中断。</p>
     *
     * <p>4.6.2(装箱):值原来是 {@code Integer} —— {@code player.tickCount} 早就超出
     * {@code Integer} 缓存区(-128..127),所以"戴龙首且在飞"的玩家<b>每 tick</b> 都要新建一个
     * {@code Integer} 才能 put。现在用长度 1 的 {@code int[]} 当可变格子:第一次出现时建一次数组,
     * 之后只写下标 0,热路径零分配。读的一方拿到的是同一个数组,只读下标 0,
     * 语义与原 {@code Integer} 逐位相同(比较的仍然是 tickCount 的整数值)。</p>
     */
    private static final java.util.Map<UUID, int[]> LAST_FLYING_TICK = new ConcurrentHashMap<>();
    /** 停飞后仍然保留穿墙的宽限 tick 数(=1 秒)。 */
    private static final int NOCLIP_GRACE_TICKS = 1;


    private EarthAmuletHandler() {}

    // ------------------------------------------------------------------ 佩戴中每 tick

    /** 佩戴时每 tick 调用(由 {@link EarthAmuletItem#curioTick} 与 onEquip 调用)。 */
    public static void onWornTick(Player player, ItemStack amulet) {
        if (player == null || player.level().isClientSide || amulet == null || amulet.isEmpty()) {
            return;
        }
        try {
            applyStoredPotionEffects(player, amulet);
            if (EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_WITHER_SKELETON) && player.hasEffect(MobEffects.WITHER)) {
                player.removeEffect(MobEffects.WITHER);
                DebugLog.info("{} {} 佩戴凋灵骷髅头护符,已清除自身凋零", LOG_TAG, player.getName().getString());
            }
            if (EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_DRAGON)) {
                grantFlight(player);
                updateNoClip(player, amulet);
            }
            else {
                revokeFlight(player);
                updateNoClip(player, amulet);
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 佩戴结算异常", t);
        }
    }

    /** 凋零被拒(log 出口,mixin 里调用;debugLog=false 时零输出)。 */
    public static void onWitherBlocked(Player player) {
        DebugLog.info("{} {} 凋灵骷髅头:免疫凋零效果", LOG_TAG, player.getName().getString());
    }

    /** 把保存的药水效果刷新成"短时长"(每 20 tick 调一次,不会显示成 ∞)。 */
    private static void applyStoredPotionEffects(Player player, ItemStack amulet) {
        // 4.3.8:先清掉"已经记录但后来进黑名单/被禁用"的效果(老存档也会被纠正)
        EarthAmuletData.sanitize(amulet);
        List<EarthAmuletData.StoredEffect> stored = EarthAmuletData.effects(amulet);
        double healthBonus = 0.0D;
        for (EarthAmuletData.StoredEffect entry : stored) {
            MobEffect effect = EarthAmuletData.effect(entry.id());
            if (effect == null) {
                continue;
            }
            // 2.11.7e:生命提升不再作为"限时 buff"反复施加,改成常驻属性加成
            if (effect == MobEffects.HEALTH_BOOST) {
                healthBonus += 4.0D * Math.max(0, entry.level());
                continue;
            }
            int amplifier = Math.max(0, entry.level() - 1);
            MobEffectInstance current = player.getEffect(effect);
            // 玩家身上已有同级更久的效果(例如自己喝的长效药水)时不覆盖,避免把时长改短
            if (current != null && current.getAmplifier() >= amplifier && current.getDuration() > EFFECT_DURATION_TICKS / 2) {
                continue;
            }
            // 2.11.7d:重刷效果会让 max_health 重算,原版随即钳血 → 满血时表现为"每次刷新掉一点"。
            // 这里在重刷前后把血量保下来,避免任何掉落。
            float before = player.getHealth();
            player.addEffect(new MobEffectInstance(effect, EFFECT_DURATION_TICKS, amplifier, true, false, true));
            if (player.getHealth() < before) {
                player.setHealth(Math.min(before, player.getMaxHealth()));
            }
        }
        applyPermanentHealthBonus(player, healthBonus);
        // 2.11.2 诊断:每秒打一次血量/上限/生命提升剩余时长
        if (player.tickCount % 20 == 0) {
            MobEffectInstance boost = player.getEffect(MobEffects.HEALTH_BOOST);
            if (RingConfig.debugLog()) {
                    DebugLog.warn("[AmuletHealthProbe] max={} health={} boostAmp={} boostDur={} stored={}",
                player.getMaxHealth(), player.getHealth(),
                boost == null ? -1 : boost.getAmplifier(),
                boost == null ? -1 : boost.getDuration(),
                stored.size());
            }
        }
    }

    // ------------------------------------------------------------------ 创造飞行

    /** 2.8.0:该玩家当前是否允许龙首穿墙(默认允许)。 */
    public static boolean noclipEnabled(Player player) {
        return player != null && NOCLIP_ENABLED.contains(player.getUUID());
    }

    /** 2.8.0:按键切换穿墙开关。 */
    public static void toggleNoclip(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUUID();
        if (!NOCLIP_ENABLED.add(id)) {
            NOCLIP_ENABLED.remove(id);
        }
        boolean on = noclipEnabled(player);
        DebugLog.info("{} {} 龙首穿墙开关 -> {}", LOG_TAG, player.getName().getString(), on ? "开" : "关");
        updateNoClip(player, EarthAmuletData.equippedAmulet(player));
    }

    /** 授予创造飞行(只记录"原本不能飞"的情形)。 */
    public static void grantFlight(Player player) {
        // 3.7.1:这里原来拦的是「复苏之叶开关关掉时不给龙首飞行」—— 那是错误理解。
        // 复苏之叶的飞行是它自己授予 abilities.mayfly(见 RevivalLeaf#handleFlight),与龙首无关;
        // 它的开关现在由 mixin.RevivalLeafFlightMixin 接管,这里不再牵连龙首。
        if (player == null || player.level().isClientSide || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (player.getAbilities().mayfly) {
            return;
        }
        FLIGHT_GRANTED.add(player.getUUID());
        player.getAbilities().mayfly = true;
        syncAbilities(player);
        DebugLog.info("{} {} 龙首:授予创造飞行(mayfly=true)", LOG_TAG, player.getName().getString());
    }

    /** 回收本模组授予的创造飞行;不是我们给的就绝不碰(别的模组/创造模式给的飞行保留)。 */
    public static void revokeFlight(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        if (!FLIGHT_GRANTED.remove(player.getUUID())) {
            return;
        }
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            syncAbilities(player);
            DebugLog.info("{} {} 龙首:回收创造飞行(mayfly=false)", LOG_TAG, player.getName().getString());
        }
    }

    /** 清掉某个玩家的飞行记录(退出/重生时用,不读实体状态)。 */
    public static void forget(UUID uuid) {
        if (uuid != null) {
            FLIGHT_GRANTED.remove(uuid);
            NOCLIP.remove(uuid);
            NOCLIP_SENT.remove(uuid);
            // 4.6.0(内存):原来漏了这一张。LAST_FLYING_TICK 是 Map<UUID, Integer>(装箱值,
            // 每条 ≈ UUID 32B + Integer 16B + Node 32B),每 tick 只要"戴龙首且在飞"就 put 一次,
            // 玩家下线后永远没人删 ⇒ 每个飞过龙首的玩家永久留一条。
            // 删掉还顺手修掉一个隐患:重登后 Player#tickCount 从 0 重新数,
            // 而残留的旧 tickCount 是个大数 ⇒ `tickCount - lastFly <= 1` 恒成立,
            // 相当于"一上线就被当成刚飞过"。
            LAST_FLYING_TICK.remove(uuid);
        }
    }

    /** 当前是否由本模组给该玩家授予了飞行(自检用)。 */
    public static boolean flightGranted(Player player) {
        return player != null && FLIGHT_GRANTED.contains(player.getUUID());
    }

    private static void syncAbilities(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.onUpdateAbilities();
        }
    }

    // ------------------------------------------------------------------ 龙首:飞行穿方块(1.1.25b)

    /**
     * 每 tick 维护"飞行时穿过方块":
     * <ul>
     * <li>条件 = 佩戴**龙首**护符 + 这份飞行是**本模组给的**({@link #FLIGHT_GRANTED})
     *     + 正在飞行({@code abilities.flying})+ 不是创造/旁观;</li>
     * <li>满足 ⇒ 服务端与客户端同时置 {@code noPhysics}(客户端由 {@code NoClipPacket} 通知);</li>
     * <li>不满足(停飞/落地/取下/死亡/切维度)⇒ **先把人从方块里推出来**,再恢复碰撞并通知客户端关掉。
     *     推不出来(极端卡位)就先保持穿方块,下一 tick 再试 —— 绝不把玩家卡死在实心方块里。</li>
     * </ul>
     * 只处理 {@link #NOCLIP} 里记录的玩家:别人/别的模组给的 noPhysics 一律不碰。
     */
    private static void updateNoClip(Player player, ItemStack amulet) {
        UUID id = player.getUUID();
        boolean dragon = !amulet.isEmpty() && EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_DRAGON);
        // 1.2.13e:创造模式也允许(之前排除创造,导致在创造里测试"龙首穿方块"完全没反应)
        // 2.1.1:改成"当前在飞 或 最近 1 秒内飞过",避免瞬停就断掉穿墙。
        boolean flyingNow = player.getAbilities().flying;
        if (dragon && flyingNow) {
            // 4.6.2(装箱):复用同一个 int[1],不再每 tick 装箱一个 Integer
            LAST_FLYING_TICK.computeIfAbsent(id, k -> new int[1])[0] = player.tickCount;
        }
        int[] lastFly = LAST_FLYING_TICK.get(id);
        boolean recentlyFlew = lastFly != null && (player.tickCount - lastFly[0]) <= NOCLIP_GRACE_TICKS;
        // 2.7.6:只有「创造模式飞行」或「本模组(龙首)授予的创造飞行」才允许穿方块。
        // 之前只看 abilities.flying —— 别的模组给的飞行(喷气背包/鞘翅等)也会穿墙。
        boolean creativeFlight = player.isCreative() || flightGranted(player);
        // 2.9.1:必须"正在创造飞行"才穿墙 —— 上一版去掉 flying 判定后,
        // 站在地上也会穿墙,直接掉进地下。这里把 flying 判定加回来,
        // 同时保留按键开关(关掉就正常碰撞、正常窒息)。
        boolean want = dragon && creativeFlight && noclipEnabled(player)
            && (flyingNow || recentlyFlew) && !player.isSpectator();
        // 判定只看:戴龙首 + (在飞 或 刚飞过) + 不是旁观(创造也允许,方便测试与创造玩法)。
        if (dragon && !want && DebugLog.oncePending()) {
            // 4.6.0(内存):key 里带 UUID + flying,原来每 tick 都要拼一次(即使 once 早已记过),
            // 戴龙首却不飞的玩家会稳定每 tick 产生一条临时 String。先问 oncePending() 再拼。
            DebugLog.once("noclip:off:" + id + ":" + player.getAbilities().flying,
                "[EarthAmulet] 戴龙首但未开启穿方块: flying={} mayfly={} creative={} spectator={}",
                player.getAbilities().flying, player.getAbilities().mayfly, player.isCreative(),
                player.isSpectator());
        }

        if (want) {
            // 1.2.12g:Player#tick() 第一句就是 noPhysics = isSpectator(),所以必须每 tick 重新置位;
            // 只在状态变化时发一次包,网络开销不变。
            player.noPhysics = true;
            NoClipState.mark(player, true);
            if (NOCLIP.add(id)) {
                player.noPhysics = true;
                player.fallDistance = 0.0F;
                sendNoClip(player, true);
                DebugLog.once("noclip:on:" + id,
                    "[EarthAmulet] 龙首穿方块开启: flying={} mayfly={} creative={} spectator={} noPhysics={}",
                    player.getAbilities().flying, player.getAbilities().mayfly, player.isCreative(),
                    player.isSpectator(), player.noPhysics);
                DebugLog.info("{} {} 龙首:飞行穿方块开启(noPhysics=true)", LOG_TAG, player.getName().getString());
            }
            else if (player.tickCount % 20 == 0) {
                // 1.2.12b:周期性重发,客户端重连/状态被别的模组重置时也能自动恢复穿方块
                player.noPhysics = true;
                sendNoClip(player, true);
            }
            return;
        }
        if (!NOCLIP.contains(id)) {
            return;
        }
        if (!pushOutOfBlocks(player)) {
            DebugLog.info("{} {} 龙首:仍卡在方块里,暂不恢复碰撞", LOG_TAG, player.getName().getString());
            return;
        }
        NOCLIP.remove(id);
        NoClipState.mark(player, false);
        player.noPhysics = false;
        player.fallDistance = 0.0F;
        sendNoClip(player, false);
        DebugLog.info("{} {} 龙首:飞行穿方块关闭(noPhysics=false)", LOG_TAG, player.getName().getString());
    }
    /**
     * 1.2.12b:猪灵头 —— 把"已经锁定玩家"的下界生物目标清掉。
     * <p>{@code LivingChangeTargetEvent} 只会在怪物**改目标**时触发;玩家戴上护符之前就被锁定的怪
     * 不会自己松口,所以每 20 tick 扫一次周围 16 格(只清目标是本玩家的下界生物)。</p>
     */
    private static void clearNetherTargets(Player player) {
        try {
            var box = player.getBoundingBox().inflate(16.0D);
            int cleared = 0;
            for (var mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
                if (mob.getTarget() == player && isNetherMob(mob)) {
                    mob.setTarget(null);
                    ++cleared;
                }
            }
            if (cleared > 0) {
                DebugLog.once("piglinClear:" + player.getUUID(),
                    "{} 猪灵头:清掉 {} 个正在锁定玩家的下界生物", LOG_TAG, cleared);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 扫描失败不影响游戏
        }
    }

    /** 把玩家从实心方块里向上推出去(最多 16 格);返回是否已经在/已经到了安全位置。 */
    public static boolean pushOutOfBlocks(Player player) {
        if (player.level().noCollision(player, player.getBoundingBox())) {
            return true;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        for (int i = 1; i <= 16; i++) {
            player.setPos(x, y + i, z);
            if (player.level().noCollision(player, player.getBoundingBox())) {
                player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                player.fallDistance = 0.0F;
                DebugLog.info("{} {} 从方块里被推出 +{} 格", LOG_TAG, player.getName().getString(), i);
                return true;
            }
        }
        player.setPos(x, y, z);
        return false;
    }

    /** 立即关掉穿方块(死亡/切维度/退出/重生时调用,不要求先推开)。 */
    public static void clearNoClip(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        // 4.6.0(内存):共享状态必须先无条件清干净。
        // NoClipState.serverActive 是 Set<UUID>,原来只有 updateNoClip 转"关"那一支会 remove;
        // 玩家在"穿墙中"下线时走的是本方法 ⇒ UUID 永久留在集合里(每换一个号 +1 条),
        // 而且重登后 EntityMoveNoClipMixin 会把它当成"仍在穿墙",人在没飞的时候也穿过方块。
        // 这里只是把"本模组给该玩家打开的那份状态"收回,别人/别的模组的状态一律不碰。
        NoClipState.mark(player, false);
        // 同理:EntityMoveNoClipMixin 记的"这个 noPhysics 是我们开的"是按实体 id 存的,
        // 实体走了就没人再调 releaseForced ⇒ 集合只增不减。这里顺手清掉自己那一份。
        boolean forced = NoClipState.releaseForced(player);
        if (!NOCLIP.remove(player.getUUID())) {
            if (forced) {
                // 状态是我们打开、但 NOCLIP 里已经没有了:照样把碰撞恢复(与 mixin 的还原分支一致)
                player.noPhysics = false;
            }
            return;
        }
        player.noPhysics = false;
        sendNoClip(player, false);
    }

    private static void sendNoClip(Player player, boolean enabled) {
        NOCLIP_SENT.put(player.getUUID(), enabled);
        if (player instanceof ServerPlayer serverPlayer) {
            try {
                EnigmaticNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new EnigmaticNetwork.NoClipPacket(enabled));
            }
            catch (Throwable t) {
                DebugLog.error(LOG_TAG + " 发送 noPhysics 同步包失败", t);
            }
        }
    }

    /** 自检:当前是否由本模组打开了穿方块。 */
    public static boolean noClipActive(Player player) {
        return player != null && NOCLIP.contains(player.getUUID());
    }

    /** 自检:最近一次通知客户端的 noPhysics 值(null = 还没发过)。 */
    public static Boolean lastNoClipSent(Player player) {
        return player == null ? null : NOCLIP_SENT.get(player.getUUID());
    }

    // ------------------------------------------------------------------ 目标选择

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        try {
            if (event.getTargetType() != LivingChangeTargetEvent.LivingTargetType.MOB_TARGET) {
                return;
            }
            LivingEntity attacker = event.getEntity();
            if (!(event.getNewTarget() instanceof Player player) || attacker == player) {
                return;
            }
            ItemStack amulet = EarthAmuletData.equippedAmulet(player);
            if (amulet.isEmpty()) {
                return;
            }
            if (EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_ZOMBIE) && isUndeadMob(attacker)) {
                suppressTarget(event, attacker);
                return;
            }
            if (EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_PIGLIN) && isNetherMob(attacker)) {
                suppressTarget(event, attacker);
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 目标判定异常", t);
        }
    }

    /**
     * 1.2.13:让"头颅升级"压过"七咒之戒"那类强制仇恨。
     * <p>七咒之戒是在自己的 {@code curioTick} 里对附近怪直接调 {@code Mob#setTarget(player)};
     * 这个调用同样会走 Forge 的 {@link LivingChangeTargetEvent},所以在这里
     * <b>既把新目标置空、又把事件取消</b>(双保险),并顺手清掉中立怪的怒气,
     * 免得它靠 persistent anger 下一 tick 又把玩家锁回去。</p>
     */
    private static void suppressTarget(LivingChangeTargetEvent event, LivingEntity attacker) {
        event.setNewTarget(null);
        event.setCanceled(true);
        if (attacker instanceof net.minecraft.world.entity.NeutralMob neutral) {
            try {
                neutral.setPersistentAngerTarget(null);
                neutral.setRemainingPersistentAngerTime(0);
            }
            catch (Throwable ignored) {
                // 某些实现可能不支持清怒气,忽略
            }
        }
        try {
            if (attacker instanceof net.minecraft.world.entity.Mob mob) {
                mob.setTarget(null);
            }
        }
        catch (Throwable ignored) {
            // 忽略
        }
    }

    /** 1.2.13:僵尸头 —— 把"已经锁定玩家"的亡灵目标也清掉(和猪灵头同款兜底)。 */
    private static void clearUndeadTargets(Player player) {
        try {
            var box = player.getBoundingBox().inflate(16.0D);
            int cleared = 0;
            for (var mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
                if (mob.getTarget() == player && isUndeadMob(mob)) {
                    mob.setTarget(null);
                    if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) {
                        neutral.setPersistentAngerTarget(null);
                        neutral.setRemainingPersistentAngerTime(0);
                    }
                    ++cleared;
                }
            }
            if (cleared > 0) {
                DebugLog.once("zombieClear:" + player.getUUID(),
                    "{} 僵尸头:清掉 {} 个正在锁定玩家的亡灵", LOG_TAG, cleared);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 扫描失败不影响游戏
        }
    }

    // ------------------------------------------------------------------ 受伤判定

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
                return;
            }
            ItemStack amulet = EarthAmuletData.equippedAmulet(player);
            if (amulet.isEmpty()) {
                return;
            }
            DamageSource source = event.getSource();
            if (EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_CREEPER) && source.is(DamageTypeTags.IS_EXPLOSION)) {
                event.setCanceled(true);
                DebugLog.info("{} 苦力怕头:{} 免疫爆炸伤害 {}", LOG_TAG, player.getName().getString(), event.getAmount());
                return;
            }
            if (EarthAmuletData.isEmpowered(amulet) && isUndeadDamage(source)) {
                event.setCanceled(true);
                DebugLog.info("{} 强化:{} 免疫亡灵生物伤害 {}", LOG_TAG, player.getName().getString(), event.getAmount());
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 受伤判定异常", t);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide) {
                return;
            }
            LivingEntity target = event.getEntity();
            if (target == player || target == null || target.level().isClientSide) {
                return;
            }
            ItemStack amulet = EarthAmuletData.equippedAmulet(player);
            if (amulet.isEmpty() || !EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_WITHER_SKELETON)) {
                return;
            }
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION_TICKS, WITHER_AMPLIFIER, false,
                true, true));
            DebugLog.info("{} 凋灵骷髅头:{} 的攻击给 {} 附加凋零", LOG_TAG, player.getName().getString(),
                target.getType());
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 附加凋零异常", t);
        }
    }

    // ------------------------------------------------------------------ 每 tick 兜底

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        try {
            ItemStack amulet = EarthAmuletData.equippedAmulet(player);
            boolean dragon = !amulet.isEmpty() && EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_DRAGON);
            if (dragon) {
                // 1.2.12b:不再只依赖 Curios 的 curioTick —— 每 tick 兜底授予(幂等),
                // 这样即使某项 Curios 回调没触发,龙首飞行也一定能给上。
                grantFlight(player);
                // 4.6.0(内存):once 的 key 是 "dragon:" + UUID.toString()(≈ 45 字符),原来每 tick
                // 都会先拼好再传进去,而 once 内部第一次之后就立刻 return —— 纯浪费。
                // oncePending() 只看记录表有没有满,返回值与"once 还会不会输出"完全一致。
                if (DebugLog.oncePending()) {
                    DebugLog.once("dragon:" + player.getUUID(),
                        "{} 龙首:授予飞行(mayfly={}, flying={}, 已记录={})", LOG_TAG,
                        player.getAbilities().mayfly, player.getAbilities().flying, flightGranted(player));
                }
            }
            else {
                revokeFlight(player);
            }
            // 1.2.12b:猪灵头 —— 已经锁定目标的怪也要放开(事件只在"改目标"时触发)
            if (!amulet.isEmpty() && EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_PIGLIN)
                && player.tickCount % 20 == 0) {
                clearNetherTargets(player);
            }
            // 1.2.13:僵尸头同款兜底(压过七咒之戒这类强制仇恨)
            if (!amulet.isEmpty() && EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_ZOMBIE)
                && player.tickCount % 20 == 0) {
                clearUndeadTargets(player);
            }
            // 1.2.13 重构时被误删的一行(1.2.14 补回):龙首穿方块的每 tick 判定
            updateNoClip(player, amulet);

            if (!amulet.isEmpty() && EarthAmuletData.hasHead(amulet, EarthAmuletData.HEAD_WITHER_SKELETON)
                && player.hasEffect(MobEffects.WITHER)) {
                player.removeEffect(MobEffects.WITHER);
            }
        }
        catch (Throwable t) {
            DebugLog.error(LOG_TAG + " 佩戴兜底异常", t);
        }
    }

    // ------------------------------------------------------------------ 死亡 / 维度 / 登录

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            clearNoClip(player);
            revokeFlight(player);
            forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearNoClip(event.getEntity());
        revokeFlight(event.getEntity());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearNoClip(event.getEntity());
        revokeFlight(event.getEntity());
        forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // 进服时先清一次:防止上一局异常退出留下的 mayfly / noPhysics 变成永久残留
        clearNoClip(event.getEntity());
        revokeFlight(event.getEntity());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        if (original != null) {
            clearNoClip(original);
            revokeFlight(original);
            forget(original.getUUID());
        }
    }

    // ------------------------------------------------------------------ 判定小工具

    /** 亡灵生物:按 {@code MobType.UNDEAD} 判定(玩家自己的 MobType 由 mixin 覆写,这里只看攻击者)。 */
    public static boolean isUndeadMob(LivingEntity entity) {
        return entity != null && entity.getMobType() == MobType.UNDEAD;
    }

    /** 下界生物(见 {@link #NETHER_MOBS})。 */
    public static boolean isNetherMob(Entity entity) {
        return entity != null && NETHER_MOBS.contains(entity.getType());
    }

    /** 这次伤害是不是亡灵生物造成的(近战/弹射物都取"起因实体")。 */
    public static boolean isUndeadDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        return source.getEntity() instanceof LivingEntity living && isUndeadMob(living);
    }
    /** 2.11.6 诊断:戴护符的玩家每次受伤都打印伤害源,用来定位"受击抖动"到底是谁打的。 */
    @SubscribeEvent
    public static void onPlayerHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            DebugLog.warn("[AmuletHurtProbe] {} 受伤 amount={} type={} direct={} max={} health={}",
                player.getName().getString(), event.getAmount(),
                event.getSource().getMsgId(),
                event.getSource().getDirectEntity(),
                player.getMaxHealth(), player.getHealth());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
    /** 2.11.7e:把"生命提升"折算成常驻属性加成(只在该值变化时改一次,不反复重算)。 */
    private static final java.util.Map<java.util.UUID, Double> APPLIED_HEALTH_BONUS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 4.6.0(内存/CPU):生命提升修饰符的固定 UUID,**算一次**。
     *
     * <p>原来是 {@code applyPermanentHealthBonus} 里的局部调用:
     * {@code UUID.nameUUIDFromBytes("...".getBytes(UTF_8))} —— 每次都要
     * {@code String.getBytes}(新建 byte[])、MD5 摘要、再新建两个 long 数组拼出一个 UUID,
     * 而这个方法的**早退分支在算完 UUID 之后**,也就是"每 tick × 每个佩戴大地护符的玩家"都会白算一遍
     * MD5。值本身只由这个固定字符串决定,提成常量后语义完全不变。</p>
     */
    private static final java.util.UUID HEALTH_BOOST_MODIFIER_ID =
        java.util.UUID.nameUUIDFromBytes("enigmaticenhance:amulet_health_boost"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static void applyPermanentHealthBonus(Player player, double bonus) {
        try {
            var inst = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (inst == null) {
                return;
            }
            java.util.UUID id = HEALTH_BOOST_MODIFIER_ID;
            Double applied = APPLIED_HEALTH_BONUS.get(player.getUUID());
            if (applied != null && Math.abs(applied - bonus) < 1.0E-4D) {
                return;   // 值没变,什么都不做
            }
            APPLIED_HEALTH_BONUS.put(player.getUUID(), bonus);
            for (net.minecraft.world.entity.ai.attributes.AttributeModifier mod :
                new java.util.ArrayList<>(inst.getModifiers())) {
                if (mod.getId().equals(id)) {
                    inst.removeModifier(mod.getId());
                }
            }
            if (bonus > 0.0D) {
                float before = player.getHealth();
                inst.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    id, "enigmaticenhance:amulet_health_boost", bonus,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                if (player.getHealth() < before) {
                    player.setHealth(Math.min(before, player.getMaxHealth()));
                }
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}
