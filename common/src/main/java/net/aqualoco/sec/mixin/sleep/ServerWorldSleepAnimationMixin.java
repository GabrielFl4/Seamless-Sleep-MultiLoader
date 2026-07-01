package net.aqualoco.sec.mixin.sleep;

import net.aqualoco.sec.acceleration.WorldSleepAccelerationManager;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.compat.FlashbackReplayServerCompat;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.SleepEligibilityMode;
import net.aqualoco.sec.network.BedHudNetworking;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.SleepAnimationStates;
import net.aqualoco.sec.sleep.SleepDimensionSupport;
import net.aqualoco.sec.sleep.SleepAnimationMode;
import net.aqualoco.sec.sleep.SleepAnimationPhase;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.aqualoco.sec.sleep.SleepAnimationStopReason;
import net.aqualoco.sec.sleep.SleepStatusUpdateSuppression;
import net.aqualoco.sec.sleep.SleepAnimationVisualContext;
import net.aqualoco.sec.sleep.SleepRequirement;
import net.aqualoco.sec.sleep.SleepWeatherHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

// Swaps instant managed sleep skip for a timed transition.
@Mixin(ServerLevel.class)
public abstract class ServerWorldSleepAnimationMixin {
    @Unique
    private static final long seamlesssleep$DAY_TICKS = 24000L;
    @Unique
    private static final long seamlesssleep$NIGHT_START_TICKS = 12542L;
    @Unique
    private static final long seamlesssleep$NIGHT_END_TICKS = 23460L;

    @Unique
    private boolean seamlesssleep$sleepAnimationWakePlayers;

    @Unique
    private boolean seamlesssleep$sleepAnimationResetWeather;

    @Unique
    private boolean seamlesssleep$madeInHeavenRollLocked;

    @Invoker("wakeUpAllPlayers")
    abstract void seamlesssleep$invokeWakeSleepingPlayers();

    @Invoker("resetWeatherCycle")
    abstract void seamlesssleep$invokeResetWeather();

