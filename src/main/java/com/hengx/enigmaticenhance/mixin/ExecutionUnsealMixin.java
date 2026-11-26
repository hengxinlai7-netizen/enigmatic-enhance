package com.hengx.enigmaticenhance.mixin;

import com.hengx.enigmaticenhance.ModEnchantments;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 4.15.0: 抗封印。
 *
 * <p>封装 / 封印 / 附魔禁用类模组常见的做法是拦截 {@code EnchantmentHelper} 的查询，
 * 让某一附魔"看起来不存在"（返回 0）。这里在查询返回后补一刀：</p>
 *
 * <ul>
 *   <li>只有当查询的是<b>执行之手</b>时才介入；</li>
 *   <li>只有当 helper 返回 0、但物品 NBT 里确实有该附魔时，才把结果补成 1。</li>
 * </ul>
 *
 * <p>所以它不是"强制给所有物品加附魔"，只修复被外部无效化的执行之手。</p>
 */
@Mixin(EnchantmentHelper.class)
public abstract class ExecutionUnsealMixin {

    @Inject(method = "getItemEnchantmentLevel", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void enigmaticenhance$unsealDev(Enchantment enchantment, ItemStack stack,
                                                   CallbackInfoReturnable<Integer> cir) {
        unseal(enchantment, stack, cir);
    }

    @Inject(method = "m_44843_", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void enigmaticenhance$unsealSrg(Enchantment enchantment, ItemStack stack,
                                                   CallbackInfoReturnable<Integer> cir) {
        unseal(enchantment, stack, cir);
    }

    private static void unseal(Enchantment enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        try {
            Integer current = cir.getReturnValue();
            if (current != null && current > 0) {
                return;
            }
            Enchantment execution = ModEnchantments.handOfExecutionOrNull();
            if (execution == null || enchantment != execution) {
                return;
            }
            if (stack == null || stack.isEmpty() || stack.getTag() == null) {
                return;
            }
            if (hasExecutionInNbt(stack)) {
                cir.setReturnValue(1);
            }
        }
        catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static boolean hasExecutionInNbt(ItemStack stack) {
        String ourId = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT
                .getKey(ModEnchantments.handOfExecutionOrNull()).toString();
        return scan(stack.getTag().getList("Enchantments", 10), ourId)
                || scan(stack.getTag().getList("StoredEnchantments", 10), ourId);
    }

    private static boolean scan(net.minecraft.nbt.ListTag list, String ourId) {
        for (int i = 0; i < list.size(); i++) {
            String id = list.getCompound(i).getString("id");
            if (id != null && (id.equals(ourId) || id.endsWith(":hand_of_execution"))) {
                return true;
            }
        }
        return false;
    }
}
