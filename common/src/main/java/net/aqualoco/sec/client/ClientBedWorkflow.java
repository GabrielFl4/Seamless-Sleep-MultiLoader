package net.aqualoco.sec.client;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.network.BedLookSyncPayload;
import net.aqualoco.sec.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.SmoothDouble;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class ClientBedWorkflow {

    private static final SmoothDouble seamlesssleep$smoothTurnYaw = new SmoothDouble();
    private static final SmoothDouble seamlesssleep$smoothTurnPitch = new SmoothDouble();
    private static final double seamlesssleep$overlayFadeStartProgress = 0.40D;
    private static final long seamlesssleep$overlayWakeFadeOutMillis = 250L;
    private static final float seamlesssleep$bedLookSyncThresholdDegrees = 0.75F;
    private static final int seamlesssleep$bedLookSyncMinIntervalTicks = 2;
    private static final int seamlesssleep$bedLookSyncHeartbeatTicks = 20;

    private static boolean seamlesssleep$wasManagedBedState;
    private static boolean seamlesssleep$hasViewState;
    private static float seamlesssleep$viewYaw;
    private static float seamlesssleep$viewPitch;
    private static float seamlesssleep$overlayAlpha;
    private static boolean seamlesssleep$overlayFadeOutActive;
    private static float seamlesssleep$overlayFadeStartAlpha;
    private static long seamlesssleep$overlayFadeStartMillis;
    private static boolean seamlesssleep$wasOverlayAnimationActive;
    private static boolean seamlesssleep$overlaySuppressedUntilWake;
    private static double seamlesssleep$lastOverlayAnimationProgress;
    private static boolean seamlesssleep$wasBedLookSyncManaged;
    private static float seamlesssleep$lastSyncedBedLookYaw = Float.NaN;
    private static float seamlesssleep$lastSyncedBedLookPitch = Float.NaN;
    private static int seamlesssleep$bedLookTicksSinceSync = seamlesssleep$bedLookSyncHeartbeatTicks;

    private ClientBedWorkflow() {
    }

    public static boolean isResting(LocalPlayer player) {
        return BedRestingHelper.isResting(player);
    }

    public static boolean isCountedForSleep(LocalPlayer player) {
        return BedRestingHelper.isCountedForSleep(player);
    }

    public static boolean isPreAnimationBedState(LocalPlayer player) {
        return isManagedBedState(player)
                && !SeamlessSleepClientState.SLEEP_ANIMATION.isActive();
    }

    public static boolean isManagedBedState(LocalPlayer player) {
        return BedRestingHelper.isManagedBedState(player);
    }

    public static boolean shouldSuppressBedScreen(LocalPlayer player) {
        return isManagedBedState(player);
    }

    public static boolean hasFreeLook(LocalPlayer player) {
        if (VivecraftClientCompat.shouldUseVrBedPolicy(player)) {
            return false;
        }
        return isManagedBedState(player);
    }

    public static boolean shouldBlockGameplayInteractions(LocalPlayer player) {
        return isManagedBedState(player);
    }

    public static boolean shouldUseBedCrosshair(LocalPlayer player) {
        if (VivecraftClientCompat.shouldUseVrBedPolicy(player)) {
            return false;
        }
        return isManagedBedState(player);
    }

    public static boolean shouldControlSleepOverlay(LocalPlayer player) {
        if (VivecraftClientCompat.shouldUseVrBedPolicy(player)
                && (player.isSleeping() || player.getSleepTimer() > 0)) {
            return true;
        }

        return BedRestingHelper.isManagedBedWorkflowSupported(player)
                && (player.isSleeping()
                || player.getSleepTimer() > 0
                || seamlesssleep$overlayFadeOutActive
                || seamlesssleep$overlayAlpha > 0.0F);
    }

    public static float getSleepOverlayAlpha(LocalPlayer player) {
        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        boolean animationActive = SeamlessSleepClientState.SLEEP_ANIMATION.isActive();
        if (VivecraftClientCompat.shouldUseVrBedPolicy(player)) {
            seamlesssleep$overlayAlpha = 0.0F;
            seamlesssleep$overlayFadeOutActive = false;
            seamlesssleep$overlaySuppressedUntilWake = false;
            seamlesssleep$lastOverlayAnimationProgress = 0.0D;
            seamlesssleep$wasOverlayAnimationActive = animationActive;
            return 0.0F;
        }

        if (!BedRestingHelper.isManagedBedWorkflowSupported(player)) {
            seamlesssleep$overlayAlpha = 0.0F;
            seamlesssleep$overlayFadeOutActive = false;
            seamlesssleep$overlaySuppressedUntilWake = false;
            seamlesssleep$lastOverlayAnimationProgress = 0.0D;
            seamlesssleep$wasOverlayAnimationActive = animationActive;
            return 0.0F;
        }

        if (!isManagedBedState(player)) {
            seamlesssleep$overlaySuppressedUntilWake = false;
        }

        if (animationActive) {
            seamlesssleep$lastOverlayAnimationProgress = SeamlessSleepClientState.SLEEP_ANIMATION.getProgress();
        } else if (seamlesssleep$wasOverlayAnimationActive) {
            seamlesssleep$overlaySuppressedUntilWake = true;
            if (seamlesssleep$lastOverlayAnimationProgress < 0.999D && seamlesssleep$overlayAlpha > 0.0F) {
                seamlesssleep$startOverlayFadeOut();
            }
        }
        seamlesssleep$wasOverlayAnimationActive = animationActive;

        float liveAlpha = seamlesssleep$computeLiveSleepOverlayAlpha(player, cfg);
        boolean liveSourceActive = isManagedBedState(player) && isCountedForSleep(player);
        if (seamlesssleep$overlayFadeOutActive) {
            return seamlesssleep$tickOverlayFadeOut();
        }

        if (liveSourceActive || liveAlpha > 0.0F) {
            seamlesssleep$overlayFadeOutActive = false;
            seamlesssleep$overlayAlpha = liveAlpha;
            return liveAlpha;
        }

        if (seamlesssleep$overlayAlpha <= 0.0F) {
            seamlesssleep$overlayFadeOutActive = false;
            return 0.0F;
        }

        seamlesssleep$startOverlayFadeOut();
        return seamlesssleep$tickOverlayFadeOut();
    }

    public static void tick(LocalPlayer player) {
        FirstPersonModelCompat.ensureBedCompatibilityInstalled();

        boolean managed = isManagedBedState(player);
        VivecraftClientCompat.tick(player, managed && !seamlesssleep$wasManagedBedState);
        boolean vivecraftVrBed = VivecraftClientCompat.shouldUseVrBedPolicy(player);

        if (!managed) {
            if (seamlesssleep$wasManagedBedState) {
                BedHudMessageManager.clearAll();
            }
            seamlesssleep$resetLookState();
            seamlesssleep$resetBedLookSyncState();
        } else {
            BedHudMessageManager.syncManagedBedState(player);
            if (vivecraftVrBed) {
                seamlesssleep$resetLookState();
                seamlesssleep$resetBedLookSyncState();
                seamlesssleep$wasManagedBedState = true;
                return;
            }
            if (!seamlesssleep$hasViewState) {
                seamlesssleep$initLookState(player, player.getBedOrientation());
            }
            if (!seamlesssleep$wasManagedBedState) {
                seamlesssleep$resetLookSmoothing();
            }
            seamlesssleep$applyView(player, player.getBedOrientation());
            seamlesssleep$syncAuthoritativeBedLook(player);
        }

        seamlesssleep$wasManagedBedState = managed;
    }

    public static boolean isAnimationLookDamped(LocalPlayer player) {
        return player.isSleeping()
                && BedRestingHelper.isManagedBedWorkflowSupported(player)
                && SeamlessSleepClientState.SLEEP_ANIMATION.isActive();
    }

    public static boolean shouldWakeOnAnimationExit(LocalPlayer player) {
        return isAnimationLookDamped(player);
    }

    public static boolean shouldDeferPauseOnFocusLoss(LocalPlayer player) {
        return isAnimationLookDamped(player);
    }

    public static boolean tryWakeFromAnimation(LocalPlayer player) {
        if (!shouldWakeOnAnimationExit(player)) {
            return false;
        }

        return seamlesssleep$sendWakePacket(player);
    }

    public static boolean tryWakeFromPreAnimation(LocalPlayer player) {
        if (!isPreAnimationBedState(player)) {
            return false;
        }

        return seamlesssleep$sendWakePacket(player);
    }

    public static boolean tryWakeFromLeaveBedIntent(LocalPlayer player) {
        return tryWakeFromAnimation(player) || tryWakeFromPreAnimation(player);
    }

    public static float getCameraYaw(LocalPlayer player) {
        if (VivecraftClientCompat.shouldUseVrBedPolicy(player)) {
            return player.getYRot();
        }
        return seamlesssleep$hasViewState && isManagedBedState(player)
                ? seamlesssleep$viewYaw
                : player.getYRot();
    }

    public static float getCameraPitch(LocalPlayer player) {
        if (VivecraftClientCompat.shouldUseVrBedPolicy(player)) {
            return player.getXRot();
        }
        return seamlesssleep$hasViewState && isManagedBedState(player)
                ? seamlesssleep$viewPitch
                : player.getXRot();
    }

    public static boolean shouldRenderFirstPersonBody(LocalPlayer player) {
        FirstPersonModelCompat.ensureBedCompatibilityInstalled();
        if (FirstPersonModelCompat.shouldDeferCameraBodyToFirstPersonModel()) {
            return false;
        }

        if (ReplayPlaybackCompat.isReplayPlaybackActive()) {
            return false;
        }
        if (VivecraftClientCompat.shouldUseVrBedPolicy(player)) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        return isManagedBedState(player)
                && client.options.getCameraType().isFirstPerson()
                && cameraEntity == player;
    }

    public static boolean shouldHideVanillaHands(LocalPlayer player) {
        return shouldRenderFirstPersonBody(player);
    }

    public static void applyBedLook(LocalPlayer player, double rawYawDelta, double rawPitchDelta) {
        if (!hasFreeLook(player)) {
            return;
        }

        if (!seamlesssleep$hasViewState) {
            seamlesssleep$initLookState(player, player.getBedOrientation());
        }

        var direction = player.getBedOrientation();
        if (direction == null) {
            return;
        }

        double lookScale = seamlesssleep$getLookScale(player);
        double smoothing = seamlesssleep$getLookSmoothing(player);
        float yawDelta;
        float pitchDelta;
        if (smoothing <= 0.0D) {
            seamlesssleep$resetLookSmoothing();
            yawDelta = (float) (rawYawDelta * 0.15D * lookScale);
            pitchDelta = (float) (rawPitchDelta * 0.15D * lookScale);
        } else {
            yawDelta = (float) seamlesssleep$smoothTurnYaw.getNewDeltaValue(rawYawDelta * 0.15D * lookScale, smoothing);
            pitchDelta = (float) seamlesssleep$smoothTurnPitch.getNewDeltaValue(rawPitchDelta * 0.15D * lookScale, smoothing);
        }

        seamlesssleep$viewYaw = BedRestingHelper.clampYawToBed(seamlesssleep$viewYaw + yawDelta, direction);
        seamlesssleep$viewPitch = BedRestingHelper.clampPitch(seamlesssleep$viewPitch + pitchDelta);
        seamlesssleep$applyView(player, direction);
    }

    private static void seamlesssleep$initLookState(LocalPlayer player, @Nullable Direction direction) {
        if (direction != null) {
            seamlesssleep$viewYaw = BedRestingHelper.getBedBaseYaw(direction);
            seamlesssleep$viewPitch = BedRestingHelper.clampPitch(seamlesssleep$getConfiguredBedPitch());
        } else {
            seamlesssleep$viewYaw = player.getYRot();
            seamlesssleep$viewPitch = player.getXRot();
        }
        seamlesssleep$hasViewState = true;
    }

    private static void seamlesssleep$applyView(LocalPlayer player, @Nullable Direction direction) {
        float yaw = seamlesssleep$viewYaw;
        float pitch = seamlesssleep$viewPitch;

        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);

        if (direction != null) {
            player.setYBodyRot(BedRestingHelper.getBedBaseYaw(direction));
        } else {
            player.setYBodyRot(yaw);
        }
    }

    private static void seamlesssleep$resetLookState() {
        seamlesssleep$hasViewState = false;
        seamlesssleep$resetLookSmoothing();
    }

    private static void seamlesssleep$resetLookSmoothing() {
        seamlesssleep$smoothTurnYaw.reset();
        seamlesssleep$smoothTurnPitch.reset();
    }

    private static double seamlesssleep$getLookScale(LocalPlayer player) {
        double configuredSmoothness = seamlesssleep$getConfiguredMouseSmoothness();
        if (configuredSmoothness <= 0.0D) {
            return 1.0D;
        }

        double baseLookScale;
        if (!isAnimationLookDamped(player)) {
            baseLookScale = BedRestingHelper.REST_LOOK_SCALE;
        } else {
            baseLookScale = BedRestingHelper.getLookScaleForAnimationProgress(
                    SeamlessSleepClientState.SLEEP_ANIMATION.getProgress()
            );
        }
        return Mth.lerp(configuredSmoothness, 1.0D, baseLookScale);
    }

    private static double seamlesssleep$getLookSmoothing(LocalPlayer player) {
        double configuredSmoothness = seamlesssleep$getConfiguredMouseSmoothness();
        if (configuredSmoothness <= 0.0D) {
            return 0.0D;
        }

        double baseSmoothing;
        if (!isAnimationLookDamped(player)) {
            baseSmoothing = BedRestingHelper.REST_LOOK_SMOOTH_FACTOR;
        } else {
            baseSmoothing = BedRestingHelper.getLookSmoothingForAnimationProgress(
                    SeamlessSleepClientState.SLEEP_ANIMATION.getProgress()
            );
        }
        return baseSmoothing * configuredSmoothness;
    }

    private static double seamlesssleep$getConfiguredMouseSmoothness() {
        return Mth.clamp(
                SeamlessSleepClientConfigManager.get().mouseSmoothnessPercent / 100.0D,
                0.0D,
                1.0D
        );
    }

    // Keeps the positive config semantics and inverts them into the real player pitch used while lying down.
    private static float seamlesssleep$getConfiguredBedPitch() {
        double configuredTiltDegrees = Math.max(0.1D, SeamlessSleepClientConfigManager.get().sleepCameraTiltDegrees);
        return (float) -configuredTiltDegrees;
    }

    private static boolean seamlesssleep$sendWakePacket(LocalPlayer player) {
        if (!player.isSleeping() || player.connection == null) {
            return false;
        }

        player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
        return true;
    }

    private static float seamlesssleep$computeLiveSleepOverlayAlpha(LocalPlayer player, SeamlessSleepClientConfig cfg) {
        if (!isManagedBedState(player) || !isCountedForSleep(player)) {
            return 0.0F;
        }

        if (seamlesssleep$overlaySuppressedUntilWake) {
            return 0.0F;
        }

        float baseAlpha = Mth.clamp((float) (player.getSleepTimer() * cfg.sleepOverlayDarknessMultiplier / 100.0D), 0.0F, 1.0F);
        if (!SeamlessSleepClientState.SLEEP_ANIMATION.isActive()) {
            return baseAlpha;
        }

        double progress = SeamlessSleepClientState.SLEEP_ANIMATION.getProgress();
        double fadeProgress = Mth.clamp(
                (progress - seamlesssleep$overlayFadeStartProgress) / (1.0D - seamlesssleep$overlayFadeStartProgress),
                0.0D,
                1.0D
        );
        float animationFadeAlpha = (float) (1.0D - fadeProgress);
        return Math.min(baseAlpha, animationFadeAlpha);
    }

    private static void seamlesssleep$startOverlayFadeOut() {
        if (seamlesssleep$overlayFadeOutActive) {
            return;
        }

        seamlesssleep$overlayFadeOutActive = true;
        seamlesssleep$overlayFadeStartAlpha = seamlesssleep$overlayAlpha;
        seamlesssleep$overlayFadeStartMillis = System.currentTimeMillis();
    }

    private static float seamlesssleep$tickOverlayFadeOut() {
        long now = System.currentTimeMillis();
        double fadeProgress = Mth.clamp(
                (now - seamlesssleep$overlayFadeStartMillis) / (double) seamlesssleep$overlayWakeFadeOutMillis,
                0.0D,
                1.0D
        );
        double easedFade = 1.0D - Math.pow(1.0D - fadeProgress, 2.0D);
        seamlesssleep$overlayAlpha = (float) (seamlesssleep$overlayFadeStartAlpha * (1.0D - easedFade));
        if (fadeProgress >= 1.0D) {
            seamlesssleep$overlayAlpha = 0.0F;
            seamlesssleep$overlayFadeOutActive = false;
        }

        return seamlesssleep$overlayAlpha;
    }

    private static void seamlesssleep$syncAuthoritativeBedLook(LocalPlayer player) {
        if (player.connection == null) {
            return;
        }

        float yaw = getCameraYaw(player);
        float pitch = getCameraPitch(player);
        boolean forceSync = !seamlesssleep$wasBedLookSyncManaged
                || Float.isNaN(seamlesssleep$lastSyncedBedLookYaw)
                || Float.isNaN(seamlesssleep$lastSyncedBedLookPitch);
        boolean heartbeat = seamlesssleep$bedLookTicksSinceSync >= seamlesssleep$bedLookSyncHeartbeatTicks;
        boolean changedEnough = Math.abs(Mth.wrapDegrees(yaw - seamlesssleep$lastSyncedBedLookYaw)) >= seamlesssleep$bedLookSyncThresholdDegrees
                || Math.abs(pitch - seamlesssleep$lastSyncedBedLookPitch) >= seamlesssleep$bedLookSyncThresholdDegrees;

        if (forceSync || heartbeat || (changedEnough && seamlesssleep$bedLookTicksSinceSync >= seamlesssleep$bedLookSyncMinIntervalTicks)) {
            Services.NETWORK.sendToServer(new BedLookSyncPayload(yaw, pitch));
            seamlesssleep$lastSyncedBedLookYaw = yaw;
            seamlesssleep$lastSyncedBedLookPitch = pitch;
            seamlesssleep$bedLookTicksSinceSync = 0;
            seamlesssleep$wasBedLookSyncManaged = true;
            return;
        }

        seamlesssleep$bedLookTicksSinceSync++;
        seamlesssleep$wasBedLookSyncManaged = true;
    }

    private static void seamlesssleep$resetBedLookSyncState() {
        seamlesssleep$wasBedLookSyncManaged = false;
        seamlesssleep$lastSyncedBedLookYaw = Float.NaN;
        seamlesssleep$lastSyncedBedLookPitch = Float.NaN;
        seamlesssleep$bedLookTicksSinceSync = seamlesssleep$bedLookSyncHeartbeatTicks;
    }
}
