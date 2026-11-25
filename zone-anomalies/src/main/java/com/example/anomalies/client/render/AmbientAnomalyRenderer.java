package com.example.anomalies.client.render;

import com.example.anomalies.block.AcidAnomalyBlock;
import com.example.anomalies.block.ElectricAnomalyBlock;
import com.example.anomalies.blockentity.AmbientAnomalyBlockEntity;
import com.example.anomalies.client.vfx.AcidBubbles;
import com.example.anomalies.client.vfx.ElectricArcs;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 3D-эффекты Электры (плазменный шар, фрактальные молнии во все стороны, вспышка-звезда) и
 * Кислоты (слои ядовитой дымки над поверхностью, клубящийся пар, растущие и лопающиеся пузыри).
 */
public class AmbientAnomalyRenderer implements BlockEntityRenderer<AmbientAnomalyBlockEntity> {
    private static final ResourceLocation PLASMA = VfxDraw.tex("electric_plasma");
    private static final ResourceLocation RING = VfxDraw.tex("gravity_ring");
    private static final ResourceLocation VAPOR = VfxDraw.tex("acid_vapor");
    private static final ResourceLocation BUBBLE = VfxDraw.tex("bubble");
    private static final ResourceLocation SCORCH = ResourceLocation.fromNamespaceAndPath("anomalies", "textures/block/electric_scorch.png");

    public AmbientAnomalyRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(AmbientAnomalyBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float time = be.time() + partialTick;
        var block = be.getBlockState().getBlock();
        if (block instanceof ElectricAnomalyBlock) {
            renderElectric(be, time, ps, buffers, light);
        } else if (block instanceof AcidAnomalyBlock) {
            renderAcid(be, time, ps, buffers);
        }
    }

    private static int c(float v) {
        return Mth.clamp((int) v, 0, 255);
    }

    // ======================================================== ЭЛЕКТРА
    private void renderElectric(AmbientAnomalyBlockEntity be, float time, PoseStack ps, MultiBufferSource buffers, int light) {
        int full = VfxDraw.FULL_BRIGHT;
        boolean powered = be.getBlockState().getValue(ElectricAnomalyBlock.POWERED);
        Player player = Minecraft.getInstance().player;
        float near = 0.0F;
        if (player != null) { // шар «оживляется» при приближении игрока
            double d = Math.sqrt(player.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.9, be.getBlockPos().getZ() + 0.5));
            near = Mth.clamp(1.0F - (float) d / 7.0F, 0.0F, 1.0F);
        }
        float boost = 1.0F + 0.45F * near + (powered ? 0.6F : 0.0F);
        float flicker = (0.85F + 0.15F * Mth.sin(time * 1.9F) * Mth.sin(time * 0.7F + 1.0F)) * boost;
        VfxMesh orb = VfxMesh.get("orb");
        VfxMesh ring = VfxMesh.get("ring");

        // обожжённое пятно + пульсирующее кольцо свечения на земле
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        VfxDraw.groundDecal(ps, buffers, SCORCH, 0.02F, 1.9F, light);
        float pulse = 0.5F + 0.2F * Mth.sin(time * 0.25F);
        ps.pushPose();
        ps.translate(0.0, 0.035, 0.0);
        float ringScale = 1.25F + 0.1F * Mth.sin(time * 0.2F);
        ps.scale(ringScale, 1.0F, ringScale);
        ps.mulPose(Axis.YP.rotationDegrees(-time * 8.0F));
        VertexConsumer rv = buffers.getBuffer(RenderType.energySwirl(RING, 0.0F, 0.0F));
        ring.render(ps.last(), rv, c(50 * pulse * boost), c(120 * pulse * boost), c(230 * pulse * boost), 255, full, false);
        ps.popPose();

