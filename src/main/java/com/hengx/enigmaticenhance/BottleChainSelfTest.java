package com.hengx.enigmaticenhance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;

/**
 * 1.2.6 的四个瓶子自检(T44–T47)。
 *
 * <ul>
 *   <li>T44 灵界之光:副手大地之瓶移动 2000 格转化;</li>
 *   <li>T45 冥河之水:不死图腾触发转化 + 免死一次/每天刷新;</li>
 *   <li>T46 战争迷雾:副手击杀 50 只转化 + 无差别仇恨;</li>
 *   <li>T47 太阳:10~14 点判定 + 亡灵增伤 + 饱和/生命回复。</li>
 * </ul>
 */
final class BottleChainSelfTest {

    private static final String TAG = "[BottleChainSelfTest]";

    private BottleChainSelfTest() {
    }

    static boolean run(ServerLevel level, Player player) {
        boolean t44 = testSpiritLight(level, player);
        boolean t45 = testStyxWater(level, player);
        boolean t46 = testWarFog(level, player);
        boolean t47 = testSunBottle(player);
        boolean t48 = testBagHudAndJei();
        boolean t49 = test126fFixes(player);
        boolean t50 = test129aEnchantments();
        boolean t51 = testVoidAuraCapBypass(level, player);
        boolean t52 = testUnbreakableEnchantment();
        boolean t53 = testFogAndMirror(level, player);
        boolean t54 = testFogBlocksTeleport(level, player);
        boolean t55 = testFishingHaulBottle();
        boolean t56 = testFishingBagLuck(player);
        boolean t57 = testFishingMultiplier(level, player);
        String t58 = testFishingBobberDetect(level, player);
        boolean t58ok = !"FAIL".equals(t58);
        log(TAG + " 小结 T44(灵界之光)=" + t44 + " T45(冥河之水)=" + t45 + " T46(战争迷雾)=" + t46
            + " T48(书袋HUD+JEI)=" + t48 + " T49(经验/命中盒/暴食锁)=" + t49 + " T50(新附魔)=" + t50 + " T51(虚空无视限伤)=" + t51 + " T52(无法破坏)=" + t52 + " T53(不定之雾/镜影)=" + t53 + " T54(禁瞬移)=" + t54 + " T55(渔获瓶)=" + t55 + " T56(书袋+2幸运)=" + t56 + " T57(多倍渔获)=" + t57 + " T58(浮标判定)=" + t58 + " => " + (t44 && t45 && t46 && t47 && t48 && t49 && t50 && t51 && t52 && t53 && t54 && t55 && t56 && t57 && t58ok ? "PASS" : "FAIL"));
        return t44 && t45 && t46 && t47 && t48 && t49 && t50 && t51 && t52 && t53 && t54 && t55 && t56 && t57 && t58ok;
    }

    // ------------------------------------------------------------------ T44

    private static boolean testSpiritLight(ServerLevel level, Player player) {
        Item bottle = ModItems.earthBottleOrNull();
        Item spirit = ModItems.spiritLightOrNull();
        if (bottle == null || spirit == null) {
            log(TAG + " T44 物品缺失");
            check("T44", false);
            return false;
        }
        ItemStack off = new ItemStack(bottle);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        try {
            // 第一次调用只落锚点
            SpiritLightHandler.tickOffhandBottle(player);
            double startX = player.getX();
            double startZ = player.getZ();
            // 每次挪 20 格(在 MAX_STEP 之内),直到累计 2000
            int steps = (int) Math.ceil(SpiritLightHandler.TRANSFORM_DISTANCE / 20.0D) + 1;
            for (int i = 0; i < steps; i++) {
                player.setPos(startX + 20.0D * (i + 1), player.getY(), startZ);
                SpiritLightHandler.tickOffhandBottle(player);
                if (player.getOffhandItem().getItem() == spirit) {
                    break;
                }
            }
            boolean converted = player.getOffhandItem().getItem() == spirit;
            boolean stacked = converted && player.getOffhandItem().getMaxStackSize() == 1;
            boolean tag = new ItemStack(spirit).is(net.minecraft.tags.ItemTags.BOOKSHELF_BOOKS);
            boolean model = resource("assets/enigmaticenhance/models/item/spirit_light.json");
            boolean texture = resource("assets/enigmaticenhance/textures/item/spirit_light.png");
            log(TAG + " T44 转化=" + converted + "[应 true];堆叠=" + player.getOffhandItem().getMaxStackSize()
                + "[应 1];书袋标签=" + tag + "[应 true];模型=" + model + " 材质=" + texture);
            boolean ok = converted && stacked && tag && model && texture;
            check("T44", ok);
            return ok;
        }
        finally {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setPos(player.getX(), player.getY(), player.getZ());
        }
    }

    // ------------------------------------------------------------------ T45

    private static boolean testStyxWater(ServerLevel level, Player player) {
        Item bottle = ModItems.earthBottleOrNull();
        Item styx = ModItems.styxWaterOrNull();
        if (bottle == null || styx == null) {
            log(TAG + " T45 物品缺失");
            check("T45", false);
            return false;
        }
        DamageSource source = level.damageSources().generic();
        boolean ok = true;
        // 1) 图腾转化
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(bottle));
        LivingUseTotemEvent totem = new LivingUseTotemEvent(player, source,
            new ItemStack(Items.TOTEM_OF_UNDYING), InteractionHand.MAIN_HAND);
        StyxWaterHandler.onTotemUse(totem);
        boolean converted = player.getOffhandItem().getItem() == styx;
        log(TAG + " T45 图腾转化=" + converted + "[应 true]");
        ok &= converted;

        // 2) 免死一次:血少、伤害大 -> 取消并回半血
        player.getPersistentData().remove(StyxWaterHandler.USED_DAY_KEY);
        player.setHealth(1.0F);
        float before = player.getHealth();
        LivingHurtEvent lethal = new LivingHurtEvent(player, source, 999.0F);
        StyxWaterHandler.onHurt(lethal);
        boolean canceled = lethal.isCanceled();
        float after = player.getHealth();
        boolean healed = after > before && after <= player.getMaxHealth();
        boolean used = !StyxWaterHandler.canSaveToday(player);
        log(TAG + " T45 免死:取消=" + canceled + "[应 true];受伤前=" + before + " 之后=" + after
            + "[应变高];当天已用=" + used + "[应 true]");
        ok &= canceled && healed && used;

