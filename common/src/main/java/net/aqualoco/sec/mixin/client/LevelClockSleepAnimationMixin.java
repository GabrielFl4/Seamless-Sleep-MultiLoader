package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Overrides client clock reads while the custom sleep interpolation is active.
@Mixin(Level.class)
public abstract class LevelClockSleepAnimationMixin {

    @Inject(method = "getDefaultClockTime()J", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$overrideDefaultClockTime(CallbackInfoReturnable<Long> cir) {
        Long override = seamlesssleep$getClientSleepClockOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Inject(method = "getOverworldClockTime()J", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$overrideOverworldClockTime(CallbackInfoReturnable<Long> cir) {
        Long override = seamlesssleep$getClientSleepClockOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Unique
    private Long seamlesssleep$getClientSleepClockOverride() {
        Level self = (Level) (Object) this;
        if (!(self instanceof ClientLevel)) {
            return null;
        }

        if (!self.dimension().equals(Level.OVERWORLD)) {
            return null;
        }

        ClientSleepAnimationState state = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (!state.isActiveForWorld(self.dimension().identifier())) {
            return null;
        }

        return state.getInterpolatedTime();
    }
}
