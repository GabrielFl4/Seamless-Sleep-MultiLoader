package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerBedWorkflowMixin {

    @Shadow public ClientInput input;

    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/ClientInput;tick()V",
                    shift = At.Shift.AFTER
            )
    )
    private void seamlesssleep$zeroMovementInputsWhileBedBound(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        ClientBedWorkflow.tick(self);

        if (!ClientBedWorkflow.shouldBlockGameplayInteractions(self)) {
            return;
        }

        ClientInputAccessor accessor = (ClientInputAccessor) this.input;
        boolean shift = accessor.seamlesssleep$getKeyPresses().shift();
        accessor.seamlesssleep$setKeyPresses(new Input(false, false, false, false, false, shift, false));
        accessor.seamlesssleep$setMoveVector(Vec2.ZERO);
    }
}
