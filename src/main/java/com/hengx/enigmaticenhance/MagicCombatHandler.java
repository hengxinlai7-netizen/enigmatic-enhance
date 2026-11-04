package com.hengx.enigmaticenhance;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.1.29a:两个新附魔的伤害结算。
 * <ul>
 * <li>魔法保护:受到的魔法伤害 × (1 - 4%×等级),上限 16%;</li>
 * <li>魔法剑气:近战伤害 × 0.2。</li>
 * </ul>
 * 纯计算都抽成 static 方法,方便服务端自检 T35 直接断言(不用构造事件)。
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID)
public final class MagicCombatHandler {

    private MagicCombatHandler() {}

    /** 1.2.12f:Forge 的"魔法伤害"标签(模组自建魔法伤害通常进这个)。 */
    private static final TagKey<DamageType> FORGE_IS_MAGIC =
        TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("forge", "is_magic"));

    /** 魔法保护:给定原始伤害与该玩家四件装备的附魔等级合计,返回结算后伤害。 */
    public static float magicDamage(float amount, int totalLevel) {
        if (totalLevel <= 0 || amount <= 0.0F) {
            return amount;
        }
        float reduction = Math.min(MagicProtectionEnchantment.CAP,
            MagicProtectionEnchantment.PER_LEVEL * totalLevel);
        return amount * (1.0F - reduction);
    }

    /** 魔法剑气:近战伤害系数(有附魔 0.2,没有 1.0)。 */
    public static float meleeDamage(float amount, int level) {
        return level > 0 ? amount * MagicSwordAuraEnchantment.MELEE_FACTOR : amount;
    }

    /** 剑气最多命中几个实体 = 附魔等级(1~3);无附魔 0。 */
    public static int pierceCount(int level) {
        return Math.max(0, Math.min(MagicSwordAuraEnchantment.MAX_LEVEL, level));
    }

    /**
     * 1.1.29c:剑气最终伤害 = 剑的面板攻击力 × 法强。面板为 0(异常)时回退 1.0。
     */
    public static float auraDamage(float panel, float spellPower) {
        float base = panel <= 0.0F ? 1.0F : panel;
        float power = spellPower <= 0.0F ? 1.0F : spellPower;
        return base * power;
    }

    /** 原版跳跃暴击的伤害倍率(与 {@code Player#attack} 里 {@code f1 *= 1.5F} 一致)。 */
    public static final float CRIT_MULTIPLIER = 1.5F;

    /**
     * 1.2.3a:剑气面板攻击力 = <b>玩家当前的 ATTACK_DAMAGE 属性值</b>。
     *
     * <p>和 1.1.29d 的旧做法({@link #swordBaseDamage},只看剑这件物品的 ADDITION 修饰符)不同:
     * 属性值已经把玩家基础 1.0、武器伤害、力量药水、饰品/宝石的 +攻击力、七咒等全部算进去,
     * 所以剑气现在<b>吃一切攻击力加成</b>。</p>
     */
    public static float attackPanel(Player player) {
        if (player == null) {
            return 1.0F;
        }
        try {
            return (float) Math.max(0.1D, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        catch (RuntimeException | LinkageError ignored) {
            return 1.0F;
        }
    }

    /**
     * 1.2.3a:剑气是否按<b>原版</b>暴击(跳跃/下落攻击)×1.5。
     *
     * <p>条件逐条照抄 {@code Player#attack} 里的 {@code flag2}:下落中、不在地面、不贴墙梯、
     * 不在水里、没有失明、不在骑乘、不在疾跑。释放瞬间取一次(和原版"出手时判定"一致),
     * 结果随剑气实体保存,命中时再乘倍率。</p>
     */
    public static boolean isVanillaCrit(Player player) {
        if (player == null) {
            return false;
        }
        try {
            return isVanillaCrit(player.fallDistance > 0.0F, player.onGround(), player.onClimbable(),
                player.isInWater(), player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS),
                player.isPassenger(), player.isSprinting());
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** 纯函数版本:便于自检直接断言整套真值表。 */
    public static boolean isVanillaCrit(boolean falling, boolean onGround, boolean onClimbable,
                                        boolean inWater, boolean blind, boolean passenger, boolean sprinting) {
        return falling && !onGround && !onClimbable && !inWater && !blind && !passenger && !sprinting;
    }

    /** 1.2.3a:暴击结算(只改数值,伤害类型仍是 indirectMagic)。 */
    public static float critDamage(float amount, boolean crit) {
        return crit ? amount * CRIT_MULTIPLIER : amount;
    }

    /** 反射读取 irons_spellbooks 的法强(取不到 1.0)。 */
    public static float spellPowerMultiplier(LivingEntity entity) {
        try {
            var key = net.minecraft.resources.ResourceLocation.tryParse("irons_spellbooks:spell_power");
            if (key == null) {
                return 1.0F;
            }
            var attr = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(key);
            if (attr != null && entity != null && entity.getAttributes().hasAttribute(attr)) {
                return (float) Math.max(0.1D, 1.0D + entity.getAttributeValue(attr));
            }
        }
        catch (Throwable ignored) {
            // 未安装该模组
        }
        return 1.0F;
    }

    /** 剑气基础伤害 = 4 × 等级(1.1.29c 起不再使用,保留给旧代码/测试引用)。 */
    public static float auraDamage(int level) {
        return MagicSwordAuraEnchantment.DAMAGE_PER_LEVEL * Math.max(1, Math.min(3, level));
    }

    /**
     * 1.1.29d:剑本身的基础攻击力 —— 只算<b>这件物品在主手上的 ATTACK_DAMAGE 修饰符</b>,
     * 不叠加玩家身上的其它属性来源(力量药水、饰品、七咒等一律不吃)。
     * <p>
     * 结果等于物品提示里"攻击伤害"的数值:玩家基础 1.0 + 剑的修饰符(钻石剑 = 7.0)。
     * 锋利这类附魔不在这里(它在伤害结算时另算),所以也不进剑气基础伤害。
     */
    /**
     * 1.1.29d 的旧做法:<b>只</b>算这件物品在主手上的 ATTACK_DAMAGE ADDITION 修饰符。
     * <p>
     * 1.2.3a 起剑气改吃玩家属性({@link #attackPanel}),这个方法只保留给旧引用/对照,
     * 不再参与剑气伤害。
     */
    public static float swordBaseDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1.0F;
        }
        double base = 1.0D;
        try {
            var modifiers = stack
                .getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            for (var modifier : modifiers) {
                if (modifier.getOperation()
                    == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) {
                    base += modifier.getAmount();
                }
            }
        }
        catch (Throwable ignored) {
            // 取不到就按玩家基础 1.0 处理,绝不让右键因为这里抛异常
        }
        return (float) Math.max(0.1D, base);
    }

    /** 玩家四件护甲上魔法保护的等级合计。 */
    public static int protectionLevel(LivingEntity entity) {
        MagicProtectionEnchantment ench = ModEnchantments.magicProtectionOrNull();
        if (ench == null) {
            return 0;
        }
        int total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                total += EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
            }
        }
        return total;
    }

    /** 主手物品上的剑气等级。 */
    public static int auraLevel(LivingEntity entity) {
        return auraLevel(entity == null ? ItemStack.EMPTY : entity.getMainHandItem());
    }

    /**
     * 1.2.4:任意物品堆上的剑气等级 —— 使用端不再限定剑,所以这里按"手上这一件"读,
     * 而不是永远读主手(否则副手带附魔的物品会串味)。
     */
    /** 1.2.9a:这件武器是否带「虚空剑气」(没注册/没装则一律 false)。 */
    public static boolean hasVoidAura(ItemStack stack) {
        try {
            VoidAuraEnchantment enchantment = ModEnchantments.voidAuraOrNull();
            return enchantment != null && stack != null && !stack.isEmpty()
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }


    public static int auraLevel(ItemStack stack) {
        MagicSwordAuraEnchantment ench = ModEnchantments.magicSwordAuraOrNull();
        if (ench == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
    }

    /** 是否魔法伤害(游戏本体 + 模组的魔法来源都算)。 */
    public static boolean isMagic(net.minecraft.world.damagesource.DamageSource source) {
        try {
            if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
                return true;
            }
            // 1.2.12f:许多模组(例如莱特兰-恶意的 killer_aura / reflect)用自己的伤害类型,
            // 但会登记进 Forge 的 #forge:is_magic 标签。用固定 id 判定会把它们漏掉。
            return source.is(FORGE_IS_MAGIC);
        }
        catch (Throwable t) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        try {
            LivingEntity victim = event.getEntity();
            if (victim == null || victim.level().isClientSide()) {
                return;
            }
            float amount = event.getAmount();
            if (amount <= 0.0F) {
                return;
            }
            // ---- 魔法保护(只有魔法伤害才算)
            if (isMagic(event.getSource())) {
                int level = protectionLevel(victim);
                if (level > 0) {
                    float reduced = magicDamage(amount, level);
                    DebugLog.info("[MagicCombat] 魔法保护 lv{} : {} -> {}", level, amount, reduced);
                    amount = reduced;
                }
            }
            // ---- 魔法剑气(近战才吃 -80%)
            net.minecraft.world.entity.Entity direct = event.getSource().getDirectEntity();
            if (direct instanceof Player attacker && direct == event.getSource().getEntity()
                && !(direct instanceof net.minecraft.world.entity.projectile.Projectile)) {
                int level = auraLevel(attacker);
                if (level > 0) {
                    float reduced = meleeDamage(amount, level);
                    DebugLog.info("[MagicCombat] 剑气近战 lv{} : {} -> {}", level, amount, reduced);
                    amount = reduced;
                }
            }
            event.setAmount(Math.max(0.0F, amount));
        }
        catch (Throwable t) {
            DebugLog.error("[MagicCombat] 结算异常", t);
        }
    }

    /**
     * 1.2.1:右键释放剑气 + <b>彻底禁用持有物本身的其它右键功能</b>。
     * <p>
     * 之前只挂在 {@code RightClickItem} 上,有两个漏网的路径:
     * <ul>
     *   <li><b>对着方块右键</b>:触发的是 {@code RightClickBlock},<b>根本不会</b>走 {@code RightClickItem}
     *       ⇒ 剑气不出来,方块/物品的原右键却照常执行;</li>
     *   <li>别的模组在自己的处理器里不检查 {@code isCanceled()}。
     *       这里用 {@link EventPriority#HIGHEST} 抢在最前面取消,后面那些处理器拿到的是已取消的事件。</li>
     * </ul>
     * 所以现在 {@code RightClickItem} 与 {@code RightClickBlock} 两处都拦。
     * <p>
     * <b>1.2.4:不再限制物品类型</b>。以前只对 {@code SwordItem} 生效,于是 TACZ 枪械/太刀这类
     * 不是 SwordItem 的武器即使带上附魔也放不出剑气;现在只认"手上这一件物品的剑气等级 &gt; 0"。
     * 能不能<b>附</b>上它仍由 {@link MagicSwordAuraEnchantment#canEnchant} 把关(正常途径只有剑)。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onRightClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        handleRightClick(event.getEntity(), event.getItemStack(), event.getHand(), event.getSide(),
            () -> {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            });
    }

    /** 对着方块右键时走这里:同样取消方块交互,改放剑气。 */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        handleRightClick(event.getEntity(), event.getItemStack(), event.getHand(), event.getSide(),
            () -> {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            });
    }

    /**
     * 右键的统一处理:手持物品带魔法剑气附魔 ⇒ 取消原右键(两端都取消),服务端生成剑气。
     *
     * <p>1.2.4:<b>不再限制物品类型</b>。以前这里有一道 {@code instanceof SwordItem} 的门槛,
     * 于是 TACZ 枪械 / 太刀这类"不是 SwordItem"的武器即使有附魔也放不出剑气。
     * 现在只认"主手物品上的魔法剑气等级 &gt; 0";能不能<b>附</b>上这个附魔仍由
     * {@link MagicSwordAuraEnchantment#canEnchant} 把关(正常途径依旧只有剑)。</p>
     *
     * @param cancel 取消动作(由调用方提供,因为两种事件的 setCancellationResult 不同)
     */
    private static void handleRightClick(Player player, ItemStack stack, net.minecraft.world.InteractionHand hand,
        net.minecraftforge.fml.LogicalSide side, Runnable cancel) {
        try {
            if (player == null || stack == null || stack.isEmpty()) {
                return;
            }
            // 1.2.4:等级按【手上这一件】读,物品类型不再限制
            int level = auraLevel(stack);
            if (level <= 0) {
                return;
            }
            // 两端都取消:客户端不取消的话,原版/别的模组会先在客户端跑一遍
            cancel.run();
            if (side == net.minecraftforge.fml.LogicalSide.CLIENT) {
                return;
            }
            // 冷却 + 耐久(创造不耗)
            if (player.getCooldowns().isOnCooldown(stack.getItem())) {
                return;
            }
            // 1.1.29e:冷却按主手攻速缩放(攻速越高越快)
            double attackSpeed = player.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            int cooldown = MagicSwordAuraEnchantment.cooldownTicks(attackSpeed);
            player.getCooldowns().addCooldown(stack.getItem(), cooldown);
            // 1.2.4:只对可损物品扣耐久(枪械/药水/食物这类没耐久的物品放剑气不能报错)
            if (!player.getAbilities().instabuild && stack.isDamageableItem()) {
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            // 1.2.3a: 面板取【玩家 ATTACK_DAMAGE 属性值】——力量药水、饰品/宝石、七咒全都吃;
            // 并在出手瞬间按原版跳跃暴击条件判定一次 (伤害类型仍是 indirectMagic,不变)。
            float panel = attackPanel(player);
            boolean crit = isVanillaCrit(player);
            float damage = auraDamage(panel, spellPowerMultiplier(player));
            boolean voidAura = hasVoidAura(stack);
            MagicSwordAuraEntity aura = new MagicSwordAuraEntity(player.level(), player, level, damage, crit, voidAura);
            player.level().addFreshEntity(aura);
        // 1.2.25:剑气释放后同样连携施法(与剑/弓/枪共用一条施法路径)
        com.hengx.enigmaticenhance.ElementalSpellCastHandler.castAfterAura(player);
            DebugLog.info("[Aura] {} 释放剑气 lv{} 面板 {} 暴击 {} 伤害 {}(暴击后 {})",
                player.getName().getString(), level, panel, crit, damage, critDamage(damage, crit));
        }
        catch (Throwable t) {
            DebugLog.error("[Aura] 右键释放异常", t);
        }
    }
}
