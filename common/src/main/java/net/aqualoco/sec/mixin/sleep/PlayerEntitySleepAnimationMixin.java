package net.aqualoco.sec.mixin.sleep;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.sleep.SleepAnimationStates;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Keeps the vanilla wake-up call path intact while this mod controls timing elsewhere.
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
        if (BedRestingHelper.isManagedBedStateServer(self)) {
            if (self.level() instanceof ServerLevel serverLevel
                    && SleepAnimationStates.getOrCreate(serverLevel).isActive()) {
                return;
            }

            if (!BedRestingHelper.isCountedForSleep(self)) {
                return;
            }

            if (SeamlessSleepServerConfigManager.get().sleepEligibility.allowsDaySleep()) {
                return;
            }
        }

        self.stopSleepInBed(skipSleepTimer, updateSleepingPlayers);
    }
}
