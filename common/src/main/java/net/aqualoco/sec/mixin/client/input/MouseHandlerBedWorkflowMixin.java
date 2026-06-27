package net.aqualoco.sec.mixin.client.input;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.ShoulderSurfingCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Routes sleeping managed-bed look through the real mouse pipeline when ShoulderSurfing consumes LocalPlayer.turn.
@Mixin(MouseHandler.class)
public abstract class MouseHandlerBedWorkflowMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$handleShoulderSurfingBedLook(double movementTime, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player == null
                || !ShoulderSurfingCompat.isShoulderSurfingPerspectiveActive()
                || !ClientBedWorkflow.hasFreeLook(player)
                || player.getBedOrientation() == null) {
            return;
        }

        double sensitivity = this.minecraft.options.sensitivity().get();
        double scaledSensitivity = sensitivity * 0.6D + 0.2D;
        double turnScale = scaledSensitivity * scaledSensitivity * scaledSensitivity * 8.0D;

        double yawDelta = this.accumulatedDX * turnScale;
        double pitchDelta = this.accumulatedDY * turnScale;
        if (((OptionsAccessor) this.minecraft.options).seamlesssleep$getInvertYMouse().get()) {
            pitchDelta = -pitchDelta;
        }

        this.accumulatedDX = 0.0D;
        this.accumulatedDY = 0.0D;

        ShoulderSurfingCompat.applyCameraTurn(player, yawDelta, pitchDelta);
        ClientBedWorkflow.applyBedLook(player, yawDelta, pitchDelta);
        ci.cancel();
    }
}
