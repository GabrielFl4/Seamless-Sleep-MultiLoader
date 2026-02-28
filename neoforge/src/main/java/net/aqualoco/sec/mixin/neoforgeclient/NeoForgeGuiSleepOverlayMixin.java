package net.aqualoco.sec.mixin.neoforgeclient;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.SleepStatusOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// NeoForge 1.20.1 still ships the ForgeGui class in the Forge package namespace.
@Mixin(ForgeGui.class)
public abstract class NeoForgeGuiSleepOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void seamlesssleep$renderSleepOverlay(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        SleepStatusOverlay.render(graphics, SeamlessSleepClientState.SLEEP_ANIMATION);
    }
}
