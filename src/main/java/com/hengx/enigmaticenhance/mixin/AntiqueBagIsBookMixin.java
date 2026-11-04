package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.enigmaticenhance.ModItems;

import net.minecraft.world.item.ItemStack;

/**
 * 1.2.11c:古旧书袋的"这本书能不能放进去"兜底。
 *
 * <p>扩展本体只认 {@code minecraft:bookshelf_books} 标签。如果整合包里别的模组把这个标签文件
 * 用 {@code "replace": true} 覆盖掉,我们这几瓶东西就会被踢出书袋 —— 表现就是"标签出问题"。</p>
 *
 * <p>这里在 {@code AntiqueBag#isBook(ItemStack)} 的 RETURN 上兜一层:只要是本模组的瓶子/书类物品,
 * 直接返回 true,不依赖标签文件是否被别人覆盖;标签本身仍然照旧生效。</p>
 */
@Mixin(targets = "auviotre.enigmatic.addon.contents.items.AntiqueBag")
public class AntiqueBagIsBookMixin {

    @Inject(method = "isBook", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void enigmaticenhance$acceptOwnBooks(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (Boolean.TRUE.equals(cir.getReturnValue())) {
                return;
            }
            if (stack == null || stack.isEmpty()) {
                return;
            }
            if (ModItems.isBookBagItem(stack.getItem())) {
                cir.setReturnValue(true);
            }
        }
        catch (Throwable ignored) {
            // 任何意外都保持扩展原本的判定
        }
    }
}
