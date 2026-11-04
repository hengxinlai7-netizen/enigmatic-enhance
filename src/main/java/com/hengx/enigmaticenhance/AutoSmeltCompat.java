package com.hengx.enigmaticenhance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.fml.ModList;

/**
 * 与「奇妙附魔」{@code wonderenchant} 的软兼容 —— <b>逐工具</b>判定(1.1.21 重写)。
 * <p>
 * 1.1.20 的写法有一个致命的失败模式:只要"对方模组在 + 对方附魔已注册 + 对方配置开着"这条链路里
 * 任意一处读错(反射拿不到、配置读不到,或判定被写成"模组级"),就会整条让位,而玩家工具上其实只有
 * <b>我们的</b>附魔 ⇒ 自动熔炼在他那儿永远不生效(用户实测:装了 wonderenchant 的整合包里挖矿没有熔炼产物)。
 * <p>
 * 现在只允许一种情况让位,三件事必须同时成立:
 * <ol>
 *   <li>对方模组在场({@link ModList#isLoaded});</li>
 *   <li><b>这把工具上真的有</b>对方的 {@code wonderenchant:auto_smelt}
 *       ({@code EnchantmentHelper.getItemEnchantmentLevel(对方附魔, 工具) > 0});</li>
 *   <li>对方自己的开关开着({@code ModConfig.autoSmeltEnabled()})。</li>
 * </ol>
 * 其余全部 ⇒ <b>本模组正常熔炼</b>,包括:对方模组不存在、反射失败、工具上没有对方附魔、对方配置关闭
 * (对方不干活,我们当然要干)、以及"读对方配置失败"(拿不到就不让位,而不是相反)。
 * <p>
 * 反射只读两处:对方的附魔实例({@code ModEnchantments.AUTO_SMELT} 这个 {@code RegistryObject} 的 {@code get()})
 * 与对方的 {@code ModConfig.autoSmeltEnabled()};任何一步抛异常都按"读不到"处理。
 * 1.20.1 的 {@code EnchantmentHelper} 没有 {@code getEnchantmentLevel(Enchantment, ItemStack)},
 * 物品堆这一侧的正规入口是 {@link EnchantmentHelper#getItemEnchantmentLevel}。
 */
public final class AutoSmeltCompat {

    /** 对方的 modid。 */
    public static final String WONDERENCHANT_MODID = "wonderenchant";
    private static final String WE_ENCHANTMENTS_CLASS = "com.hengx.wonderenchant.ModEnchantments";
    private static final String WE_ENCHANTMENT_FIELD = "AUTO_SMELT";
    private static final String WE_CONFIG_CLASS = "com.hengx.wonderenchant.ModConfig";
    private static final String WE_CONFIG_METHOD = "autoSmeltEnabled";

    /** 解析成功后缓存的对方附魔(解析失败不缓存,注册表填好后还能再试)。 */
    @Nullable
    private static Enchantment wonderAutoSmelt;
    private static Field enchantmentField;
    private static Method registryGet;
    private static Method configEnabled;
    /** 自检/日志用:上一次判定的"对方等级"。 */
    private static int lastWonderLevel;

    private AutoSmeltCompat() {}

