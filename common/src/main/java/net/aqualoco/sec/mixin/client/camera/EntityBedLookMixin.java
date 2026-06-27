package net.aqualoco.sec.mixin.client.camera;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Replaces vanilla turn handling with the bed-specific free-look controller while the workflow is active.
@Mixin(Entity.class)
public abstract class EntityBedLookMixin {

    @Shadow public float xRotO;
    @Shadow public float yRotO;

    @Shadow public abstract float getXRot();

    @Shadow public abstract float getYRot();

    @Shadow public abstract void setXRot(float xRot);

    @Shadow public abstract void setYRot(float yRot);

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$clampBedLook(double yRot, double xRot, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || player != (Object) this) {
            return;
        }

        if (!ClientBedWorkflow.hasFreeLook(player)) {
            return;
        }

        if (player.getBedOrientation() == null) {
            return;
        }

        ClientBedWorkflow.applyBedLook(player, yRot, xRot);
        float currentPitch = ClientBedWorkflow.getCameraPitch(player);
        float currentYaw = ClientBedWorkflow.getCameraYaw(player);

        this.setXRot(currentPitch);
        this.setYRot(currentYaw);
        this.xRotO = currentPitch;
        this.yRotO = currentYaw;
        player.setYHeadRot(currentYaw);
        player.setYBodyRot(BedRestingHelper.getBedBaseYaw(player.getBedOrientation()));

        ci.cancel();
    }
}
