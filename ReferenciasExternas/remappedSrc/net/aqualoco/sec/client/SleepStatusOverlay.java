package net.aqualoco.sec.client;

import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public final class SleepStatusOverlay {

    private SleepStatusOverlay() {}

    public static void register(ClientSleepAnimationState state) {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null) {
                return;
            }

            if (!client.level.dimension().equals(Level.OVERWORLD)) {
                return;
            }

            if (!state.isActive()) {
                return;
            }

            Component text = Component.translatable("seamlesssleep.text.sleeping");

            boolean hasXaero = FabricLoader.getInstance().isModLoaded("xaerominimap")
                    || FabricLoader.getInstance().isModLoaded("xaerominimapfair");

            long now = System.currentTimeMillis();
            double pulse = 0.6D + 0.4D * Math.sin(now / 400.0D); // 0.2..1.0
            double clamped = Math.max(0.2D, Math.min(1.0D, pulse));
            int alpha = (int) (clamped * 255.0D);
            int color = (alpha << 24) | 0x00FFFFFF; // texto branco variando alpha

            int x;
            int y;
            if (hasXaero) {
                int sw = drawContext.guiWidth();
                int sh = drawContext.guiHeight();
                int textWidth = client.font.width(text);
                x = (sw - textWidth) / 2;
                y = sh - 68; // posicao aproximada da action bar
            } else {
                x = 6;
                y = 6;
            }
            drawContext.drawString(client.font, text, x, y, color);
        });
    }
}
