package net.aqualoco.sec.client;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.SmoothDouble;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class ClientBedWorkflow {

    private static final SmoothDouble seamlesssleep$smoothTurnYaw = new SmoothDouble();
    private static final SmoothDouble seamlesssleep$smoothTurnPitch = new SmoothDouble();

    private static boolean seamlesssleep$wasResting;
    private static boolean seamlesssleep$wasManagedBedState;
    private static boolean seamlesssleep$wasAnimationActive;
    private static boolean seamlesssleep$wasAnimationWakeShiftDown;
    private static boolean seamlesssleep$hasViewState;
    private static float seamlesssleep$viewYaw;
    private static float seamlesssleep$viewPitch;

    private ClientBedWorkflow() {
    }

    public static boolean isResting(LocalPlayer player) {
        return BedRestingHelper.isResting(player);
    }

    public static boolean isPreAnimationBedState(LocalPlayer player) {
        if (!BedRestingHelper.isOverworldWorkflow(player)) {
            return false;
        }
        return isResting(player)
                || (player.isSleeping() && !SeamlessSleepClientState.SLEEP_ANIMATION.isActive());
    }

    public static boolean isManagedBedState(LocalPlayer player) {
        return BedRestingHelper.isOverworldWorkflow(player)
                && (isResting(player) || player.isSleeping());
    }

    public static boolean shouldSuppressBedScreen(LocalPlayer player) {
        return isManagedBedState(player);
    }

    public static boolean hasFreeLook(LocalPlayer player) {
        return isManagedBedState(player);
    }

    public static boolean shouldBlockGameplayInteractions(LocalPlayer player) {
        return isManagedBedState(player);
    }

    public static boolean shouldUseBedCrosshair(LocalPlayer player) {
        return isManagedBedState(player);
    }

    public static void tick(LocalPlayer player) {
        FirstPersonModelCompat.ensureBedCompatibilityInstalled();

        boolean resting = isResting(player);
        boolean managed = isManagedBedState(player);
        boolean animationActive = isAnimationLookDamped(player);

        if (!managed) {
            if (seamlesssleep$wasManagedBedState) {
                BedHudMessageManager.clearAll();
            }
            seamlesssleep$resetLookState();
            seamlesssleep$wasAnimationWakeShiftDown = false;
        } else {
            BedHudMessageManager.syncManagedBedState(player);
            if (!seamlesssleep$hasViewState) {
                seamlesssleep$initLookState(player, player.getBedOrientation());
            }
            if (!seamlesssleep$wasManagedBedState) {
                seamlesssleep$resetLookSmoothing();
            }
            seamlesssleep$applyView(player, player.getBedOrientation());
        }

        seamlesssleep$wasResting = resting;
        seamlesssleep$wasManagedBedState = managed;
        seamlesssleep$wasAnimationActive = animationActive;
    }

    public static boolean isAnimationLookDamped(LocalPlayer player) {
        return player.isSleeping()
                && BedRestingHelper.isOverworldWorkflow(player)
                && SeamlessSleepClientState.SLEEP_ANIMATION.isActive();
    }

    public static boolean shouldWakeOnAnimationExit(LocalPlayer player) {
        return isAnimationLookDamped(player);
    }

    public static boolean shouldDeferPauseOnFocusLoss(LocalPlayer player) {
        return isAnimationLookDamped(player);
    }

    public static void handleAnimationWakeInput(LocalPlayer player, boolean shiftDown) {
        if (!shouldWakeOnAnimationExit(player)) {
            seamlesssleep$wasAnimationWakeShiftDown = shiftDown;
            return;
        }

        if (shiftDown && !seamlesssleep$wasAnimationWakeShiftDown) {
            seamlesssleep$sendWakePacket(player);
        }

        seamlesssleep$wasAnimationWakeShiftDown = shiftDown;
    }

    public static boolean tryWakeFromAnimation(LocalPlayer player) {
        if (!shouldWakeOnAnimationExit(player)) {
            return false;
        }

        return seamlesssleep$sendWakePacket(player);
    }

    public static float getCameraYaw(LocalPlayer player) {
        return seamlesssleep$hasViewState && isManagedBedState(player)
                ? seamlesssleep$viewYaw
                : player.getYRot();
    }

    public static float getCameraPitch(LocalPlayer player) {
        return seamlesssleep$hasViewState && isManagedBedState(player)
                ? seamlesssleep$viewPitch
                : player.getXRot();
    }

    public static boolean shouldRenderFirstPersonBody(LocalPlayer player) {
        FirstPersonModelCompat.ensureBedCompatibilityInstalled();
        if (FirstPersonModelCompat.shouldDeferCameraBodyToFirstPersonModel()) {
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

        float yawDelta = (float) seamlesssleep$smoothTurnYaw.getNewDeltaValue(rawYawDelta * 0.15D * lookScale, smoothing);
        float pitchDelta = (float) seamlesssleep$smoothTurnPitch.getNewDeltaValue(rawPitchDelta * 0.15D * lookScale, smoothing);

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
        if (!isAnimationLookDamped(player)) {
            return BedRestingHelper.REST_LOOK_SCALE;
        }

        return BedRestingHelper.getLookScaleForAnimationProgress(
                SeamlessSleepClientState.SLEEP_ANIMATION.getProgress()
        );
    }

    private static double seamlesssleep$getLookSmoothing(LocalPlayer player) {
        if (!isAnimationLookDamped(player)) {
            return BedRestingHelper.REST_LOOK_SMOOTH_FACTOR;
        }

        return BedRestingHelper.getLookSmoothingForAnimationProgress(
                SeamlessSleepClientState.SLEEP_ANIMATION.getProgress()
        );
    }

    // Keeps the existing config value semantics, but now maps them directly to the player's real pitch.
    private static float seamlesssleep$getConfiguredBedPitch() {
        return (float) -SeamlessSleepClientConfigManager.get().sleepCameraTiltDegrees;
    }

    private static boolean seamlesssleep$sendWakePacket(LocalPlayer player) {
        if (!player.isSleeping() || player.connection == null) {
            return false;
        }

        player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
        return true;
    }
}
