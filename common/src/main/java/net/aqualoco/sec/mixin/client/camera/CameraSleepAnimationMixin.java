package net.aqualoco.sec.mixin.client.camera;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.client.ClientBedWorkflow;
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

// Applies the bed workflow camera rotation and resting camera offset without adding extra fake tilt.
@Mixin(Camera.class)
public abstract class CameraSleepAnimationMixin {

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    private Vec3 position;

    @Shadow
    protected abstract void setPosition(Vec3 position);

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

        if (thirdPerson) {
            return;
        }

        if (player instanceof LocalPlayer localPlayer && ClientBedWorkflow.isManagedBedState(localPlayer)) {
            this.setRotation(
                    ClientBedWorkflow.getCameraYaw(localPlayer),
                    ClientBedWorkflow.getCameraPitch(localPlayer)
            );

            if (ClientBedWorkflow.isResting(localPlayer)) {
                this.setPosition(this.position.add(BedRestingHelper.getRestingCameraOffset()));
            }
            return;
        }
    }
}
