package net.aqualoco.sec.mixin.client.body;

import net.aqualoco.sec.bed.BedRestingPlayer;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.client.BedCameraRenderState;
import net.aqualoco.sec.client.ClientBedWorkflow;
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

// Marks the local first-person body pass and applies managed-bed head/body softening from the authoritative bed look state.
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererBedBodyMixin {

    private static final double seamlesssleep$LOCAL_THIRD_PERSON_BED_Y_OFFSET = 0.125D;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void seamlesssleep$markCameraBody(Avatar avatar, AvatarRenderState avatarRenderState, float tickDelta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        boolean managedSleepingAvatar = avatar.isSleeping() && avatar.level().dimension().equals(Level.OVERWORLD);
        boolean renderCameraBody = client.player != null
                && cameraEntity == client.player
                && avatar == cameraEntity
                && ClientBedWorkflow.shouldRenderFirstPersonBody(client.player);
        ((BedCameraRenderState) avatarRenderState).seamlesssleep$setCameraBody(renderCameraBody);

        if (!managedSleepingAvatar
                || (avatar == client.player && client.options.getCameraType().isFirstPerson())
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

        avatarRenderState.yRot = Mth.clamp(Mth.wrapDegrees(lookYaw - avatarRenderState.bodyRot) * 0.35F, -35.0F, 35.0F);
        avatarRenderState.xRot = Mth.clamp(lookPitch * 0.35F, -50.0F, 42.0F);
    }

    @Inject(
            method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void seamlesssleep$liftLocalSleepingAvatarInThirdPerson(AvatarRenderState avatarRenderState, CallbackInfoReturnable<Vec3> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null
                || avatarRenderState.id != client.player.getId()
                || client.options.getCameraType().isFirstPerson()
                || !ClientBedWorkflow.isManagedBedState(client.player)
                || !avatarRenderState.hasPose(Pose.SLEEPING)) {
            return;
        }

        Vec3 baseOffset = cir.getReturnValue();
        cir.setReturnValue(baseOffset.add(0.0D, seamlesssleep$LOCAL_THIRD_PERSON_BED_Y_OFFSET, 0.0D));
    }
}