        // плазменный шар: ядро + две вращающиеся оболочки
        double bob = Math.sin(time * 0.1) * 0.05;
        ps.translate(0.0, ElectricArcs.CENTER.y + bob, 0.0);
        float[][] layers = {{0.15F, 255, 255, 255, 1.0F, 22.0F}, {0.25F, 170, 225, 255, 0.85F, -15.0F}, {0.36F, 70, 130, 255, 0.5F, 9.0F}};
        for (int i = 0; i < layers.length; i++) {
            float[] l = layers[i];
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(time * l[5]));
            ps.mulPose(Axis.XP.rotationDegrees(time * l[5] * 0.6F));
            ps.scale(l[0], l[0], l[0]);
            VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(PLASMA, (time * 0.02F * (i + 1)) % 1.0F, (time * 0.015F) % 1.0F));
            float k = l[4] * flicker;
            orb.render(ps.last(), vc, c(l[1] * k), c(l[2] * k), c(l[3] * k), 255, full, false);
            ps.popPose();
        }
        // яркий блик-звезда и мягкое гало, как на референсе
        VfxDraw.flare(ps, buffers, 0.0, 0.0, 0.0, 0.85F * boost, time * 0.7F, c(210 * flicker), c(235 * flicker), 255);
        VfxDraw.flare(ps, buffers, 0.0, 0.0, 0.0, 0.5F * boost, -time * 1.1F, c(255 * flicker), 255, 255);
        VfxDraw.glow(ps, buffers, 0.0, 0.0, 0.0, 1.35F, c(55 * flicker), c(130 * flicker), c(255 * flicker));
        ps.popPose();

        // молнии: тонкое белое ядро + мягкое голубое свечение
        float scaled = time / ElectricArcs.PERIOD;
        long index = (long) Math.floor(scaled);
        float frac = scaled - index;
        float alphaK = (1.0F - frac * 0.7F) * boost;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ());
        List<ElectricArcs.Arc> arcs = be.arcs(index, powered ? 8 : 0);
        VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(VfxDraw.BOLT, 0.0F, 0.0F));
        PoseStack.Pose pose = ps.last();
        for (ElectricArcs.Arc arc : arcs) {
            Vec3[] pts = arc.points();
            float k = arc.brightness() * alphaK;
            float glowWidth = arc.branch() ? 0.026F : 0.055F;
            float coreWidth = arc.branch() ? 0.006F : 0.011F;
            for (int i = 0; i < pts.length - 1; i++) {
                VfxDraw.ribbonTex(vc, pose, pts[i], pts[i + 1], camera, glowWidth, 45 * k, 115 * k, 255 * k);
                VfxDraw.ribbonTex(vc, pose, pts[i], pts[i + 1], camera, coreWidth, 235 * k, 248 * k, 255 * k);
            }
        }
        for (ElectricArcs.Arc arc : arcs) { // вспышки в точках, где молния бьёт в поверхность
            if (arc.grounded() && !arc.branch()) {
                Vec3 e = arc.end();
                float g = 150 * alphaK * arc.brightness();
                VfxDraw.glow(ps, buffers, e.x, e.y + 0.03, e.z, 0.36F, c(g * 0.5F), c(g * 0.8F), c(g));
            }
        }
    }

    // ======================================================== КИСЛОТА
    private void renderAcid(AmbientAnomalyBlockEntity be, float time, PoseStack ps, MultiBufferSource buffers) {
        int full = VfxDraw.FULL_BRIGHT;
        VfxMesh plume = VfxMesh.get("plume");
        VfxMesh sphere = VfxMesh.get("sphere");

        // ядовитый воздух: тонкие слои дымки над поверхностью, плывущие в разные стороны, чем выше — тем прозрачнее
        for (int i = 0; i < 5; i++) {
            double y = AcidBubbles.SURFACE + 0.06 + i * 0.19 + 0.04 * Math.sin(time * 0.04 + i * 1.7);
            float k = 0.26F * (float) Math.pow(1.0 - i / 5.0, 1.2);
            float dir = i % 2 == 0 ? 1.0F : -1.0F;
            VfxDraw.hazeLayer(ps, buffers, VfxDraw.HAZE, 0.5, y, 0.5, 0.95F,
                    (time * 0.0025F * dir + i * 0.31F) % 1.0F, (time * 0.0020F + i * 0.17F) % 1.0F,
                    c(205 * k), c(255 * k), c(95 * k));
        }

        // клубящийся пар над самой жижей
        for (int s = 0; s < 2; s++) {
            ps.pushPose();
            ps.translate(0.5, AcidBubbles.SURFACE, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(time * (2.5F + s * 2.0F) * (s % 2 == 0 ? 1 : -1)));
            ps.scale(0.5F + 0.14F * s, 1.0F + 0.3F * s, 0.5F + 0.14F * s);
            VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(VAPOR, (time * 0.003F * (s + 1)) % 1.0F, (-time * 0.009F * (1.0F + 0.4F * s)) % 1.0F));
            float k = 0.34F - 0.08F * s;
            plume.render(ps.last(), vc, c(115 * k * 2), c(255 * k * 2), c(70 * k * 2), 255, full, true);
            ps.popPose();
        }
        VfxDraw.glow(ps, buffers, 0.5, AcidBubbles.SURFACE + 0.25, 0.5, 0.95F, 22, 60, 14);

        // растущие и лопающиеся пузыри
        VertexConsumer bubbles = buffers.getBuffer(RenderType.entityTranslucentEmissive(BUBBLE));
        for (int i = 0; i < AcidBubbles.COUNT; i++) {
            float shifted = time + i * 9.0F;
            long cycle = (long) Math.floor(shifted / AcidBubbles.CYCLE);
            float life = (shifted % AcidBubbles.CYCLE) / AcidBubbles.CYCLE;
            if (life > 0.88F) {
                continue; // лопнул
            }
            Vec3 p = AcidBubbles.position(be.getBlockPos(), i, cycle);
            float radius = 0.03F + 0.13F * (life / 0.88F);
            float swell = life > 0.8F ? 1.0F + (life - 0.8F) * 2.0F : 1.0F; // перед лопаньем «набухает»
            ps.pushPose();
            ps.translate(p.x, p.y + radius * 0.6F + life * 0.08F, p.z);
            ps.scale(radius * swell, radius * swell, radius * swell);
            sphere.render(ps.last(), bubbles, 255, 255, 255, 255, full, false);
            ps.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(AmbientAnomalyBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(4.0, 0.5, 4.0).expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    public boolean shouldRenderOffScreen(AmbientAnomalyBlockEntity be) {
        return true;
    }
}
