package com.hengx.enigmaticenhance.client;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.MagicSwordAuraEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.2.0:魔法剑气的渲染。
 * <p>
 * <b>完全照抄工程里已验证可用的 {@link PureRuneProjectileRenderer} 的写法</b>:
 * <ul>
 *   <li>{@link RenderType#entityTranslucentEmissive}(自发光、半透明、不写深度冲突);</li>
 *   <li>顶点用 {@code .normal(Matrix3f, ...)} 补法线,并给全亮 {@code 15728880};</li>
 *   <li>pushPose / popPose 成对,写完立刻 {@code endBatch},避免污染其它实体的批次
 *       (之前那版没有 endBatch,导致白片串到别的实体上);</li>
 *   <li>尺寸缩到 {@link #SIZE}(0.55),不再像之前 1.45 那样铺满半个屏幕。</li>
 * </ul>
 */
public class MagicSwordAuraRenderer extends EntityRenderer<MagicSwordAuraEntity> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(EnigmaticEnhance.MODID, "textures/entity/magic_sword_aura.png");

    /** 全亮,黑夜里也看得见。 */
    private static final int FULL_BRIGHT = 15728880;

    /** 贴图半边长(格)。1.2.0 从 1.45 缩到 0.55。 */
    private static final float SIZE = 0.55F;

    /**
     * 4.6.1(帧率):{@link RenderType#entityTranslucentEmissive(ResourceLocation)} 的结果<b>缓存一次</b>。
     *
     * <p>那个方法并不是"按贴图取现成对象",而是 <b>每调用一次就新建</b>一个
     * {@code CompositeRenderType} + {@code CompositeState} + {@code TextureStateShard}
     * (里面是一堆 ImmutableList / Optional / lambda)。而 {@link #render} 对<b>每一个</b>剑气实体、
     * <b>每一帧</b>都会执行,剑气一多就是每秒几百个这种大对象 —— 全是纯垃圾,直接推高 Young GC 频率。
     * 贴图是常量,渲染类型自然也是常量,缓存下来调用点就只剩一次字段读取。</p>
     *
     * <p>用"首次渲染时懒建"而不是静态初始化:确保对象一定是在渲染线程上第一次被构造的。</p>
     */
    private static RenderType auraRenderType;

    public MagicSwordAuraRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MagicSwordAuraEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MagicSwordAuraEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
        MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        try {
            // 只跟随相机的水平朝向(不跟俯仰),这样剑气永远是"正面"朝着玩家,不会因为抬头低头而歪。
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(auraAngle(entity)));

            Matrix4f matrix = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            RenderType type = auraRenderType;
            if (type == null) {
                type = RenderType.entityTranslucentEmissive(TEXTURE);
                auraRenderType = type;
            }
            VertexConsumer consumer = bufferSource.getBuffer(type);
            vertex(consumer, matrix, normal, -SIZE, -SIZE, 0.0F, 1.0F);
            vertex(consumer, matrix, normal, SIZE, -SIZE, 1.0F, 1.0F);
            vertex(consumer, matrix, normal, SIZE, SIZE, 1.0F, 0.0F);
            vertex(consumer, matrix, normal, -SIZE, SIZE, 0.0F, 0.0F);
            // 关键:立刻把自发光这一批刷掉,绝不和别的实体共用同一个 BufferBuilder
            if (bufferSource instanceof MultiBufferSource.BufferSource batched) {
                batched.endBatch(type);
            }
        }
        finally {
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y,
        float u, float v) {
        consumer.vertex(matrix, x, y, 0.0F)
            .color(255, 255, 255, 255)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(FULL_BRIGHT)
            .normal(normal, 0.0F, 0.0F, 1.0F)
            .endVertex();
    }

    /**
     * 剑气滚动角 —— 四种随机:横(0°)、竖(90°)、右斜(45°)、左斜(-45°)。
     * 用实体 id 取模决定,服务端/客户端算出来必然一致(不需要额外同步字段)。
     */
    public static float auraAngle(MagicSwordAuraEntity entity) {
        return randomAuraAngle(entity);
    }

    /** 四方向随机用的朝向表。 */
    public static float randomAuraAngle(MagicSwordAuraEntity entity) {
        int idx = Math.floorMod(entity.getId(), 4);
        return switch (idx) {
            case 0 -> 0.0F;
            case 1 -> 90.0F;
            case 2 -> 45.0F;
            default -> -45.0F;
        };
    }
}
