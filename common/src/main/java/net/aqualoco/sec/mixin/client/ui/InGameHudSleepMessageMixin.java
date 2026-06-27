package net.aqualoco.sec.mixin.client.ui;

import net.aqualoco.sec.client.BedHudMessageManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Routes vanilla bed overlay messages into the custom two-slot Seamless bed HUD.
@Mixin(Gui.class)
public abstract class InGameHudSleepMessageMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$skipVanillaSleepMessage(Component message, boolean tinted, CallbackInfo ci) {
        if (BedHudMessageManager.captureOverlayMessage(message)) {
            ci.cancel();
        }
    }
}
