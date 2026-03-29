package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Applies a configurable first-person camera tilt while the player is sleeping.
@Mixin(Camera.class)
public abstract class CameraSleepAnimationMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
    private void seamlesssleep$tiltSleepCamera(float partialTicks, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        if (!(self.entity() instanceof Player player)) {
            return;
        }

        if (self.isDetached() || !player.isSleeping()) {
            return;
        }

        float yaw = self.yRot();
        float pitch = self.xRot();

        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        float tilt = (float) -cfg.sleepCameraTiltDegrees;

        this.setRotation(yaw, pitch + tilt);
    }
}
