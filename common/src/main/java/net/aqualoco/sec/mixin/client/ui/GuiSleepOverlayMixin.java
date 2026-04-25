package net.aqualoco.sec.mixin.client.ui;

import net.aqualoco.sec.client.BedHudMessageRenderer;
import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.sleepindicator.SleepIndicatorSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hooks GUI rendering to draw the Seamless bed HUD overlays after the vanilla GUI pass.
@Mixin(Gui.class)
public abstract class GuiSleepOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void seamlesssleep$renderSleepOverlay(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        SleepIndicatorSystem.render(graphics, deltaTracker, SeamlessSleepClientState.SLEEP_ANIMATION);
        BedHudMessageRenderer.render(graphics, deltaTracker);
    }
}
