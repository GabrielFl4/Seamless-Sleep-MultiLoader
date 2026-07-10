package net.aqualoco.sec.mixin.client.body;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.bed.BedRestingPlayer;
import net.aqualoco.sec.client.BedCameraRenderState;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.EssentialCompat;
import net.aqualoco.sec.client.ReplayPlaybackCompat;
import net.aqualoco.sec.client.VivecraftClientCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Marks the local first-person body pass and remaps sleeping head look in bed-local space.
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererBedBodyMixin {

    private static final double seamlesssleep$LOCAL_THIRD_PERSON_BED_Y_OFFSET = 0.125D;
    private static final float seamlesssleep$VISUAL_YAW_SOURCE_LIMIT = 80.0F;
    private static final float seamlesssleep$VISUAL_YAW_TARGET_LIMIT = 55.0F;
    private static final float seamlesssleep$VISUAL_FEET_PITCH_SOURCE_LIMIT = 90.0F;
    private static final float seamlesssleep$VISUAL_FEET_PITCH_TARGET_LIMIT = 18.0F;
    private static final float seamlesssleep$VISUAL_UP_PITCH_SOURCE_LIMIT = 12.0F;
    private static final float seamlesssleep$VISUAL_UP_PITCH_TARGET_LIMIT = 7.0F;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL")
    )
    private void seamlesssleep$markCameraBody(AbstractClientPlayer player, PlayerRenderState playerRenderState, float tickDelta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        boolean managedSleepingPlayer = BedRestingHelper.isManagedBedState(player);
        boolean renderCameraBody = client.player != null
                && cameraEntity == client.player
                && player == cameraEntity
                && ClientBedWorkflow.shouldRenderFirstPersonBody(client.player);
        ((BedCameraRenderState) playerRenderState).seamlesssleep$setCameraBody(renderCameraBody);

        if (!managedSleepingPlayer
                || !playerRenderState.hasPose(Pose.SLEEPING)) {
            return;
        }
        boolean replayPlaybackActive = ReplayPlaybackCompat.isReplayPlaybackActive();
        if (player == client.player && client.options.getCameraType().isFirstPerson() && !replayPlaybackActive) {
            return;
        }
        if (VivecraftClientCompat.shouldPreserveVrPlayerRender(player)) {
            return;
        }

        float lookYaw = playerRenderState.bodyRot + playerRenderState.yRot;
        float lookPitch = playerRenderState.xRot;
        if (player == client.player && client.player != null && ClientBedWorkflow.isManagedBedState(client.player)) {
            lookYaw = ClientBedWorkflow.getCameraYaw(client.player);
            lookPitch = ClientBedWorkflow.getCameraPitch(client.player);
        } else if (player instanceof BedRestingPlayer restingPlayer) {
            lookYaw = restingPlayer.seamlesssleep$getVisualBedLookYaw(tickDelta);
            lookPitch = restingPlayer.seamlesssleep$getVisualBedLookPitch(tickDelta);
        }

        if (playerRenderState.bedOrientation != null) {
            playerRenderState.bodyRot = BedRestingHelper.getBedBaseYaw(playerRenderState.bedOrientation);
        }

        Vec3 lookVector = Vec3.directionFromRotation(lookPitch, lookYaw);
        Vec3 bedFeetAxis = Vec3.directionFromRotation(0.0F, playerRenderState.bodyRot);
        Vec3 bedSideAxis = Vec3.directionFromRotation(0.0F, playerRenderState.bodyRot + 90.0F);

        float side = (float) Mth.clamp(lookVector.dot(bedSideAxis), -1.0D, 1.0D);
        float feet = (float) Mth.clamp(lookVector.dot(bedFeetAxis), -1.0D, 1.0D);

        float rawHeadYaw = (float) Math.toDegrees(Math.asin(side));
        float rawHeadPitch = (float) Math.toDegrees(Math.asin(feet));

        playerRenderState.yRot = seamlesssleep$mapVisualYaw(rawHeadYaw);
        playerRenderState.xRot = seamlesssleep$mapVisualPitch(rawHeadPitch);
    }

    @Inject(
            method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void seamlesssleep$liftLocalSleepingPlayerInThirdPerson(PlayerRenderState playerRenderState, CallbackInfoReturnable<Vec3> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null
                || playerRenderState.id != client.player.getId()
                || !playerRenderState.hasPose(Pose.SLEEPING)) {
            return;
        }
        boolean replayPlaybackActive = ReplayPlaybackCompat.isReplayPlaybackActive();
        if ((client.options.getCameraType().isFirstPerson() && !replayPlaybackActive)
                || (!replayPlaybackActive && !ClientBedWorkflow.isManagedBedState(client.player))
                || VivecraftClientCompat.shouldUseVrBedPolicy(client.player)) {
            return;
        }

        Vec3 baseOffset = cir.getReturnValue();
        cir.setReturnValue(baseOffset.add(0.0D, seamlesssleep$LOCAL_THIRD_PERSON_BED_Y_OFFSET, 0.0D));
    }

    @Inject(
            method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void seamlesssleep$suppressEssentialOwnNameTagOnBedCameraBody(PlayerRenderState playerRenderState,
                                                                           Component component,
                                                                           PoseStack poseStack,
                                                                           MultiBufferSource multiBufferSource,
                                                                           int light,
                                                                           CallbackInfo ci) {
        if (!EssentialCompat.shouldSuppressOwnNameTagForBedCameraBody()) {
            return;
        }

        if (!((BedCameraRenderState) playerRenderState).seamlesssleep$isCameraBody()) {
            return;
        }

        ci.cancel();
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
