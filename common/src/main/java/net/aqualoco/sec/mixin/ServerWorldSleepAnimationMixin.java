package net.aqualoco.sec.mixin;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

// Replaces instant overworld sleep-skip with a timed transition and synced client animation.
@Mixin(ServerLevel.class)
public abstract class ServerWorldSleepAnimationMixin {

    @Unique
    private boolean seamlesssleep$sleepAnimationWakePlayers;

    @Unique
    private boolean seamlesssleep$sleepAnimationResetWeather;

    @Unique
    private int seamlesssleep$sleepSubtitleTicks;

    @Invoker("wakeUpAllPlayers")
    abstract void seamlesssleep$invokeWakeSleepingPlayers();

    @Invoker("resetWeatherCycle")
    abstract void seamlesssleep$invokeResetWeather();

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

        state.start(currentTime, newTime);
        this.seamlesssleep$sleepAnimationWakePlayers = true;
        this.seamlesssleep$sleepAnimationResetWeather = world.getGameRules()
                .getBoolean(GameRules.RULE_WEATHER_CYCLE)
                && SeamlessSleepServerConfigManager.get().sleepClearsWeather;
        this.seamlesssleep$sleepSubtitleTicks = 0;

        SleepAnimationNetworking.sendStart(world, state);

        Constants.LOG.debug("Iniciando animacao de sono: {} -> {}", currentTime, newTime);
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
                && this.seamlesssleep$sleepAnimationWakePlayers
                && (this.seamlesssleep$sleepAnimationResetWeather
                || !SeamlessSleepServerConfigManager.get().sleepClearsWeather)) {
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

        if (!seamlesssleep$hasEnoughSleeping(self)) {
            state.cancel();
            this.seamlesssleep$sleepAnimationWakePlayers = false;
            this.seamlesssleep$sleepAnimationResetWeather = false;
            SleepAnimationNetworking.sendStop(self);
            Constants.LOG.debug("Animacao de sono cancelada: jogadores dormindo insuficientes.");
            return;
        }

        if (!state.isActive() && this.seamlesssleep$sleepAnimationWakePlayers) {
            this.seamlesssleep$invokeWakeSleepingPlayers();
            if (this.seamlesssleep$sleepAnimationResetWeather) {
                this.seamlesssleep$invokeResetWeather();
            }
            this.seamlesssleep$sleepAnimationWakePlayers = false;
            this.seamlesssleep$sleepAnimationResetWeather = false;

            Constants.LOG.debug("Animacao de sono concluida, jogadores acordados.");
        }
    }

    @Unique
    private boolean seamlesssleep$hasEnoughSleeping(ServerLevel world) {
        int percentage = world.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
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
            if (player.isSleeping()) {
                sleeping++;
            }
        }

        if (total == 0) {
            return false;
        }

        // Match vanilla behavior: at least one player and enough sleepers for the configured percentage.
        int required = Math.max(1, total * percentage / 100);
        return sleeping >= required;
    }
}
