package net.aqualoco.sec.mixin;

import net.aqualoco.sec.AquaSec;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldSleepAnimationMixin {

    @Unique
    private boolean aquasec$sleepAnimationWakePlayers;

    @Unique
    private boolean aquasec$sleepAnimationResetWeather;

    @Unique
    private int aquasec$sleepSubtitleTicks;

    @Invoker("wakeSleepingPlayers")
    abstract void aquasec$invokeWakeSleepingPlayers();

    @Invoker("resetWeather")
    abstract void aquasec$invokeResetWeather();

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;setTimeOfDay(J)V"
            )
    )
    private void aquasec$redirectSetTimeOfDay(ServerWorld world, long newTime) {
        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            world.setTimeOfDay(newTime);
            return;
        }

        SleepAnimationState state = AquaSec.OVERWORLD_SLEEP_ANIMATION;
        if (state.isActive()) {
            return;
        }

        long currentTime = world.getTimeOfDay();
        if (newTime <= currentTime) {
            world.setTimeOfDay(newTime);
            return;
        }

        state.start(currentTime, newTime);
        this.aquasec$sleepAnimationWakePlayers = true;
        this.aquasec$sleepAnimationResetWeather =
                world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE);
        this.aquasec$sleepSubtitleTicks = 0;

        SleepAnimationNetworking.sendStart(world, state);

        AquaSec.LOGGER.debug("Iniciando animacao de sono: {} -> {}", currentTime, newTime);
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;wakeSleepingPlayers()V"
            )
    )
    private void aquasec$redirectWakeSleepingPlayers(ServerWorld world) {
        if (world.getRegistryKey().equals(World.OVERWORLD)
                && this.aquasec$sleepAnimationWakePlayers) {
            return;
        }

        this.aquasec$invokeWakeSleepingPlayers();
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;resetWeather()V"
            )
    )
    private void aquasec$redirectResetWeather(ServerWorld world) {
        if (world.getRegistryKey().equals(World.OVERWORLD)
                && this.aquasec$sleepAnimationWakePlayers
                && this.aquasec$sleepAnimationResetWeather) {
            return;
        }

        this.aquasec$invokeResetWeather();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aquasec$tickSleepAnimation(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerWorld self = (ServerWorld) (Object) this;

        if (!self.getRegistryKey().equals(World.OVERWORLD)) {
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
    private boolean aquasec$hasEnoughSleeping(ServerWorld world) {
        int percentage = world.getGameRules().getInt(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        if (percentage <= 0) {
            return false;
        }

        int total = 0;
        int sleeping = 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
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
