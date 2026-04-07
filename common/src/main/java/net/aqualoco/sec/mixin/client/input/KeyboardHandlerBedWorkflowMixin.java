package net.aqualoco.sec.mixin.client.input;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Converts ESC into a wake-up packet during the Seamless skip so the game does not open pause mid-animation.
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerBedWorkflowMixin {

    @Shadow @Final private Minecraft minecraft;

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
