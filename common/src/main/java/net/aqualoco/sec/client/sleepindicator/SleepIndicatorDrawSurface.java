package net.aqualoco.sec.client.sleepindicator;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Minimal 2D drawing target used by GuiGraphics-backed indicator renderers.
public interface SleepIndicatorDrawSurface {
    default void withTranslation(float x, float y, Runnable draw) {
        draw.run();
    }

    void fill(int x1, int y1, int x2, int y2, int argb);

    void blit(ResourceLocation texture,
              int destX,
              int destY,
              float sourceU,
              float sourceV,
              int width,
              int height,
              int textureWidth,
              int textureHeight,
              int argb);

    default void blit(ResourceLocation texture,
                      int destX,
                      int destY,
                      float sourceU,
                      float sourceV,
                      int width,
                      int height,
                      int textureWidth,
                      int textureHeight) {
        blit(texture, destX, destY, sourceU, sourceV, width, height, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    void blit(ResourceLocation texture,
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
              int argb);

    default void blit(ResourceLocation texture,
                      int destX,
                      int destY,
                      float sourceU,
                      float sourceV,
                      int width,
                      int height,
                      int sourceWidth,
                      int sourceHeight,
                      int textureWidth,
                      int textureHeight) {
        blit(
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
                0xFFFFFFFF
        );
    }

    void drawString(Font font, Component text, int x, int y, int argb, boolean shadow);
}
