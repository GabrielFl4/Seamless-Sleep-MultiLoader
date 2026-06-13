package net.aqualoco.sec.mixin.compat.vivecraft;

import net.aqualoco.sec.client.VivecraftClientCompat;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Adds Seamless' VR sleep offset to the same room-origin Y path Vivecraft uses for crawl/swim poses.
@Mixin(value = LocalPlayer.class, priority = 800)
public abstract class VivecraftLocalPlayerRoomYOffsetMixin {

    @Inject(
            method = "vivecraft$getRoomYOffsetFromPose()D",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void seamlesssleep$addManagedBedRoomYOffset(CallbackInfoReturnable<Double> cir) {
        if (VivecraftClientCompat.shouldApplyVrBedRoomYOffset()) {
            cir.setReturnValue(cir.getReturnValueD() + VivecraftClientCompat.vrBedRoomYOffset());
        }
    }
}
