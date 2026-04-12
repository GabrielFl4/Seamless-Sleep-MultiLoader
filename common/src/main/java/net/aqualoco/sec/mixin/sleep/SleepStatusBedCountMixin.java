package net.aqualoco.sec.mixin.sleep;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.util.Mth;
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

        cir.setReturnValue(
                (previousSleepingPlayers > 0 || this.sleepingPlayers > 0)
                        && (previousActivePlayers != this.activePlayers || previousSleepingPlayers != this.sleepingPlayers)
        );
    }

    @Inject(method = "areEnoughDeepSleeping", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$countOnlyDeepManagedSleepers(int requiredSleepPercentage,
                                                            List<ServerPlayer> players,
                                                            CallbackInfoReturnable<Boolean> cir) {
        int deepSleepers = (int) players.stream()
                .filter(player -> BedRestingHelper.isCountedForSleep(player) && player.isSleepingLongEnough())
                .count();
        int sleepersNeeded = Math.max(1, Mth.ceil(this.activePlayers * requiredSleepPercentage / 100.0F));
        cir.setReturnValue(deepSleepers >= sleepersNeeded);
    }
}
