package net.aqualoco.sec.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
        return (int) (vanilla * 0.35f);
    }
}
