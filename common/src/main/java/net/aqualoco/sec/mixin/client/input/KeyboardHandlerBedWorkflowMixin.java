package net.aqualoco.sec.mixin.client.input;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

// Routes bed-exit inputs to the same vanilla STOP_SLEEPING packet path the client uses for leaving a bed screen.
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerBedWorkflowMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$wakeFromBedSneak(long windowPointer, int action, KeyEvent event, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS || this.minecraft.screen != null) {
            return;
        }

        LocalPlayer player = this.minecraft.player;
        if (player == null || !this.minecraft.options.keyShift.matches(event)) {
            return;
        }

        if (ClientBedWorkflow.tryWakeFromAnimation(player) || ClientBedWorkflow.tryWakeFromPreAnimation(player)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "keyPress",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;pauseGame(Z)V"
            )
    )
    private void seamlesssleep$wakeInsteadOfPausing(Minecraft client, boolean pauseOnly) {
        LocalPlayer player = this.minecraft.player;
        if (player != null && ClientBedWorkflow.tryWakeFromAnimation(player)) {
            return;
        }

        client.pauseGame(pauseOnly);
    }
}
