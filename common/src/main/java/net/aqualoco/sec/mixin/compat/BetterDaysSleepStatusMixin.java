package net.aqualoco.sec.mixin.compat;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.compat.BetterDaysCompat;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.sleep.SleepDimensionSupport;
import net.aqualoco.sec.sleep.SleepRequirement;
import net.aqualoco.sec.sleep.SleepStatusUpdateSuppression;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Better Days replaces ServerLevel.sleepStatus; bridge its overrides back to Seamless managed counts.
@Pseudo
@Mixin(targets = "betterdays.time.SleepStatus", remap = false)
public abstract class BetterDaysSleepStatusMixin {

    @Shadow(remap = false)
    protected int activePlayerCount;

    @Shadow(remap = false)
    protected int sleepingPlayerCount;

    @Unique
    private boolean seamlesssleep$managedBridgeActive;

    @Inject(method = {"update", "method_33814"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void seamlesssleep$countOnlyManagedSleepers(List<ServerPlayer> players, CallbackInfoReturnable<Boolean> cir) {
        this.seamlesssleep$managedBridgeActive = seamlesssleep$shouldBridgeManagedSleepStatus(players);
        if (!this.seamlesssleep$managedBridgeActive) {
            return;
        }

        int previousActivePlayers = this.activePlayerCount;
        int previousSleepingPlayers = this.sleepingPlayerCount;
        seamlesssleep$updateManagedPlayerCounts(players);

        boolean changed = (previousSleepingPlayers > 0 || this.sleepingPlayerCount > 0)
                && (previousActivePlayers != this.activePlayerCount || previousSleepingPlayers != this.sleepingPlayerCount);
        if (SleepStatusUpdateSuppression.isNaturalFinishWakeSuppressed()) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(changed);
    }

    @Inject(method = {"areEnoughSleeping", "method_33812"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void seamlesssleep$areEnoughManagedSleeping(int requiredSleepPercentage, CallbackInfoReturnable<Boolean> cir) {
        if (!this.seamlesssleep$managedBridgeActive || !BetterDaysCompat.shouldDisableBetterDaysSleepFeature()) {
            return;
        }

        int sleepersNeeded = SleepRequirement.sleepersNeeded(this.activePlayerCount, requiredSleepPercentage);
        cir.setReturnValue(this.sleepingPlayerCount >= sleepersNeeded);
    }

    @Inject(method = {"areEnoughDeepSleeping", "method_33813"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void seamlesssleep$areEnoughDeepManagedSleeping(int requiredSleepPercentage,
                                                            List<ServerPlayer> players,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (!seamlesssleep$shouldBridgeManagedSleepStatus(players)) {
            return;
        }

        int deepSleepers = (int) players.stream()
                .filter(player -> BedRestingHelper.hasSleptLongEnough(
                        player,
                        SeamlessSleepServerConfigManager.get().fallAsleepDelayTicks
                ))
                .count();
        int sleepersNeeded = SleepRequirement.sleepersNeeded(this.activePlayerCount, requiredSleepPercentage);
        cir.setReturnValue(deepSleepers >= sleepersNeeded);
    }

    @Unique
    private boolean seamlesssleep$shouldBridgeManagedSleepStatus(List<ServerPlayer> players) {
        return BetterDaysCompat.shouldDisableBetterDaysSleepFeature()
                && SleepDimensionSupport.shouldUseManagedSleepStatus(players);
    }

    @Unique
    private void seamlesssleep$updateManagedPlayerCounts(List<ServerPlayer> players) {
        this.activePlayerCount = 0;
        this.sleepingPlayerCount = 0;

        for (ServerPlayer player : players) {
            if (player.isSpectator()) {
                continue;
            }

            this.activePlayerCount++;
            if (BedRestingHelper.isCountedForSleep(player)) {
                this.sleepingPlayerCount++;
            }
        }
    }
}
