package com.hengx.enigmaticenhance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.RenderGuiEvent;

import com.hengx.enigmaticenhance.ModItems;

import top.theillusivec4.curios.api.CuriosApi;

/**
 * 1.2.14:「生命刻度」的数字血条渲染(纯客户端)。
 *
 * <h2>触发条件</h2>
 * 玩家**携带**生命刻度 —— 判定范围:背包 0..35、副手、Curios 饰品栏。
 * 没带 ⇒ 什么都不画,原版心形血条保持原样。
 *
 * <h2>位置</h2>
 * 画在心形血条<b>上方</b>(底部偏移 51 像素,心形那一行是 39),
 * 这样不会像 1.2.10 的兜底那样把心形盖住 —— 之前的"红黑锯齿"就是两套叠在一起。
 */
public final class NumericHealthHud {

    private NumericHealthHud() {
    }

    /**
     * 4.6.1(帧率):{@link #carrying(Player)} 的<b>按 tick 缓存</b>。
     *
     * <p>本方法由 {@code RenderGuiEvent.Post} 调用 —— <b>每帧一次</b>(60~200 次/秒)。
     * 而 {@link #carrying(Player)} 要扫背包 36 格 + 副手,再走一遍 Curios 的
     * {@code getCuriosInventory(...).map(...)} 与 {@code findFirstCurio(谓词)}(每次都会包一层
     * {@code Optional}/lambda,并遍历全部饰品槽)。没带生命刻度的玩家(绝大多数)是<b>最坏情况</b>:
     * 36 格全扫完、饰品栏也全查完才返回 false —— 60~200 次/秒的纯白工。
     *
     * <p>背包/饰品栏内容只会在 <b>tick</b> 里变化(拾取、拖拽落格、饰品换装都在 tick 内完成),
     * 所以同一 tick 内的多次渲染用同一份结果,画面与原来完全一致(血条该出现的那一 tick 就出现)。
     * 缓存键就是"玩家实例 + {@code tickCount}":不需要注册事件、也不会跨玩家串味。</p>
     */
    private static Player cachedPlayer;
    private static int cachedTick = Integer.MIN_VALUE;
    private static boolean cachedCarrying;

    /**
     * 4.6.3(帧率):血条文本 + 它的像素宽度缓存(只在数字变化 / 字体对象变化时重算)。
     * <p>{@code font} 实例在资源包重载时会被换掉,所以把字体对象本身也放进缓存键里,
     * 换字体/换语言后立刻能重算宽度。{@code %.0f} 对非负数就是四舍五入,
     * 所以"取整后的三个整数相等"⇒ 格式化出来的字符串<b>逐字节相同</b>。</p>
     */
    private static Object cachedTextFont;
    private static int cachedTextHealth = Integer.MIN_VALUE;
    private static int cachedTextMax = Integer.MIN_VALUE;
    private static int cachedTextAbsorption = Integer.MIN_VALUE;
    private static String cachedText = "";
    private static int cachedTextWidth;

    /** 客户端渲染入口(由 {@code LifeMeterHud} 在 RenderGuiEvent.Post 里调用)。 */
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        Player player = minecraft.player;
        if (!carryingCached(player)) {
            return;
        }
        float max = player.getMaxHealth();
        if (max <= 0.0F) {
            return;
        }
        try {
            GuiGraphics graphics = event.getGuiGraphics();
            int width = event.getWindow().getGuiScaledWidth();
            int height = event.getWindow().getGuiScaledHeight();
            int barWidth = 182;
            int x = width / 2 - barWidth / 2;
            int y = height - 51;

            float health = Mth.clamp(player.getHealth() / max, 0.0F, 1.0F);
            graphics.fill(x, y, x + barWidth, y + 5, 0x90000000);
            graphics.fill(x, y, x + (int) (barWidth * health), y + 5, 0xFFE03030);

            float absorption = player.getAbsorptionAmount();
            if (absorption > 0.0F) {
                float ratio = Mth.clamp(absorption / max, 0.0F, 1.0F);
                graphics.fill(x, y - 7, x + barWidth, y - 2, 0x90000000);
                graphics.fill(x, y - 7, x + (int) (barWidth * ratio), y - 2, 0xFFFFC800);
            }

            var font = minecraft.font;
            // 4.6.3(帧率):文本与它测出来的宽度都只在"数字真的变了"时重算。
            // 原来每帧 1~2 次 String.format + 1 次字符串拼接 + font.width(text):
            // 其中 {code font.width()} 要逐字走字形表(十几~几十个字符),是这几行里最贵的一步,
            // 而血量在绝大多数帧里根本没变(满血站着、跑图时都是同一个数)。
            int healthKey = Math.round(player.getHealth());
            int maxKey = Math.round(max);
            int absorptionKey = Math.round(absorption);
            String text;
            int textWidth;
            if (font == cachedTextFont && healthKey == cachedTextHealth && maxKey == cachedTextMax
                && absorptionKey == cachedTextAbsorption) {
                text = cachedText;
                textWidth = cachedTextWidth;
            }
            else {
                text = String.format("%.0f / %.0f", player.getHealth(), max);
                if (absorption > 0.0F) {
                    text = text + String.format(" (+%.0f)", absorption);
                }
                textWidth = font.width(text);
                cachedTextFont = font;
                cachedTextHealth = healthKey;
                cachedTextMax = maxKey;
                cachedTextAbsorption = absorptionKey;
                cachedText = text;
                cachedTextWidth = textWidth;
            }
            graphics.drawString(font, text, x + barWidth / 2 - textWidth / 2, y - 20, 0xFFFFFFFF);
        }
        catch (RuntimeException | LinkageError ignored) {
            // 纯表现层,绝不影响游戏
        }
    }

    /**
     * {@link #carrying(Player)} 的按 tick 缓存版本(见 {@link #cachedPlayer} 的注释)。
     * 只在客户端渲染线程调用,所以不需要同步。
     */
    private static boolean carryingCached(Player player) {
        int tick = player.tickCount;
        if (player == cachedPlayer && tick == cachedTick) {
            return cachedCarrying;
        }
        boolean result = carrying(player);
        cachedPlayer = player;
        cachedTick = tick;
        cachedCarrying = result;
        return result;
    }

    /** 玩家是否携带生命刻度(背包 / 副手 / 饰品栏)。 */
    public static boolean carrying(Player player) {
        if (player == null) {
            return false;
        }
        Item meter = ModItems.lifeMeterOrNull();
        if (meter == null) {
            return false;
        }
        try {
            if (player.getOffhandItem().is(meter)) {
                return true;
            }
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(meter)) {
                    return true;
                }
            }
            // 4.6.2(内存):.map(...).orElse(...) ⇒ orElse(null) + 判空,不再每次新建 LazyOptional + lambda。
            var handler = CuriosApi.getCuriosInventory(player).orElse(null);
            return handler != null && handler.findFirstCurio(stack -> stack.is(meter)).isPresent();
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
