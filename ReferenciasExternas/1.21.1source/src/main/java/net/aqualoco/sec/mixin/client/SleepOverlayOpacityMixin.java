package net.aqualoco.sec.mixin.client;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
public abstract class SleepOverlayOpacityMixin {

    /**
     * Reduz levemente a opacidade do escurecimento ao dormir
     * escalando o sleepTimer usado pelo overlay.
     */
    @Redirect(
            method = "renderSleepOverlay",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getSleepTimer()I"
            )
    )
    private int aquasec$scaleSleepTimer(ClientPlayerEntity player) {
        int vanilla = player.getSleepTimer();
        // 0.6f = overlay ~40% mais claro que o vanilla
        return (int) (vanilla * 0.35f);
    }
}

