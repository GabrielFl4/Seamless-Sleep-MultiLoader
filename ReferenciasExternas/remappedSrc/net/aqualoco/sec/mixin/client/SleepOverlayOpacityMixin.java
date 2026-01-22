package net.aqualoco.sec.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class SleepOverlayOpacityMixin {

    /**
     * Reduz levemente a opacidade do escurecimento ao dormir
     * escalando o sleepTimer usado pelo overlay.
     */
    @Redirect(
            method = "renderSleepOverlay",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getSleepTimer()I"
            )
    )
    private int aquasec$scaleSleepTimer(LocalPlayer player) {
        int vanilla = player.getSleepTimer();
        // 0.6f = overlay ~40% mais claro que o vanilla
        return (int) (vanilla * 0.35f);
    }
}

