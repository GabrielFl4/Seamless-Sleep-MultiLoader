package net.aqualoco.sec.mixin.client.body;

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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Marks the locally injected first-person body render and softens third-person head motion while in bed.
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererBedBodyMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void seamlesssleep$markCameraBody(Avatar avatar, AvatarRenderState avatarRenderState, float tickDelta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        boolean localManagedBedState = client.player != null
                && avatar == client.player
                && ClientBedWorkflow.isManagedBedState(client.player);
        boolean renderCameraBody = client.player != null
                && cameraEntity == client.player
                && avatar == cameraEntity
                && ClientBedWorkflow.shouldRenderFirstPersonBody(client.player);
        ((BedCameraRenderState) avatarRenderState).seamlesssleep$setCameraBody(renderCameraBody);

        if (!localManagedBedState
                || client.options.getCameraType().isFirstPerson()
                || !avatarRenderState.hasPose(Pose.SLEEPING)) {
            return;
        }

        if (avatarRenderState.bedOrientation != null) {
            avatarRenderState.bodyRot = BedRestingHelper.getBedBaseYaw(avatarRenderState.bedOrientation);
        }

        avatarRenderState.yRot = Mth.clamp(Mth.wrapDegrees(avatarRenderState.yRot) * 0.35F, -35.0F, 35.0F);
        avatarRenderState.xRot = Mth.clamp(avatarRenderState.xRot * 0.35F, -50.0F, 42.0F);
    }
}
