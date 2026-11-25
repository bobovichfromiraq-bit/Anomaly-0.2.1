package com.example.anomalies.client;

import com.example.anomalies.AnomaliesMod;
import com.example.anomalies.registry.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

/** Зелёное покрытие экрана, пока на игроке висит эффект «Покрытие кислотой». */
public final class AcidOverlay {
    private static final ResourceLocation TEXTURE = AnomaliesMod.id("textures/misc/acid_overlay.png");
    private static float visibility;

    private AcidOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        float target = 0.0F;
        if (player != null) {
            MobEffectInstance effect = player.getEffect(ModEffects.ACID_COVER);
            if (effect != null) {
                target = Math.min(1.0F, effect.getDuration() / 25.0F); // плавно сходит на нет в последние ~1,2 с
            }
        }
        // плавное появление/исчезновение
        visibility += (target - visibility) * Math.min(1.0F, 0.2F * deltaTracker.getRealtimeDeltaTicks());
        if (visibility < 0.01F || player == null) {
            return;
        }

        float pulse = 0.92F + 0.08F * Mth.sin((player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) * 0.15F);
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();

        // 1) едва заметная зелёная дымка
        int tint = (int) (visibility * 28.0F);
        graphics.fill(0, 0, w, h, (tint << 24) | 0x3CC81E);
        // 2) текстура: виньетка, потёки, пузыри и лужа внизу
        graphics.setColor(1.0F, 1.0F, 1.0F, visibility * pulse * 0.85F);
        graphics.blit(TEXTURE, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