    @Inject(method = "tick", at = @At("HEAD"))
    private void seamlesssleep$prepareWorldAcceleration(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (!SleepDimensionSupport.supportsWorldAcceleration(self)) {
            return;
        }
        if (seamlesssleep$isFlashbackReplayServer(self)) {
            seamlesssleep$clearReplayServerSleepState(self);
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
        if (seamlesssleep$isFlashbackReplayServer(world)) {
            seamlesssleep$clearReplayServerSleepState(world);
            world.setDayTime(newTime);
            return;
        }

        SleepAnimationState existingState = SleepAnimationStates.getIfPresent(world);
        if (!SleepDimensionSupport.supportsSleepAnimation(world)
                && (existingState == null || !existingState.isActive())) {
            world.setDayTime(newTime);
            return;
        }

        SleepAnimationState state = existingState == null ? SleepAnimationStates.getOrCreate(world) : existingState;
        if (state.isActive()) {
            return;
        }

        long currentTime = world.getDayTime();
        if (newTime <= currentTime) {
            world.setDayTime(newTime);
            return;
        }

        if (!seamlesssleep$startBedSleepAnimation(world, state, currentTime, newTime)) {
            return;
        }

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
        SleepAnimationState state = SleepAnimationStates.getIfPresent(world);
        if (seamlesssleep$shouldSuppressVanillaSleepCompletion(world, state)) {
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
        SleepAnimationState state = SleepAnimationStates.getIfPresent(world);
        if (seamlesssleep$shouldSuppressVanillaSleepCompletion(world, state)) {
            return;
        }

        this.seamlesssleep$invokeResetWeather();
    }

    @Unique
    private boolean seamlesssleep$shouldSuppressVanillaSleepCompletion(ServerLevel world, SleepAnimationState state) {
        if (!SleepDimensionSupport.supportsSleepAnimation(world)
                && (state == null || !state.isActive())) {
            return false;
        }
        return this.seamlesssleep$sleepAnimationWakePlayers
                || (state != null && state.isActive() && state.getMode() == SleepAnimationMode.COMMAND_TIMELAPSE);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void seamlesssleep$tickSleepAnimation(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;

        if (seamlesssleep$isFlashbackReplayServer(self)) {
            seamlesssleep$clearReplayServerSleepState(self);
            return;
        }

        SleepAnimationState existingState = SleepAnimationStates.getIfPresent(self);
        if (!SleepDimensionSupport.supportsSleepAnimation(self)
                && (existingState == null || !existingState.isActive())) {
            return;
        }

        SleepAnimationState state = existingState == null ? SleepAnimationStates.getOrCreate(self) : existingState;
        if (!state.isActive()) {
            seamlesssleep$tryStartCustomBedSleep(self, state);
            if (!state.isActive()) {
                seamlesssleep$tryStartMadeInHeavenBedSleep(self, state);
            }
            if (!state.isActive()) {
                return;
            }
        }

        state.tick(self);

        if (state.consumeCancelTerminalEvent()) {
            this.seamlesssleep$sleepAnimationWakePlayers = false;
            this.seamlesssleep$sleepAnimationResetWeather = false;
            WorldSleepAccelerationManager.refreshForLevelTick(self);
            SleepAnimationNetworking.sendStop(
                    self,
                    state,
                    SleepAnimationStopReason.CANCELLED_NOT_ENOUGH_SLEEPERS
            );
            Constants.debug("Normal sleep cancel braking finished.");
            return;
        }

        if (state.consumeVisualFinishEvent()) {
            WorldSleepAccelerationManager.refreshForLevelTick(self);
            SleepAnimationNetworking.sendFinish(self, state);
        }

        if (!state.isActive() && state.isFinishedNaturally()) {
            if (state.getMode() == SleepAnimationMode.MADE_IN_HEAVEN_BED
                    && state.shouldWakePlayersOnFinish()
                    && !seamlesssleep$hasEnoughSleeping(self)) {
                state.suppressWakePlayersOnFinish();
            }
            this.seamlesssleep$finishSleepAnimation(state);
            WorldSleepAccelerationManager.refreshForLevelTick(self);
            if (!state.hasVisualFinishBeenAnnounced()) {
                SleepAnimationNetworking.sendFinish(self, state);
            }
            return;
        }

        if (!state.isActive()) {
            return;
        }

        if (state.getMode().requiresSleepers()
                && state.getPhase() == SleepAnimationPhase.RUNNING
                && !seamlesssleep$hasEnoughSleeping(self)) {
            if (state.getMode() == SleepAnimationMode.MADE_IN_HEAVEN_BED && state.startBraking(self)) {
                this.seamlesssleep$sleepAnimationWakePlayers = state.shouldWakePlayersOnFinish();
                this.seamlesssleep$sleepAnimationResetWeather = false;
                WorldSleepAccelerationManager.refreshForLevelTick(self);
                SleepAnimationNetworking.sendStart(self, state);
                Constants.debug("Made In Heaven bed animation braking: not enough players sleeping.");
                return;
            }
            if (state.getMode() == SleepAnimationMode.NORMAL_SLEEP
                    && state.startNormalCancelBraking(self)) {
                this.seamlesssleep$sleepAnimationWakePlayers = false;
                this.seamlesssleep$sleepAnimationResetWeather = false;
                WorldSleepAccelerationManager.refreshForLevelTick(self);
                SleepAnimationNetworking.sendStart(self, state);
                Constants.debug("Normal sleep gameplay canceled; visual cancel braking started.");
                return;
            }

            state.cancel();
            this.seamlesssleep$sleepAnimationWakePlayers = false;
            this.seamlesssleep$sleepAnimationResetWeather = false;
            WorldSleepAccelerationManager.refreshForLevelTick(self);
            SleepAnimationNetworking.sendStop(self, state, SleepAnimationStopReason.CANCELLED_NOT_ENOUGH_SLEEPERS);
            Constants.debug("Sleep animation canceled immediately: not enough players sleeping.");
            return;
        }

        if (state.startMadeInHeavenAutoBraking(self)) {
            this.seamlesssleep$sleepAnimationWakePlayers = state.shouldWakePlayersOnFinish();
            this.seamlesssleep$sleepAnimationResetWeather = false;
            WorldSleepAccelerationManager.refreshForLevelTick(self);
            SleepAnimationNetworking.sendStart(self, state);
            Constants.debug("Made In Heaven bed animation auto braking before final dawn.");
            return;
        }
    }

    @Unique
    private boolean seamlesssleep$startBedSleepAnimation(ServerLevel world,
                                                        SleepAnimationState state,
                                                        long currentTime,
                                                        long normalTargetTime) {
        SleepAnimationMode mode = seamlesssleep$rollBedSleepMode(world);
        boolean started;
        if (mode == SleepAnimationMode.MADE_IN_HEAVEN_BED) {
            started = state.startMadeInHeavenBed(world, currentTime);
        } else {
            started = state.start(
                    world,
                    currentTime,
                    normalTargetTime,
                    SleepAnimationMode.NORMAL_SLEEP,
                    seamlesssleep$resolveVisualContext(world, currentTime)
            );
        }
        if (!started) {
            return false;
        }

        this.seamlesssleep$sleepAnimationWakePlayers = state.shouldWakePlayersOnFinish();
        if (state.getMode() == SleepAnimationMode.MADE_IN_HEAVEN_BED) {
            SleepWeatherHelper.clearWeatherCycle(world);
        }
        int weatherChancePercent = SeamlessSleepServerConfigManager.get().sleepWeatherClearChancePercent;
        this.seamlesssleep$sleepAnimationResetWeather = state.getMode().resetsWeatherOnFinish()
                && seamlesssleep$rollWeatherClearChance(world, weatherChancePercent);
        WorldSleepAccelerationManager.refreshForLevelTick(world);
        SleepAnimationNetworking.sendStart(world, state);
        return true;
    }

    @Unique
    private void seamlesssleep$tryStartCustomBedSleep(ServerLevel world, SleepAnimationState state) {
        SleepEligibilityMode eligibility = SeamlessSleepServerConfigManager.get().sleepEligibility;
        if (eligibility == SleepEligibilityMode.VANILLA || eligibility.preventsSleepSkip()) {
            return;
        }
        if (!world.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            return;
        }
        if (!seamlesssleep$hasEnoughDeepSleeping(world)) {
            return;
        }

        long currentTime = world.getDayTime();
        long targetTime = seamlesssleep$nextMorning(currentTime);
        if (targetTime <= currentTime) {
            return;
        }

        if (seamlesssleep$startBedSleepAnimation(world, state, currentTime, targetTime)) {
            Constants.debug("Starting custom sleep animation on server: {} -> {}", currentTime, targetTime);
        }
    }

    @Unique
    private void seamlesssleep$tryStartMadeInHeavenBedSleep(ServerLevel world, SleepAnimationState state) {
        int chancePercent = SeamlessSleepServerConfigManager.get().madeInHeavenChancePercent;
        if (chancePercent <= 0 || !world.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            this.seamlesssleep$madeInHeavenRollLocked = false;
            return;
        }
        if (!seamlesssleep$hasEnoughMadeInHeavenSleeping(world)) {
            this.seamlesssleep$madeInHeavenRollLocked = false;
            return;
        }
        if (this.seamlesssleep$madeInHeavenRollLocked) {
            return;
        }

        this.seamlesssleep$madeInHeavenRollLocked = true;
        if (chancePercent < 100 && world.getRandom().nextInt(100) >= chancePercent) {
            return;
        }

        long currentTime = world.getDayTime();
        if (!state.startMadeInHeavenBed(world, currentTime)) {
            return;
        }

        this.seamlesssleep$sleepAnimationWakePlayers = state.shouldWakePlayersOnFinish();
        this.seamlesssleep$sleepAnimationResetWeather = false;
        SleepWeatherHelper.clearWeatherCycle(world);
        WorldSleepAccelerationManager.refreshForLevelTick(world);
        SleepAnimationNetworking.sendStart(world, state);
        Constants.debug("Starting Made In Heaven bed animation outside normal sleep eligibility: {}", currentTime);
    }

    @Unique
    private void seamlesssleep$finishSleepAnimation(SleepAnimationState state) {
        if (state.shouldWakePlayersOnFinish()) {
            SleepStatusUpdateSuppression.beginNaturalFinishWake();
            try {
                this.seamlesssleep$invokeWakeSleepingPlayers();
            } finally {
                SleepStatusUpdateSuppression.endNaturalFinishWake();
            }
        }
        if (state.getMode().resetsWeatherOnFinish() && this.seamlesssleep$sleepAnimationResetWeather) {
            this.seamlesssleep$invokeResetWeather();
        }
        this.seamlesssleep$sleepAnimationWakePlayers = false;
        this.seamlesssleep$sleepAnimationResetWeather = false;
        Constants.debug("Sleep animation finished.");
    }

    @Unique
    private boolean seamlesssleep$rollWeatherClearChance(ServerLevel world, int chancePercent) {
        if (!world.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
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
    private static boolean seamlesssleep$isFlashbackReplayServer(ServerLevel world) {
        return FlashbackReplayServerCompat.isReplayServer(world.getServer());
    }

    @Unique
    private static void seamlesssleep$clearReplayServerSleepState(ServerLevel world) {
        SleepAnimationState state = SleepAnimationStates.getIfPresent(world);
        if (state != null && state.isActive()) {
            state.cancel();
        }
    }

    @Unique
    private boolean seamlesssleep$hasEnoughSleeping(ServerLevel world) {
        int percentage = world.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        int total = 0;
        int sleeping = 0;
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) {
                continue;
            }
            total++;
            if (player.isAlive() && BedRestingHelper.isManagedBedStateServer(player)) {
                sleeping++;
            }
        }

        if (total == 0) {
            return false;
        }

        int required = SleepRequirement.sleepersNeeded(total, percentage);
        return sleeping >= required;
    }

    @Unique
    private boolean seamlesssleep$hasEnoughDeepSleeping(ServerLevel world) {
        int percentage = world.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        int total = 0;
        int deepSleeping = 0;
        int delayTicks = SeamlessSleepServerConfigManager.get().fallAsleepDelayTicks;
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) {
                continue;
            }
            total++;
            if (BedRestingHelper.hasSleptLongEnough(player, delayTicks)) {
                deepSleeping++;
            }
        }

        if (total == 0) {
            return false;
        }

        int required = SleepRequirement.sleepersNeeded(total, percentage);
        return deepSleeping >= required;
    }

    @Unique
    private boolean seamlesssleep$hasEnoughMadeInHeavenSleeping(ServerLevel world) {
        int percentage = world.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        int total = 0;
        int sleepers = 0;
        int delayTicks = SeamlessSleepServerConfigManager.get().fallAsleepDelayTicks;
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) {
                continue;
            }
            total++;
            if (BedRestingHelper.hasMadeInHeavenSleepLongEnough(player, delayTicks)) {
                sleepers++;
            }
        }

