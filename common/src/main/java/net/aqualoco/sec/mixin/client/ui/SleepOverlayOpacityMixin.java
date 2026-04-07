package net.aqualoco.sec.mixin.client.ui;

import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Scales vanilla sleep overlay darkness using the client config multiplier.
@Mixin(Gui.class)
public abstract class SleepOverlayOpacityMixin {

    @Redirect(
            method = "renderSleepOverlay",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getSleepTimer()I"
            )
    )
    private int seamlesssleep$scaleSleepTimer(LocalPlayer player) {
        int vanilla = player.getSleepTimer();
        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        double factor = cfg.sleepOverlayDarknessMultiplier;
        return (int) (vanilla * factor);
    }
}
