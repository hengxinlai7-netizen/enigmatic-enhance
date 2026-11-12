package com.hengx.enigmaticenhance;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 2.4.2:铁魔法(Iron's Spells 'n Spellbooks)药水 / 精灵药的灌注兼容。
 *
 * <h2>为什么需要单独一层</h2>
 * <p>铁魔法的药水不是原版 {@code PotionContents} 结构，{@code PotionUtils.getMobEffects(stack)} 读出来是空的，
 * 所以大地护符灌注会把它们当成"不是药水"。这里用<b>反射扫描物品类里的效果字段</b>把它们读出来：
 * 铁魔法的 Elixir 通常把效果存在 {@code MobEffectInstance}（或 {@code List<MobEffectInstance>}）字段里，
 * 沿类继承链一路找，静态/实例都认。</p>
 *
 * <p>找不到时不报错、返回空列表 —— 大地护符只是"不支持这一瓶"，不会崩。</p>
 */
public final class IronElixirCompat {

    /** 只对铁魔法命名空间的物品做额外解析，避免误扫别的模组。 */
    private static final String NAMESPACE = "irons_spellbooks";

    private IronElixirCompat() {
    }

    /** 这瓶东西是不是铁魔法的药水类物品。 */
    public static boolean isIronPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && NAMESPACE.equals(id.getNamespace());
    }

    /** 把铁魔法物品里的效果追加到 out（失败就是什么也不加）。 */
    public static void addEffects(ItemStack stack, List<MobEffectInstance> out) {
        if (!isIronPotion(stack)) {
            return;
        }
        int before = out.size();
        try {
            collect(stack.getItem(), out);
        }
        catch (RuntimeException | LinkageError e) {
            DebugLog.debug("[IronElixir] 扫描 {} 的效果字段失败: {}", stack.getItem(), e.toString());
        }
        int added = out.size() - before;
        DebugLog.once("ironelixir:" + stack.getItem(), "[IronElixir] {} 解析出 {} 个效果", stack.getItem(), added);
    }

    private static void collect(Item item, List<MobEffectInstance> out) {
        List<MobEffectInstance> found = new ArrayList<>();
        // 0) 铁魔法 3.x 的 SimpleElixir 把效果存成 Supplier<MobEffectInstance> 并暴露 getMobEffect(),
        //    直接调这个公开方法最稳(字段扫描扫不到 Supplier)。
        try {
            java.lang.reflect.Method m = item.getClass().getMethod("getMobEffect");
            Object v = m.invoke(item);
            if (v instanceof MobEffectInstance inst) {
                found.add(inst);
            }
        }
        catch (ReflectiveOperationException | RuntimeException ignored) {
            // 不是 SimpleElixir:继续扫字段
        }
        // 1) 扫字段(静态/实例),并解包 Supplier
        for (Class<?> c = item.getClass(); c != null && c != Item.class; c = c.getSuperclass()) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            }
            catch (Throwable ignored) {
                // 字段签名里引用了不存在的类 → 跳过这个类
                continue;
            }
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object v = Modifier.isStatic(f.getModifiers()) ? f.get(null) : f.get(item);
                    if (v instanceof java.util.function.Supplier<?> sup) {
                        try {
                            v = sup.get();
                        }
                        catch (RuntimeException ignored2) {
                            v = null;
                        }
                    }
                    absorb(v, found);
                }
                catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 下一个字段
                }
            }
        }
        for (MobEffectInstance inst : found) {
            if (!out.contains(inst)) {
                out.add(inst);
            }
        }
    }

    private static void absorb(Object v, List<MobEffectInstance> found) {
        if (v instanceof MobEffectInstance inst) {
            found.add(inst);
        }
        else if (v instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof MobEffectInstance inst) {
                    found.add(inst);
                }
            }
        }
        else if (v instanceof net.minecraft.world.effect.MobEffect effect && !found.isEmpty()) {
            // 极少见：只存了效果本体，时长交给下面的兜底
            found.add(new MobEffectInstance(effect, 20 * 60, 0));
        }
    }
}
