package net.aqualoco.sec.mixin;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

// Swaps instant overworld sleep skip for a timed transition.
@Mixin(ServerLevel.class)
public abstract class ServerWorldSleepAnimationMixin {

    @Unique
    private static final long seamlesssleep$OVERWORLD_CLOCK_PERIOD = 24000L;

    @Unique
    private boolean seamlesssleep$sleepAnimationWakePlayers;

    @Unique
    private boolean seamlesssleep$sleepAnimationResetWeather;

    @Invoker("wakeUpAllPlayers")
    abstract void seamlesssleep$invokeWakeSleepingPlayers();

    @Redirect(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Z"
            ),
            require = 0
    )
    private boolean seamlesssleep$redirectMoveToTimeMarker(ServerClockManager clockManager,
                                                           Holder<WorldClock> clock,
                                                           ResourceKey<ClockTimeMarker> markerId) {
        ServerLevel world = (ServerLevel) (Object) this;

        if (!this.seamlesssleep$tryStartSleepAnimation(world, clockManager, clock, markerId)) {
            return clockManager.moveToTimeMarker(clock, markerId);
        }
        return true;
    }

    @Redirect(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/util/ClockAdjustment;apply(Lnet/minecraft/world/clock/ServerClockManager;Lnet/minecraft/core/Holder;)V"
            ),
            require = 0
    )
    private void seamlesssleep$redirectNeoForgeSleepAdjustment(@Coerce Object adjustment,
                                                               ServerClockManager clockManager,
                                                               Holder<WorldClock> clock) {
        ServerLevel world = (ServerLevel) (Object) this;
        ResourceKey<ClockTimeMarker> markerId = seamlesssleep$extractNeoForgeMarkerId(adjustment);
        if (markerId != null && this.seamlesssleep$tryStartSleepAnimation(world, clockManager, clock, markerId)) {
            return;
        }
        this.seamlesssleep$invokeClockAdjustmentApply(adjustment, clockManager, clock);
    }

    @Unique
    private boolean seamlesssleep$tryStartSleepAnimation(ServerLevel world,
                                                         ServerClockManager clockManager,
                                                         Holder<WorldClock> clock,
                                                         ResourceKey<ClockTimeMarker> markerId) {
        if (!world.dimension().equals(Level.OVERWORLD)
                || !markerId.equals(ClockTimeMarkers.WAKE_UP_FROM_SLEEP)) {
            return false;
        }

        long currentTime = clockManager.getTotalTicks(clock);
        long newTime = seamlesssleep$resolveWakeUpTargetTime(currentTime);
        return this.seamlesssleep$tryStartSleepAnimation(world, clockManager, clock, newTime);
    }

    @Redirect(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/event/ForgeEventFactory;onSleepFinished(Lnet/minecraft/server/level/ServerLevel;JJ)J"
            ),
            require = 0
    )
    private long seamlesssleep$redirectForgeSleepFinished(ServerLevel world,
                                                          long targetTime,
                                                          long currentTime) {
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return this.seamlesssleep$invokeForgeOnSleepFinished(world, targetTime, currentTime);
        }

        if (SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION.isActive()
                || this.seamlesssleep$hasPendingSleepAnimationSession()) {
            return currentTime;
        }

        return this.seamlesssleep$invokeForgeOnSleepFinished(world, targetTime, currentTime);
    }

    @Redirect(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/clock/ServerClockManager;setTotalTicks(Lnet/minecraft/core/Holder;J)V"
            ),
            require = 0
    )
    private void seamlesssleep$redirectForgeSleepSetTotalTicks(ServerClockManager clockManager,
                                                               Holder<WorldClock> clock,
                                                               long targetTime) {
        ServerLevel world = (ServerLevel) (Object) this;

        if (!this.seamlesssleep$tryStartSleepAnimation(world, clockManager, clock, targetTime)) {
            clockManager.setTotalTicks(clock, targetTime);
        }
    }

    @Unique
    private boolean seamlesssleep$tryStartSleepAnimation(ServerLevel world,
                                                         ServerClockManager clockManager,
                                                         Holder<WorldClock> clock,
                                                         long targetTime) {
        SleepAnimationState state = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return false;
        }
        if (state.isActive()) {
            return true;
        }

        long currentTime = clockManager.getTotalTicks(clock);
        if (targetTime <= currentTime) {
            return false;
        }

        state.start(currentTime, targetTime);
        this.seamlesssleep$sleepAnimationWakePlayers = true;
        int weatherChancePercent = SeamlessSleepServerConfigManager.get().sleepWeatherClearChancePercent;
        this.seamlesssleep$sleepAnimationResetWeather = seamlesssleep$rollWeatherClearChance(world, weatherChancePercent);

        SleepAnimationNetworking.sendStart(world, state);

        Constants.debug("Starting sleep animation on server: {} -> {}", currentTime, targetTime);
        return true;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static ResourceKey<ClockTimeMarker> seamlesssleep$extractNeoForgeMarkerId(Object adjustment) {
        if (adjustment == null) {
            return null;
        }
        if (!adjustment.getClass().getName().equals("net.neoforged.neoforge.common.util.ClockAdjustment$Marker")) {
            return null;
        }

        try {
            Method markerAccessor = adjustment.getClass().getMethod("marker");
            Object marker = markerAccessor.invoke(adjustment);
            if (marker instanceof ResourceKey<?> resourceKey) {
                return (ResourceKey<ClockTimeMarker>) resourceKey;
            }
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to inspect NeoForge ClockAdjustment marker", exception);
        }

        return null;
    }

    @Unique
    private void seamlesssleep$invokeClockAdjustmentApply(Object adjustment,
                                                          ServerClockManager clockManager,
                                                          Holder<WorldClock> clock) {
        try {
            Method apply = adjustment.getClass().getMethod("apply", ServerClockManager.class, Holder.class);
            apply.invoke(adjustment, clockManager, clock);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to invoke NeoForge ClockAdjustment.apply", exception);
        }
    }

    @Unique
    private long seamlesssleep$invokeForgeOnSleepFinished(ServerLevel world,
                                                          long targetTime,
                                                          long currentTime) {
        try {
            Class<?> eventFactoryClass = Class.forName("net.minecraftforge.event.ForgeEventFactory");
            Method onSleepFinished = eventFactoryClass.getMethod(
                    "onSleepFinished",
                    ServerLevel.class,
                    long.class,
                    long.class
            );
            return ((Long) onSleepFinished.invoke(null, world, targetTime, currentTime)).longValue();
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to invoke ForgeEventFactory.onSleepFinished", exception);
        }
    }

    @Redirect(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
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
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
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

        world.resetWeatherCycle();
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void seamlesssleep$tickSleepAnimation(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;

        if (!self.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        SleepAnimationState state = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
        if (!state.isActive()) {
            if (this.seamlesssleep$hasPendingSleepAnimationSession()) {
                this.seamlesssleep$abortSleepAnimation(self, state, "state_inactive_before_server_tick", false);
            }
            return;
        }

        Holder<WorldClock> clock = self.dimensionType().defaultClock().orElse(null);
        if (clock == null) {
            this.seamlesssleep$abortSleepAnimation(self, state, "overworld_default_clock_unavailable", true);
            return;
        }

        state.tick(self, clock);

        if (!state.isActive()) {
            if (this.seamlesssleep$sleepAnimationWakePlayers) {
                this.seamlesssleep$finishSleepAnimation(self);
            } else if (this.seamlesssleep$hasPendingSleepAnimationSession()) {
                this.seamlesssleep$abortSleepAnimation(self, state, "state_inactive_after_server_tick", false);
            }
            return;
        }

        if (!self.isBrightOutside()
                && !seamlesssleep$hasEnoughSleeping(self)) {
            this.seamlesssleep$abortSleepAnimation(self, state, "not_enough_players_sleeping", false);
        }
    }

    @Unique
    private void seamlesssleep$finishSleepAnimation(ServerLevel world) {
        this.seamlesssleep$invokeWakeSleepingPlayers();
        if (this.seamlesssleep$sleepAnimationResetWeather) {
            world.resetWeatherCycle();
        }
        SleepAnimationNetworking.sendStop(world);
        this.seamlesssleep$clearSleepAnimationFlags();
        Constants.debug("Sleep animation finished. Woke up sleeping players.");
    }

    @Unique
    private void seamlesssleep$abortSleepAnimation(ServerLevel world,
                                                   SleepAnimationState state,
                                                   String reason,
                                                   boolean warn) {
        boolean shouldNotifyClients = state.isActive() || this.seamlesssleep$hasPendingSleepAnimationSession();
        state.cancel();
        this.seamlesssleep$clearSleepAnimationFlags();

        if (shouldNotifyClients) {
            SleepAnimationNetworking.sendStop(world);
        }

        if (warn) {
            Constants.warn("Sleep animation aborted: {}", reason);
        } else {
            Constants.debug("Sleep animation aborted: {}", reason);
        }
    }

    @Unique
    private boolean seamlesssleep$hasPendingSleepAnimationSession() {
        return this.seamlesssleep$sleepAnimationWakePlayers
                || this.seamlesssleep$sleepAnimationResetWeather;
    }

    @Unique
    private void seamlesssleep$clearSleepAnimationFlags() {
        this.seamlesssleep$sleepAnimationWakePlayers = false;
        this.seamlesssleep$sleepAnimationResetWeather = false;
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
            if (player.isSleeping()) {
                sleeping++;
            }
        }

        if (total == 0) {
            return false;
        }

        // Match 26.1 SleepStatus.sleepersNeeded(): at least one player, then ceil(active * pct / 100).
        int required = Math.max(1, Mth.ceil(total * percentage / 100.0F));
        return sleeping >= required;
    }

    @Unique
    private static long seamlesssleep$resolveWakeUpTargetTime(long currentTime) {
        long currentCycleTicks = Math.floorMod(currentTime, seamlesssleep$OVERWORLD_CLOCK_PERIOD);
        long delta = currentCycleTicks == 0L
                ? seamlesssleep$OVERWORLD_CLOCK_PERIOD
                : seamlesssleep$OVERWORLD_CLOCK_PERIOD - currentCycleTicks;
        return currentTime + delta;
    }
}
