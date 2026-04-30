package net.aqualoco.sec.mixin.sleep;

import net.aqualoco.sec.acceleration.WorldSleepAccelerationManager;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.BedHudNetworking;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.SleepAnimationMode;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.aqualoco.sec.sleep.SleepAnimationStopReason;
import net.aqualoco.sec.sleep.SleepStatusUpdateSuppression;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

// Swaps instant overworld sleep skip for a timed transition.
@Mixin(ServerLevel.class)
public abstract class ServerWorldSleepAnimationMixin {

    @Unique
    private boolean seamlesssleep$sleepAnimationWakePlayers;

    @Unique
    private boolean seamlesssleep$sleepAnimationResetWeather;

    @Invoker("wakeUpAllPlayers")
    abstract void seamlesssleep$invokeWakeSleepingPlayers();

    @Invoker("resetWeatherCycle")
    abstract void seamlesssleep$invokeResetWeather();

    @Inject(method = "tick", at = @At("HEAD"))
    private void seamlesssleep$prepareWorldAcceleration(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (!self.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        WorldSleepAccelerationManager.prepareForLevelTick(self);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"
            )
    )
    private void seamlesssleep$redirectSetTimeOfDay(ServerLevel world, long newTime) {
        if (!world.dimension().equals(Level.OVERWORLD)) {
            world.setDayTime(newTime);
            return;
        }

        SleepAnimationState state = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        if (state.isActive()) {
            return;
        }

        long currentTime = world.getDayTime();
        if (newTime <= currentTime) {
            world.setDayTime(newTime);
            return;
        }

        if (!state.start(world, currentTime, newTime, SleepAnimationMode.NORMAL_SLEEP)) {
            return;
        }
        this.seamlesssleep$sleepAnimationWakePlayers = true;
        int weatherChancePercent = SeamlessSleepServerConfigManager.get().sleepWeatherClearChancePercent;
        this.seamlesssleep$sleepAnimationResetWeather = seamlesssleep$rollWeatherClearChance(world, weatherChancePercent);
        WorldSleepAccelerationManager.refreshForLevelTick(world);

        SleepAnimationNetworking.sendStart(world, state);

        Constants.debug("Starting sleep animation on server: {} -> {}", currentTime, newTime);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;wakeUpAllPlayers()V"
            )
    )
    private void seamlesssleep$redirectWakeSleepingPlayers(ServerLevel world) {
        if (world.dimension().equals(Level.OVERWORLD)
                && this.seamlesssleep$sleepAnimationWakePlayers) {
            return;
        }

        this.seamlesssleep$invokeWakeSleepingPlayers();
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;resetWeatherCycle()V"
            )
    )
    private void seamlesssleep$redirectResetWeather(ServerLevel world) {
        if (world.dimension().equals(Level.OVERWORLD)
                && this.seamlesssleep$sleepAnimationWakePlayers) {
            return;
        }

        this.seamlesssleep$invokeResetWeather();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void seamlesssleep$tickSleepAnimation(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;

        if (!self.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        SleepAnimationState state = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        if (!state.isActive()) {
            return;
        }

        state.tick(self);

        if (!state.isActive() && this.seamlesssleep$sleepAnimationWakePlayers) {
            this.seamlesssleep$finishSleepAnimation();
            WorldSleepAccelerationManager.refreshForLevelTick(self);
            SleepAnimationNetworking.sendFinish(self, state);
            return;
        }

        if (!state.isActive()) {
            return;
        }

        if (!((LevelSleepBrightnessAccessor) self).seamlesssleep$invokeIsBrightOutside()
                && !seamlesssleep$hasEnoughSleeping(self)) {
            state.cancel();
            this.seamlesssleep$sleepAnimationWakePlayers = false;
            this.seamlesssleep$sleepAnimationResetWeather = false;
            WorldSleepAccelerationManager.refreshForLevelTick(self);
            SleepAnimationNetworking.sendStop(self, state, SleepAnimationStopReason.CANCELLED_NOT_ENOUGH_SLEEPERS);
            Constants.debug("Sleep animation canceled: not enough players sleeping.");
        }
    }

    @Unique
    private void seamlesssleep$finishSleepAnimation() {
        SleepStatusUpdateSuppression.beginNaturalFinishWake();
        try {
            this.seamlesssleep$invokeWakeSleepingPlayers();
        } finally {
            SleepStatusUpdateSuppression.endNaturalFinishWake();
        }
        if (this.seamlesssleep$sleepAnimationResetWeather) {
            this.seamlesssleep$invokeResetWeather();
        }
        this.seamlesssleep$sleepAnimationWakePlayers = false;
        this.seamlesssleep$sleepAnimationResetWeather = false;
        Constants.debug("Sleep animation finished. Woke up sleeping players.");
    }

    @Unique
    private boolean seamlesssleep$rollWeatherClearChance(ServerLevel world, int chancePercent) {
        if (!world.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
            return false;
        }
        if (chancePercent <= 0) {
            return false;
        }
        if (chancePercent >= 100) {
            return true;
        }
        return world.getRandom().nextInt(100) < chancePercent;
    }

    @Unique
    private boolean seamlesssleep$hasEnoughSleeping(ServerLevel world) {
        int percentage = world.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (percentage <= 0) {
            return false;
        }

        int total = 0;
        int sleeping = 0;
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) {
                continue;
            }
            total++;
            if (BedRestingHelper.isCountedForSleep(player)) {
                sleeping++;
            }
        }

        if (total == 0) {
            return false;
        }

        // Keep vanilla-style threshold: at least one player, then percentage.
        int required = Math.max(1, total * percentage / 100);
        return sleeping >= required;
    }

    @Inject(method = "updateSleepingPlayerList", at = @At("TAIL"))
    private void seamlesssleep$syncBedHudSleepProgress(CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (!self.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if (SleepStatusUpdateSuppression.isNaturalFinishWakeSuppressed()) {
            return;
        }
        BedHudNetworking.syncSleepProgress(self);
    }
}
