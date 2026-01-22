package net.aqualoco.sec.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudSleepMessageMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void aquasec$skipVanillaSleepMessage(Component message, boolean tinted, CallbackInfo ci) {
        if (message == null) {
            return;
        }
        if (message.getContents() instanceof TranslatableContents content) {
            String key = content.getKey();
            if ("sleep.skipping_night".equals(key)) {
                ci.cancel(); // nao deixa o vanilla mostrar "Passando a noite"
            }
        }
    }
}
