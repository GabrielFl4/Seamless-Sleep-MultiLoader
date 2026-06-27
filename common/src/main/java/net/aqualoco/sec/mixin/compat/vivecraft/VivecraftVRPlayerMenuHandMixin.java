package net.aqualoco.sec.mixin.compat.vivecraft;

import net.aqualoco.sec.client.VivecraftSleepWristPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Adds the Vivecraft menu-hand visual for the Seamless wrist panel without touching hotbar state.
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.gameplay.VRPlayer", remap = false)
public abstract class VivecraftVRPlayerMenuHandMixin {

    @Inject(
            method = "preRender",
            at = @At("TAIL"),
            require = 0,
            remap = false
    )
    private void seamlesssleep$showMenuHandForSleepPanelHover(float partialTick, CallbackInfo ci) {
        VivecraftSleepWristPanel.applyHoveredMenuHandToVivecraft();
    }
}
