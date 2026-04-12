package net.aqualoco.sec.mixin.sleep;

import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
        if (self.level().dimension().equals(Level.OVERWORLD)) {
            if (SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION.isActive()) {
                return;
            }

            if (BedRestingHelper.isManagedBedStateServer(self)
                    && !BedRestingHelper.isCountedForSleep(self)) {
                return;
            }
        }

        self.stopSleepInBed(skipSleepTimer, updateSleepingPlayers);
    }
}
