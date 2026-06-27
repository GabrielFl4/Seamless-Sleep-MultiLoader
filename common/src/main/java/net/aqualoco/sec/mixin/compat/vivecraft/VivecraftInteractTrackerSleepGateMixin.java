package net.aqualoco.sec.mixin.compat.vivecraft;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.VivecraftClientCompat;
import net.aqualoco.sec.client.VivecraftSleepWristPanel;
import net.aqualoco.sec.compat.VivecraftInteractModuleBridge;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Vivecraft disables all InteractModules while vanilla thinks the player is sleeping.
// Seamless keeps the native pipeline alive for the Vivecraft hotbar and the managed-bed wrist panel.
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.gameplay.trackers.InteractTracker", remap = false)
public abstract class VivecraftInteractTrackerSleepGateMixin {
    @Redirect(
            method = "isActive",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isSleeping()Z",
                    remap = true
            ),
            require = 0,
            remap = false
    )
    private boolean seamlesssleep$allowInteractModulesDuringManagedBed(LocalPlayer player) {
        return player.isSleeping()
                && !(VivecraftClientCompat.shouldUseVrBedPolicy(player)
                && ClientBedWorkflow.isManagedBedState(player));
    }

    @Redirect(
            method = "activeProcess",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/vivecraft/api/client/InteractModule;isActive(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Z",
                    remap = true
            ),
            require = 0,
            remap = false
    )
    private boolean seamlesssleep$limitManagedBedInteractModules(@Coerce Object module,
                                                                  LocalPlayer player,
                                                                  InteractionHand hand,
                                                                  Vec3 handPosition) {
        if (VivecraftClientCompat.shouldUseVrBedPolicy(player)
                && ClientBedWorkflow.isManagedBedState(player)
                && !VivecraftSleepWristPanel.shouldAllowManagedBedInteractModule(module)) {
            return false;
        }

        return VivecraftInteractModuleBridge.isActive(module, player, hand, handPosition);
    }
}
