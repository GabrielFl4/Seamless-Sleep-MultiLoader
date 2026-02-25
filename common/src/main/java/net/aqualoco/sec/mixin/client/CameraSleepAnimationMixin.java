package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

        if (thirdPerson || !player.isSleeping()) {
            return;
        }

        Camera self = (Camera) (Object) this;
        float yaw = self.yRot();
        float pitch = self.xRot();

        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        float tilt = (float) -cfg.sleepCameraTiltDegrees;

        this.setRotation(yaw, pitch + tilt);
    }
}
