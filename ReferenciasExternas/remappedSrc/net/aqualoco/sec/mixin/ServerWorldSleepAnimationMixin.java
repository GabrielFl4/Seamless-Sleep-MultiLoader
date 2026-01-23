package net.aqualoco.sec.mixin;

import net.aqualoco.sec.AquaSec;
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

@Mixin(ServerLevel.class)
public abstract class ServerWorldSleepAnimationMixin {

    @Unique
    private boolean aquasec$sleepAnimationWakePlayers;

    @Unique
    private boolean aquasec$sleepAnimationResetWeather;

    @Unique
    private int aquasec$sleepSubtitleTicks;

    @Invoker("wakeUpAllPlayers")
    abstract void aquasec$invokeWakeSleepingPlayers();

    @Invoker("resetWeatherCycle")
    abstract void aquasec$invokeResetWeather();

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"
            )
    )
    private void aquasec$redirectSetTimeOfDay(ServerLevel world, long newTime) {
        if (!world.dimension().equals(Level.OVERWORLD)) {
            world.setDayTime(newTime);
            return;
        }

        SleepAnimationState state = AquaSec.OVERWORLD_SLEEP_ANIMATION;
        if (state.isActive()) {
            return;
        }

        long currentTime = world.getDayTime();
        if (newTime <= currentTime) {
            world.setDayTime(newTime);
            return;
        }

        state.start(currentTime, newTime);
        this.aquasec$sleepAnimationWakePlayers = true;
        this.aquasec$sleepAnimationResetWeather =
                world.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        this.aquasec$sleepSubtitleTicks = 0;

        SleepAnimationNetworking.sendStart(world, state);

        AquaSec.LOGGER.debug("Iniciando animacao de sono: {} -> {}", currentTime, newTime);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;wakeUpAllPlayers()V"
            )
    )
    private void aquasec$redirectWakeSleepingPlayers(ServerLevel world) {
        if (world.dimension().equals(Level.OVERWORLD)
                && this.aquasec$sleepAnimationWakePlayers) {
            return;
        }

        this.aquasec$invokeWakeSleepingPlayers();
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;resetWeatherCycle()V"
            )
    )
    private void aquasec$redirectResetWeather(ServerLevel world) {
        if (world.dimension().equals(Level.OVERWORLD)
                && this.aquasec$sleepAnimationWakePlayers
                && this.aquasec$sleepAnimationResetWeather) {
            return;
        }

        this.aquasec$invokeResetWeather();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aquasec$tickSleepAnimation(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;

        if (!self.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        SleepAnimationState state = AquaSec.OVERWORLD_SLEEP_ANIMATION;
        if (!state.isActive()) {
            return;
        }

        state.tick(self);

        if (!aquasec$hasEnoughSleeping(self)) {
            state.cancel();
            this.aquasec$sleepAnimationWakePlayers = false;
            this.aquasec$sleepAnimationResetWeather = false;
            SleepAnimationNetworking.sendStop(self);
            AquaSec.LOGGER.debug("Animacao de sono cancelada: jogadores dormindo insuficientes.");
            return;
        }

        if (!state.isActive() && this.aquasec$sleepAnimationWakePlayers) {
            this.aquasec$invokeWakeSleepingPlayers();
            if (this.aquasec$sleepAnimationResetWeather) {
                this.aquasec$invokeResetWeather();
            }
            this.aquasec$sleepAnimationWakePlayers = false;
            this.aquasec$sleepAnimationResetWeather = false;

            AquaSec.LOGGER.debug("Animacao de sono concluida, jogadores acordados.");
        }
    }

    @Unique
    private boolean aquasec$hasEnoughSleeping(ServerLevel world) {
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

        int required = Math.max(1, total * percentage / 100);
        return sleeping >= required;
    }
}
