package com.example.anomalies.client;

import com.example.anomalies.AnomaliesMod;
import com.example.anomalies.client.render.AmbientAnomalyRenderer;
import com.example.anomalies.client.render.GravityAnomalyRenderer;
import com.example.anomalies.client.vfx.VfxMesh;
import com.example.anomalies.registry.ModBlockEntities;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = AnomaliesMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, AnomaliesMod.id("acid_overlay"), AcidOverlay::render);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, AnomaliesMod.id("gravity_overlay"), EffectOverlays::renderGrip);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, AnomaliesMod.id("shock_overlay"), EffectOverlays::renderShock);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.GRAVITY_ANOMALY.get(), GravityAnomalyRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.AMBIENT_ANOMALY.get(), AmbientAnomalyRenderer::new);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            VfxMesh.clear();
            WorldDistortion.reset();
        });
    }
}
