package com.example.anomalies.client.render;

import com.example.anomalies.blockentity.GravityAnomalyBlockEntity;
import com.example.anomalies.client.vfx.VfxDraw;
import com.example.anomalies.client.vfx.VfxMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Объёмный 3D-вихрь гравитационной аномалии (в духе «Воронки/Карусели» S.T.A.L.K.E.R. 2):
 * четыре вложенные вращающиеся оболочки-воронки со спиральным рельефом и бегущими потоками воздуха,
 * расходящиеся ударные кольца, летящие по спирали обломки (мини-блоки той земли, на которой стоит аномалия, и камни),
 * свечение у основания. Яркость плавно зависит от intensity. На большом расстоянии детализация снижается.
 */
public class GravityAnomalyRenderer implements BlockEntityRenderer<GravityAnomalyBlockEntity> {
    private static final ResourceLocation SWIRL = VfxDraw.tex("gravity_swirl");
    private static final ResourceLocation RING = VfxDraw.tex("gravity_ring");
    private static final ResourceLocation DEBRIS = VfxDraw.tex("debris");
    private static final String[] ROCKS = {"rock_a", "rock_b", "rock_c"};

    public GravityAnomalyRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GravityAnomalyBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        float intensity = be.intensity(partialTick);
        if (intensity < 0.02F) {
            return;
        }
        float time = level.getGameTime() + partialTick;
        int full = VfxDraw.FULL_BRIGHT;
        double distSq = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(
                be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 2.0, be.getBlockPos().getZ() + 0.5);
        boolean far = distSq > 48.0 * 48.0;

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);

        // ---- оболочки воронки
        VfxMesh funnel = VfxMesh.get("funnel");
        int shells = far ? 2 : 4;
        for (int s = 0; s < shells; s++) {
            ps.pushPose();
            float dir = s % 2 == 0 ? 1.0F : -1.0F;
            ps.mulPose(Axis.YP.rotationDegrees(time * (8.0F + s * 6.0F) * dir));
            ps.scale(1.3F + 0.38F * s, 5.0F + 0.45F * s, 1.3F + 0.38F * s);
            VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(SWIRL, (time * 0.004F * (s + 1)) % 1.0F, (-time * 0.011F * (1.0F + 0.28F * s)) % 1.0F));
            float k = intensity * (0.60F - 0.11F * s);
            funnel.render(ps.last(), vc, (int) (215 * k), (int) (232 * k), (int) (255 * k), 255, full, true);
            ps.popPose();
        }

        // ---- ударные кольца на земле
        VfxMesh ring = VfxMesh.get("ring");
        for (int k = 0; k < 3; k++) {
            float phase = (time * 0.018F + k / 3.0F) % 1.0F;
            float radius = 0.5F + phase * 4.2F;
            float bright = (1.0F - phase) * (1.0F - phase) * intensity * Mth.clamp(phase * 8.0F, 0.0F, 1.0F);
            ps.pushPose();
            ps.translate(0.0, 0.04, 0.0);
            ps.mulPose(Axis.YP.rotationDegrees(time * 6.0F + k * 40.0F));
            ps.scale(radius, 1.0F, radius);
            VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(RING, 0.0F, 0.0F));
            ring.render(ps.last(), vc, (int) (200 * bright), (int) (225 * bright), (int) (245 * bright), 255, full, false);
            ps.popPose();
        }

        // ---- обломки: камни и мини-блоки земли, закрученные вихрем
        float debrisScale = Mth.clamp(intensity * 1.4F - 0.25F, 0.0F, 1.0F);
        if (!far && debrisScale > 0.01F) {
            final int count = 10;
            var blocks = Minecraft.getInstance().getBlockRenderer();
            for (int j = 0; j < count; j++) {
                float life = (time * 0.011F + j / (float) count) % 1.0F;
                float h = life * 4.6F;
                float angle = time * 0.11F + j * 2.39996F;
                float r = 0.55F + h * 0.32F + 0.25F * Mth.sin(j * 1.7F);
                float edge = Mth.sqrt(Mth.sin(life * Mth.PI));
                ps.pushPose();
                ps.translate(Mth.cos(angle) * r, h + 0.1F, Mth.sin(angle) * r);
                ps.mulPose(Axis.XP.rotationDegrees(time * 5.0F + j * 20.0F));
                ps.mulPose(Axis.YP.rotationDegrees(time * 7.0F + j * 40.0F));
                if (j % 3 == 0) { // кусок настоящей земли под аномалией
                    float size = (0.16F + 0.08F * ((j * 13) % 3)) * debrisScale * edge;
                    ps.scale(size, size, size);
                    ps.translate(-0.5, -0.5, -0.5);
                    blocks.renderSingleBlock(be.groundState(), ps, buffers, light, OverlayTexture.NO_OVERLAY);
                } else {
                    float size = (0.09F + 0.08F * ((j * 37) % 5) / 4.0F) * debrisScale * edge;
                    ps.scale(size, size, size);
                    VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(DEBRIS));
                    VfxMesh.get(ROCKS[j % 3]).render(ps.last(), vc, 255, 255, 255, 255, light, false);
                }
                ps.popPose();
            }
        }

        // ---- свечение у основания и в «горле» воронки
        int glow = (int) (120 * intensity);
        VfxDraw.glow(ps, buffers, 0.0, 0.35, 0.0, 1.7F, (int) (glow * 0.8F), (int) (glow * 0.92F), glow);
        if (!far) {
            int core = (int) (70 * intensity);
            VfxDraw.glow(ps, buffers, 0.0, 2.6, 0.0, 2.4F, (int) (core * 0.8F), (int) (core * 0.9F), core);
        }

        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(GravityAnomalyBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(5.0, 0.0, 5.0).expandTowards(0.0, 6.0, 0.0);
    }

    @Override
    public boolean shouldRenderOffScreen(GravityAnomalyBlockEntity be) {
        return true;
    }
}
