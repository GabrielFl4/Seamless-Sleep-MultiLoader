package net.aqualoco.sec.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

// Draws the custom two-slot bed HUD using the same anchor and backdrop style as the vanilla overlay message.
public final class BedHudMessageRenderer {

    private static final int OVERLAY_BASELINE_Y = 68;
    private static final int LINE_SPACING = 16;
    private static final int FADE_OUT_TICKS = 20;
    private static final long DUPLICATE_RENDER_WINDOW_NANOS = 1_000_000L;
    private static GuiGraphics seamlesssleep$lastRenderedGraphics;
    private static long seamlesssleep$lastRenderNanos;

    private BedHudMessageRenderer() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (seamlesssleep$skipDuplicateRender(graphics)) {
            return;
        }

        if (ReplayPlaybackCompat.isReplayPlaybackActive()) {
            BedHudMessageManager.clearAll();
            return;
        }

        BedHudMessageManager.pruneExpired();

        BedHudMessageManager.TimedMessage topMessage = BedHudMessageManager.getTopMessage();
        BedHudMessageManager.TimedMessage bottomMessage = BedHudMessageManager.getBottomMessage();
        if (topMessage == null && bottomMessage == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) {
            return;
        }

        Font font = client.font;
        int centerX = graphics.guiWidth() / 2;
        int baseY = graphics.guiHeight() - OVERLAY_BASELINE_Y;
        boolean reserveBottomSlot = BedHudMessageManager.shouldReserveBottomSlot();

        if (topMessage != null) {
            int topY = reserveBottomSlot ? baseY - LINE_SPACING : baseY;
            seamlesssleep$renderMessage(graphics, deltaTracker, font, centerX, topY, topMessage);
        }

        if (bottomMessage != null) {
            seamlesssleep$renderMessage(graphics, deltaTracker, font, centerX, baseY, bottomMessage);
        }
    }

    private static void seamlesssleep$renderMessage(
            GuiGraphics graphics,
            DeltaTracker deltaTracker,
            Font font,
            int centerX,
            int y,
            BedHudMessageManager.TimedMessage message
    ) {
        float remainingTicks = message.expiresAtTick() - BedHudMessageManager.getHudTime(deltaTracker.getGameTimeDeltaPartialTick(false));
        if (remainingTicks <= 0.0F) {
            return;
        }

        int alpha = Mth.clamp((int) (Math.min(remainingTicks, FADE_OUT_TICKS) * 255.0F / FADE_OUT_TICKS), 0, 255);
        alpha = Mth.clamp((int) (alpha * message.alphaMultiplier()), 0, 255);
        if (alpha <= 0) {
            return;
        }

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, y);
        if (message.scale() != 1.0F) {
            graphics.pose().scale(message.scale(), message.scale());
        }

        int textWidth = font.width(message.text());
        int color = (alpha << 24) | (message.colorRgb() & 0x00FFFFFF);
        graphics.drawStringWithBackdrop(font, message.text(), -textWidth / 2, -4, textWidth, color);
        graphics.pose().popMatrix();
    }

    private static boolean seamlesssleep$skipDuplicateRender(GuiGraphics graphics) {
        long now = System.nanoTime();
        if (seamlesssleep$lastRenderedGraphics == graphics && now - seamlesssleep$lastRenderNanos < DUPLICATE_RENDER_WINDOW_NANOS) {
            return true;
        }
        seamlesssleep$lastRenderedGraphics = graphics;
        seamlesssleep$lastRenderNanos = now;
        return false;
    }
}
