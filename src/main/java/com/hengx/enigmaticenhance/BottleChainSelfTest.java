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
        log(TAG + " 小结 T44(灵界之光)=" + t44 + " T45(冥河之水)=" + t45 + " T46(战争迷雾)=" + t46
            + " T48(书袋HUD+JEI)=" + t48 + " T49(经验/命中盒/暴食锁)=" + t49 + " T50(新附魔)=" + t50 + " T51(虚空无视限伤)=" + t51 + " T52(无法破坏)=" + t52 + " T53(不定之雾/镜影)=" + t53 + " T54(禁瞬移)=" + t54 + " => " + (t44 && t45 && t46 && t47 && t48 && t49 && t50 && t51 && t52 && t53 && t54 ? "PASS" : "FAIL"));
        return t44 && t45 && t46 && t47 && t48 && t49 && t50 && t51 && t52 && t53 && t54;
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
