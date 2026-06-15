package net.aqualoco.sec.client.sleepindicator;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.VivecraftSleepWristPanel;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;

// Renders the configured sleep indicator content onto the Vivecraft wrist panel.
public final class VivecraftSleepWristIndicatorRenderer {
    // Base visual size on the approved wrist plane. Hitbox size stays in VivecraftSleepWristPanel.
    private static final float WRIST_VISUAL_SCALE = 1.75F;
    // Hover bump target scale. 1.06F means +6% while hovered.
    private static final float HOVER_TARGET_SCALE = 1.12F;
    // Higher values make the hover bump settle faster.
    private static final float HOVER_RESPONSE_PER_SECOND = 12.0F;

    private static final BiomeClockSleepIndicatorRenderer BIOME_CLOCK_RENDERER = new BiomeClockSleepIndicatorRenderer();
    private static final VivecraftWristIndicatorTextureComposer TEXTURE_COMPOSER = new VivecraftWristIndicatorTextureComposer();

    private static float hoverScale = 1.0F;
    private static long lastHoverScaleUpdateNanos = -1L;

    private VivecraftSleepWristIndicatorRenderer() {
    }

    public static void submitRender(PoseStack poseStack,
                                    CameraRenderState cameraRenderState,
                                    SubmitNodeCollector submitNodeCollector,
                                    VivecraftSleepWristPanel.PanelPose panel,
                                    boolean hovered) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null || panel == null || client.options.hideGui) {
            resetTransientState();
            return;
        }

        SeamlessSleepClientConfig clientConfig = SeamlessSleepClientConfigManager.get();
        SleepIndicatorMode mode = clientConfig.sleepIndicatorMode == null
                ? SleepIndicatorMode.BIOME_CLOCK
                : clientConfig.sleepIndicatorMode;
        if (!shouldRender(mode, clientConfig, player)) {
            resetTransientState();
            return;
        }

        float tickDelta = resolvePartialTick(client);
        SleepIndicatorContext context = SleepIndicatorContext.create(client, level, player, SeamlessSleepClientState.SLEEP_ANIMATION, tickDelta);
        VivecraftWristIndicatorTextureComposer.ComposedTexture texture = TEXTURE_COMPOSER.compose(BIOME_CLOCK_RENDERER, context, tickDelta);
        if (texture == null) {
            return;
        }

        float physicalSize = panel.halfSize() * 2.0F * WRIST_VISUAL_SCALE * updateHoverScale(hovered);
        VivecraftWristIndicatorQuadRenderer.submit(
                poseStack,
                cameraRenderState.pos,
                submitNodeCollector,
                panel,
                texture.texture(),
                physicalSize
        );
    }

    public static void resetTransientState() {
        hoverScale = 1.0F;
        lastHoverScaleUpdateNanos = -1L;
    }

    public static boolean shouldRenderForPlayer(LocalPlayer player) {
        Minecraft client = Minecraft.getInstance();
        if (player == null || client.level == null || client.options.hideGui) {
            return false;
        }

        SeamlessSleepClientConfig clientConfig = SeamlessSleepClientConfigManager.get();
        SleepIndicatorMode mode = clientConfig.sleepIndicatorMode == null
                ? SleepIndicatorMode.BIOME_CLOCK
                : clientConfig.sleepIndicatorMode;
        return shouldRender(mode, clientConfig, player);
    }

    private static boolean shouldRender(SleepIndicatorMode mode,
                                        SeamlessSleepClientConfig clientConfig,
                                        LocalPlayer player) {
        return switch (mode) {
            case OFF -> false;
            case BIOME_CLOCK, TIMESTAMP, TEXT -> shouldRenderVisualIndicator(clientConfig, player);
        };
    }

    private static boolean shouldRenderVisualIndicator(SeamlessSleepClientConfig clientConfig, LocalPlayer player) {
        VivecraftWristIndicatorVisibility visibility = clientConfig.vivecraftWristIndicatorVisibility == null
                ? VivecraftWristIndicatorVisibility.SLEEPING
                : clientConfig.vivecraftWristIndicatorVisibility;
        return switch (visibility) {
            case ALWAYS -> true;
            case SLEEPING -> ClientBedWorkflow.isManagedBedState(player);
        };
    }

    private static float updateHoverScale(boolean hovered) {
        long nowNanos = System.nanoTime();
        if (lastHoverScaleUpdateNanos < 0L) {
            lastHoverScaleUpdateNanos = nowNanos;
        }

        float deltaSeconds = Mth.clamp((nowNanos - lastHoverScaleUpdateNanos) / 1_000_000_000.0F, 0.0F, 0.25F);
        lastHoverScaleUpdateNanos = nowNanos;
        float target = hovered ? HOVER_TARGET_SCALE : 1.0F;
        float blend = 1.0F - (float) Math.exp(-HOVER_RESPONSE_PER_SECOND * deltaSeconds);
        hoverScale = Mth.lerp(Mth.clamp(blend, 0.0F, 1.0F), hoverScale, target);
        return hoverScale;
    }

    private static float resolvePartialTick(Minecraft client) {
        if (client.gameRenderer == null) {
            return 1.0F;
        }
        Camera camera = client.gameRenderer.getMainCamera();
        return camera == null ? 1.0F : camera.getPartialTickTime();
    }
}
