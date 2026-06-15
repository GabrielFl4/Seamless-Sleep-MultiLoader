package net.aqualoco.sec.client.sleepindicator;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.aqualoco.sec.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

final class VivecraftWristIndicatorTextureComposer {
    private static final int BIOME_CANVAS_SIZE = 128;
    private static final int CANVAS_EDGE_PADDING = 8;
    private static final int TRANSPARENT_CLEAR_COLOR = 0x00000000;
    private static final int BIOME_BACKING_COLOR = 0xF0000000;
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "vivecraft_wrist_indicator_composed");

    private final GuiRenderState renderState = new GuiRenderState();
    private final WristIndicatorGuiStateRenderer guiRenderer = new WristIndicatorGuiStateRenderer();
    private TextureTarget target;
    private WristIndicatorTargetTexture targetTexture;

    ComposedTexture compose(SleepIndicatorRenderer renderer,
                            SleepIndicatorContext context,
                            float tickDelta) {
        if (renderer == null) {
            return null;
        }

        CanvasSpec canvas = new CanvasSpec(BIOME_CANVAS_SIZE, BIOME_CANVAS_SIZE);
        ensureTarget(canvas);
        if (this.target == null || this.targetTexture == null) {
            return null;
        }

        clearTarget();
        drawIndicator(renderer, context, tickDelta, canvas);
        this.guiRenderer.render(this.renderState, this.target);
        return new ComposedTexture(TEXTURE_ID);
    }

    private void ensureTarget(CanvasSpec canvas) {
        if (this.target == null) {
            this.target = new TextureTarget("Seamless Sleep Vivecraft wrist indicator", canvas.width(), canvas.height(), true);
            this.targetTexture = new WristIndicatorTargetTexture(this.target);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, this.targetTexture);
            return;
        }

        if (this.target.width != canvas.width() || this.target.height != canvas.height()) {
            this.target.resize(canvas.width(), canvas.height());
            this.targetTexture.updateTarget(this.target);
        }
    }

    private void clearTarget() {
        RenderSystem.getDevice()
                .createCommandEncoder()
                .clearColorAndDepthTextures(this.target.getColorTexture(), TRANSPARENT_CLEAR_COLOR, this.target.getDepthTexture(), 1.0);
        this.renderState.reset();
    }

    private void drawIndicator(SleepIndicatorRenderer renderer,
                               SleepIndicatorContext context,
                               float tickDelta,
                               CanvasSpec canvas) {
        IndicatorSize size = renderer.measure(context);
        Placement placement = resolvePlacement(size, canvas);
        GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), this.renderState, 0, 0);

        drawBacking(graphics, placement, context.alpha());
        graphics.pose().pushMatrix();
        graphics.pose().translate(placement.contentX(), placement.contentY());
        if (placement.scale() != 1.0F) {
            graphics.pose().scale(placement.scale(), placement.scale());
        }
        renderer.render(graphics, context, tickDelta);
        graphics.pose().popMatrix();
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

    private static void drawBacking(GuiGraphics graphics, Placement placement, float alpha) {
        drawCircularBacking(graphics, placement, ARGB.multiplyAlpha(BIOME_BACKING_COLOR, alpha));
    }

    private static void drawCircularBacking(GuiGraphics graphics, Placement placement, int color) {
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

    record ComposedTexture(Identifier texture) {
    }

    private record CanvasSpec(int width, int height) {
    }

    private record Placement(float x, float y, float width, float height, float contentX, float contentY, float scale) {
    }
}
