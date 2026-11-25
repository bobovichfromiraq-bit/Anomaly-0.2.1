package com.example.anomalies.client;

import com.example.anomalies.AnomaliesMod;
import com.example.anomalies.registry.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;

/** Экранные эффекты аномалий: вспышка от удара током и туннельное зрение в хватке гравитации. */
public final class EffectOverlays {
    private static final ResourceLocation GRIP_TEXTURE = AnomaliesMod.id("textures/misc/gravity_overlay.png");
    private static float gripVisibility;

    private EffectOverlays() {}

    /** Сине-белая вспышка, быстро затухающая (≈0,7 с). */
    public static void renderShock(GuiGraphics graphics, DeltaTracker deltaTracker) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        MobEffectInstance effect = player.getEffect(ModEffects.ELECTRO_SHOCK);
        if (effect == null) {
            return;
        }
        float k = Math.min(1.0F, effect.getDuration() / 14.0F);
        int alpha = (int) (k * k * 150.0F);
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        RenderSystem.enableBlend();
        graphics.fill(0, 0, w, h, (alpha << 24) | 0xCFEAFF);
        graphics.fill(0, 0, w, h, ((int) (k * 40.0F) << 24) | 0x3C78FF);
    }

    /** Туннельное зрение со скоростными линиями, плавно появляется и исчезает. */
    public static void renderGrip(GuiGraphics graphics, DeltaTracker deltaTracker) {
        LocalPlayer player = Minecraft.getInstance().player;
        float target = player != null && player.hasEffect(ModEffects.GRAVITY_GRIP) ? 1.0F : 0.0F;
        gripVisibility += (target - gripVisibility) * Math.min(1.0F, 0.2F * deltaTracker.getRealtimeDeltaTicks());
        if (gripVisibility < 0.01F) {
            return;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, gripVisibility * 0.9F);
        graphics.blit(GRIP_TEXTURE, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
