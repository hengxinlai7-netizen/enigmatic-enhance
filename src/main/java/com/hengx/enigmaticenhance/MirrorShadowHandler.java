package com.hengx.enigmaticenhance;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.2.11a:「瓶装镜中之影」的全部逻辑。
 *
 * <h2>1. 获得(转化)</h2>
 * 手持大地之瓶右键**两格高的玻璃类方块的下半块**:上下两格玻璃都碎掉。
 * <ul>
 *   <li>身上没有未使用的镜中之影 ⇒ 大地之瓶当场变成「瓶装镜中之影」,并记录此刻的位置/状态/血量/饱食度/饱和度;</li>
 *   <li>身上已经有一个未使用的 ⇒ **转化失败**:玻璃照样碎,对周围生物造成 1 点魔法伤害并施加 30 秒镜碎,
 *       大地之瓶不消耗,玩家下方(动作栏)显示提示。</li>
 * </ul>
 *
 * <h2>2. 回溯</h2>
 * 手持右键 1 秒(见 {@link MirrorShadowItem}) ⇒ 回到记录位置、把记录的状态/血量/饱食度/饱和度替换当前值,
 * 并获得 3 分钟「镜碎」;物品消耗 1 个。
 *
 * <p>记录写在物品 NBT 上,所以换手、丢地上再捡回来都还在。</p>
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MirrorShadowHandler {

    public static final String KEY_RECORDED = "MirrorRecorded";
    public static final String KEY_DIM = "MirrorDim";
    public static final String KEY_X = "MirrorX";
    public static final String KEY_Y = "MirrorY";
    public static final String KEY_Z = "MirrorZ";
    public static final String KEY_HEALTH = "MirrorHealth";
    public static final String KEY_FOOD = "MirrorFood";
    public static final String KEY_SATURATION = "MirrorSaturation";
    public static final String KEY_EFFECTS = "MirrorEffects";

    /** 转化失败时对周围造成多少点魔法伤害。 */
    public static final float FAIL_DAMAGE = 1.0F;
    /** 转化失败时的伤害半径(格)。 */
    public static final double FAIL_RADIUS = 5.0D;
    /** 转化失败时施加的镜碎时长(秒)。 */
    public static final int FAIL_SHATTER_SECONDS = 30;
    /** 使用后自身获得的镜碎时长(秒)= 3 分钟。 */
    public static final int USE_SHATTER_SECONDS = 180;

    private MirrorShadowHandler() {
    }

    // ------------------------------------------------------------------ 获得

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide()) {
                return;
            }
            Item bottle = ModItems.earthBottleOrNull();
            Item mirror = ModItems.mirrorShadowOrNull();
            if (bottle == null || mirror == null) {
                return;
            }
            ItemStack held = player.getItemInHand(event.getHand());
            if (held.isEmpty() || held.getItem() != bottle) {
                return;
            }
            var level = event.getLevel();
            var pos = event.getPos();
            var lower = level.getBlockState(pos);
            var upper = level.getBlockState(pos.above());
            if (!isGlassLike(lower) || !isGlassLike(upper)) {
                return;   // 必须是两格高的玻璃类方块,且点在下面那一格
            }
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

            // 两格玻璃都碎
            level.destroyBlock(pos, false);
            level.destroyBlock(pos.above(), false);
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);

            if (hasUnconsumedMirror(player)) {
                // 已经有一个没用的 ⇒ 转化失败
                failConversion(player, level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                return;
            }
            ItemStack recorded = new ItemStack(mirror);
            record(player, recorded);
            BottleConversionUtil.convertOne(player, event.getHand(), recorded);
            // 1.2.11b:刚拿到时给 1 秒冷却,避免"右键玻璃"那一下被继续当作使用,原地把自己传送走。
            player.getCooldowns().addCooldown(mirror, 20);
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.enigmaticenhance.mirror_shadow.gained"), true);
            DebugLog.info("[MirrorShadow] {} 获得瓶装镜中之影", player.getName().getString());
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[MirrorShadow] 转化异常", ex);
        }
    }

    /** 转化失败:1 点魔法伤害 + 30 秒镜碎给周围生物,并给玩家动作栏提示。 */
    private static void failConversion(Player player, net.minecraft.world.level.Level level, double x, double y, double z) {
        AABB box = new AABB(x - FAIL_RADIUS, y - FAIL_RADIUS, z - FAIL_RADIUS, x + FAIL_RADIUS, y + FAIL_RADIUS, z + FAIL_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
            try {
                target.hurt(player.damageSources().indirectMagic(player, player), FAIL_DAMAGE);
                MirrorShatterHandler.apply(target, FAIL_SHATTER_SECONDS);
            }
            catch (RuntimeException | LinkageError ignored) {
                // 单个目标失败不影响其它
            }
        }
        player.displayClientMessage(
            net.minecraft.network.chat.Component.translatable("message.enigmaticenhance.mirror_shadow.busy"), true);
        DebugLog.info("[MirrorShadow] {} 已有一个未使用的镜中之影,转化失败", player.getName().getString());
    }

    /** 玻璃类方块判定:名字里带 glass 或属于不透水方块(玻璃/彩色玻璃/玻璃板)。 */
    public static boolean isGlassLike(net.minecraft.world.level.block.state.BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.is(net.minecraft.tags.BlockTags.IMPERMEABLE)) {
            return true;
        }
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key != null && key.getPath().contains("glass");
    }

    // ------------------------------------------------------------------ 持有判定

    /** 这个玩家身上(背包/副手/书袋)有没有"还没用掉"的镜中之影。 */
    public static boolean hasUnconsumedMirror(Player player) {
        Item mirror = ModItems.mirrorShadowOrNull();
        if (mirror == null || player == null) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == mirror) {
                return true;
            }
        }
        ItemStack inBag = BookBag.findBook(player, ModIds.MIRROR_SHADOW_ID);
        return !inBag.isEmpty() && inBag.getItem() == mirror;
    }

    // ------------------------------------------------------------------ 记录 / 回溯

    /** 记录玩家当前状态到物品 NBT。 */
    public static void record(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        var tag = stack.getOrCreateTag();
        tag.putBoolean(KEY_RECORDED, true);
        tag.putString(KEY_DIM, player.level().dimension().location().toString());
        tag.putDouble(KEY_X, player.getX());
        tag.putDouble(KEY_Y, player.getY());
        tag.putDouble(KEY_Z, player.getZ());
        tag.putFloat(KEY_HEALTH, player.getHealth());
        tag.putInt(KEY_FOOD, player.getFoodData().getFoodLevel());
        tag.putFloat(KEY_SATURATION, player.getFoodData().getSaturationLevel());
        // 状态(药水效果)也记下来:id/时长/等级
        var list = new net.minecraft.nbt.ListTag();
        for (var instance : player.getActiveEffects()) {
            var entry = new net.minecraft.nbt.CompoundTag();
            var key = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect());
            if (key == null) {
                continue;
            }
            entry.putString("Id", key.toString());
            entry.putInt("Duration", instance.getDuration());
            entry.putInt("Amplifier", instance.getAmplifier());
            list.add(entry);
        }
        tag.put(KEY_EFFECTS, list);
    }

    public static boolean hasRecord(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(KEY_RECORDED);
    }

    /** tooltip 里显示记录位置。 */
    public static String recordedText(ItemStack stack) {
        if (!hasRecord(stack)) {
            return "-";
        }
        var tag = stack.getTag();
        return String.format("%s @ %.1f, %.1f, %.1f", tag.getString(KEY_DIM),
            tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
    }

    /** 回溯:位置 + 状态 + 血量 + 饱食度/饱和度,并给 3 分钟镜碎。 */
    public static void restore(Player player, ItemStack stack) {
        if (player == null || stack == null || !hasRecord(stack)) {
            return;
        }
        var tag = stack.getTag();
        try {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
                return;
            }
            net.minecraft.server.level.ServerLevel target = serverPlayer.serverLevel();
            String dim = tag.getString(KEY_DIM);
            double x = tag.getDouble(KEY_X);
            double y = tag.getDouble(KEY_Y);
            double z = tag.getDouble(KEY_Z);
            var dimKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation(dim));
            var server = serverPlayer.server;
            if (server != null) {
                var level = server.getLevel(dimKey);
                if (level != null) {
                    target = level;
                }
            }
            serverPlayer.teleportTo(target, x, y, z, serverPlayer.getYRot(), serverPlayer.getXRot());
            player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0F, tag.getFloat(KEY_HEALTH))));
            player.getFoodData().setFoodLevel(tag.getInt(KEY_FOOD));
            player.getFoodData().setSaturation(tag.getFloat(KEY_SATURATION));
            // 状态替换:先清空当前效果,再按记录恢复
            player.getActiveEffects().stream().map(i -> i.getEffect()).toList()
                .forEach(player::removeEffect);
            if (tag.contains(KEY_EFFECTS)) {
                var list = tag.getList(KEY_EFFECTS, 10);
                for (int i = 0; i < list.size(); i++) {
                    var entry = list.getCompound(i);
                    var id = net.minecraft.resources.ResourceLocation.tryParse(entry.getString("Id"));
                    if (id == null) {
                        continue;
                    }
                    var effect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(id);
                    if (effect != null) {
                        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,
                            entry.getInt("Duration"), entry.getInt("Amplifier")));
                    }
                }
            }
            MirrorShatterHandler.apply(player, USE_SHATTER_SECONDS);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
            DebugLog.info("[MirrorShadow] {} 回溯到 {} @ {} {} {}", player.getName().getString(), dim, x, y, z);
        }
        catch (RuntimeException | LinkageError ex) {
            DebugLog.error("[MirrorShadow] 回溯异常", ex);
        }
    }

    /** 兼容:让外部能直接对目标施加减伤(当前未使用)。 */
    public static void noop(LivingHurtEvent event) {
        // 占位,避免误删 import
    }
}