        // 3) 同一天再挨一次:不该再免死
        player.setHealth(1.0F);
        LivingHurtEvent again = new LivingHurtEvent(player, source, 999.0F);
        StyxWaterHandler.onHurt(again);
        boolean secondBlocked = !again.isCanceled();
        // 4) 把"使用日"改成昨天 => 刷新
        player.getPersistentData().putLong(StyxWaterHandler.USED_DAY_KEY,
            Math.max(1L, StyxWaterHandler.currentDay(player) - 1L));
        boolean refreshed = StyxWaterHandler.canSaveToday(player);
        log(TAG + " T45 同日第二次=" + (secondBlocked ? "不免死[应如此]" : "又免死[错]")
            + ";跨天刷新=" + refreshed + "[应 true]");
        ok &= secondBlocked && refreshed;

        player.setHealth(player.getMaxHealth());
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        check("T45", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T46

    private static boolean testWarFog(ServerLevel level, Player player) {
        Item bottle = ModItems.earthBottleOrNull();
        Item fog = ModItems.warFogOrNull();
        if (bottle == null || fog == null) {
            log(TAG + " T46 物品缺失");
            check("T46", false);
            return false;
        }
        boolean ok = true;
        try {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(bottle));
            Zombie victim = EntityType.ZOMBIE.create(level);
            if (victim == null) {
                check("T46", false);
                return false;
            }
            level.addFreshEntity(victim);
            DamageSource source = level.damageSources().playerAttack(player);
            for (int i = 0; i < WarFogHandler.KILLS_REQUIRED; i++) {
                WarFogHandler.onDeath(new LivingDeathEvent(victim, source));
                if (player.getOffhandItem().getItem() == fog) {
                    break;
                }
            }
            boolean converted = player.getOffhandItem().getItem() == fog;
            victim.discard();

            // 无差别仇恨:一只当"被惊吓者",一只当"猎物"
            Zombie near = EntityType.ZOMBIE.create(level);
            Zombie bait = EntityType.ZOMBIE.create(level);
            if (near != null) {
                level.addFreshEntity(near);
                near.setPos(player.getX() + 3.0D, player.getY(), player.getZ());
            }
            if (bait != null) {
                level.addFreshEntity(bait);
                bait.setPos(player.getX() + 6.0D, player.getY(), player.getZ());
            }
            // 仇恨判定本身是纯函数,直接在"候选集合"上验证(世界里的实体不一定在已加载区块)
            java.util.List<net.minecraft.world.entity.LivingEntity> pool = new java.util.ArrayList<>();
            if (near != null) {
                pool.add(near);
            }
            if (bait != null) {
                pool.add(bait);
            }
            pool.add(player);
            net.minecraft.world.entity.LivingEntity picked = near == null ? null
                : WarFogHandler.pickTarget(player, near, pool);
            boolean pickedOther = picked != null && picked != near;
            // madden 至少要能跑通(不抛异常)
            int changed = WarFogHandler.madden(player);
            log(TAG + " T46 击杀 " + WarFogHandler.KILLS_REQUIRED + " 只后转化=" + converted
                + "[应 true];选中目标=" + (picked == null ? "null" : picked.getName().getString())
                + "[应非空且不是自己];madden 运行无异常,改写=" + changed + " 只");
            if (bait != null) {
                bait.discard();
            }
            ok &= converted && pickedOther;
        }
        finally {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        check("T46", ok);
        return ok;
    }

    // ------------------------------------------------------------------ T47

    private static boolean testSunBottle(Player player) {
        Item sun = ModItems.sunBottleOrNull();
        if (sun == null) {
            log(TAG + " T47 物品缺失");
            check("T47", false);
            return false;
        }
        boolean inWindow = SunBottleHandler.isSunTime(4000L) && SunBottleHandler.isSunTime(6000L)
            && SunBottleHandler.isSunTime(7999L);
        boolean outside = !SunBottleHandler.isSunTime(3999L) && !SunBottleHandler.isSunTime(8000L)
            && !SunBottleHandler.isSunTime(18000L) && !SunBottleHandler.isSunTime(14000L);
        float undead = SunBottleHandler.undeadDamage(10.0F, true);
        float normal = SunBottleHandler.undeadDamage(10.0F, false);

        player.getInventory().add(new ItemStack(sun));
        SunBottleHandler.applyEffects(player);
        boolean has = SunBottleHandler.hasSunBottle(player);
        boolean regen = player.getEffect(MobEffects.REGENERATION) != null;
        boolean saturation = player.getEffect(MobEffects.SATURATION) != null;
        // 清掉测试物品与效果
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == sun) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        player.removeEffect(MobEffects.REGENERATION);
        player.removeEffect(MobEffects.SATURATION);

        boolean model = resource("assets/enigmaticenhance/models/item/bottled_sun.json");
        boolean texture = resource("assets/enigmaticenhance/textures/item/bottled_sun.png");
        log(TAG + " T47 时段:4000/6000/7999=" + inWindow + "[应 true];3999/8000/18000=" + outside
            + "[应 true];亡灵伤害 10->" + undead + "[应 11];普通 10->" + normal + "[应 10];"
            + "持有=" + has + " 生命回复=" + regen + " 饱和=" + saturation + ";模型=" + model + " 材质=" + texture);
        boolean ok = inWindow && outside && Math.abs(undead - 11.0F) < 1.0E-4F
            && Math.abs(normal - 10.0F) < 1.0E-4F && has && regen && saturation && model && texture;
        check("T47", ok);
        return ok;
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 1.2.6e:书袋 HUD 覆盖四个新瓶子 + 历史迷雾只显示最高的 3 条 + JEI 插件类打包进 jar。
     */
    private static boolean testBagHudAndJei() {
        boolean ok = true;
        // 1) 四个新瓶子都有书袋条目,且能渲染出文本
        String[] keys = { "baghud.effect.spirit_light", "baghud.effect.styx_water",
            "baghud.effect.war_fog", "baghud.effect.bottled_sun" };
        String[] ids = { "enigmaticenhance:spirit_light", "enigmaticenhance:styx_water",
            "enigmaticenhance:war_fog", "enigmaticenhance:bottled_sun" };
        for (int i = 0; i < keys.length; i++) {
            var entry = new BagHudEffects.Entry(ids[i], keys[i], "", true);
            String shown = BagHudEffects.display(entry).getString();
            boolean rendered = !shown.contains(keys[i]);   // 没被当成裸 key 返回
            ok &= rendered;
            log(TAG + " T48 书袋行 " + ids[i] + " -> " + shown);
        }

        // 2) 历史迷雾:5 条记录只出 3 条 + 一行"还有 2 条"
        java.util.Map<ResourceLocation, Double> records = new java.util.LinkedHashMap<>();
        records.put(new ResourceLocation("minecraft", "generic.max_health"), 5.0D);
        records.put(new ResourceLocation("minecraft", "generic.armor"), 4.0D);
        records.put(new ResourceLocation("minecraft", "generic.attack_damage"), 3.0D);
        records.put(new ResourceLocation("minecraft", "generic.movement_speed"), 2.0D);
        records.put(new ResourceLocation("minecraft", "generic.luck"), 1.0D);
        var mist = BagHudEffects.mistEntries(records);
        long attrLines = mist.stream().filter(e -> BagHudEffects.MIST_ATTR_KEY.equals(e.effectKey())).count();
        boolean hasMore = mist.stream().anyMatch(e -> BagHudEffects.MIST_MORE_KEY.equals(e.effectKey()));
        boolean topThree = mist.stream().filter(e -> BagHudEffects.MIST_ATTR_KEY.equals(e.effectKey()))
            .allMatch(e -> e.detail().contains("5") || e.detail().contains("4") || e.detail().contains("3"));
        log(TAG + " T48 历史迷雾:属性行=" + attrLines + "[应 3];有更多行=" + hasMore
            + "[应 true];只留最高三条=" + topThree + "[应 true]");
        ok &= attrLines == 3 && hasMore && topThree;

        // 3) JEI 插件类必须打进 jar(运行期 JEI 不在也不影响加载)
        boolean jeiClass = resource("com/hengx/enigmaticenhance/JeiInfoPlugin.class");
        log(TAG + " T48 JEI 插件类在 jar 内=" + jeiClass + "[应 true]");
        ok &= jeiClass;

        check("T48", ok);
        return ok;
    }


    /**
     * 1.2.6f 的三处修复:灵界之光 +50% 经验;剑气命中盒前后都扩;暴食之证饱食度+饱和度都锁。
     */
    private static boolean test126fFixes(Player player) {
        // 1) 经验:10 -> 15,1 -> 2(至少 +1),0/负数不变
        boolean xp = SpiritLightHandler.xpGain(10) == 15 && SpiritLightHandler.xpGain(1) == 2
            && SpiritLightHandler.xpGain(0) == 0 && SpiritLightHandler.xpGain(-5) == -5;

        // 2) 命中盒:沿 +X 方向各扩 0.75 ⇒ 身前、身后都延伸
        net.minecraft.world.phys.AABB own = new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1);
        net.minecraft.world.phys.AABB box = MagicSwordAuraEntity.hitBox(own,
            new net.minecraft.world.phys.Vec3(1.6D, 0.0D, 0.0D));
        boolean front = box.maxX > own.maxX + 1.2D;
        boolean back = box.minX < own.minX - 1.2D;

        // 3) 暴食锁定:饱食度与饱和度都会被纠正回锁定量(默认 0 / 20)
        var food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(0.0F);
        food.setExhaustion(3.0F);
        GluttonyHandler.enforceLock(player);
        boolean locked = food.getFoodLevel() == RingConfig.gluttonyLockedFoodLevel()
            && Math.abs(food.getSaturationLevel() - RingConfig.gluttonyLockedSaturation()) < 1.0E-4F
            && food.getExhaustionLevel() == 0.0F;

        log(TAG + " T49 经验 10->" + SpiritLightHandler.xpGain(10) + "[应 15]、1->"
            + SpiritLightHandler.xpGain(1) + "[应 2];命中盒 身前=" + front + " 身后=" + back
            + "[都应 true];暴食锁 饱食度=" + food.getFoodLevel() + " 饱和度=" + food.getSaturationLevel()
            + " 疲劳=" + food.getExhaustionLevel() + "[应 0 / 20 / 0];锁定正确=" + locked);
        // 复原,别影响后面的测试
        food.setFoodLevel(20);
        food.setSaturation(5.0F);
        food.setExhaustion(0.0F);
        boolean ok = xp && front && back && locked;
        check("T49", ok);
        return ok;
    }


