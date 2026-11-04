package com.hengx.enigmaticenhance.client;

import com.hengx.enigmaticenhance.EnigmaticEnhance;
import com.hengx.enigmaticenhance.PureRuneDamage;
import com.hengx.enigmaticenhance.PureRuneFlash;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.joml.Matrix4f;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 「纯净符文」落点白光的渲染器(1.1.28d:贴地圆环 ⇒ <b>半球形罩子</b>)。
 *
 * <h2>画什么</h2>
 * 一个以落点为球心、半径 3 格的**半球罩**(下半球):
 * <ul>
 * <li>经纬网格:12 段经线 × 8 圈纬线,相邻两圈之间用四边形拼成一层很薄的壳体;</li>
 * <li>透明度由下往上衰减(贴地那圈最亮 0.42,顶部 0.08),并随寿命整体淡入淡出;</li>
 * <li>另外单独画一圈贴地的亮环(内 0.97R、外 R),让"半径 3"这条边界一眼能看见;</li>
 * <li>整块壳体是半透明的,站在罩子里的生物/方块只会被"提亮",不会被盖住。</li>
 * </ul>
 *
 * <h2>"不遮挡实体"的实现(逐条对应要求)</h2>
 * <ul>
 * <li>不走任何 {@code RenderType}:直接拿 {@link Tesselator} 的 {@link BufferBuilder} 写
 *     {@code POSITION_COLOR} 顶点,自己 {@link RenderSystem#setShader} +
 *     {@link BufferUploader#drawWithShader} 画掉,渲染状态完全由这里说了算;</li>
 * <li>{@code RenderSystem.depthMask(false)} —— <b>不写深度</b>;</li>
 * <li>{@code RenderSystem.disableDepthTest()} —— 连深度测试都关掉,所以它既不会被别人挡、
 *     也不会挡住别人(只是往画面最上层叠一层光);</li>
 * <li><b>加色混合</b>({@code SRC_ALPHA, ONE}):只给已有像素加亮度,不会覆盖成不透明面;</li>
 * <li>壳体是空心的半球面 + 半透明,不是实心球 ⇒ 里面的生物一目了然;</li>
 * <li>{@code disableCull}:单面三角形,不依赖朝向;</li>
 * <li>画完立刻恢复 {@code depthMask/depthTest/cull/blend} 默认值,不影响后续渲染。</li>
 * </ul>
 * 半径与伤害判定同源({@link PureRuneDamage#RADIUS} = 3),不会"看着 3 格、打 4 格"。
 */
public class PureRuneFlashRenderer extends EntityRenderer<PureRuneFlash> {

    /** 物品贴图(EntityRenderer 要求一个贴图;这个视觉体不用贴图,只作为返回值)。 */
    private static final ResourceLocation TEXTURE =
        new ResourceLocation(EnigmaticEnhance.MODID, "textures/item/pure_rune.png");

    /** 经线分段(绕一圈切多少刀)。 */
    private static final int SEGMENTS = 12;
    /** 纬线环数(从贴地那圈到顶点)。 */
    private static final int RINGS = 8;

    public PureRuneFlashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PureRuneFlash entity) {
        return TEXTURE;
    }

    @Override
    public void render(PureRuneFlash entity, float entityYaw, float partialTick, PoseStack poseStack,
        MultiBufferSource bufferSource, int packedLight) {
        float life = (entity.tickCount + partialTick) / (float) PureRuneFlash.LIFETIME;
        if (life < 0.0F || life > 1.0F) {
            return;
        }
        // 前 1/8 淡入,之后线性淡出
        float alpha = Math.min(1.0F, life * 8.0F) * (1.0F - life);
        if (alpha <= 0.01F) {
            return;
        }
        float radius = (float) PureRuneDamage.RADIUS;
        Matrix4f matrix = poseStack.last().pose();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        try {
            drawDome(buffer, matrix, radius, alpha);
            drawBaseRing(buffer, matrix, radius, alpha);
        }
        finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** 半球壳体:12 段 × 8 圈,相邻两圈之间拼四边形。 */
    private static void drawDome(BufferBuilder buffer, Matrix4f matrix, float radius, float alpha) {
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int ring = 0; ring < RINGS; ring++) {
            double theta0 = (Math.PI * 0.5D) * ring / RINGS;
            double theta1 = (Math.PI * 0.5D) * (ring + 1) / RINGS;
            float y0 = (float) Math.sin(theta0) * radius;
            float y1 = (float) Math.sin(theta1) * radius;
            float r0 = (float) Math.cos(theta0) * radius;
            float r1 = (float) Math.cos(theta1) * radius;
            float a0 = alpha * ringAlpha(ring);
            float a1 = alpha * ringAlpha(ring + 1);
            for (int seg = 0; seg < SEGMENTS; seg++) {
                double phi0 = Math.PI * 2.0D * seg / SEGMENTS;
                double phi1 = Math.PI * 2.0D * (seg + 1) / SEGMENTS;
                float cos0 = (float) Math.cos(phi0);
                float sin0 = (float) Math.sin(phi0);
                float cos1 = (float) Math.cos(phi1);
                float sin1 = (float) Math.sin(phi1);
                buffer.vertex(matrix, cos0 * r0, y0, sin0 * r0).color(0.82F, 0.93F, 1.0F, a0).endVertex();
                buffer.vertex(matrix, cos1 * r0, y0, sin1 * r0).color(0.82F, 0.93F, 1.0F, a0).endVertex();
                buffer.vertex(matrix, cos1 * r1, y1, sin1 * r1).color(1.0F, 1.0F, 1.0F, a1).endVertex();
                buffer.vertex(matrix, cos0 * r1, y1, sin0 * r1).color(1.0F, 1.0F, 1.0F, a1).endVertex();
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    /** 一圈贴地的亮环,标出精确的半径 3 边界。 */
    private static void drawBaseRing(BufferBuilder buffer, Matrix4f matrix, float radius, float alpha) {
        float y = 0.02F;
        float inner = radius * 0.97F;
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int seg = 0; seg <= SEGMENTS * 8; seg++) {
            double phi = Math.PI * 2.0D * seg / (SEGMENTS * 8);
            float cos = (float) Math.cos(phi);
            float sin = (float) Math.sin(phi);
            buffer.vertex(matrix, cos * inner, y, sin * inner)
                .color(1.0F, 1.0F, 1.0F, alpha * 0.55F).endVertex();
            buffer.vertex(matrix, cos * radius, y, sin * radius)
                .color(1.0F, 1.0F, 1.0F, alpha * 0.95F).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    /** 每一圈的透明度权重:贴地最亮,越往上越淡。 */
    private static float ringAlpha(int ring) {
        float t = ring / (float) RINGS;
        return 0.42F * (1.0F - t) + 0.08F;
    }
}
