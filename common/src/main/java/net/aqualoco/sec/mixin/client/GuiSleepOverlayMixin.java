package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.SleepStatusOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiSleepOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void seamlesssleep$renderSleepOverlay(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        SleepStatusOverlay.render(graphics, SeamlessSleepClientState.SLEEP_ANIMATION);
    }
}
