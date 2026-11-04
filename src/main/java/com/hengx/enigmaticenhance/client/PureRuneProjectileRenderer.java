package com.hengx.enigmaticenhance.client;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.PureRuneProjectile;
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
 * 「纯净符文」投掷物的渲染器(1.1.28c):飞行中显示一个**自发光的小符文贴图**
 * (朝向摄像机的小方片,随飞行时间缓慢自转),和原版的雪球/末影珍珠同一档尺寸。
 * <p>
 * 用 {@link RenderType#entityTranslucentEmissive} —— 半透明 + 不吃光照(黑夜里也亮),
 * 贴图就是物品贴图 {@code textures/item/pure_rune.png}。
 */
public class PureRuneProjectileRenderer extends EntityRenderer<PureRuneProjectile> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(EnigmaticEnhance.MODID, "textures/item/pure_rune.png");

    /** 全亮(和发光物品同一档)。 */
    private static final int FULL_BRIGHT = 15728880;

    public PureRuneProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PureRuneProjectile entity) {
        return TEXTURE;
    }

    @Override
    public void render(PureRuneProjectile entity, float entityYaw, float partialTick, PoseStack poseStack,
        MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.12D, 0.0D);
        // 朝向摄像机 + 缓慢自转:远看是个发光的小符文,近看能认出贴图
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((entity.tickCount + partialTick) * 18.0F));
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        float size = 0.22F;
        vertex(consumer, matrix, normal, -size, -size, 0.0F, 0.0F);
        vertex(consumer, matrix, normal, size, -size, 1.0F, 0.0F);
        vertex(consumer, matrix, normal, size, size, 1.0F, 1.0F);
        vertex(consumer, matrix, normal, -size, size, 0.0F, 1.0F);
        poseStack.popPose();
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
}
