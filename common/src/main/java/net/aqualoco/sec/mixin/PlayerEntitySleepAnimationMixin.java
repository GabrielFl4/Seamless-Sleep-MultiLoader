package net.aqualoco.sec.mixin;

import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Prevents vanilla daybreak auto-wake while the server-side sleep animation is still active.
@Mixin(Player.class)
public abstract class PlayerEntitySleepAnimationMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;stopSleepInBed(ZZ)V"
            )
    )
    private void seamlesssleep$forwardWakeUp(Player self,
                                             boolean skipSleepTimer,
                                             boolean updateSleepingPlayers) {
        if (!self.level().dimension().equals(Level.OVERWORLD)) {
            self.stopSleepInBed(skipSleepTimer, updateSleepingPlayers);
            return;
        }

        SleepAnimationState state = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        if (state.isActive()) {
            return;
        }

        self.stopSleepInBed(skipSleepTimer, updateSleepingPlayers);
    }
}
