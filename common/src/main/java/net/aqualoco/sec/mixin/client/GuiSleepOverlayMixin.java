package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.SleepStatusOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hooks HUD rendering to draw the custom sleep status text.
//
// In 26.2, `Gui#extractRenderState` was changed to `extractRenderState(DeltaTracker, boolean,
// boolean)` (no more GuiGraphicsExtractor param). The old
// `extractRenderState(GuiGraphicsExtractor, DeltaTracker)` signature this mixin needs moved to
// the new `Hud` class instead, unchanged. So we retarget the mixin from `Gui` to `Hud`.
@Mixin(Hud.class)
public abstract class GuiSleepOverlayMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void seamlesssleep$renderSleepOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        SleepStatusOverlay.render(graphics, SeamlessSleepClientState.SLEEP_ANIMATION);
    }
}
