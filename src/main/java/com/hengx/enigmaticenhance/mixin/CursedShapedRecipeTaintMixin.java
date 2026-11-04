package com.hengx.enigmaticenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.aizistral.enigmaticlegacy.api.items.ITaintable;
import com.hengx.enigmaticenhance.AbyssalEarthHeart;

import net.minecraft.world.item.ItemStack;

/**
 * 让「深渊侵蚀的大地之心」能顶替「受污染的大地之心」用(1.2.2)。
 *
 * <h2>注入点是怎么找的</h2>
 * 反编译 {@code com/aizistral/enigmaticlegacy/crafting/CursedShapedRecipe.class}(本体 2.30.1),
 * 整个模组里判定"受污染"的地方只有这一个方法(全 jar 只有 4 个类出现 {@code isTainted}:
 * {@code ITaintable}、{@code CursedShapedRecipe}、{@code CursedShapedRecipe$Serializer}、{@code EarthHeart}):
 * <pre>
 * m_5818_(CraftingContainer, Level)Z          // ShapedRecipe#matches 的覆写
 *   5..60: 遍历合成格
 *    26..34: stack.getItem() instanceof ITaintable ?
 *    37..47: ITaintable.isTainted(stack)        &lt;-- 唯一一次调用,本 mixin 就包这一条
 *    55..56: 只要有一个"可污染但没被污染"的材料 -> 整个配方判失败
 *   63..81: 再调 super.matches(...) 并且两边都为真才返回 true
 * </pre>
 * 因此这里用 {@code @Redirect} 只把这一次 {@code isTainted} 的**返回值**换掉:本体说是受污染就照旧通过;
 * 本体说不是、但我们打了深渊侵蚀标记,也当作受污染。{@code matches} 的其余字节码一个都没动。
 *
 * <h2>为什么不改 ITaintable 本身</h2>
 * 用户要求「不要干涉原版的受污染的大地之心」。改接口会把 {@code EarthHeart} 的说明文字、
 * {@code handleTaintable} 的每 tick 写回逻辑一起卷进来;只包这一处调用,本体的
 * {@code isTainted} 标记从产生到清除都还是它自己的事,我们的键 {@code abyssalTaint} 与它互不写改。
 *
 * <h2>注册位置</h2>
 * {@code enigmaticenhance.optional.mixins.json}({@code required:false} + {@code injectors.defaultRequire:0}),
 * 与本项目其它针对第三方模组的 mixin 一致:{@code remap = false} 是因为运行期方法名是 SRG 的
 * {@code m_5818_};{@code require = 0} 保证本体万一改了这个方法也只是静默失效,不会让游戏崩。
 * 失效与否由自检 T40(扭曲之心配方能否认出深渊侵蚀的心)来断言。
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.crafting.CursedShapedRecipe")
public class CursedShapedRecipeTaintMixin {

    private static final String IS_TAINTED =
        "Lcom/aizistral/enigmaticlegacy/api/items/ITaintable;isTainted(Lnet/minecraft/world/item/ItemStack;)Z";

    @Redirect(
        method = "m_5818_(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z",
        at = @At(value = "INVOKE", target = IS_TAINTED),
        remap = false, require = 0, expect = 0)
    private boolean enigmaticenhance$abyssalCountsAsTainted(ITaintable taintable, ItemStack stack) {
        return AbyssalEarthHeart.countsAsTainted(taintable.isTainted(stack), stack);
    }
}
