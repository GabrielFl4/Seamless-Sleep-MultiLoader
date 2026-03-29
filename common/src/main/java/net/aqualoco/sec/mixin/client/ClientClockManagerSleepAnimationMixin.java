package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.minecraft.client.ClientClockManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Makes client timeline/environment clock reads follow the sleep interpolation while active.
@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerSleepAnimationMixin {

    @Inject(method = "getTotalTicks(Lnet/minecraft/core/Holder;)J", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$overrideOverworldClockTicks(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
        if (!definition.is(WorldClocks.OVERWORLD)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null || !world.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        ClientSleepAnimationState state = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (!state.isActiveForWorld(world.dimension().identifier())) {
            return;
        }

        cir.setReturnValue(state.getInterpolatedTime());
    }
}
