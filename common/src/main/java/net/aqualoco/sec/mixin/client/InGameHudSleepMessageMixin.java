package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hides vanilla bed messages that clash with the managed Seamless workflow.
@Mixin(Gui.class)
public abstract class InGameHudSleepMessageMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$skipVanillaSleepMessage(Component message, boolean tinted, CallbackInfo ci) {
        if (message == null) {
            return;
        }
        if (message.getContents() instanceof TranslatableContents content) {
            String key = content.getKey();
            if ("sleep.skipping_night".equals(key)) {
                ci.cancel();
                return;
            }

            if ("sleep.players_sleeping".equals(key)) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null && ClientBedWorkflow.isManagedBedState(player)) {
                    ci.cancel();
                }
            }
        }
    }
}
