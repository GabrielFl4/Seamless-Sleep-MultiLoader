package net.aqualoco.sec.client;

import net.aqualoco.sec.platform.Services;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

// Draws a lightweight "sleeping" status hint while the transition is running.
public final class SleepStatusOverlay {

    private SleepStatusOverlay() {
    }

    public static void render(GuiGraphics graphics, ClientSleepAnimationState state) {
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

        if (!SeamlessSleepClientConfigManager.get().sleepOverlayEnabled) {
            return;
        }

        Component text = Component.translatable("seamlesssleep.text.sleeping");

        boolean hasXaero = Services.PLATFORM.isModLoaded("xaerominimap")
                || Services.PLATFORM.isModLoaded("xaerominimapfair");

        long now = System.currentTimeMillis();
        double pulse = 0.6D + 0.4D * Math.sin(now / 400.0D);
        double clamped = Math.max(0.2D, Math.min(1.0D, pulse));
        int alpha = (int) (clamped * 255.0D);
        int color = (alpha << 24) | 0x00FFFFFF;

        int x;
        int y;
        if (hasXaero) {
            int sw = graphics.guiWidth();
            int sh = graphics.guiHeight();
            int textWidth = client.font.width(text);
            x = (sw - textWidth) / 2;
            y = sh - 68;
        } else {
            x = 6;
            y = 6;
        }
        // 1.21.8+ fix
        graphics.nextStratum();

        graphics.drawString(client.font, text, x, y, color, true);
    }
}
