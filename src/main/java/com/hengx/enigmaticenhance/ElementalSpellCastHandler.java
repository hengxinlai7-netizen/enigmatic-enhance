package com.hengx.enigmaticenhance;

import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.2.15:「元素魔法」的**法术连击**。
 *
 * <p>近战命中时,如果主手武器上<b>注入了法术</b>(铁魔法的 Imbued:物品自身带
 * {@code ISpellContainer}),或者玩家**魔法书里选中的法术**,就把这个法术顺手放一次:</p>
 * <ul>
 *   <li>施法路径不经过咏唱:直接触发,不需要 cast time;</li>
 *   <li>不进入冷却;</li>
 *   <li>正常消耗魔力(由铁魔法自己的扣蓝逻辑处理)。</li>
 * </ul>
 *
 * <p>全部通过反射访问铁魔法(它不在编译期依赖里),任一环节取不到就静默跳过 ——
 * 宁可"这次不触发法术",也不能让近战本身出错。</p>
 */
public final class ElementalSpellCastHandler {


    private static Method containerGet;           // SpellContainer.get(ItemStack)
    private static Method getActiveSpells;        // ISpellContainer#getActiveSpells()
    private static Method slotGetSpell;           // SpellSlot#spell() / getSpell()
    private static Method spellCast;              // AbstractSpell#castSpell(...)
    private static Method magicDataOf;            // MagicData.getPlayerMagicData(Player)
    private static Method spellbookOf;            // Utils.getPlayerSpellbookStack(Player)
    private static boolean resolved;

    /** 最近一次从 NBT 读到的法术等级。 */
    private static int lastSpellLevel = 1;

    /** 最近一次释放的法术实例(用于按品质取伤害比例)。 */
    private static Object lastSpell;

    /** 1.2.26:本途径施法的"减伤窗口" —— 记录玩家与施法所在 tick。 */
    private static final java.util.Map<java.util.UUID, Integer> LAST_CAST = new java.util.concurrent.ConcurrentHashMap<>();

    /** 本途径释放的法术只造成原伤害的 20%。 */
    /** 本途径释放的法术伤害 = 原伤害 × 20% × 法术等级(封顶 100%)。 */
    /** 连携法术伤害比例:按法术**品质**取,普通 20% → 传说 100%。 */
    public static float chainRatio(Object spell) {
        String rarity = rarityOf(spell);
        return switch (rarity) {
            case "LEGENDARY" -> 1.00F;
            case "EPIC" -> 0.80F;
            case "RARE" -> 0.60F;
            case "UNCOMMON" -> 0.40F;
            default -> 0.20F;   // COMMON / 取不到
        };
    }

