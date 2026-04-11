package net.aqualoco.sec.mixin.client.ui;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Defers pause-on-lost-focus while the Seamless skip is running in singleplayer.
 *
 * Vanilla keeps sleeping smooth by not immediately pausing when the window loses focus during bed use.
 * Seamless uses the same idea for the custom skip: the focus loss is remembered, the world keeps running,
 * and the pause menu is only processed once the player leaves the skip state.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSleepPauseDeferralMixin {

    @Shadow @Final public Options options;
    @Shadow public LocalPlayer player;
    @Shadow private boolean windowActive;
    @Shadow public Screen screen;

    @Shadow public abstract boolean isSingleplayer();

    @Shadow public abstract void pauseGame(boolean pauseOnly);

    @Unique
    private boolean seamlesssleep$hasDeferredFocusPause;

    @Unique
    private boolean seamlesssleep$actualWindowActive = true;

    @Inject(method = "setWindowActive", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$deferFocusLossDuringAnimation(boolean windowActive, CallbackInfo ci) {
        this.seamlesssleep$actualWindowActive = windowActive;

        if (windowActive) {
            this.seamlesssleep$hasDeferredFocusPause = false;
            return;
        }

        LocalPlayer localPlayer = this.player;
        if (!this.seamlesssleep$shouldDeferPause(localPlayer)) {
            return;
        }

        this.seamlesssleep$hasDeferredFocusPause = true;
        this.windowActive = true;
        ci.cancel();
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$deferPauseCallWhileUnfocused(boolean pauseOnly, CallbackInfo ci) {
        LocalPlayer localPlayer = this.player;
        if (!this.seamlesssleep$shouldDeferPause(localPlayer) || this.seamlesssleep$actualWindowActive) {
            return;
        }

        this.seamlesssleep$hasDeferredFocusPause = true;
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void seamlesssleep$flushDeferredFocusPause(CallbackInfo ci) {
        if (!this.seamlesssleep$hasDeferredFocusPause) {
            return;
        }

        LocalPlayer localPlayer = this.player;
        if (this.seamlesssleep$shouldDeferPause(localPlayer)) {
            return;
        }

        if (localPlayer != null && ClientBedWorkflow.isManagedBedState(localPlayer)) {
            return;
        }

        boolean shouldPauseNow = !this.seamlesssleep$actualWindowActive
                && this.options.pauseOnLostFocus
                && this.isSingleplayer();

        this.seamlesssleep$hasDeferredFocusPause = false;
        this.windowActive = this.seamlesssleep$actualWindowActive;

        if (shouldPauseNow) {
            this.pauseGame(false);
        }
    }

    @Unique
    private boolean seamlesssleep$shouldDeferPause(LocalPlayer localPlayer) {
        return localPlayer != null
                && this.options.pauseOnLostFocus
                && this.isSingleplayer()
                && ClientBedWorkflow.shouldDeferPauseOnFocusLoss(localPlayer);
    }
}