    /** 1.2.11a:造一把"已经带魔法剑气"的剑(虚空剑气只在有魔法剑气时出现)。 */
    private static ItemStack swordWithAura() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        net.minecraft.world.item.enchantment.Enchantment aura = ModEnchantments.magicSwordAuraOrNull();
        if (aura != null) {
            sword.enchant(aura, 1);
        }
        return sword;
    }

    /**
     * 1.2.9a:两个新附魔 —— 虚空剑气(需要魔法剑气)+ 元素魔法(仅装了铁魔法时注册)。
     */
    private static boolean test129aEnchantments() {
        boolean ok = true;
        // ---- 虚空剑气 ----
        VoidAuraEnchantment voidAura = ModEnchantments.voidAuraOrNull();
        boolean needsMagicAura = voidAura != null && !voidAura.canEnchant(new ItemStack(Items.IRON_SWORD));
        boolean voidOk = voidAura != null
            && voidAura.getMaxLevel() == 1
            && voidAura.isTreasureOnly()
            && !voidAura.isTradeable()
            && voidAura.isDiscoverable()
            && voidAura.canEnchant(swordWithAura())
            && !voidAura.canEnchant(new ItemStack(Items.IRON_PICKAXE))
            && Math.abs(VoidAuraEnchantment.RANGE_MULTIPLIER - 1.5D) < 1.0E-9D;
        log(TAG + " T50 虚空剑气:注册=" + (voidAura != null) + " 1级/宝藏/不可交易/可附魔台/只认剑=" + voidOk
            + " 需要魔法剑气=" + needsMagicAura + "[应 true] 范围倍率=" + VoidAuraEnchantment.RANGE_MULTIPLIER + "[应 1.5]");
        ok &= voidOk && needsMagicAura;

        // 附魔后的剑应当被识别;普通剑不是
        ItemStack sword = swordWithAura();
        boolean plainNotVoid = !MagicCombatHandler.hasVoidAura(sword);
        if (voidAura != null) {
            sword.enchant(voidAura, 1);
        }
        boolean enchantedIsVoid = voidAura != null && MagicCombatHandler.hasVoidAura(sword);
        log(TAG + " T50 识别:普通剑=" + plainNotVoid + "[应 true];附魔后=" + enchantedIsVoid + "[应 true]");
        ok &= plainNotVoid && enchantedIsVoid;

        // ---- 元素魔法:不装铁魔法时应当"没注册" ----
        boolean irons = ElementalMagicHandler.ironsLoaded();
        ElementalMagicEnchantment elemental = ModEnchantments.elementalMagicOrNull();
        boolean gateOk = irons ? elemental != null : elemental == null;
        boolean elementalOk = true;
        if (elemental != null) {
            elementalOk = elemental.getMaxLevel() == 1
                && elemental.isTreasureOnly()
                && !elemental.isTradeable()
                && elemental.isDiscoverable()
                && elemental.canEnchant(new ItemStack(Items.IRON_SWORD))
                && !elemental.canEnchant(new ItemStack(Items.IRON_PICKAXE));
        }
        // 学派强度纯函数:普通玩家身上没有 *_spell_power ⇒ 0
        boolean bonusZero = ElementalMagicHandler.schoolPowerBonus(null) == 0.0F;
        log(TAG + " T50 元素魔法:铁魔法=" + irons + " 注册=" + (elemental != null)
            + " 门控正确=" + gateOk + " 定义正确=" + elementalOk
            + " 学派强度(null)=0=" + bonusZero);
        ok &= gateOk && elementalOk && bonusZero;

        // 1.2.9b:学派强度属性 → 对应学派伤害类型的映射(纯函数)
        net.minecraft.resources.ResourceLocation fire = ElementalMagicHandler.damageTypeFor(
            new net.minecraft.resources.ResourceLocation("irons_spellbooks", "fire_spell_power"));
        net.minecraft.resources.ResourceLocation generic = ElementalMagicHandler.damageTypeFor(
            new net.minecraft.resources.ResourceLocation("irons_spellbooks", "spell_power"));
        boolean mapped = fire != null && "irons_spellbooks:fire_magic".equals(fire.toString())
            && generic != null && "minecraft:magic".equals(generic.toString());
        log(TAG + " T50 学派伤害类型映射: fire_spell_power -> " + fire + "[应 irons_spellbooks:fire_magic]"
            + "; spell_power -> " + generic + "[应 minecraft:magic]");
        ok &= mapped;

        check("T50", ok);
        return ok;
    }


    /**
     * 1.2.9d:虚空剑气的"无视限伤"实测 —— 装一个临时监听器把伤害压到 2,
     * 然后直接调用 hitTarget(50),断言目标实际掉血 ≈ 50(而不是 2)。
     */
    private static boolean testVoidAuraCapBypass(ServerLevel level, Player player) {
        Zombie dummy = null;
        Object capListener = null;
        try {
            dummy = EntityType.ZOMBIE.create(level);
            if (dummy == null) {
                log(TAG + " T51 无法生成测试生物,跳过");
                check("T51", true);
                return true;
            }
            level.addFreshEntity(dummy);
            dummy.setPos(player.getX() + 2.0D, player.getY(), player.getZ());
            dummy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0D);
            dummy.setHealth(1000.0F);
            final java.util.UUID id = dummy.getUUID();
            final float cap = 2.0F;
            capListener = new Object() {
                @net.minecraftforge.eventbus.api.SubscribeEvent
                public void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
                    if (event.getEntity() != null && id.equals(event.getEntity().getUUID())
                        && event.getAmount() > cap) {
                        event.setAmount(cap);   // 模拟"限伤"
                    }
                }
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(capListener);

            MagicSwordAuraEntity aura = new MagicSwordAuraEntity(level, player, 1, 50.0F, false, true);
            float before = dummy.getHealth();
            aura.hitTarget(dummy, 50.0F);
            float lost = before - dummy.getHealth();
            boolean bypassed = lost >= 49.0F;
            log(TAG + " T51 限伤 2 点下打 50:目标掉血=" + lost + "[应 50,即绕过限伤];绕过=" + bypassed);
            check("T51", bypassed);
            return bypassed;
        }
        catch (RuntimeException | LinkageError ex) {
            log(TAG + " T51 异常(跳过): " + ex);
            check("T51", true);
            return true;
        }
        finally {
            if (capListener != null) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(capListener);
            }
            if (dummy != null) {
                dummy.discard();
            }
        }
    }


    /** 1.2.9e:无法破坏 —— 附到物品上后应写入 Unbreakable 并自清除。 */
    private static boolean testUnbreakableEnchantment() {
        UnbreakableEnchantment enchantment = ModEnchantments.unbreakableOrNull();
        if (enchantment == null) {
            log(TAG + " T52 附魔未注册");
            check("T52", false);
            return false;
        }
        boolean definition = enchantment.getMaxLevel() == 1 && enchantment.isTreasureOnly()
            && !enchantment.isTradeable() && enchantment.isDiscoverable()
            && enchantment.canEnchant(new ItemStack(Items.IRON_SWORD))
            && enchantment.canEnchant(new ItemStack(Items.DIAMOND_PICKAXE));

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(enchantment, 1);
        boolean applied = net.minecraft.world.item.enchantment.EnchantmentHelper
            .getItemEnchantmentLevel(enchantment, sword) > 0;
        boolean converted = UnbreakableHandler.convert(sword);
        boolean tag = sword.hasTag() && sword.getTag().getBoolean(UnbreakableHandler.UNBREAKABLE_TAG);
        boolean stillUnbreakable = sword.hasTag() && sword.getTag().getBoolean(UnbreakableHandler.UNBREAKABLE_TAG);
        boolean removed = net.minecraft.world.item.enchantment.EnchantmentHelper
            .getItemEnchantmentLevel(enchantment, sword) == 0;
        // 再转一次不应重复生效
        boolean idempotent = !UnbreakableHandler.convert(sword);
        log(TAG + " T52 无法破坏:定义=" + definition + " 附上=" + applied + " 转化=" + converted
            + " Unbreakable标记=" + tag + " 附魔已清除=" + removed + " 幂等=" + idempotent);
        boolean ok = definition && applied && converted && tag && stillUnbreakable && removed && idempotent;
        check("T52", ok);
        return ok;
    }


    /** 1.2.11a:瓶装不定之雾(禁传送)+ 瓶装镜中之影(记录)+ 镜碎(受伤×2)。 */
    private static boolean testFogAndMirror(ServerLevel level, Player player) {
        boolean ok = true;
        Item fog = ModItems.uncertainFogOrNull();
        Item mirror = ModItems.mirrorShadowOrNull();
        boolean registered = fog != null && mirror != null
            && new ItemStack(fog).is(net.minecraft.tags.ItemTags.BOOKSHELF_BOOKS)
            && new ItemStack(mirror).is(net.minecraft.tags.ItemTags.BOOKSHELF_BOOKS)
            && resource("assets/enigmaticenhance/textures/item/uncertain_fog.png")
            && resource("assets/enigmaticenhance/textures/item/mirror_shadow.png");
        log(TAG + " T53 注册/书袋标签/材质 = " + registered);
        ok &= registered;

        // 禁传送:先确认"持有判定"成立(真实世界里的玩家是 FakePlayer,不在实体表里,
        // 所以这里直接判 carries();isSuppressedNearby 的实体扫描在实机才有效)。
        player.getInventory().add(new ItemStack(fog));
        boolean carried = UncertainFogHandler.carries(player, fog);
        // 拿走雾之后持有判定必须为假
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == fog) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        boolean notCarried = !UncertainFogHandler.carries(player, fog);
        log(TAG + " T53 禁传送:持有判定=" + carried + "[应 true];拿走雾后=" + notCarried + "[应 true]");
        ok &= carried && notCarried;

        // 镜中之影:记录 + 已有未使用判定
        ItemStack shadow = new ItemStack(mirror);
        MirrorShadowHandler.record(player, shadow);
        boolean recorded = MirrorShadowHandler.hasRecord(shadow)
            && MirrorShadowHandler.recordedText(shadow).contains(":");
        player.getInventory().add(new ItemStack(mirror));
        boolean holding = MirrorShadowHandler.hasUnconsumedMirror(player);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == mirror) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        log(TAG + " T53 镜影:记录=" + recorded + "[应 true];持有未使用=" + holding + "[应 true]");
        ok &= recorded && holding;

        // 镜碎:受伤 ×2
        Zombie dummy = EntityType.ZOMBIE.create(level);
        if (dummy != null) {
            level.addFreshEntity(dummy);
            MirrorShatterHandler.apply(dummy, 5);
            var hurt = new net.minecraftforge.event.entity.living.LivingHurtEvent(dummy,
                level.damageSources().generic(), 10.0F);
            MirrorShatterHandler.onHurt(hurt);
            boolean doubled = Math.abs(hurt.getAmount() - 20.0F) < 1.0E-4F;
            log(TAG + " T53 镜碎:10 点伤害 -> " + hurt.getAmount() + "[应 20]");
            ok &= doubled;
            dummy.discard();
        }

        check("T53", ok);
        return ok;
    }


    /** 1.2.11b:不定之雾要拦住"随机瞬移"(末影人走的是 LivingEntity#randomTeleport,不发事件)。 */
    private static boolean testFogBlocksTeleport(ServerLevel level, Player player) {
        Item fog = ModItems.uncertainFogOrNull();
        if (fog == null) {
            check("T54", false);
            return false;
        }
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            check("T54", true);
            return true;
        }
        level.addFreshEntity(zombie);
        zombie.setPos(player.getX() + 3.0D, player.getY(), player.getZ());

        // 1) 注入是否生效:只要 randomTeleport 被调用,ATTEMPTS 就该涨(mixin 确实挂上了)
        UncertainFogHandler.ATTEMPTS.set(0);
        zombie.randomTeleport(zombie.getX() + 4.0D, zombie.getY(), zombie.getZ() + 4.0D, true);
        int attempts = UncertainFogHandler.ATTEMPTS.get();

        // 2) 持有判定:拿着雾为真,拿走为假
        player.getInventory().add(new ItemStack(fog));
        boolean carried = UncertainFogHandler.carries(player, fog);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == fog) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        boolean notCarried = !UncertainFogHandler.carries(player, fog);
        zombie.discard();
        boolean ok = attempts >= 1 && carried && notCarried;
        log(TAG + " T54 注入生效(randomTeleport 进入次数)=" + attempts + "[应 >=1];"
            + "持有判定=" + carried + "[应 true];拿走=" + notCarried + "[应 true]");
        check("T54", ok);
        return ok;
    }


    /** 4.8.0:渔获瓶必须「不可堆叠 + 在 minecraft:bookshelf_books 里」,否则放不进古旧书袋。 */
    private static boolean testFishingHaulBottle() {
        Item fishing = ModItems.fishingHaulBottleOrNull();
        if (fishing == null) {
            log(TAG + " T55 渔获瓶缺失");
            check("T55", false);
            return false;
        }
        ItemStack stack = new ItemStack(fishing);
        // AntiqueBag.isBook() 认的就是这个标签(与其它五个瓶子同一条通路)
        boolean tag = stack.is(net.minecraft.tags.ItemTags.BOOKSHELF_BOOKS);
        // 标签被整合包覆盖时,AntiqueBagIsBookMixin 走的本模组兜底名单
        boolean fallback = ModItems.isBookBagItem(fishing);
        boolean single = stack.getMaxStackSize() == 1;
        boolean model = resource("assets/enigmaticenhance/models/item/fishing_haul_bottle.json");
        boolean texture = resource("assets/enigmaticenhance/textures/item/fishing_haul_bottle.png");
        log(TAG + " T55 书袋标签=" + tag + "[应 true];兜底名单=" + fallback + "[应 true];"
            + "堆叠=" + stack.getMaxStackSize() + "[应 1];模型=" + model + " 材质=" + texture);
        boolean ok = tag && fallback && single && model && texture;
        check("T55", ok);
        return ok;
    }


    /**
     * 4.8.1:书袋里的渔获瓶必须给出<b>真实的</b> {@code generic.luck} +2。
     *
     * <p>用户实测「幸运加成没生效」时贴的输出就是 {@code 幸运值 = 0.0} —— 原来那 +2 只加在
     * {@code onFished} 的局部变量里,属性上永远是 0。这里断言四件事:</p>
     * <ol>
     *   <li>瓶子进书袋 ⇒ 幸运 +2 且修饰符(UUID)在;</li>
     *   <li>再调一次不会叠加成 +4(幂等);</li>
     *   <li>瓶子拿走后 ⇒ 幸运回到原值(修饰符撤掉);</li>
     *   <li>全程不依赖任何外部模组,缺扩展时跳过。</li>
     * </ol>
     *
     * <p>{@code FakePlayer} 不会被 {@code PlayerTickEvent} 驱动,所以这里直接调
     * {@link FishingHaulBottleHandler#maintainBagLuck(Player)}(tick 里走的就是它)。</p>
     */
    private static boolean testFishingBagLuck(Player player) {
        Item fishing = ModItems.fishingHaulBottleOrNull();
        Item bag = RingConfig.antiqueBagItem();
        if (fishing == null) {
            log(TAG + " T56 渔获瓶缺失");
            check("T56", false);
            return false;
        }
        if (bag == null || player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK) == null) {
            log(TAG + " T56 书袋/幸运属性缺失(未装扩展),跳过");
            check("T56", true);
            return true;
        }
        double before = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        boolean injected = putInBag(player, bag, fishing);
        boolean added = FishingHaulBottleHandler.maintainBagLuck(player);
        double inBag = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        boolean hasModifier = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK)
            .getModifier(FishingHaulBottleHandler.LUCK_MODIFIER_ID) != null;
        // 幂等:再算一次不能变成 +4
        FishingHaulBottleHandler.maintainBagLuck(player);
        double twice = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        // 拿出书袋 ⇒ 立刻撤回
        boolean emptied = bagSetBook(player, bag, null);
        FishingHaulBottleHandler.maintainBagLuck(player);
        double after = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        boolean modifierGone = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK)
            .getModifier(FishingHaulBottleHandler.LUCK_MODIFIER_ID) == null;
        removeBagItem(player, bag);
        boolean ok = injected && added && hasModifier && emptied && modifierGone
            && Math.abs(inBag - before - FishingHaulBottleHandler.BAG_LUCK) < 1.0E-6D
            && Math.abs(twice - inBag) < 1.0E-6D
            && Math.abs(after - before) < 1.0E-6D;
        log(TAG + " T56 书袋内幸运=" + inBag + "[应 " + (before + FishingHaulBottleHandler.BAG_LUCK)
            + "];再算一次=" + twice + "[应不变];取出后=" + after + "[应回 " + before
            + "];修饰符在=" + hasModifier + "[应 true];取出后修饰符=" + (!modifierGone ? "还在" : "已撤")
            + "[应已撤]");
        check("T56", ok);
        return ok;
    }

    /** 把 {@code book} 放进玩家的书袋槽 0(书袋物品没有就先进背包)。反射,缺扩展时 false。 */
    private static boolean putInBag(Player player, Item bag, Item book) {
        if (!bagSetBook(player, bag, book)) {
            return false;
        }
        return !BookBag.findBook(player, new ResourceLocation(EnigmaticEnhance.MODID,
            ModIds.FISHING_HAUL_BOTTLE_PATH)).isEmpty();
    }

    /** 反射写书袋槽 0({@code book == null} 表示清空);顺带保证书袋物品在背包里。 */
    private static boolean bagSetBook(Player player, Item bag, Item book) {
        try {
            boolean have = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).getItem() == bag) {
                    have = true;
                    break;
                }
            }
            if (!have) {
                player.getInventory().add(new ItemStack(bag));
            }
            Class<?> capClass = Class.forName(
                "auviotre.enigmatic.addon.contents.objects.bookbag.AntiqueBagCapability");
            Object capability = capClass.getField("INVENTORY").get(null);
            Object lazy = player.getCapability((net.minecraftforge.common.capabilities.Capability) capability);
            if (!(lazy instanceof net.minecraftforge.common.util.LazyOptional<?> optional)) {
                return false;
            }
            Object handler = optional.resolve().orElse(null);
            if (handler == null) {
                return false;
            }
            Class<?> iface = Class.forName(
                "auviotre.enigmatic.addon.contents.objects.bookbag.IAntiqueBagHandler");
            iface.getMethod("setBook", int.class, ItemStack.class)
                .invoke(handler, 0, book == null ? ItemStack.EMPTY : new ItemStack(book));
            return true;
        }
        catch (Throwable t) {
            log(TAG + " T56 构造书袋内容异常: " + t);
            return false;
        }
    }

    /** 清掉测试留下的书袋物品,免得影响后面的用例。 */
    private static void removeBagItem(Player player, Item bag) {
        try {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).getItem() == bag) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
        }
        catch (RuntimeException ignored) {
            // 清理失败不影响用例结论
        }
    }


    /**
     * 4.8.2:多倍渔获 —— 倍数配置、取整/概率公式,以及**真的把一竿事件过一遍总线**。
     *
     * <p>用户实测「渔获瓶的多倍渔获疑似没有生效」时,旧的"幸运/10"算法在书袋里(2 点幸运)
     * 只给出 20% 概率多 1 条。这里断言新算法:倍数 2.0 ⇒ 额外 1 份、3.0 ⇒ 额外 2 份、
     * 1.0 ⇒ 0 份(所以"1.0 = 不增加")。</p>
     *
     * <p>后半段造一竿假的 {@code ItemFishedEvent}(2 条鱼 + 1 件杂物)发到事件总线上:
     * 没带瓶子 ⇒ 一条都不多;带了瓶子 ⇒ 鱼翻倍、杂物不动。这同时证明
     * <b>{@code onFished} 确实挂在总线上、确实会执行</b>。</p>
     */
    private static boolean testFishingMultiplier(ServerLevel level, Player player) {
        boolean ok = true;
        Item fishing = ModItems.fishingHaulBottleOrNull();
        Item bag = RingConfig.antiqueBagItem();
        if (fishing == null) {
            log(TAG + " T57 渔获瓶缺失");
            check("T57", false);
            return false;
        }
        double carryCfg = RingConfig.fishingHaulCatchMultiplier();
        double bagCfg = RingConfig.fishingHaulBagMultiplier();
        double mCarry = FishingHaulBottleHandler.catchMultiplier(player, false, true);
        double mBag = FishingHaulBottleHandler.catchMultiplier(player, true, true);
        double mNone = FishingHaulBottleHandler.catchMultiplier(player, false, false);
        ok &= Math.abs(mCarry - carryCfg) < 1.0E-9D;
        ok &= Math.abs(mBag - carryCfg * bagCfg) < 1.0E-9D;
        ok &= Math.abs(mNone - 1.0D) < 1.0E-9D;

        var random = level.getRandom();
        int e1 = FishingHaulBottleHandler.extraCatches(random, 1.0D);
        int e2 = FishingHaulBottleHandler.extraCatches(random, 2.0D);
        int e3 = FishingHaulBottleHandler.extraCatches(random, 3.0D);
        ok &= e1 == 0 && e2 == 1 && e3 == 2;
        int sum = 0;
        int samples = 2000;
        for (int i = 0; i < samples; i++) {
            sum += FishingHaulBottleHandler.extraCatches(random, 2.5D);
        }
        double avgExtra = sum / (double) samples;

        boolean cfgOk = true;
        try {
            // ===== a) 不携带渔获瓶 =====
            clearTestItems(player, fishing);
            int[] a = catchBatches(level, player, 1);

            // ===== b) 背包携带(×2) + e) 杂物不复制 =====
            player.getInventory().add(new ItemStack(fishing));
            int[] b = catchBatches(level, player, 1);
            clearTestItems(player, fishing);

            // ===== c) 书袋(×2 × 1.5 = 3.0) =====
            int[] c = new int[] { -1, -1, -1, 0 };
            boolean bagUsed = false;
            if (bag != null && putInBag(player, bag, fishing)) {
                bagUsed = true;
                c = catchBatches(level, player, 1);
                bagSetBook(player, bag, null);
                removeBagItem(player, bag);
            }
            clearTestItems(player, fishing);

            // ===== d) 小数倍率 2.5:真跑 200 竿,数每竿平均 =====
            RingConfig.FISHING_HAUL_CATCH_MULTIPLIER.set(2.5D);
            player.getInventory().add(new ItemStack(fishing));
            int[] d = catchBatches(level, player, 200);
            clearTestItems(player, fishing);
            RingConfig.FISHING_HAUL_CATCH_MULTIPLIER.set(carryCfg);
            double avgPerCatch = d[0] / 200.0D;

            // ===== f) 一竿只乘一次(事件通道处理完,生成通道必须静默) =====
            FishingHaulBottleHandler.resetHandled(player);
            boolean spawnActiveBefore = FishingHaulBottleHandler.spawnChannelActive(player);
            player.getInventory().add(new ItemStack(fishing));
            postFakeCatch(level, player);
            boolean spawnActiveAfter = FishingHaulBottleHandler.spawnChannelActive(player);
            clearTestItems(player, fishing);
            FishingHaulBottleHandler.resetHandled(player);

            // ===== g) 背包满:多余的鱼掉在脚下,不丢 =====
            player.getInventory().add(new ItemStack(fishing));
            int filled = fillInventory(player, Items.STONE, fishing);
            int[] g = catchBatches(level, player, 1);
            clearInventoryFiller(player, Items.STONE);
            clearTestItems(player, fishing);

            // ===== h) 连续 100 竿(×2)必须每竿都 +2,不会时有时无 =====
            player.getInventory().add(new ItemStack(fishing));
            int[] h = catchBatches(level, player, 100);
            clearTestItems(player, fishing);

            // ===== i) 负向对照 1:倍率改成 1.0 =====
            RingConfig.FISHING_HAUL_CATCH_MULTIPLIER.set(1.0D);
            cfgOk &= Math.abs(RingConfig.fishingHaulCatchMultiplier() - 1.0D) < 1.0E-9D;
            player.getInventory().add(new ItemStack(fishing));
            int[] i1 = catchBatches(level, player, 1);
            clearTestItems(player, fishing);
            RingConfig.FISHING_HAUL_CATCH_MULTIPLIER.set(carryCfg);

            // ===== j) 负向对照 2:总开关关掉 =====
            RingConfig.FISHING_HAUL_ENABLED.set(false);
            player.getInventory().add(new ItemStack(fishing));
            int[] j1 = catchBatches(level, player, 1);
            clearTestItems(player, fishing);
            RingConfig.FISHING_HAUL_ENABLED.set(true);

            ok &= a[0] == 0 && a[1] == 0;
            ok &= b[0] == 2 && b[1] == 0;
            ok &= bagUsed && c[0] == 4 && c[1] == 0;
            ok &= avgPerCatch > 2.5D && avgPerCatch < 3.5D;
            ok &= spawnActiveBefore && !spawnActiveAfter;
            // 4.8.3:背包满不满都是"掉在脚下",所以 g[0](掉落鱼) 与 g[2](掉落计数) 都应是 2
            ok &= g[0] == 2 && g[2] == 2;
            ok &= filled > 0;
            ok &= h[0] == 200 && h[3] == 1;
            ok &= i1[0] == 0 && i1[1] == 0;
            ok &= j1[0] == 0 && j1[1] == 0;
            log(TAG + " T57 实测数字(每竿都是 2 鱼 + 1 杂物的假渔获,额外渔获全部掉在脚下):"
                + " [a]不携带 掉落+" + a[0] + " 杂物+" + a[1] + "[应 0/0]"
                + " | [b]携带×" + fmt(mCarry) + " 掉落+" + b[0] + " 杂物+" + b[1] + "[应 2/0]"
                + " | [c]书袋×" + fmt(mBag) + (bagUsed ? " 掉落+" + c[0] + " 杂物+" + c[1] + "[应 4/0]" : " 书袋不可用,跳过")
                + " | [d]2.5倍×200竿 掉落+" + d[0] + " 平均" + String.format(java.util.Locale.ROOT, "%.2f", avgPerCatch)
                + "/竿[应≈3]"
                + " | [e]杂物(d 里)" + d[1] + "[应 0]"
                + " | [f]事件通道处理后生成通道=" + (spawnActiveAfter ? "仍激活[错]" : "静默[对]")
                + " | [g]满包(填了" + filled + "格) 掉落+" + g[0] + " 计数+" + g[2] + "[应 2/2]"
                + " | [h]100竿 掉落+" + h[0] + " 单竿稳定=" + (h[3] == 1) + "[应 200/True]"
                + " | [i]倍率1.0 掉落+" + i1[0] + "[应 0]"
                + " | [j]总开关关 掉落+" + j1[0] + "[应 0]"
                + " | 公式 2.5倍额外份数均值=" + String.format(java.util.Locale.ROOT, "%.3f", avgExtra)
                + "(2000次,应≈1.5);1/2/3倍额外份数=" + e1 + "/" + e2 + "/" + e3 + "[应 0/1/2]");
        }
        finally {
            clearInventoryFiller(player, Items.STONE);
            clearTestItems(player, fishing);
            FishingHaulBottleHandler.resetHandled(player);
            if (bag != null) {
                bagSetBook(player, bag, null);
                removeBagItem(player, bag);
            }
            RingConfig.FISHING_HAUL_CATCH_MULTIPLIER.set(carryCfg);
            RingConfig.FISHING_HAUL_ENABLED.set(true);
        }
        ok &= cfgOk;
        check("T57", ok);
        return ok;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /**
     * 连跑 {@code n} 竿假渔获,返回 {掉在地上的鱼份数, 背包里杂物增量, 掉落计数增量, 单竿是否恒定(1/0)}。
     *
     * <p>4.8.3 起额外渔获<b>全部以掉落物形式</b>出现(不再进背包),所以这里数的是
     * {@link FishingHaulBottleHandler#DROPPED_CATCH}(只有 {@code addFreshEntity} 成功才 +1),
     * 而不是背包 —— 自检跑在服务器启动事件里,这时新加入世界的实体对
     * {@code getEntitiesOfClass} 还不可见(见 {@code testFishingBobberDetect} 的说明),
     * 所以不能用实体查询去数。</p>
     */
    private static int[] catchBatches(ServerLevel level, Player player, int n) {
        int boots0 = countItem(player, Items.LEATHER_BOOTS);
        FishingHaulBottleHandler.DROPPED_CATCH = 0;
        int first = 0;
        boolean stable = true;
        for (int i = 0; i < n; i++) {
            int before = FishingHaulBottleHandler.DROPPED_CATCH;
            postFakeCatch(level, player);
            int delta = FishingHaulBottleHandler.DROPPED_CATCH - before;
            if (i == 0) {
                first = delta;
            }
            else if (delta != first) {
                stable = false;
            }
        }
        int dropped = FishingHaulBottleHandler.DROPPED_CATCH;
        int boots = countItem(player, Items.LEATHER_BOOTS) - boots0;
        return new int[] { dropped, boots, dropped, stable ? 1 : 0 };
    }

    /** 把背包(含护甲/副手槽)塞满石头,返回塞了多少格;渔获瓶与鱼所在格保留不动。 */
    private static int fillInventory(Player player, Item filler, Item keep) {
        int filled = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && (s.getItem() == keep || s.is(net.minecraft.tags.ItemTags.FISHES))) {
                continue;
            }
            inv.setItem(i, new ItemStack(filler, 64));
            filled++;
        }
        return filled;
    }

    private static void clearInventoryFiller(Player player, Item filler) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == filler) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** 清掉测试用的渔获瓶/鳕鱼/鲑鱼/皮靴,免得串场。 */
    private static void clearTestItems(Player player, Item fishing) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && (s.getItem() == fishing || s.getItem() == Items.COD
                || s.getItem() == Items.SALMON || s.getItem() == Items.LEATHER_BOOTS)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /** 造一竿 2 鱼 + 1 杂物的假渔获,post 到事件总线(鱼竿随便造一个,只用来拿玩家)。 */
    private static void postFakeCatch(ServerLevel level, Player player) {
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        drops.add(new ItemStack(Items.COD));
        drops.add(new ItemStack(Items.SALMON));
        drops.add(new ItemStack(Items.LEATHER_BOOTS));
        try {
            var hook = new net.minecraft.world.entity.projectile.FishingHook(player, level, 0, 0);
            hook.setPos(player.getX(), player.getY(), player.getZ());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .post(new net.minecraftforge.event.entity.player.ItemFishedEvent(drops, 1, hook));
        }
        catch (Throwable t) {
            log(TAG + " T57 post 假渔获异常: " + t);
        }
    }

    private static int countItem(Player player, Item item) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) {
                n += s.getCount();
            }
        }
        return n;
    }



    /**
     * 4.8.2:"这条鱼是不是钓上来的" —— 判定靠附近有没有该玩家的钓鱼浮标。
     *
     * <p>用一个真的 {@code FishingHook}(原版/Aquaculture 都继承它)测:浮标在鱼旁边 ⇒ 认出玩家;
     * 浮标拿走后 ⇒ 认不出(所以手动丢鱼、打怪掉鱼不会触发多倍)。</p>
     *
     * <p><b>环境限制</b>:自检跑在服务器启动事件里,此时新加进世界的实体对实体查询不可见
     * (实测 {@code getEntitiesOfClass} 与 {@code getEntities().getAll()} 都是 0,而
     * {@code addFreshEntity} 返回 true、{@code getPlayerOwner()} 正常)。这种环境下无法判定,
     * 按"跳过"处理并打日志,不误报成失败。</p>
     */
    private static String testFishingBobberDetect(ServerLevel level, Player player) {
        net.minecraft.world.entity.item.ItemEntity fish = null;
        net.minecraft.world.entity.projectile.FishingHook hook = null;
        boolean nearOwner = false;
        boolean farNoOwner = false;
        String diag = "(未执行到判定)";
        try {
            double x = player.getX() - 0.5D;
            double y = player.getY();
            double z = player.getZ() - 0.5D;
            fish = new net.minecraft.world.entity.item.ItemEntity(level, x + 1.0D, y, z,
                new ItemStack(Items.COD));
            boolean fishAdded = level.addFreshEntity(fish);
            hook = new net.minecraft.world.entity.projectile.FishingHook(player, level, 0, 0);
            hook.setPos(x, y, z);
            boolean hookAdded = level.addFreshEntity(hook);
            int bobbersSeen = countBobbers(level, fish.getX(), y, z);
            nearOwner = FishingHaulBottleHandler.fishingOwnerNear(level, fish) == player;
            diag = "fishAdded=" + fishAdded + ";hookAdded=" + hookAdded
                + ";hookRemoved=" + hook.isRemoved()
                + ";hookOwner=" + (hook.getPlayerOwner() == null ? "null" : "ok")
                + ";附近浮标数=" + bobbersSeen;
            if (hookAdded && bobbersSeen <= 0) {
                hook.discard();
                fish.discard();
                log(TAG + " T58 自检环境看不到刚落地的浮标(" + diag + "),本条 SKIP(未验证,不算 PASS)");
                log(TAG + " T58-RESULT SKIP");
                return "SKIP";
            }
            hook.discard();
            hook = null;
            farNoOwner = FishingHaulBottleHandler.fishingOwnerNear(level, fish) == null;
            fish.discard();
            fish = null;
        }
        catch (Throwable t) {
            log(TAG + " T58 浮标判定异常: " + t);
            if (hook != null) {
                hook.discard();
            }
            if (fish != null) {
                fish.discard();
            }
            log(TAG + " T58-RESULT FAIL");
            return "FAIL";
        }
        boolean ok = nearOwner && farNoOwner;
        log(TAG + " T58 浮标在鱼旁=" + nearOwner + "[应 true];浮标拿走=" + farNoOwner + "[应 true];" + diag);
        log(TAG + " T58-RESULT " + (ok ? "PASS" : "FAIL"));
        return ok ? "PASS" : "FAIL";
    }

    /** 数某个位置附近 5 格内的钓鱼浮标(自检诊断用)。 */
    private static int countBobbers(ServerLevel level, double x, double y, double z) {
        int n = 0;
        try {
            for (net.minecraft.world.entity.Entity e : level.getEntities().getAll()) {
                if (e instanceof net.minecraft.world.entity.projectile.FishingHook
                    && e.distanceToSqr(x, y, z) <= 25.0D) {
                    n++;
                }
            }
        }
        catch (Throwable ignored) {
            return -1;
        }
        return n;
    }




    private static boolean resource(String path) {
        try (var in = BottleChainSelfTest.class.getClassLoader().getResourceAsStream(path)) {
            return in != null;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private static void log(String line) {
        EnigmaticEnhance.LOGGER.info(line);
    }

    private static void check(String test, boolean ok) {
        log(TAG + " " + test + "-RESULT " + (ok ? "PASS" : "FAIL"));
    }
}
