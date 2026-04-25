package net.aqualoco.sec.client.sleepindicator;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

// Coordinates sleep indicator visibility, placement, animation, and renderer selection.
public final class SleepIndicatorSystem {
    private static final int DEFAULT_MARGIN = 8;
    private static final long DUPLICATE_RENDER_WINDOW_NANOS = 1_000_000L;
    private static final SleepIndicatorRenderer OVERLAY_RENDERER = new OverlaySleepIndicatorRenderer();
    private static final SleepIndicatorRenderer BIOME_CLOCK_RENDERER = new BiomeClockSleepIndicatorRenderer();
    private static final SleepIndicatorPresentationState PRESENTATION = new SleepIndicatorPresentationState();
    private static GuiGraphics lastRenderedGraphics;
    private static long lastRenderNanos;

    private SleepIndicatorSystem() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker, ClientSleepAnimationState sleepAnimation) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            PRESENTATION.reset();
            return;
        }
        if (client.options.hideGui) {
            return;
        }

        SleepIndicatorConfig config = SleepIndicatorConfig.from(SeamlessSleepClientConfigManager.get());
        SleepIndicatorRenderer renderer = rendererFor(config.mode());
        boolean targetVisible = renderer != null && shouldRender(config, player, sleepAnimation);
        if (renderer == null) {
            PRESENTATION.update(false, config.anchor(), presentationTimeMs());
            return;
        }

        if (skipDuplicateRender(graphics)) {
            return;
        }

        PRESENTATION.update(targetVisible, config.anchor(), presentationTimeMs());
        if (!PRESENTATION.shouldRender()) {
            return;
        }

        float tickDelta = deltaTracker == null ? 0.0F : deltaTracker.getGameTimeDeltaPartialTick(false);
        SleepIndicatorContext context = SleepIndicatorContext.create(client, level, player, sleepAnimation, tickDelta);
        SleepIndicatorPlacement placement = SleepIndicatorPlacement.resolve(
                graphics.guiWidth(),
                graphics.guiHeight(),
                renderer.width(),
                renderer.height(),
                config.anchor(),
                config.scale(),
                DEFAULT_MARGIN
        );

        SleepIndicatorContext animatedContext = context.withAlphaMultiplier(PRESENTATION.alpha());
        float animatedScale = placement.scale() * PRESENTATION.scaleMultiplier();
        float animatedOffsetY = PRESENTATION.offsetY() * placement.scale();
        float pivotX = pivotX(config.anchor(), renderer.width());
        float pivotY = pivotY(config.anchor(), renderer.height());
        float animatedX = placement.x() + pivotX * placement.scale() - pivotX * animatedScale;
        float animatedY = placement.y() + animatedOffsetY + pivotY * placement.scale() - pivotY * animatedScale;

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(animatedX, animatedY);
        if (animatedScale != 1.0F) {
            graphics.pose().scale(animatedScale, animatedScale);
        }
        renderer.render(graphics, animatedContext, tickDelta);
        graphics.pose().popMatrix();
    }

    private static boolean shouldRender(
            SleepIndicatorConfig config,
            LocalPlayer player,
            ClientSleepAnimationState sleepAnimation
    ) {
        if (config.mode() == SleepIndicatorMode.OFF) {
            return false;
        }

        return switch (config.visibility()) {
            case ALWAYS -> true;
            case BED -> ClientBedWorkflow.isManagedBedState(player);
            case SLEEP -> sleepAnimation != null && sleepAnimation.isActive();
        };
    }

    private static SleepIndicatorRenderer rendererFor(SleepIndicatorMode mode) {
        return switch (mode) {
            case OFF -> null;
            case OVERLAY -> OVERLAY_RENDERER;
            case BIOME_CLOCK -> BIOME_CLOCK_RENDERER;
        };
    }

    private static boolean skipDuplicateRender(GuiGraphics graphics) {
        long now = System.nanoTime();
        if (lastRenderedGraphics == graphics && now - lastRenderNanos < DUPLICATE_RENDER_WINDOW_NANOS) {
            return true;
        }
        lastRenderedGraphics = graphics;
        lastRenderNanos = now;
        return false;
    }

    private static long presentationTimeMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private static float pivotX(SleepIndicatorAnchor anchor, int width) {
        return switch (anchor == null ? SleepIndicatorAnchor.CENTER : anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> 0.0F;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> width * 0.5F;
            case TOP_RIGHT, BOTTOM_RIGHT -> width;
        };
    }

    private static float pivotY(SleepIndicatorAnchor anchor, int height) {
        return switch (anchor == null ? SleepIndicatorAnchor.CENTER : anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0.0F;
            case CENTER -> height * 0.5F;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> height;
        };
    }
}
