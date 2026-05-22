package net.aqualoco.sec.mixin.sleep;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.sleep.SleepDimensionSupport;
import net.aqualoco.sec.sleep.SleepRequirement;
import net.aqualoco.sec.sleep.SleepStatusUpdateSuppression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Replaces vanilla "any isSleeping()" counting with the managed counted-for-sleep state.
@Mixin(SleepStatus.class)
public abstract class SleepStatusBedCountMixin {

    @Shadow
    private int activePlayers;

    @Shadow
    private int sleepingPlayers;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$countOnlyManagedSleepers(List<ServerPlayer> players, CallbackInfoReturnable<Boolean> cir) {
        if (!SleepDimensionSupport.shouldUseManagedSleepStatus(players)) {
            return;
        }

        int previousActivePlayers = this.activePlayers;
        int previousSleepingPlayers = this.sleepingPlayers;
        this.activePlayers = 0;
        this.sleepingPlayers = 0;

        for (ServerPlayer player : players) {
            if (player.isSpectator()) {
                continue;
            }

            this.activePlayers++;
            if (BedRestingHelper.isCountedForSleep(player)) {
                this.sleepingPlayers++;
            }
        }

        boolean changed = (previousSleepingPlayers > 0 || this.sleepingPlayers > 0)
                && (previousActivePlayers != this.activePlayers || previousSleepingPlayers != this.sleepingPlayers);
        if (SleepStatusUpdateSuppression.isNaturalFinishWakeSuppressed()) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(changed);
    }

    @Inject(method = "areEnoughDeepSleeping", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$countOnlyDeepManagedSleepers(int requiredSleepPercentage,
                                                            List<ServerPlayer> players,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (!SleepDimensionSupport.shouldUseManagedSleepStatus(players)) {
            return;
        }

        int deepSleepers = (int) players.stream()
                .filter(player -> BedRestingHelper.hasSleptLongEnough(
                        player,
                        SeamlessSleepServerConfigManager.get().fallAsleepDelayTicks
                ))
                .count();
        int sleepersNeeded = SleepRequirement.sleepersNeeded(this.activePlayers, requiredSleepPercentage);
        cir.setReturnValue(deepSleepers >= sleepersNeeded);
    }
}
