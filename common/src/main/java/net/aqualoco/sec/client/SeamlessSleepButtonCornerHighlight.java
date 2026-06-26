package net.aqualoco.sec.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.util.Util;

// Procedural four-corner CTA highlight used by the YACL guide button.
public final class SeamlessSleepButtonCornerHighlight implements Renderable {
    private static final int CORNER_LENGTH = 4;
    private static final int THICKNESS = 1;
    private static final int MAIN_RGB = 0x75FF00;
    private static final int GHOST_RGB = 0x3C5A3C;
    private static final double PULSE_SPEED = 5.0D;

    private final AbstractWidget button;

    public SeamlessSleepButtonCornerHighlight(AbstractWidget button) {
        this.button = button;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
        if (this.button == null
                || !this.button.visible
                || this.button.getWidth() <= 0
                || this.button.getHeight() <= 0) {
            return;
        }

        double seconds = Util.getMillis() / 1000.0D;
        double wave = Math.abs(Math.sin(seconds * PULSE_SPEED));
        int pulseOffset = 1 + (int) (wave * 3.0D);
        int mainColor = argb(255, MAIN_RGB);
        int ghostColor = argb(255, GHOST_RGB);

        drawCorners(graphics, pulseOffset, 1, 1, ghostColor);
        drawCorners(graphics, pulseOffset, 0, 0, mainColor);
    }

    private void drawCorners(GuiGraphics graphics, int outset, int xOffset, int yOffset, int color) {
        int left = this.button.getX() - outset + xOffset;
        int top = this.button.getY() - outset + yOffset;
        int right = this.button.getX() + this.button.getWidth() + outset + xOffset;
        int bottom = this.button.getY() + this.button.getHeight() + outset + yOffset;
        int length = Math.min(CORNER_LENGTH, Math.max(3, Math.min(this.button.getWidth(), this.button.getHeight())));

        drawTopLeft(graphics, left, top, length, color);
        drawTopRight(graphics, right, top, length, color);
        drawBottomLeft(graphics, left, bottom, length, color);
        drawBottomRight(graphics, right, bottom, length, color);
    }

    private static void drawTopLeft(GuiGraphics graphics, int left, int top, int length, int color) {
        graphics.fill(left, top, left + length, top + THICKNESS, color);
        graphics.fill(left, top, left + THICKNESS, top + length, color);
    }

    private static void drawTopRight(GuiGraphics graphics, int right, int top, int length, int color) {
        graphics.fill(right - length, top, right, top + THICKNESS, color);
        graphics.fill(right - THICKNESS, top, right, top + length, color);
    }

    private static void drawBottomLeft(GuiGraphics graphics, int left, int bottom, int length, int color) {
        graphics.fill(left, bottom - THICKNESS, left + length, bottom, color);
        graphics.fill(left, bottom - length, left + THICKNESS, bottom, color);
    }

    private static void drawBottomRight(GuiGraphics graphics, int right, int bottom, int length, int color) {
        graphics.fill(right - length, bottom - THICKNESS, right, bottom, color);
        graphics.fill(right - THICKNESS, bottom - length, right, bottom, color);
    }

    private static int argb(int alpha, int rgb) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
