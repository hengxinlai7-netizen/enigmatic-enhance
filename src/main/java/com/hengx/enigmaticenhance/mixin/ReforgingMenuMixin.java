package com.hengx.enigmaticenhance.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.enigmaticenhance.PearlUtil;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 1.2.27:携带附魔师珍珠时,**神化重铸台不消耗素材**。
 *
 * <p>4.6.4:本类不再 {@code import} 神化/Placebo 的任何类型 —— 素材容器字段
 * 改为运行时按名字反射读取,精确 {@code getDeclaredField("choicesInv")},
 * 不做 {@code getDeclaredFields()} 批量扫描。神化缺失时本 mixin 所在的可选
 * 配置整体跳过,不可能因为缺类而崩。</p>
 */
@Mixin(targets = "dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu")
public class ReforgingMenuMixin {

    @Inject(method = "m_6877_", at = @At("HEAD"), remap = false, require = 0)
    private void enigmaticenhance$restoreOnClose(Player player, CallbackInfo ci) {
        try {
            if (!PearlUtil.wearing(player)) {
                return;
            }
            Object choices = field(this, "choicesInv");
            if (choices == null) {
                return;
            }
            Method slots = choices.getClass().getMethod("getSlots");
            Method stackInSlot = choices.getClass().getMethod("getStackInSlot", int.class);
            int count = ((Number) slots.invoke(choices)).intValue();
            for (int i = 0; i < count; i++) {
                ItemStack stack = (ItemStack) stackInSlot.invoke(choices, i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                stack.setCount(Math.max(1, stack.getCount()));
            }
        }
        catch (Throwable ignored) {
            // 神化重铸台版本不符/字段改名:静默跳过
        }
    }

    /** 沿类层次按名字取实例字段(找不到 → null)。 */
    private static Object field(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            }
            catch (NoSuchFieldException ignored) {
                // 继续往父类找
            }
            catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
