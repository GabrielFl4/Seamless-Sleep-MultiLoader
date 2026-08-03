package net.aqualoco.sec.client.sleepindicator;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// CPU-side draw surface used by the Vivecraft wrist compositor.
final class NativeImageSleepIndicatorDrawSurface implements SleepIndicatorDrawSurface {
    private static final int WHITE = 0xFFFFFFFF;

    private final NativeImage target;
    private final Map<ResourceLocation, NativeImage> textureCache = new HashMap<>();
    private float translateX;
    private float translateY;
    private float scale = 1.0F;

    NativeImageSleepIndicatorDrawSurface(NativeImage target) {
        this.target = target;
    }

    void setTransform(float translateX, float translateY, float scale) {
        this.translateX = translateX;
        this.translateY = translateY;
        this.scale = Math.max(0.001F, scale);
    }

    void resetTransform() {
        setTransform(0.0F, 0.0F, 1.0F);
    }

    void invalidateTextureCache() {
        for (NativeImage image : this.textureCache.values()) {
            if (image != null) {
                image.close();
            }
        }
        this.textureCache.clear();
    }

    @Override
    public void withTranslation(float x, float y, Runnable draw) {
        float previousX = this.translateX;
        float previousY = this.translateY;
        this.translateX += x * this.scale;
        this.translateY += y * this.scale;
        try {
            draw.run();
        } finally {
            this.translateX = previousX;
            this.translateY = previousY;
        }
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if ((argb >>> 24) <= 0) {
            return;
        }

        int minX = transformFloor(Math.min(x1, x2), this.translateX);
        int maxX = transformCeil(Math.max(x1, x2), this.translateX);
        int minY = transformFloor(Math.min(y1, y2), this.translateY);
        int maxY = transformCeil(Math.max(y1, y2), this.translateY);

        minX = Mth.clamp(minX, 0, this.target.getWidth());
        maxX = Mth.clamp(maxX, 0, this.target.getWidth());
        minY = Mth.clamp(minY, 0, this.target.getHeight());
        maxY = Mth.clamp(maxY, 0, this.target.getHeight());
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                blendPixel(x, y, argb);
            }
        }
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
        blit(texture, destX, destY, sourceU, sourceV, width, height, width, height, textureWidth, textureHeight, argb);
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
        if (width <= 0
                || height <= 0
                || sourceWidth <= 0
                || sourceHeight <= 0
                || textureWidth <= 0
                || textureHeight <= 0
                || (argb >>> 24) <= 0) {
            return;
        }

        NativeImage source = texture(texture);
        if (source == null) {
            return;
        }

        int outX = transformFloor(destX, this.translateX);
        int outY = transformFloor(destY, this.translateY);
        int outWidth = Math.max(1, Math.round(width * this.scale));
        int outHeight = Math.max(1, Math.round(height * this.scale));
        int clipX1 = Mth.clamp(outX, 0, this.target.getWidth());
        int clipY1 = Mth.clamp(outY, 0, this.target.getHeight());
        int clipX2 = Mth.clamp(outX + outWidth, 0, this.target.getWidth());
        int clipY2 = Mth.clamp(outY + outHeight, 0, this.target.getHeight());

        for (int y = clipY1; y < clipY2; y++) {
            int localY = y - outY;
            int sampleY = Mth.clamp((int) sourceV + localY * sourceHeight / outHeight, 0, Math.min(source.getHeight(), textureHeight) - 1);
            for (int x = clipX1; x < clipX2; x++) {
                int localX = x - outX;
                int sampleX = Mth.clamp((int) sourceU + localX * sourceWidth / outWidth, 0, Math.min(source.getWidth(), textureWidth) - 1);
                int sourceColor = multiply(source.getPixel(sampleX, sampleY), argb);
                blendPixel(x, y, sourceColor);
            }
        }
    }

    @Override
    public void drawString(Font font, Component text, int x, int y, int argb, boolean shadow) {
        // The wrist compositor intentionally renders only the Biome Clock path, which has no text.
    }

    private NativeImage texture(ResourceLocation location) {
        if (this.textureCache.containsKey(location)) {
            return this.textureCache.get(location);
        }

        NativeImage image = loadTexture(location);
        this.textureCache.put(location, image);
        return image;
    }

    private static NativeImage loadTexture(ResourceLocation location) {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(location).orElse(null);
            if (resource == null) {
                return null;
            }
            try (InputStream stream = resource.open()) {
                return NativeImage.read(stream);
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private void blendPixel(int x, int y, int sourceColor) {
        int sourceAlpha = sourceColor >>> 24;
        if (sourceAlpha <= 0) {
            return;
        }
        if (sourceAlpha >= 255) {
            this.target.setPixel(x, y, sourceColor);
            return;
        }

        int destination = this.target.getPixel(x, y);
        int destinationAlpha = destination >>> 24;
        int inverseSourceAlpha = 255 - sourceAlpha;
        int outAlpha = sourceAlpha + destinationAlpha * inverseSourceAlpha / 255;
        if (outAlpha <= 0) {
            this.target.setPixel(x, y, 0);
            return;
        }

        int sourceRed = red(sourceColor);
        int sourceGreen = green(sourceColor);
        int sourceBlue = blue(sourceColor);
        int destinationWeight = destinationAlpha * inverseSourceAlpha / 255;
        int outRed = (sourceRed * sourceAlpha + red(destination) * destinationWeight) / outAlpha;
        int outGreen = (sourceGreen * sourceAlpha + green(destination) * destinationWeight) / outAlpha;
        int outBlue = (sourceBlue * sourceAlpha + blue(destination) * destinationWeight) / outAlpha;
        this.target.setPixel(x, y, argb(outAlpha, outRed, outGreen, outBlue));
    }

    private static int multiply(int source, int tint) {
        if (tint == WHITE) {
            return source;
        }

        return argb(
                (source >>> 24) * (tint >>> 24) / 255,
                red(source) * red(tint) / 255,
                green(source) * green(tint) / 255,
                blue(source) * blue(tint) / 255
        );
    }

    private int transformFloor(int coordinate, float translation) {
        return Mth.floor(translation + coordinate * this.scale);
    }

    private int transformCeil(int coordinate, float translation) {
        return Mth.ceil(translation + coordinate * this.scale);
    }

    private static int red(int color) {
        return color >> 16 & 0xFF;
    }

    private static int green(int color) {
        return color >> 8 & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (Mth.clamp(alpha, 0, 255) << 24)
                | (Mth.clamp(red, 0, 255) << 16)
                | (Mth.clamp(green, 0, 255) << 8)
                | Mth.clamp(blue, 0, 255);
    }
}
