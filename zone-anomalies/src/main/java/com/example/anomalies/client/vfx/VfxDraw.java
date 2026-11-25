package com.example.anomalies.client.vfx;

import com.example.anomalies.AnomaliesMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Общие приёмы рисования VFX: свечение-спрайт, лента молнии, плоский декаль. */
public final class VfxDraw {
    public static final int FULL_BRIGHT = 0xF000F0;
    public static final ResourceLocation GLOW = tex("glow");
    public static final ResourceLocation FLARE = tex("flare");
    public static final ResourceLocation BOLT = tex("bolt");
    public static final ResourceLocation HAZE = tex("haze");

    private VfxDraw() {}

    public static ResourceLocation tex(String name) {
        return AnomaliesMod.id("textures/vfx/" + name + ".png");
    }

    /** Мягкое аддитивное свечение, всегда повёрнутое к камере. Цвет — это яркость (чёрный = невидимо). */
    public static void glow(PoseStack ps, MultiBufferSource buffers, double x, double y, double z, float size, int r, int g, int b) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(GLOW, 0.0F, 0.0F));
        PoseStack.Pose pose = ps.last();
        vertex(vc, pose, -size, -size, 0, 0, 1, r, g, b);
        vertex(vc, pose, size, -size, 0, 1, 1, r, g, b);
        vertex(vc, pose, size, size, 0, 1, 0, r, g, b);
        vertex(vc, pose, -size, size, 0, 0, 0, r, g, b);
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int r, int g, int b) {
        vc.addVertex(pose.pose(), x, y, z).setColor(r, g, b, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    /** Лента между двумя точками, развёрнутая к камере (формат POSITION_COLOR, как у ванильной молнии). */
    public static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 camera, float halfWidth, int r, int g, int bl, int alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 view = camera.subtract(a.add(b).scale(0.5));
        Vec3 side = dir.cross(view);
        double len = side.length();
        if (len < 1.0E-6) {
            return;
        }
        side = side.scale(halfWidth / len);
        quadColor(vc, m, a.subtract(side), a.add(side), b.add(side), b.subtract(side), r, g, bl, alpha);
    }

    private static void quadColor(VertexConsumer vc, Matrix4f m, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, int r, int g, int b, int a) {
        vc.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, a);
        vc.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, a);
        vc.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, a);
        vc.addVertex(m, (float) p4.x, (float) p4.y, (float) p4.z).setColor(r, g, b, a);
    }

    /** Плоская текстура на земле (обожжённое пятно). */
    public static void groundDecal(PoseStack ps, MultiBufferSource buffers, ResourceLocation texture, float y, float half, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose pose = ps.last();
        decalVertex(vc, pose, -half, y, -half, 0, 0, light);
        decalVertex(vc, pose, -half, y, half, 0, 1, light);
        decalVertex(vc, pose, half, y, half, 1, 1, light);
        decalVertex(vc, pose, half, y, -half, 1, 0, light);
    }

    private static void decalVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(pose.pose(), x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static int clamp255(float v) {
        return v < 0 ? 0 : Math.min(255, (int) v);
    }

    /** Аддитивная лента молнии с мягким профилем (яркая середина, плавный спад к краям). Цвет — яркость. */
    public static void ribbonTex(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 camera, float halfWidth, float r, float g, float bl) {
        Vec3 dir = b.subtract(a);
        Vec3 view = camera.subtract(a.add(b).scale(0.5));
        Vec3 side = dir.cross(view);
        double len = side.length();
        if (len < 1.0E-6) {
            return;
        }
        side = side.scale(halfWidth / len);
        int ri = clamp255(r), gi = clamp255(g), bi = clamp255(bl);
        ribbonVertex(vc, pose, a.subtract(side), 0.0F, 0.0F, ri, gi, bi);
        ribbonVertex(vc, pose, a.add(side), 1.0F, 0.0F, ri, gi, bi);
        ribbonVertex(vc, pose, b.add(side), 1.0F, 1.0F, ri, gi, bi);
        ribbonVertex(vc, pose, b.subtract(side), 0.0F, 1.0F, ri, gi, bi);
    }

    private static void ribbonVertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, int r, int g, int b) {
        vc.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** Звезда-вспышка (ядро + лучи), всегда лицом к камере, с вращением вокруг линии взгляда. */
    public static void flare(PoseStack ps, MultiBufferSource buffers, double x, double y, double z, float size, float rotationDeg, int r, int g, int b) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        ps.mulPose(Axis.ZP.rotationDegrees(rotationDeg));
        VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(FLARE, 0.0F, 0.0F));
        PoseStack.Pose pose = ps.last();
        vertex(vc, pose, -size, -size, 0, 0, 1, r, g, b);
        vertex(vc, pose, size, -size, 0, 1, 1, r, g, b);
        vertex(vc, pose, size, size, 0, 1, 0, r, g, b);
        vertex(vc, pose, -size, size, 0, 0, 0, r, g, b);
        ps.popPose();
    }

    /** Горизонтальный полупрозрачный слой (дымка/туман) с бегущей текстурой, аддитивный. */
    public static void hazeLayer(PoseStack ps, MultiBufferSource buffers, ResourceLocation texture, double x, double y, double z,
                                 float half, float uOffset, float vOffset, int r, int g, int b) {
        VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(texture, uOffset, vOffset));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        float[][] c = {{-half, -half, 0, 0}, {-half, half, 0, 1}, {half, half, 1, 1}, {half, -half, 1, 0}};
        for (float[] k : c) {
            vc.addVertex(m, (float) x + k[0], (float) y, (float) z + k[1]).setColor(r, g, b, 255).setUv(k[2], k[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }
}
