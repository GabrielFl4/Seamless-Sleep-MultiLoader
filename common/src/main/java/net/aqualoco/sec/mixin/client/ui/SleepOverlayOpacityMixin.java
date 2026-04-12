package net.aqualoco.sec.mixin.client.ui;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Replaces the raw sleepTimer-driven overlay with a managed alpha that can fade out cleanly at animation end or abrupt wake.
@Mixin(Gui.class)
public abstract class SleepOverlayOpacityMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "renderSleepOverlay", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$renderManagedSleepOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player == null || !ClientBedWorkflow.shouldControlSleepOverlay(player)) {
            return;
        }

        ci.cancel();

        float alpha = ClientBedWorkflow.getSleepOverlayAlpha(player);
        if (alpha <= 0.0F) {
            return;
        }

        guiGraphics.nextStratum();
        int color = (int) (220.0F * alpha) << 24 | 1052704;
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), color);
    }
}
