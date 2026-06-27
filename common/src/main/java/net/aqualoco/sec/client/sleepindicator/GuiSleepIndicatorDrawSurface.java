package net.aqualoco.sec.client.sleepindicator;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// GuiGraphics-backed surface used by the normal HUD path.
public final class GuiSleepIndicatorDrawSurface implements SleepIndicatorDrawSurface {
    private final GuiGraphics graphics;

    public GuiSleepIndicatorDrawSurface(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    @Override
    public void withTranslation(float x, float y, Runnable draw) {
        this.graphics.pose().pushMatrix();
        this.graphics.pose().translate(x, y);
        try {
            draw.run();
        } finally {
            this.graphics.pose().popMatrix();
        }
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        this.graphics.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void blit(ResourceLocation texture,
                     int destX,
                     int destY,
                     float sourceU,
                     float sourceV,
                     int width,
                     int height,
                     int textureWidth,
                     int textureHeight,
                     int argb) {
        this.graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                destX,
                destY,
                sourceU,
                sourceV,
                width,
                height,
                textureWidth,
                textureHeight,
                argb
        );
    }

    @Override
    public void blit(ResourceLocation texture,
                     int destX,
                     int destY,
                     float sourceU,
                     float sourceV,
                     int width,
                     int height,
                     int sourceWidth,
                     int sourceHeight,
                     int textureWidth,
                     int textureHeight,
                     int argb) {
        this.graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                destX,
                destY,
                sourceU,
                sourceV,
                width,
                height,
                sourceWidth,
                sourceHeight,
                textureWidth,
                textureHeight,
                argb
        );
    }

    @Override
    public void drawString(Font font, Component text, int x, int y, int argb, boolean shadow) {
        this.graphics.drawString(font, text, x, y, argb, shadow);
    }
}
