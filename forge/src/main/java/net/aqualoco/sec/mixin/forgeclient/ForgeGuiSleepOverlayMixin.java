package net.aqualoco.sec.mixin.forgeclient;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.SleepStatusOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Forge 1.20 renders HUD through ForgeGui, so hook here to draw the custom sleep text.
@Mixin(ForgeGui.class)
public abstract class ForgeGuiSleepOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void seamlesssleep$renderSleepOverlay(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        SleepStatusOverlay.render(graphics, SeamlessSleepClientState.SLEEP_ANIMATION);
    }
}