    /** 对方的模组是否在场(离线/服务端都能用;拿不到 ModList 就当不在)。 */
    public static boolean wonderenchantLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded(WONDERENCHANT_MODID);
        }
        catch (Throwable t) {
            return false;
        }
    }

    /** 对方的 {@code wonderenchant:auto_smelt} 附魔实例;对方没装 / 还没注册好 ⇒ null。 */
    @Nullable
    public static Enchantment wonderAutoSmelt() {
        if (wonderAutoSmelt != null) {
            return wonderAutoSmelt;
        }
        if (!wonderenchantLoaded()) {
            return null;
        }
        try {
            Class<?> holder = Class.forName(WE_ENCHANTMENTS_CLASS, true, AutoSmeltCompat.class.getClassLoader());
            if (enchantmentField == null) {
                enchantmentField = holder.getField(WE_ENCHANTMENT_FIELD);
            }
            Object registryObject = enchantmentField.get(null);
            if (registryObject == null) {
                return null;
            }
            if (registryGet == null) {
                registryGet = registryObject.getClass().getMethod("get");
            }
            Object value = registryGet.invoke(registryObject);
            if (value instanceof Enchantment enchantment) {
                wonderAutoSmelt = enchantment;
                debug("resolved peer enchantment {}", idOf(enchantment));
            }
        }
        catch (Throwable t) {
            // 按"对方没有这个附魔"处理:绝不让"读不到"变成"让位"
            debug("cannot resolve peer enchantment (treat as absent): {}", String.valueOf(t));
        }
        return wonderAutoSmelt;
    }

    /**
     * 这把工具上对方的附魔等级:0 = 没有(对方模组不在 / 反射失败 / 工具上确实没有都算 0)。
     * <p>
     * 这是"逐工具"的核心:只看工具,不看模组是否存在、也不看对方的配置。
     */
    public static int wonderLevel(ItemStack tool) {
        int level = 0;
        Enchantment enchantment = wonderAutoSmelt();
        if (enchantment != null && tool != null && !tool.isEmpty()) {
            try {
                // 与 AutoSmeltHandler#levelOf 一致:走 getEnchantments(附魔书会读 StoredEnchantments)。
                // 1.20.1 的物品堆侧没有 getEnchantmentLevel(Enchantment, ItemStack),getItemEnchantmentLevel
                // 又只认 Enchantments,这里用映射读最稳。
                Integer value = EnchantmentHelper.getEnchantments(tool).get(enchantment);
                level = value == null ? 0 : Math.max(0, value);
            }
            catch (Throwable t) {
                level = 0;
            }
        }
        lastWonderLevel = level;
        return level;
    }

    /**
     * 对方自己的总开关。
     * <p>
     * <b>读不到就返回 false</b>(= 当作对方不干活):这样"反射失败"只会让本模组多干活,
     * 绝不会像 1.1.20 那样因为读不到而整条让位、把功能静默吃掉。
     */
    public static boolean wonderFeatureEnabled() {
        try {
            if (configEnabled == null) {
                Class<?> config = Class.forName(WE_CONFIG_CLASS, true, AutoSmeltCompat.class.getClassLoader());
                configEnabled = config.getMethod(WE_CONFIG_METHOD);
            }
            Object value = configEnabled.invoke(null);
            return value instanceof Boolean enabled && enabled;
        }
        catch (Throwable t) {
            debug("cannot read peer autoSmeltEnabled (treat as off): {}", String.valueOf(t));
            return false;
        }
    }

    /**
     * 本模组是否要让位(把这条掉落交给对方熔炼)。
     * <p>
     * 只有"这把工具上真的有对方附魔 + 对方自己开着"才让位;其余一律 false ⇒ 本模组照常熔炼。
     */
    public static boolean otherModTakesOver(ItemStack tool) {
        if (!wonderenchantLoaded()) {
            lastWonderLevel = 0;
            return false;
        }
        int theirLevel = wonderLevel(tool);
        if (theirLevel <= 0) {
            debug("theirLevel=0 -> our mod handles it");
            return false;
        }
        if (!wonderFeatureEnabled()) {
            debug("theirLevel={} but theirConfig=false -> our mod handles it", theirLevel);
            return false;
        }
        debug("theirLevel={} and theirConfig=true -> yieldToWonderenchant is ON, yielding", theirLevel);
        return true;
    }

    /** 自检用:上一次 {@link #wonderLevel} 的返回值。 */
    public static int lastWonderLevel() {
        return lastWonderLevel;
    }

    /** 对方附魔的 id(未解析到返回 "absent"),启动日志用。 */
    public static String wonderEnchantmentId() {
        Enchantment enchantment = wonderAutoSmelt();
        return enchantment == null ? "absent" : idOf(enchantment);
    }

    private static String idOf(Enchantment enchantment) {
        try {
            return String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKey(enchantment));
        }
        catch (Throwable t) {
            return enchantment.getDescriptionId();
        }
    }

    /** 全部日志 ASCII(中文在这套环境里会乱码)。 */
    private static void debug(String format, Object... args) {
        if (RingConfig.debugLog()) {
            DebugLog.info("[AutoSmelt] compat: " + format, args);
        }
    }
}
