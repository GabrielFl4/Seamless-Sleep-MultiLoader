package net.aqualoco.sec.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.aqualoco.sec.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;

// Draws a fixed-size bed crosshair from a standalone PNG centered on the HUD.
public final class BedCrosshairRenderer {

    private static final ResourceLocation BED_CROSSHAIR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/bed_crosshair.png");
    private static final int RENDER_SIZE = 15;
    private static final int DEFAULT_TEXTURE_SIZE = 15;

    private static int seamlesssleep$textureWidth = -1;
    private static int seamlesssleep$textureHeight = -1;

    private BedCrosshairRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        seamlesssleep$resolveTextureSize();

        int x = (graphics.guiWidth() - RENDER_SIZE) / 2;
        int y = (graphics.guiHeight() - RENDER_SIZE) / 2;

        graphics.nextStratum();
        graphics.blit(
                RenderPipelines.CROSSHAIR,
                BED_CROSSHAIR_TEXTURE,
                x,
                y,
                0.0F,
                0.0F,
                RENDER_SIZE,
                RENDER_SIZE,
                seamlesssleep$textureWidth,
                seamlesssleep$textureHeight,
                seamlesssleep$textureWidth,
                seamlesssleep$textureHeight
        );
    }

    private static void seamlesssleep$resolveTextureSize() {
        if (seamlesssleep$textureWidth > 0 && seamlesssleep$textureHeight > 0) {
            return;
        }

        seamlesssleep$textureWidth = DEFAULT_TEXTURE_SIZE;
        seamlesssleep$textureHeight = DEFAULT_TEXTURE_SIZE;

        Minecraft client = Minecraft.getInstance();
        if (client.getResourceManager() == null) {
            return;
        }

        try (InputStream stream = client.getResourceManager().open(BED_CROSSHAIR_TEXTURE);
             NativeImage image = NativeImage.read(stream)) {
            seamlesssleep$textureWidth = image.getWidth();
            seamlesssleep$textureHeight = image.getHeight();
        } catch (IOException ignored) {
        }
    }
}
