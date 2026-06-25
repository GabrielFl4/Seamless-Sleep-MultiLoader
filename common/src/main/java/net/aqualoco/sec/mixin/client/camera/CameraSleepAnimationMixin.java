package net.aqualoco.sec.mixin.client.camera;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.ShoulderSurfingCompat;
import net.aqualoco.sec.client.VivecraftClientCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Applies the managed bed camera rotation without re-adding the legacy fake-resting height offset.
@Mixin(Camera.class)
public abstract class CameraSleepAnimationMixin {

    @Shadow
    protected abstract void move(float x, float y, float z);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void seamlesssleep$applyBedCamera(Level area,
                                              Entity focusedEntity,
                                              boolean thirdPerson,
                                              boolean inverseView,
                                              float tickDelta,
                                              CallbackInfo ci) {
        if (!(focusedEntity instanceof Player player)) {
            return;
        }

        if (player instanceof LocalPlayer localPlayer
                && ClientBedWorkflow.isManagedBedState(localPlayer)
                && !VivecraftClientCompat.shouldUseVrBedPolicy(localPlayer)) {
            float yaw = ClientBedWorkflow.getCameraYaw(localPlayer);
            float pitch = ClientBedWorkflow.getCameraPitch(localPlayer);

            if (thirdPerson) {
                if (!ShoulderSurfingCompat.isShoulderSurfingPerspectiveActive()) {
                    return;
                }

                Vec3 eyePosition = focusedEntity.getEyePosition(tickDelta);
                this.setPosition(eyePosition.x, eyePosition.y, eyePosition.z);

                ShoulderSurfingCompat.CameraRotation shoulderRotation = ShoulderSurfingCompat.getCameraRotation();
                if (shoulderRotation != null) {
                    this.setRotation(shoulderRotation.yaw(), shoulderRotation.pitch());
                } else {
                    this.setRotation(yaw, pitch);
                }

                Vec3 shoulderOffset = ShoulderSurfingCompat.getCameraOffset((Camera) (Object) this, area, tickDelta, focusedEntity);
                if (shoulderOffset == null) {
                    return;
                }

                this.move((float) -shoulderOffset.z(), (float) shoulderOffset.y(), (float) -shoulderOffset.x());
                return;
            }

            this.setRotation(
                    yaw,
                    pitch
            );
        }
    }
}
