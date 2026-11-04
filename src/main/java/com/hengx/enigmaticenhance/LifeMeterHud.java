package com.hengx.enigmaticenhance;

import com.hengx.enigmaticenhance.client.NumericHealthHud;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.2.14:客户端「生命刻度」HUD。
 *
 * <p>只在玩家**携带 {@link ModItems#lifeMeterOrNull() 生命刻度}**(背包/副手/饰品栏)时才画,
 * 画在心形血条**上方**,不会覆盖原版心形;没带就完全不介入渲染。</p>
 *
 * <p>内容:一条 182×5 的血条(红) + 可选吸收条(金) + 居中文字 {@code 当前 / 最大 (+吸收)}。
 * 之前 1.2.10 的"最大生命 > 60 自动兜底"已整块删除 —— 那条会盖住心形,
 * 视觉上很难看,现在改成道具驱动。</p>
 */
@Mod.EventBusSubscriber(modid = EnigmaticEnhance.MODID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LifeMeterHud {

    /** 血条离屏幕底部的距离(比心形那一行高 12 像素,避免重叠)。 */
    public static final int BOTTOM_OFFSET = 51;
    /** 血条宽度 = 原版 10 颗心。 */
    public static final int BAR_WIDTH = 182;

    private LifeMeterHud() {
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        NumericHealthHud.render(event);
    }
}
