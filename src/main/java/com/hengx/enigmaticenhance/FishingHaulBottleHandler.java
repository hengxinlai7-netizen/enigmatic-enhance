package com.hengx.enigmaticenhance;

import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.7.2:「渔获瓶」效果:每点幸运值 +10% 额外钓鱼产物,超过 100% 允许多次结算;
 * 放入古旧书袋({@code enigmaticaddons:antique_bag})时额外 +2 幸运。
 *
 * <p>4.8.1(用户实测「渔获瓶的幸运加成实际没有生效」,{@code generic.luck} = 0.0):
 * 原来那 +2 只加在 {@link #onFished} 的<b>局部变量</b>里,从来没落到属性上 —— 玩家用
 * {@code /attribute} 看到的幸运永远是 0,书袋里的加成在面板上是"不存在"的。现在改成
 * <b>真正的 {@code generic.luck} 属性修饰符</b>({@link #BAG_LUCK},ADDITION,固定 UUID,
 * transient 不写存档):书袋里有渔获瓶 ⇒ 幸运 +2,拿出书袋/关掉开关 ⇒ 立刻撤回。</p>
 *
 * <p>因此 {@link #onFished} 里<b>不再</b>手工 {@code luck += 2}:{@code Player#getLuck()}
 * 读的就是 {@code generic.luck},属性加了 2 它自然就含了这 2,再手工加一次会变成 +4。</p>
 */
public final class FishingHaulBottleHandler {

    /**
     * 书袋加成用的固定 UUID(4.8.1)。
     *
     * <p>必须是常量:修饰符靠 UUID 去重/撤回,随机 UUID 会每次叠加一条。</p>
     */
    static final UUID LUCK_MODIFIER_ID = UUID.fromString("7b0e1f2c-3a4d-4e5f-8a9b-0c1d2e3f4a5b");

    /** 修饰符名字:属性面板/调试里能看出是谁加的。 */
    private static final String LUCK_MODIFIER_NAME = "enigmaticenhance:fishing_haul_bottle";

    /** 书袋里那瓶给的幸运点数。 */
    public static final double BAG_LUCK = 2.0D;

    /** 每 10 tick(0.5 秒)维护一次属性:够快,又不必每 tick 扫背包/书袋。 */
    private static final int TICK_PERIOD = 10;

    private FishingHaulBottleHandler() {
    }

    private static boolean isBottle(ItemStack s) {
        return s != null && !s.isEmpty()
            && ModItems.FISHING_HAUL_BOTTLE.isPresent()
            && s.getItem() == ModItems.FISHING_HAUL_BOTTLE.get();
    }

    private static boolean inBag(Player p) {
        ItemStack found = BookBag.findBook(p,
            new ResourceLocation(EnigmaticEnhance.MODID, ModIds.FISHING_HAUL_BOTTLE_PATH));
        return found != null && !found.isEmpty();
    }

    private static boolean carried(Player p, boolean inBag) {
        for (ItemStack s : p.getInventory().items) {
            if (isBottle(s)) {
                return true;
            }
        }
        return isBottle(p.getOffhandItem()) || isBottle(p.getMainHandItem()) || inBag;
    }

    /**
     * 4.8.1:把"书袋里有渔获瓶 ⇒ {@code generic.luck} +2"落到<b>真实属性</b>上(幂等)。
     *
     * @return 是否改动了属性(加了或撤了)
     */
    static boolean maintainBagLuck(Player p) {
        if (p == null) {
            return false;
        }
        return maintainBagLuck(p, inBag(p));
    }

    /** 已经算过 {@code inBag} 的重载(钓鱼时一次判定两用,少扫一遍背包)。 */
    private static boolean maintainBagLuck(Player p, boolean inBag) {
        AttributeInstance luck = p.getAttribute(Attributes.LUCK);
        if (luck == null) {
            return false;
        }
        boolean want = inBag && RingConfig.fishingHaulEnabled();
        boolean has = luck.getModifier(LUCK_MODIFIER_ID) != null;
        if (want == has) {
            return false;
        }
        if (want) {
            luck.addTransientModifier(new AttributeModifier(LUCK_MODIFIER_ID, LUCK_MODIFIER_NAME,
                BAG_LUCK, AttributeModifier.Operation.ADDITION));
        }
        else {
            luck.removeModifier(LUCK_MODIFIER_ID);
        }
        return true;
    }

    /**
     * 4.8.1:书袋内容的增减由这里跟上(瓶子进出书袋、书袋换手、死亡重生、配置关掉)。
     *
     * <p>只跑服务端:{@code PlayerTickEvent} 两端都会触发,先挡掉客户端 —— 属性以服务端为准再同步。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player == null) {
            return;
        }
        Player p = event.player;
        if (p.level().isClientSide) {
            return;
        }
        if (p.tickCount % TICK_PERIOD != 0) {
            return;
        }
        try {
            maintainBagLuck(p);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        // 4.5.0:配置开关(默认开启)
        if (!RingConfig.fishingHaulEnabled()) {
            return;
        }
        try {
            Player p = event.getEntity();
            if (p == null || p.level().isClientSide) {
                return;
            }
            boolean inBag = inBag(p);
            // 4.8.1:先保证 +2 已经在属性上(玩家刚上线、tick 还没轮到就直接钓鱼的情况)
            maintainBagLuck(p, inBag);
            if (!carried(p, inBag)) {
                return;
            }
            // 4.8.1:这里读到的已经是含书袋 +2 的真实幸运值,不再本地 +2(否则会变成 +4)
            int luck = (int) Math.floor((double) p.getLuck());
            if (luck <= 0) {
                return;
            }
            int extra = luck / 10;
            float frac = (luck % 10) / 10.0F;
            if (p.level().random.nextFloat() < frac) {
                extra++;
            }
            if (extra <= 0) {
                return;
            }
            ServerLevel sl = (ServerLevel) p.level();
            LootTable table = sl.getServer().getLootData().getLootTable(new ResourceLocation("gameplay/fishing"));
            LootParams params = new LootParams.Builder(sl)
                .withParameter(LootContextParams.ORIGIN, p.position())
                .withParameter(LootContextParams.TOOL, p.getMainHandItem())
                .withParameter(LootContextParams.THIS_ENTITY, p)
                .withLuck(p.getLuck())
                .create(LootContextParamSets.FISHING);
            for (int i = 0; i < extra; i++) {
                table.getRandomItems(params, sl.random.nextLong(), event.getDrops()::add);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }
}
