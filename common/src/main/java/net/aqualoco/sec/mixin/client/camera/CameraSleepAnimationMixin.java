package net.aqualoco.sec.mixin.client.camera;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Applies the managed bed camera rotation without re-adding the legacy fake-resting height offset.
@Mixin(Camera.class)
public abstract class CameraSleepAnimationMixin {

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
            return;
        }
    }
}
