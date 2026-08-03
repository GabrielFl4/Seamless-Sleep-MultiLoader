package net.aqualoco.sec.client.sleepindicator;

import com.mojang.blaze3d.platform.NativeImage;
import net.aqualoco.sec.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

final class VivecraftWristIndicatorTextureComposer {
    private static final int BIOME_CANVAS_SIZE = 128;
    private static final int CANVAS_EDGE_PADDING = 8;
    private static final int TRANSPARENT_CLEAR_COLOR = 0x00000000;
    private static final int BIOME_BACKING_COLOR = 0xF0000000;
    private static final long MIN_COMPOSE_INTERVAL_NANOS = 33_000_000L;
    private static final ResourceLocation TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "vivecraft_wrist_indicator_composed");

    private NativeImage canvasImage;
    private DynamicTexture texture;
    private NativeImageSleepIndicatorDrawSurface surface;
    private ComposedTexture lastComposedTexture;
    private long lastComposeNanos;

    ComposedTexture compose(SleepIndicatorRenderer renderer,
                            SleepIndicatorContext context,
                            float tickDelta) {
        if (!(renderer instanceof BiomeClockSleepIndicatorRenderer biomeClockRenderer)) {
            return null;
        }

        long nowNanos = System.nanoTime();
        if (canReuseLastTexture(nowNanos)) {
            return this.lastComposedTexture;
        }

        CanvasSpec canvas = new CanvasSpec(BIOME_CANVAS_SIZE, BIOME_CANVAS_SIZE);
        ensureTexture(canvas);
        if (this.canvasImage == null || this.texture == null || this.surface == null) {
            return null;
        }

        clearCanvas();
        drawIndicator(biomeClockRenderer, context, tickDelta, canvas);
        this.texture.upload();
        this.lastComposeNanos = System.nanoTime();
        this.lastComposedTexture = new ComposedTexture(TEXTURE_ID);
        return this.lastComposedTexture;
    }

    private boolean canReuseLastTexture(long nowNanos) {
        return this.canvasImage != null
                && this.texture != null
                && this.lastComposedTexture != null
                && this.lastComposeNanos > 0L
                && nowNanos >= this.lastComposeNanos
                && nowNanos - this.lastComposeNanos < MIN_COMPOSE_INTERVAL_NANOS;
    }

    private void ensureTexture(CanvasSpec canvas) {
        if (this.canvasImage != null
                && this.canvasImage.getWidth() == canvas.width()
                && this.canvasImage.getHeight() == canvas.height()
                && this.texture != null
                && this.surface != null) {
            return;
        }

        this.canvasImage = new NativeImage(canvas.width(), canvas.height(), true);
        this.texture = new DynamicTexture(this.canvasImage);
        this.surface = new NativeImageSleepIndicatorDrawSurface(this.canvasImage);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, this.texture);
    }

    void invalidateResources() {
        if (this.surface != null) {
            this.surface.invalidateTextureCache();
        }
        this.lastComposedTexture = null;
        this.lastComposeNanos = 0L;
    }

    private void clearCanvas() {
        this.canvasImage.fillRect(0, 0, this.canvasImage.getWidth(), this.canvasImage.getHeight(), TRANSPARENT_CLEAR_COLOR);
        this.surface.resetTransform();
    }

    private void drawIndicator(BiomeClockSleepIndicatorRenderer renderer,
                               SleepIndicatorContext context,
                               float tickDelta,
                               CanvasSpec canvas) {
        IndicatorSize size = renderer.measure(context);
        Placement placement = resolvePlacement(size, canvas);

        drawBacking(this.surface, placement, context.alpha());
        this.surface.setTransform(placement.contentX(), placement.contentY(), placement.scale());
        renderer.render(this.surface, context, tickDelta);
        this.surface.resetTransform();
    }

    private static Placement resolvePlacement(IndicatorSize size, CanvasSpec canvas) {
        int width = Math.max(1, size.width());
        int height = Math.max(1, size.height());
        float availableWidth = canvas.width() - CANVAS_EDGE_PADDING * 2.0F;
        float availableHeight = canvas.height() - CANVAS_EDGE_PADDING * 2.0F;
        float scale = Math.min(1.0F, Math.min(availableWidth / width, availableHeight / height));
        float drawWidth = width * scale;
        float drawHeight = height * scale;
        float x = (canvas.width() - drawWidth) * 0.5F;
        float y = (canvas.height() - drawHeight) * 0.5F;
        return new Placement(
                x,
                y,
                drawWidth,
                drawHeight,
                x,
                y,
                scale
        );
    }

    private static void drawBacking(NativeImageSleepIndicatorDrawSurface graphics, Placement placement, float alpha) {
        drawCircularBacking(graphics, placement, multiplyAlpha(BIOME_BACKING_COLOR, alpha));
    }

    private static void drawCircularBacking(NativeImageSleepIndicatorDrawSurface graphics, Placement placement, int color) {
        if ((color >>> 24) <= 0) {
            return;
        }

        float centerX = placement.x() + placement.width() * 0.5F;
        float centerY = placement.y() + placement.height() * 0.5F;
        float radius = Math.max(placement.width(), placement.height()) * 0.5F;
        int top = Mth.floor(centerY - radius);
        int bottom = Mth.ceil(centerY + radius);
        for (int y = top; y < bottom; y++) {
            float normalizedY = (y + 0.5F - centerY) / radius;
            float halfWidth = (float) Math.sqrt(Math.max(0.0F, 1.0F - normalizedY * normalizedY)) * radius;
            graphics.fill(Mth.floor(centerX - halfWidth), y, Mth.ceil(centerX + halfWidth), y + 1, color);
        }
    }

    private static int multiplyAlpha(int color, float alpha) {
        int alphaByte = Mth.clamp((int) (ARGB.alpha(color) * Mth.clamp(alpha, 0.0F, 1.0F)), 0, 255);
        return ARGB.color(alphaByte, color);
    }

    record ComposedTexture(ResourceLocation texture) {
    }

    private record CanvasSpec(int width, int height) {
    }

    private record Placement(float x, float y, float width, float height, float contentX, float contentY, float scale) {
    }
}
