package net.aqualoco.sec.mixin.client.ui;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Prevents vanilla from auto-opening the in-bed chat screen while Seamless manages the bed workflow itself.
@Mixin(Minecraft.class)
public abstract class MinecraftBedWorkflowMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"
            )
    )
    private void seamlesssleep$suppressAutoBedChat(Minecraft client, Screen screen) {
        LocalPlayer player = client.player;
        if (screen instanceof InBedChatScreen
                && player != null
                && ClientBedWorkflow.shouldSuppressBedScreen(player)) {
            return;
        }

        client.setScreen(screen);
    }
}
