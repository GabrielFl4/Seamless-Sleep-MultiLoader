package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
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

// Applies a configurable first-person camera tilt while the player is sleeping.
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
    private void seamlesssleep$tiltSleepCamera(Level area,
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

        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        float tilt = (float) -cfg.sleepCameraTiltDegrees;

        if (player instanceof LocalPlayer localPlayer && ClientBedWorkflow.isManagedBedState(localPlayer)) {
            this.setRotation(
                    ClientBedWorkflow.getCameraYaw(localPlayer),
                    ClientBedWorkflow.getCameraPitch(localPlayer) + tilt
            );

            if (ClientBedWorkflow.isResting(localPlayer)) {
                this.setPosition(this.position.add(BedRestingHelper.getRestingCameraOffset()));
            }
            return;
        }

        if (player.isSleeping()) {
            this.setRotation(this.yRot, this.xRot + tilt);
        }
    }
}
