package com.example.anomalies.client;

import com.example.anomalies.AnomaliesMod;
import com.example.anomalies.AnomalyClientConfig;
import com.example.anomalies.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Камера и атмосфера: тряска при ударе током, раскачка и расширение FOV в хватке гравитации,
 * дрожь земли рядом с аномалиями, зеленоватый ядовитый туман у кислоты.
 */
@EventBusSubscriber(modid = AnomaliesMod.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private ClientGameEvents() {}

    /** Сила дрожи камеры 0..1 от ближайших гравитационных и электрических аномалий. */
    private static float proximityShake(LocalPlayer player) {
        float shake = 0.0F;
        Vec3 eye = player.getEyePosition();
        for (ClientAnomalies.Entry e : ClientAnomalies.active(player.level().getGameTime())) {
            if (e.type() == ClientAnomalies.ACID) {
                continue;
            }
            boolean gravity = e.type() == ClientAnomalies.GRAVITY;
            double d = eye.distanceTo(new Vec3(e.pos().getX() + 0.5, e.pos().getY() + (gravity ? 2.0 : 0.9), e.pos().getZ() + 0.5));
            float range = gravity ? 9.0F : 6.0F;
            if (d < range) {
                float k = 1.0F - (float) d / range;
                shake = Math.max(shake, k * k * e.strength() * (gravity ? 1.0F : 0.6F));
            }
        }
        return shake;
    }

    /** 0..1: насколько камера близка к кислотной аномалии (для тумана). */
    private static float acidProximity(Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0.0F;
        }
        double best = Double.MAX_VALUE;
        for (ClientAnomalies.Entry e : ClientAnomalies.active(mc.level.getGameTime())) {
            if (e.type() == ClientAnomalies.ACID) {
                best = Math.min(best, camera.distanceTo(new Vec3(e.pos().getX() + 0.5, e.pos().getY() + 1.0, e.pos().getZ() + 0.5)));
            }
        }
        if (best == Double.MAX_VALUE) {
            return 0.0F;
        }
        return (float) Math.pow(Mth.clamp(1.0 - best / 10.0, 0.0, 1.0), 1.2);
    }

    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        RandomSource random = player.getRandom();
        float t = player.tickCount + (float) event.getPartialTick();

        MobEffectInstance shock = player.getEffect(ModEffects.ELECTRO_SHOCK);
        if (shock != null) {
            float k = Math.min(1.0F, shock.getDuration() / 14.0F);
            event.setRoll(event.getRoll() + (random.nextFloat() - 0.5F) * 14.0F * k);
            event.setPitch(event.getPitch() + (random.nextFloat() - 0.5F) * 5.0F * k);
            event.setYaw(event.getYaw() + (random.nextFloat() - 0.5F) * 5.0F * k);
        }
        if (player.hasEffect(ModEffects.GRAVITY_GRIP)) {
            event.setRoll(event.getRoll() + Mth.sin(t * 0.32F) * 5.0F);
            event.setPitch(event.getPitch() + Mth.sin(t * 0.21F) * 1.6F);
        }
        if (AnomalyClientConfig.CAMERA_SHAKE.get()) {
            float shake = proximityShake(player);
            if (shake > 0.01F) {
                event.setRoll(event.getRoll() + (Mth.sin(t * 1.7F) * 1.4F + (random.nextFloat() - 0.5F) * 1.0F) * shake);
                event.setPitch(event.getPitch() + (Mth.sin(t * 2.3F) * 0.8F + (random.nextFloat() - 0.5F) * 0.6F) * shake);
            }
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.GRAVITY_GRIP)) {
            float t = player.tickCount + (float) event.getPartialTick();
            event.setFOV(event.getFOV() * (1.12 + 0.04 * Mth.sin(t * 0.3F)));
        }
    }

    /** Ядовитый туман: рядом с кислотой видимость падает, воздух зеленеет. */
    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        if (!AnomalyClientConfig.TOXIC_FOG.get() || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float t = acidProximity(event.getCamera().getPosition());
        if (t <= 0.02F) {
            return;
        }
        event.setCanceled(true);
        event.setNearPlaneDistance(Mth.lerp(t, event.getNearPlaneDistance(), 0.0F));
        event.setFarPlaneDistance(Mth.lerp(t, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), 22.0F)));
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!AnomalyClientConfig.TOXIC_FOG.get() || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float t = acidProximity(event.getCamera().getPosition()) * 0.55F;
        if (t > 0.01F) {
            event.setRed(Mth.lerp(t, event.getRed(), 0.46F));
            event.setGreen(Mth.lerp(t, event.getGreen(), 0.64F));
            event.setBlue(Mth.lerp(t, event.getBlue(), 0.16F));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAnomalies.clear();
    }
}