    /** 反射读法术品质(SpellRarity 枚举名)。 */
    private static String rarityOf(Object spell) {
        try {
            for (java.lang.reflect.Method m : spell.getClass().getMethods()) {
                if (!"getRarity".equals(m.getName()) || m.getParameterCount() != 1) {
                    continue;
                }
                Object value = m.invoke(spell, lastSpellLevel);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        }
        catch (Throwable ignored) {
            // 取不到按普通
        }
        return "COMMON";
    }

    private ElementalSpellCastHandler() {
    }

    /**
     * 1.2.22:远程武器的"攻击"是右键——弓/弩/TACZ 枪右键射击时同样同步施法。
     * <p>判定:主手物品带「元素魔法」且是远程武器(弓/弩/枪)。</p>
     */
    /**
     * 1.2.23:左键开火的武器(TACZ 枪)也要施法。
     * <p>左键点方块时服务端能收到 {@code LeftClickBlock};点空气的 {@code LeftClickEmpty} 只在客户端,
     * 这里只处理服务端能收到的那条(枪对着方块/怪开火时生效)。</p>
     */
    @SubscribeEvent
    public static void onLeftClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            if (!ElementalMagicHandler.hasEnchantment(player, stack)) {
                return;
            }
            Object spell = spellOnItem(stack);
            if (spell == null) {
                spell = spellInSpellbook(player);
            }
            if (spell != null) {
                cast(player, spell);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    /**
     * 1.2.24:弓/弩单独处理 —— **箭矢射出之后**再连携施法({@code ArrowLooseEvent} 在放箭时触发)。
     */
    /**
     * 1.2.26:本途径释放的法术伤害按 {@link #CHAIN_DAMAGE_RATIO} 缩放。
     * <p>只在施法后 2 tick 内的伤害上生效(法术伤害基本当场结算),避免误伤别的来源。</p>
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onChainSpellHurt(LivingHurtEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof Player player)) {
                return;
            }
            Integer tick = LAST_CAST.get(player.getUUID());
            // 法术伤害常常延后结算(投射物/连锁),给 40 tick(2 秒)窗口
            if (tick == null || Math.abs(player.tickCount - tick) > 40) {
                return;
            }
            float ratio = chainRatio(lastSpell);
            event.setAmount(event.getAmount() * ratio);
            com.hengx.enigmaticenhance.DebugLog.once("ratiox" + ratio,
                "[ElementalMagic] 连携伤害 x{} : {} -> {}", ratio, event.getAmount() / ratio, event.getAmount());
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(net.minecraftforge.event.entity.player.ArrowLooseEvent event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide) {
                return;
            }
            ItemStack stack = event.getBow();
            if (!ElementalMagicHandler.hasEnchantment(player, stack)) {
                return;
            }
            Object spell = spellOnItem(stack);
            if (spell == null) {
                spell = spellInSpellbook(player);
            }
            if (spell != null) {
                cast(player, spell);
            }
            // 1.2.21:铁魔法施法会占住"使用物品"状态,弓会因此停在蓄满姿势;
            // 放箭后主动结束使用动作,避免要再点一次右键才恢复。
            player.stopUsingItem();
            if (false) {
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent
    public static void onRightClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        try {
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide) {
                return;
            }
            ItemStack stack = player.getItemInHand(event.getHand());
            if (!ElementalMagicHandler.hasEnchantment(player, stack)) {
                return;
            }
            if (!isGun(stack)) {
                return;
            }
            Object spell = spellOnItem(stack);
            if (spell == null) {
                spell = spellInSpellbook(player);
            }
            if (spell != null) {
                cast(player, spell);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 远程施法失败只是这一下不放法术
        }
    }

    /** TACZ 的枪(物品 id 以 tacz: 开头)。 */
    private static boolean isGun(ItemStack stack) {
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "tacz".equals(id.getNamespace());
    }

    /** 1.2.24:客户端"空挥/对空开火"发包后,服务端走同一条施法路径。 */
    /** 1.2.25:剑气释放后连携施法。 */
    public static void castAfterAura(Player player) {
        castFromClientSwing(player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
    }

    public static void castFromClientSwing(net.minecraft.server.level.ServerPlayer player) {
        try {
            if (player == null) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            if (!ElementalMagicHandler.hasEnchantment(player, stack)) {
                return;
            }
            Object spell = spellOnItem(stack);
            if (spell == null) {
                spell = spellInSpellbook(player);
            }
            if (spell != null) {
                cast(player, spell);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 忽略
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        try {
            // 1.2.21 诊断:攻击入口是否命中、附魔在不在、法术取没取到
            if (event.getEntity() != null && !event.getEntity().level().isClientSide) {
                ItemStack held = event.getEntity().getMainHandItem();
                com.hengx.enigmaticenhance.DebugLog.once("attack:" + event.getEntity().getUUID() + ":" + held.getItem(),
                    "[ElementalMagic] 攻击: 主手={} 有附魔={} 反射就绪={}",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem()),
                    ElementalMagicHandler.hasEnchantment(event.getEntity(), held), ready());
            }
            Player player = event.getEntity();
            if (player == null || player.level().isClientSide) {
                return;
            }
            Entity target = event.getTarget();
            if (target == null || target == player) {
                return;
            }
            ItemStack weapon = player.getMainHandItem();
            if (!ElementalMagicHandler.hasEnchantment(player, weapon)) {
                return;
            }
            Object spell = spellOnItem(weapon);
            if (spell == null) {
                spell = spellInSpellbook(player);
            }
            if (spell == null) {
                return;
            }
            if (cast(player, spell)) {
                DebugLog.info("[ElementalMagic] {} 触发法术 {}", player.getName().getString(), spell);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
            // 任何反射/版本差异都只是"这次不放法术"
        }
    }

    // ------------------------------------------------------------------ 取法术

    /** 物品自身注入的法术(Imbued 武器/法杖)。 */
    private static Object spellOnItem(ItemStack stack) {
        resolve();
        // 铁魔法把法术存在物品 NBT 里:ISpellContainer.NBT -> SPELL_DATA 列表 -> SPELL_ID
        try {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag == null) {
                return null;
            }
            String containerKey = constString("io.redspace.ironsspellbooks.api.spells.ISpellContainer", "NBT");
            String dataKey = constString("io.redspace.ironsspellbooks.capabilities.magic.SpellContainer", "SPELL_DATA");
            String idKey = constString("io.redspace.ironsspellbooks.capabilities.magic.SpellContainer", "SPELL_ID");
            String levelKey = constString("io.redspace.ironsspellbooks.capabilities.magic.SpellContainer", "SPELL_LEVEL");
            if (containerKey == null || dataKey == null || idKey == null) {
                return null;
            }
            if (!tag.contains(containerKey)) {
                return null;
            }
            net.minecraft.nbt.CompoundTag container = tag.getCompound(containerKey);
            if (!container.contains(dataKey)) {
                return null;
            }
            net.minecraft.nbt.ListTag data = container.getList(dataKey, 10);
            if (data.isEmpty()) {
                return null;
            }
            String spellId = data.getCompound(0).getString(idKey);
            if (levelKey != null) {
                lastSpellLevel = Math.max(1, data.getCompound(0).getInt(levelKey));
            }
            if (spellId == null || spellId.isEmpty()) {
                return null;
            }
            com.hengx.enigmaticenhance.DebugLog.once("spellid:" + spellId,
                "[ElementalMagic] 物品上的法术 = {}", spellId);
            return spellFromId(spellId);
        }
        catch (Throwable t) {
            com.hengx.enigmaticenhance.DebugLog.once("nbt:" + t.getClass().getName(),
                "[ElementalMagic] 读物品法术失败: {}", t.toString());
            return null;
        }
    }

    /** 反射读取某个类的字符串常量值。 */
    private static String constString(String className, String fieldName) {
        try {
            Class<?> cls = Class.forName(className);
            java.lang.reflect.Field field = cls.getField(fieldName);
            Object value = field.get(null);
            return value instanceof String s ? s : null;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    /** 法术 id -> AbstractSpell 实例(SpellRegistry.getSpell)。 */
    private static Object spellFromId(String spellId) {
        try {
            Class<?> registry = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            Object spell = null;
            for (String method : new String[] { "getSpell", "getSpellFromId" }) {
                try {
                    Method m = registry.getMethod(method, net.minecraft.resources.ResourceLocation.class);
                    spell = m.invoke(null, new net.minecraft.resources.ResourceLocation(spellId));
                    break;
                }
                catch (Throwable ignored) {
                    // 试下一个
                }
            }
            return spell;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    /** 通过 capability 取 ISpellContainer(类名/字段名各试几种写法)。 */
    private static Object capsule(ItemStack stack) {
        // 候选 1:静态 helper
        for (String cls : new String[] {
            "io.redspace.ironsspellbooks.api.spells.SpellContainerHelper",
            "io.redspace.ironsspellbooks.capabilities.magic.SpellContainerHelper" }) {
            try {
                Class<?> helper = Class.forName(cls);
                for (Method method : helper.getMethods()) {
                    if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == ItemStack.class
                        && method.getReturnType().getName().endsWith("ISpellContainer")) {
                        return method.invoke(null, stack);
                    }
                }
            }
            catch (Throwable ignored) {
                // 试下一个
            }
        }
        return null;
    }

    /** 玩家魔法书里第一个可用法术(近似"选中的魔法")。 */
    private static Object spellInSpellbook(Player player) {
        resolve();
        if (spellbookOf == null) {
            return null;
        }
        try {
            Object book = spellbookOf.invoke(null, player);
            if (!(book instanceof ItemStack stack) || stack.isEmpty()) {
                return null;
            }
            return spellOnItem(stack);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static Object firstSpell(Object container) {
        if (container == null || getActiveSpells == null) {
            return null;
        }
        try {
            Object list = getActiveSpells.invoke(container);
            if (!(list instanceof java.util.List<?> spells) || spells.isEmpty()) {
                return null;
            }
            Object slot = spells.get(0);
            if (slot != null && slotGetSpell != null) {
                return slotGetSpell.invoke(slot);
            }
            return null;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 施法

    /** 1.2.26:双倍耗蓝 —— 再扣掉一份该法术的魔力消耗。 */
    /** 当前魔力是否够放这个法术(按双倍消耗判定,因为本途径扣两份)。 */
    private static boolean hasManaFor(Player player, Object spell) {
        try {
            int cost = manaCost(spell);
            if (cost <= 0) {
                return true;
            }
            Class<?> magicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Object data = magicData.getMethod("getPlayerMagicData", net.minecraft.world.entity.LivingEntity.class)
                .invoke(null, player);
            if (data == null) {
                return true;
            }
            float mana = ((Number) magicData.getMethod("getMana").invoke(data)).floatValue();
            return mana >= cost;   // 留一份给它自己的扣费,再留一份给双倍
        }
        catch (Throwable ignored) {
            return true;   // 取不到就按原逻辑走
        }
    }

    /** 该法术的基础魔力消耗。 */
    private static int manaCost(Object spell) {
        try {
            Object c = spell.getClass().getMethod("getManaCost", int.class).invoke(spell, lastSpellLevel);
            return c instanceof Number n ? n.intValue() : 0;
        }
        catch (Throwable ignored) {
            return 0;
        }
    }

    private static void drainDoubleMana(Player player, Object spell) {
        try {
            // 法术消耗:AbstractSpell#getManaCost(int)
            int cost = 0;
            try {
                Object c = spell.getClass().getMethod("getManaCost", int.class).invoke(spell, lastSpellLevel);
                cost = c instanceof Number n ? n.intValue() : 0;
            }
            catch (Throwable ignored) {
                // 拿不到消耗就只按铁魔法原本的扣
            }
            if (cost <= 0) {
                return;
            }
            Class<?> magicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Object data = magicData.getMethod("getPlayerMagicData", net.minecraft.world.entity.LivingEntity.class)
                .invoke(null, player);
            if (data == null) {
                return;
            }
            float mana = ((Number) magicData.getMethod("getMana").invoke(data)).floatValue();
            float after = Math.max(0.0F, mana - cost);   // 再扣一份 = 双倍
            magicData.getMethod("setMana", float.class).invoke(data, after);
            com.hengx.enigmaticenhance.DebugLog.once("mana2x",
                "[ElementalMagic] 双倍耗蓝: {} -> {} (消耗 {})", mana, after, cost);
        }
        catch (Throwable ignored) {
            // 取不到就只按铁魔法原本的消耗走
        }
    }

    private static boolean cast(Player player, Object spell) {
        // 1.2.27:魔力不足时不施法(之前会"没蓝也放")
        if (!hasManaFor(player, spell)) {
            com.hengx.enigmaticenhance.DebugLog.once("nomanа" + player.getUUID(),
                "[ElementalMagic] 魔力不足,跳过连携施法");
            return false;
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        // 铁魔法自己放法术走的是 attemptInitiateCast(它会建好玩家施法状态),
        // 直接 castSpell 只会"跑完方法但没有效果"。
        //   public boolean attemptInitiateCast(ItemStack, int, Level, Player, CastSource, boolean, String)
        try {
            Class<?> castSource = Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");
            Object source = pickCastSource(castSource);
            Method method = spell.getClass().getMethod("attemptInitiateCast",
                net.minecraft.world.item.ItemStack.class, int.class,
                net.minecraft.world.level.Level.class, Player.class,
                castSource, boolean.class, String.class);
            Object result = method.invoke(spell, player.getMainHandItem(), lastSpellLevel,
                player.level(), serverPlayer, source, Boolean.FALSE, "");
            com.hengx.enigmaticenhance.DebugLog.once("initiateok",
                "[ElementalMagic] attemptInitiateCast: {} lv{} source={} result={}",
                spell, lastSpellLevel, source, result);
            if (Boolean.TRUE.equals(result)) {
                lastSpell = spell;
                LAST_CAST.put(player.getUUID(), player.tickCount);
                drainDoubleMana(player, spell);
            }
            return Boolean.TRUE.equals(result);
        }
        catch (Throwable t) {
            com.hengx.enigmaticenhance.DebugLog.once("initiatefail",
                "[ElementalMagic] attemptInitiateCast 失败: {}", t.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ 反射绑定

    /** 优先用 SCROLL:卷轴/物品施法不要求手持法术书,SPELLBOOK 会被铁魔法判成"没拿书"。 */
    private static Object pickCastSource(Class<?> castSource) {
        Object fallback = null;
        for (Object constant : castSource.getEnumConstants()) {
            if (fallback == null) {
                fallback = constant;
            }
            String name = String.valueOf(constant);
            if ("SCROLL".equals(name) || "SPELL_SCROLL".equals(name)) {
                return constant;
            }
        }
        return fallback;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        // 铁魔法的容器类在 capabilities.magic(不是 api.spells),两个都试一遍
        Class<?> containerClass = null;
        for (String name : new String[] {
            "io.redspace.ironsspellbooks.capabilities.magic.SpellContainer",
            "io.redspace.ironsspellbooks.api.spells.SpellContainer" }) {
            try {
                containerClass = Class.forName(name);
                break;
            }
            catch (Throwable ignored) {
                // 试下一个包名
            }
        }
        if (containerClass != null) {
            for (String method : new String[] { "get", "getContainer", "getSpellContainer" }) {
                try {
                    containerGet = containerClass.getMethod(method, ItemStack.class);
                    break;
                }
                catch (Throwable ignored) {
                    // 试下一个方法名
                }
            }
        }
        try {
            Class<?> iContainer = Class.forName("io.redspace.ironsspellbooks.api.spells.ISpellContainer");
            getActiveSpells = iContainer.getMethod("getActiveSpells");
        }
        catch (Throwable t) {
            com.hengx.enigmaticenhance.DebugLog.once("islots", "[ElementalMagic] ISpellContainer: {}", t.toString());
        }
        try {
            Class<?> slot = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellSlot");
            slotGetSpell = pick(slot, "spell", "getSpell");
        }
        catch (Throwable t) {
            com.hengx.enigmaticenhance.DebugLog.once("slot", "[ElementalMagic] SpellSlot: {}", t.toString());
        }
        Class<?> magicData = null;
        try {
            Class<?> spellClass = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            magicData = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            magicDataOf = magicData.getMethod("getPlayerMagicData", net.minecraft.world.entity.LivingEntity.class);
            spellCast = pickCast(spellClass, magicData);
        }
        catch (Throwable t) {
            com.hengx.enigmaticenhance.DebugLog.once("cast", "[ElementalMagic] castSpell/MagicData: {}", t.toString());
        }
        try {
            Class<?> utils = Class.forName("io.redspace.ironsspellbooks.api.util.Utils");
            spellbookOf = utils.getMethod("getPlayerSpellbookStack", Player.class);
        }
        catch (Throwable ignored) {
            // 没有魔法书栈也不影响"物品注入"那条路
        }
        com.hengx.enigmaticenhance.DebugLog.once("bind:" + ready(),
            "[ElementalMagic] 反射绑定: container={} slots={} cast={} magicData={} ready={}",
            containerGet != null, getActiveSpells != null, spellCast != null, magicDataOf != null, ready());
    }

    private static Method pick(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                return owner.getMethod(name);
            }
            catch (NoSuchMethodException ignored) {
                // 试下一个
            }
        }
        return null;
    }

    private static Method pickCast(Class<?> spellClass, Class<?> magicData) {
        for (Method method : spellClass.getMethods()) {
            if (!"castSpell".equals(method.getName()) && !"cast".equals(method.getName())) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            boolean hasMagicData = false;
            for (Class<?> type : types) {
                if (type == magicData) {
                    hasMagicData = true;
                    break;
                }
            }
            if (hasMagicData) {
                return method;
            }
        }
        return null;
    }

    /** 供自检/调试:反射是否全部绑定成功。 */
    public static boolean ready() {
        resolve();
        return containerGet != null && spellCast != null && magicDataOf != null;
    }

    /** 供自检/调试:某个物品上是否有可用法术。 */
    public static Optional<Object> spellOn(ItemStack stack) {
        return Optional.ofNullable(spellOnItem(stack));
    }
}
