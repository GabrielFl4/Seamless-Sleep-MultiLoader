package net.aqualoco.sec.mixin.client.body;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.bed.BedRestingPlayer;
import net.aqualoco.sec.client.BedCameraRenderState;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.ReplayPlaybackCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Marks the local first-person body pass and remaps sleeping head look in bed-local space.
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererBedBodyMixin {

    private static final double seamlesssleep$LOCAL_THIRD_PERSON_BED_Y_OFFSET = 0.125D;
    private static final float seamlesssleep$VISUAL_YAW_SOURCE_LIMIT = 80.0F;
    private static final float seamlesssleep$VISUAL_YAW_TARGET_LIMIT = 55.0F;
    private static final float seamlesssleep$VISUAL_FEET_PITCH_SOURCE_LIMIT = 90.0F;
    private static final float seamlesssleep$VISUAL_FEET_PITCH_TARGET_LIMIT = 18.0F;
    private static final float seamlesssleep$VISUAL_UP_PITCH_SOURCE_LIMIT = 12.0F;
    private static final float seamlesssleep$VISUAL_UP_PITCH_TARGET_LIMIT = 7.0F;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void seamlesssleep$markCameraBody(Avatar avatar, AvatarRenderState avatarRenderState, float tickDelta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        boolean replayPlaybackActive = ReplayPlaybackCompat.isReplayPlaybackActive();
        boolean managedSleepingAvatar = avatar.isSleeping() && avatar.level().dimension().equals(Level.OVERWORLD);
        boolean renderCameraBody = client.player != null
                && cameraEntity == client.player
                && avatar == cameraEntity
                && ClientBedWorkflow.shouldRenderFirstPersonBody(client.player);
        ((BedCameraRenderState) avatarRenderState).seamlesssleep$setCameraBody(renderCameraBody);

        if (!managedSleepingAvatar
                || (avatar == client.player && client.options.getCameraType().isFirstPerson() && !replayPlaybackActive)
                || !avatarRenderState.hasPose(Pose.SLEEPING)) {
            return;
        }

        float lookYaw = avatarRenderState.bodyRot + avatarRenderState.yRot;
        float lookPitch = avatarRenderState.xRot;
        if (avatar == client.player && client.player != null && ClientBedWorkflow.isManagedBedState(client.player)) {
            lookYaw = ClientBedWorkflow.getCameraYaw(client.player);
            lookPitch = ClientBedWorkflow.getCameraPitch(client.player);
        } else if (avatar instanceof BedRestingPlayer restingPlayer) {
            lookYaw = restingPlayer.seamlesssleep$getVisualBedLookYaw(tickDelta);
            lookPitch = restingPlayer.seamlesssleep$getVisualBedLookPitch(tickDelta);
        }

        if (avatarRenderState.bedOrientation != null) {
            avatarRenderState.bodyRot = BedRestingHelper.getBedBaseYaw(avatarRenderState.bedOrientation);
        }

        Vec3 lookVector = Vec3.directionFromRotation(lookPitch, lookYaw);
        Vec3 bedFeetAxis = Vec3.directionFromRotation(0.0F, avatarRenderState.bodyRot);
        Vec3 bedSideAxis = Vec3.directionFromRotation(0.0F, avatarRenderState.bodyRot + 90.0F);

        float side = (float) Mth.clamp(lookVector.dot(bedSideAxis), -1.0D, 1.0D);
        float feet = (float) Mth.clamp(lookVector.dot(bedFeetAxis), -1.0D, 1.0D);

        float rawHeadYaw = (float) Math.toDegrees(Math.asin(side));
        float rawHeadPitch = (float) Math.toDegrees(Math.asin(feet));

        avatarRenderState.yRot = seamlesssleep$mapVisualYaw(rawHeadYaw);
        avatarRenderState.xRot = seamlesssleep$mapVisualPitch(rawHeadPitch);
    }

    @Inject(
            method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void seamlesssleep$liftLocalSleepingAvatarInThirdPerson(AvatarRenderState avatarRenderState, CallbackInfoReturnable<Vec3> cir) {
        Minecraft client = Minecraft.getInstance();
        boolean replayPlaybackActive = ReplayPlaybackCompat.isReplayPlaybackActive();
        if (client.player == null
                || avatarRenderState.id != client.player.getId()
                || (client.options.getCameraType().isFirstPerson() && !replayPlaybackActive)
                || (!replayPlaybackActive && !ClientBedWorkflow.isManagedBedState(client.player))
                || !avatarRenderState.hasPose(Pose.SLEEPING)) {
            return;
        }

        Vec3 baseOffset = cir.getReturnValue();
        cir.setReturnValue(baseOffset.add(0.0D, seamlesssleep$LOCAL_THIRD_PERSON_BED_Y_OFFSET, 0.0D));
    }

    private static float seamlesssleep$mapVisualYaw(float rawHeadYaw) {
        return seamlesssleep$mapSignedAngle(
                rawHeadYaw,
                seamlesssleep$VISUAL_YAW_SOURCE_LIMIT,
                seamlesssleep$VISUAL_YAW_TARGET_LIMIT
        );
    }

    private static float seamlesssleep$mapVisualPitch(float rawHeadPitch) {
        if (rawHeadPitch >= 0.0F) {
            return seamlesssleep$mapSignedAngle(
                    rawHeadPitch,
                    seamlesssleep$VISUAL_FEET_PITCH_SOURCE_LIMIT,
                    seamlesssleep$VISUAL_FEET_PITCH_TARGET_LIMIT
            );
        }

        return seamlesssleep$mapSignedAngle(
                rawHeadPitch,
                seamlesssleep$VISUAL_UP_PITCH_SOURCE_LIMIT,
                seamlesssleep$VISUAL_UP_PITCH_TARGET_LIMIT
        );
    }

    private static float seamlesssleep$mapSignedAngle(float rawAngle, float sourceLimit, float targetLimit) {
        float sign = Math.signum(rawAngle);
        float normalized = Mth.clamp(Math.abs(rawAngle) / sourceLimit, 0.0F, 1.0F);
        float eased = normalized * normalized * (3.0F - 2.0F * normalized);
        return sign * eased * targetLimit;
    }
}
