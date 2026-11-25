package com.example.anomalies.client;

import com.example.anomalies.AnomaliesMod;
import com.example.anomalies.AnomalyClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Настоящее искажение мира: после отрисовки сцены запускается шейдер (assets/anomalies/shaders),
 * который по картинке и буферу глубины сдвигает пиксели вокруг ближайших аномалий (до 8 слотов; воронка занимает 3 — по высоте, чтобы искажение имело форму воронки).
 * Воронка — закрутка и линза, Электра — тепловая рябь с голубым свечением. Всё, что находится
 * ближе аномалии (стены, игрок, предметы), не искажается. При любой ошибке шейдера эффект тихо отключается.
 */
@EventBusSubscriber(modid = AnomaliesMod.MODID, value = Dist.CLIENT)
public final class WorldDistortion {
    private static final int MAX = 8;
    private static final float MAX_DISTANCE = 56.0F;

    private static PostChain chain;
    private static int width = -1, height = -1;
    private static boolean failed;

    private WorldDistortion() {}

    /** Вызывается при перезагрузке ресурсов (F3+T): шейдер будет создан заново. */
    public static void reset() {
        if (chain != null) {
            chain.close();
        }
        chain = null;
        failed = false;
    }

    private record Projected(float x, float y, float radius, float strength, float depth, int type) {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || failed || !AnomalyClientConfig.WORLD_DISTORTION.get()) {
            return;
        }
        if (IrisCompat.shaderPackActive() && !AnomalyClientConfig.DISTORTION_WITH_SHADER_PACKS.get()) {
            return; // шейдерпак Iris/Oculus владеет кадром: наш проход его бы испортил
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        long gameTime = mc.level.getGameTime();
        List<ClientAnomalies.Entry> entries = ClientAnomalies.active(gameTime);
        if (entries.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f modelView = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();
        float userStrength = AnomalyClientConfig.DISTORTION_STRENGTH.get().floatValue();

        List<Projected> visible = new ArrayList<>();
        for (ClientAnomalies.Entry e : entries) {
            if (e.type() == ClientAnomalies.ACID) {
                continue; // кислота искажения не даёт (у неё дымка и туман)
            }
            if (e.type() == ClientAnomalies.GRAVITY) { // воронка = три линзы по высоте
                project(visible, e, 0.9, 1.9F, 1.0F, camera, modelView, projection, userStrength);
                project(visible, e, 2.5, 2.8F, 0.9F, camera, modelView, projection, userStrength);
                project(visible, e, 4.1, 3.5F, 0.75F, camera, modelView, projection, userStrength);
            } else {
                project(visible, e, 0.95, 2.6F, 1.0F, camera, modelView, projection, userStrength);
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        visible.sort(Comparator.comparingDouble(Projected::depth));
        if (visible.size() > MAX) {
            visible = visible.subList(0, MAX);
        }

        if (!ensureChain(mc)) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        chain.setUniform("Count", visible.size());
        chain.setUniform("Aspect", (float) mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight()));
        chain.setUniform("Far", mc.options.getEffectiveRenderDistance() * 16.0F * 4.0F);
        chain.setUniform("Clock", ((gameTime % 24000L) + partial) / 20.0F);
        for (int i = 0; i < MAX; i++) {
            boolean has = i < visible.size();
            Projected p = has ? visible.get(i) : null;
            chain.setUniform("A" + i + "X", has ? p.x() : 0.0F);
            chain.setUniform("A" + i + "Y", has ? p.y() : 0.0F);
            chain.setUniform("A" + i + "R", has ? p.radius() : 0.0F);
            chain.setUniform("A" + i + "S", has ? p.strength() : 0.0F);
            chain.setUniform("A" + i + "D", has ? p.depth() : 0.0F);
            chain.setUniform("A" + i + "T", has ? p.type() : 0.0F);
        }

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(partial);
        mc.getMainRenderTarget().bindWrite(false);
    }

    /** Переводит точку аномалии в экранные координаты и добавляет слот искажения, если она в кадре. */
    private static void project(List<Projected> out, ClientAnomalies.Entry e, double height, float worldRadius, float mul,
                                Vec3 camera, Matrix4f modelView, Matrix4f projection, float userStrength) {
        double wx = e.pos().getX() + 0.5, wy = e.pos().getY() + height, wz = e.pos().getZ() + 0.5;
        Vector4f view = new Vector4f((float) (wx - camera.x), (float) (wy - camera.y), (float) (wz - camera.z), 1.0F);
        modelView.transform(view);
        float dist = -view.z;
        if (dist < 0.5F || dist > MAX_DISTANCE) {
            return;
        }
        Vector4f clip = new Vector4f(view);
        projection.transform(clip);
        if (clip.w <= 0.0F) {
            return;
        }
        float ux = clip.x / clip.w * 0.5F + 0.5F;
        float uy = clip.y / clip.w * 0.5F + 0.5F;
        float radius = worldRadius * projection.m11() / dist * 0.5F;
        if (radius < 0.01F || ux < -0.7F || ux > 1.7F || uy < -0.7F || uy > 1.7F) {
            return;
        }
        float fade = Mth.clamp(1.0F - (dist - 10.0F) / 40.0F, 0.0F, 1.0F);
        out.add(new Projected(ux, uy, radius, e.strength() * fade * userStrength * mul, dist, e.type()));
    }

    private static boolean ensureChain(Minecraft mc) {
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
                        AnomaliesMod.id("shaders/post/distortion.json"));
                chain.resize(w, h);
                width = w;
                height = h;
            } else if (w != width || h != height) {
                chain.resize(w, h);
                width = w;
                height = h;
            }
            return true;
        } catch (Exception e) {
            AnomaliesMod.LOGGER.error("Не удалось загрузить шейдер искажения мира, эффект отключён до перезагрузки ресурсов", e);
            failed = true;
            chain = null;
            return false;
        }
    }
}