        if (total == 0) {
            return false;
        }

        int required = SleepRequirement.sleepersNeeded(total, percentage);
        return sleepers >= required;
    }

    @Unique
    private SleepAnimationMode seamlesssleep$rollBedSleepMode(ServerLevel world) {
        int chancePercent = SeamlessSleepServerConfigManager.get().madeInHeavenChancePercent;
        if (chancePercent <= 0) {
            return SleepAnimationMode.NORMAL_SLEEP;
        }
        if (chancePercent >= 100 || world.getRandom().nextInt(100) < chancePercent) {
            return SleepAnimationMode.MADE_IN_HEAVEN_BED;
        }
        return SleepAnimationMode.NORMAL_SLEEP;
    }

    @Unique
    private SleepAnimationVisualContext seamlesssleep$resolveVisualContext(ServerLevel world, long startTime) {
        if (world.isThundering()) {
            return SleepAnimationVisualContext.STORM;
        }
        return seamlesssleep$isNight(startTime)
                ? SleepAnimationVisualContext.NIGHT
                : SleepAnimationVisualContext.DAY;
    }

    @Unique
    private boolean seamlesssleep$isNight(long dayTime) {
        long wrapped = Math.floorMod(dayTime, seamlesssleep$DAY_TICKS);
        return wrapped >= seamlesssleep$NIGHT_START_TICKS && wrapped < seamlesssleep$NIGHT_END_TICKS;
    }

    @Unique
    private long seamlesssleep$nextMorning(long dayTime) {
        return (Math.floorDiv(dayTime, seamlesssleep$DAY_TICKS) + 1L) * seamlesssleep$DAY_TICKS;
    }

    @Inject(method = "updateSleepingPlayerList", at = @At("TAIL"))
    private void seamlesssleep$syncBedHudSleepProgress(CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        SleepAnimationState state = SleepAnimationStates.getIfPresent(self);
        if (!SleepDimensionSupport.supportsSleepAnimation(self)
                && (state == null || !state.isActive())) {
            BedHudNetworking.syncSleepProgress(self);
            return;
        }
        if (seamlesssleep$isFlashbackReplayServer(self)) {
            seamlesssleep$clearReplayServerSleepState(self);
            return;
        }
        if (SleepStatusUpdateSuppression.isNaturalFinishWakeSuppressed()) {
            return;
        }
        BedHudNetworking.syncSleepProgress(self);
    }
}
