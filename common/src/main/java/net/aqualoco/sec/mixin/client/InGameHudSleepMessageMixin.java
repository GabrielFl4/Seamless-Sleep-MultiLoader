package net.aqualoco.sec.mixin.client;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hides the vanilla "skipping night" toast so the custom overlay owns that feedback.
//
// 26.2 CHANGE (confirmed via the official migration primer,
// https://docs.neoforged.net/primer/docs/26.2/): the heads-up display was split out of
// `Gui` into a new `net.minecraft.client.gui.Hud` class, and `Gui#setOverlayMessage` moved
// to `Hud#setOverlayMessage`. This mixin was retargeted accordingly. The method descriptor
// itself (Component;Z) was not reported as changed, but please regenerate sources
// (IDE "Generate Sources" / `./gradlew genSources`) against the 26.2 Minecraft jar and
// confirm the exact signature before shipping.
@Mixin(Hud.class)
public abstract class InGameHudSleepMessageMixin {

    @Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$skipVanillaSleepMessage(Component message, boolean tinted, CallbackInfo ci) {
        if (message == null) {
            return;
        }
        if (message.getContents() instanceof TranslatableContents content) {
            String key = content.getKey();
            if ("sleep.skipping_night".equals(key)) {
                ci.cancel();
            }
        }
    }
}
