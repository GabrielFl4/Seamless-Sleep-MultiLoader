package net.aqualoco.sec.mixin.client;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudSleepMessageMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void aquasec$skipVanillaSleepMessage(Text message, boolean tinted, CallbackInfo ci) {
        if (message == null) {
            return;
        }
        if (message.getContent() instanceof TranslatableTextContent content) {
            String key = content.getKey();
            if ("sleep.skipping_night".equals(key)) {
                ci.cancel(); // nao deixa o vanilla mostrar "Passando a noite"
            }
        }
    }
}
