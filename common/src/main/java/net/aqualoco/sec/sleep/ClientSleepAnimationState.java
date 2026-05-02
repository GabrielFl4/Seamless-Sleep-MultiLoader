package net.aqualoco.sec.sleep;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.ReplayPlaybackCompat;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.OptionalLong;

// Client mirror for the active sleep transition.
public final class ClientSleepAnimationState {
    private static final long DAY_TICKS = 24000L;
    private static final long NIGHT_START_TICKS = 12542L;
    private static final long NIGHT_END_TICKS = 23460L;
    private static final int FINISH_GRACE_FRAMES = 80;
    private static final long CLOSED_SESSION_NONE = -1L;

    private boolean active;
    private long sessionId = -1L;
    private long sequenceId = -1L;
    private long closedSessionId = CLOSED_SESSION_NONE;
    private SleepAnimationMode mode = SleepAnimationMode.NORMAL_SLEEP;
    private SleepAnimationPhase phase = SleepAnimationPhase.IDLE;
    private SleepAnimationVisualContext visualContext = SleepAnimationVisualContext.NIGHT;
    private SleepAnimationSoundMode soundMode = SleepAnimationSoundMode.NONE;
    private long startTimeOfDay;
    private long endTimeOfDay;
    private int durationTicks;
    private long serverStartGameTime;
    private long serverGameTimeAtSend;
    private long currentAuthoritativeDayTime;
    private long currentVisualDayTime;
    private double cachedProgress;
    private boolean awaitingFinish;
    private int finishGraceFrames;
    private int activeWorldIdentity;
    private boolean replayCompatMode;
    private long replayCompatStartTimelineMillis;
    private float replayCompatElapsedTicksFallback;
    private boolean loggedReplayTimelineFallback;

    public boolean isActive() {
        return this.active;
    }

    public boolean isReplayCompatMode() {
        return this.active && this.replayCompatMode;
    }

    public boolean startedDuringDay() {
        return !isNightStart(this.startTimeOfDay);
    }

    public SleepAnimationVisualContext getVisualContext() {
        return this.visualContext;
    }

    public SleepAnimationSoundMode getSoundMode() {
        return this.soundMode;
    }

    public OptionalLong getReplayTimelineMillisSnapshot() {
        if (!this.isReplayCompatMode()) {
            return OptionalLong.empty();
        }
        return ReplayPlaybackCompat.getReplayTimelineMillis();
    }

    public double getProgress() {
        if (this.awaitingFinish) {
            return 1.0D;
        }
        if (!this.active) {
            return 0.0D;
        }
        if (this.replayCompatMode) {
            return clamp01(this.computeReplayCompatProgressSnapshot());
        }
        return clamp01(this.cachedProgress);
    }

    public double getEasedVelocityFactor() {
        if (!this.active && !this.awaitingFinish) {
            return 0.0D;
        }

        double x = this.getProgress();
        double epsilon = 1.0D / Math.max(20.0D, this.durationTicks);
        double from = Math.max(0.0D, x - epsilon);
        double to = Math.min(1.0D, x + epsilon);
        if (to <= from) {
            return 0.0D;
        }

        double easedFrom = easeForPhase(from);
        double easedTo = easeForPhase(to);
        return Math.max(0.0D, (easedTo - easedFrom) / (to - from));
    }

    public double getCurrentDayTimeSpeedPerTick() {
        if ((!this.active && !this.awaitingFinish) || this.durationTicks <= 0) {
            return 0.0D;
        }

        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            return SleepAnimationState.madeInHeavenVelocityForElapsed(this.estimateMadeInHeavenElapsedTicks());
        }

        long deltaTime = Math.max(0L, this.endTimeOfDay - this.startTimeOfDay);
        if (deltaTime <= 0L) {
            return 0.0D;
        }

        double baseSpeedPerTick = deltaTime / (double) this.durationTicks;
        return Math.max(0.0D, this.getEasedVelocityFactor() * baseSpeedPerTick);
    }

    public void reset() {
        this.clearPlaybackState(false);
    }

    public void resetForWorldExit(String reason) {
        if (this.sessionId >= 0L) {
            this.closedSessionId = this.sessionId;
        }
        if (this.active || this.awaitingFinish) {
            Constants.debug("Client sleep animation reset for world exit: {}", reason);
        }
        this.sessionId = -1L;
        this.sequenceId = -1L;
        this.activeWorldIdentity = 0;
        this.clearPlaybackState(false);
    }

    public void resetIfWorldMismatch(ClientLevel world, String reason) {
        if ((this.active || this.awaitingFinish)
                && this.activeWorldIdentity != 0
                && this.activeWorldIdentity != System.identityHashCode(world)) {
            this.resetForWorldExit(reason);
        }
    }

    public void start(ClientLevel world,
                      long sessionId,
                      long sequenceId,
                      SleepAnimationMode mode,
                      SleepAnimationPhase phase,
                      SleepAnimationVisualContext visualContext,
                      SleepAnimationSoundMode soundMode,
                      long startTimeOfDay,
                      long endTimeOfDay,
                      int durationTicks,
                      long serverStartGameTime,
                      long serverGameTimeAtSend,
                      long currentAuthoritativeDayTime) {
        if (world == null) {
            return;
        }
        this.resetIfWorldMismatch(world, "start_world_changed");

        if (sessionId < this.sessionId) {
            Constants.debug(
                    "Ignored stale sleep animation start on client (session {}, current {})",
                    sessionId,
                    this.sessionId
            );
            return;
        }

        if (sessionId == this.sessionId && this.active && sequenceId <= this.sequenceId) {
            return;
        }

        boolean replayCompatEnabled = SeamlessSleepClientConfigManager.get().replayCompatibilityEnabled;
        boolean replayCompatActive = replayCompatEnabled && ReplayPlaybackCompat.isReplayPlaybackActive();

        this.sessionId = sessionId;
        this.sequenceId = sequenceId;
        this.closedSessionId = CLOSED_SESSION_NONE;
        this.mode = mode == null ? SleepAnimationMode.NORMAL_SLEEP : mode;
        this.phase = phase == null ? SleepAnimationPhase.RUNNING : phase;
        this.visualContext = visualContext == null ? SleepAnimationVisualContext.NIGHT : visualContext;
        this.soundMode = soundMode == null ? SleepAnimationSoundMode.NONE : soundMode;
        this.startTimeOfDay = startTimeOfDay;
        this.endTimeOfDay = endTimeOfDay;
        this.durationTicks = Math.max(1, durationTicks);
        this.serverStartGameTime = serverStartGameTime;
        this.serverGameTimeAtSend = serverGameTimeAtSend;
        this.currentAuthoritativeDayTime = currentAuthoritativeDayTime;
        this.currentVisualDayTime = currentAuthoritativeDayTime;
        this.cachedProgress = computeProgressFromDayTime(currentAuthoritativeDayTime);
        this.awaitingFinish = false;
        this.finishGraceFrames = 0;
        this.activeWorldIdentity = System.identityHashCode(world);
        this.active = true;
        this.replayCompatMode = replayCompatActive;
        this.replayCompatStartTimelineMillis = -1L;
        this.replayCompatElapsedTicksFallback = 0.0F;
        this.loggedReplayTimelineFallback = false;

        if (this.replayCompatMode) {
            OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
            if (replayTimeline.isPresent()) {
                this.replayCompatStartTimelineMillis = replayTimeline.getAsLong();
            }
        } else if (this.isLiveClockUsable(world)) {
            this.cachedProgress = clamp01(this.computeLiveProgress(world, 0.0F));
            this.currentVisualDayTime = interpolateDayTime(this.cachedProgress);
        }

        Constants.debug(
                "Client sleep animation mode: {} (session {}, gameTime {} -> {}, duration {} ticks)",
                replayCompatActive ? "REPLAY_COMPAT" : "NORMAL",
                this.sessionId,
                this.serverStartGameTime,
                this.serverGameTimeAtSend,
                this.durationTicks
        );
    }

    public void finish(ClientLevel world, long sessionId, long finalDayTime, SleepAnimationStopReason reason) {
        if (sessionId == this.closedSessionId && !this.active && !this.awaitingFinish) {
            return;
        }
        if (sessionId < this.sessionId) {
            return;
        }

        if ((this.active || this.awaitingFinish)
                && this.activeWorldIdentity != 0
                && world != null
                && this.activeWorldIdentity != System.identityHashCode(world)) {
            this.resetForWorldExit("finish_world_mismatch");
            return;
        }

        if (world != null) {
            world.getLevelData().setDayTime(finalDayTime);
        }

        this.sessionId = sessionId;
        this.closedSessionId = CLOSED_SESSION_NONE;
        this.currentAuthoritativeDayTime = finalDayTime;
        this.currentVisualDayTime = finalDayTime;
        this.cachedProgress = reason == SleepAnimationStopReason.FINISHED
                ? 1.0D
                : computeProgressFromDayTime(finalDayTime);
        this.phase = reason == SleepAnimationStopReason.FINISHED
                ? SleepAnimationPhase.FINISHED
                : SleepAnimationPhase.CANCELLED;
        this.clearPlaybackState(false);

        Constants.debug("Client sleep animation finished/stopped: {} (session {})", reason, sessionId);
    }

    public void tick(ClientLevel world, DeltaTracker deltaTracker) {
        if (!this.active) {
            return;
        }

        this.resetIfWorldMismatch(world, "world_changed");
        if (!this.active) {
            return;
        }

        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            this.tickMadeInHeavenRunning(world, deltaTracker);
            return;
        }

        double x = this.replayCompatMode
                ? this.computeReplayCompatProgress(deltaTracker)
                : this.computeLiveProgressOrFallback(world, deltaTracker);
        x = clamp01(x);
        this.cachedProgress = x;

        long newTimeOfDay = interpolateDayTime(x);
        if (!this.replayCompatMode && this.endTimeOfDay >= this.startTimeOfDay) {
            newTimeOfDay = Math.max(newTimeOfDay, this.currentVisualDayTime);
        }

        world.getLevelData().setDayTime(newTimeOfDay);
        this.currentVisualDayTime = newTimeOfDay;

        if (this.replayCompatMode) {
            return;
        }

        if (x >= 1.0D) {
            world.getLevelData().setDayTime(this.endTimeOfDay);
            this.currentVisualDayTime = this.endTimeOfDay;
            this.awaitingFinish = true;
            this.finishGraceFrames++;
            if (this.finishGraceFrames >= FINISH_GRACE_FRAMES) {
                this.active = false;
                this.replayCompatMode = false;
            }
        } else {
            this.awaitingFinish = false;
            this.finishGraceFrames = 0;
        }
    }

    public void tick(ClientLevel world) {
        this.tick(world, DeltaTracker.ONE);
    }

    private double computeLiveProgressOrFallback(ClientLevel world, DeltaTracker deltaTracker) {
        if (!this.isLiveClockUsable(world)) {
            this.currentVisualDayTime = this.currentAuthoritativeDayTime;
            return computeProgressFromDayTime(this.currentAuthoritativeDayTime);
        }

        float partialTick = 0.0F;
        if (deltaTracker != null) {
            partialTick = Math.max(0.0F, deltaTracker.getGameTimeDeltaPartialTick(false));
        }
        return this.computeLiveProgress(world, partialTick);
    }

    private double computeLiveProgress(ClientLevel world, float partialTick) {
        double visualGameTime = world.getGameTime() + partialTick;
        double elapsedTicks = visualGameTime - this.serverStartGameTime;
        return this.durationTicks <= 0 ? 1.0D : elapsedTicks / this.durationTicks;
    }

    private void tickMadeInHeavenRunning(ClientLevel world, DeltaTracker deltaTracker) {
        double elapsedTicks = this.computeMadeInHeavenElapsedTicks(world, deltaTracker);
        double distance = SleepAnimationState.madeInHeavenDistanceForElapsed(elapsedTicks);
        long newTimeOfDay = this.startTimeOfDay + Math.max(0L, (long) distance);
        if (newTimeOfDay >= this.endTimeOfDay) {
            newTimeOfDay = this.endTimeOfDay;
            this.awaitingFinish = true;
            this.finishGraceFrames++;
            if (this.finishGraceFrames >= FINISH_GRACE_FRAMES) {
                this.active = false;
                this.replayCompatMode = false;
            }
        } else {
            this.awaitingFinish = false;
            this.finishGraceFrames = 0;
        }

        newTimeOfDay = Math.max(newTimeOfDay, this.currentVisualDayTime);
        world.getLevelData().setDayTime(newTimeOfDay);
        this.currentVisualDayTime = newTimeOfDay;

        long delta = this.endTimeOfDay - this.startTimeOfDay;
        this.cachedProgress = delta <= 0L ? 0.0D : clamp01((newTimeOfDay - this.startTimeOfDay) / (double) delta);
    }

    private double computeMadeInHeavenElapsedTicks(ClientLevel world, DeltaTracker deltaTracker) {
        if (this.replayCompatMode) {
            return this.computeReplayCompatElapsedTicks(deltaTracker);
        }
        if (!this.isLiveClockUsable(world)) {
            return estimateMadeInHeavenElapsedTicks();
        }

        float partialTick = 0.0F;
        if (deltaTracker != null) {
            partialTick = Math.max(0.0F, deltaTracker.getGameTimeDeltaPartialTick(false));
        }
        return Math.max(0.0D, world.getGameTime() + partialTick - this.serverStartGameTime);
    }

    private double computeReplayCompatElapsedTicks(DeltaTracker deltaTracker) {
        OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
        if (replayTimeline.isPresent()) {
            long replayNowMs = replayTimeline.getAsLong();

            if (this.replayCompatStartTimelineMillis < 0L) {
                long fallbackElapsedMs = (long) (this.replayCompatElapsedTicksFallback * 50.0F);
                this.replayCompatStartTimelineMillis = replayNowMs - fallbackElapsedMs;
            }

            return Math.max(0.0D, (replayNowMs - this.replayCompatStartTimelineMillis) / 50.0D);
        }

        if (deltaTracker != null) {
            this.replayCompatElapsedTicksFallback += Math.max(0.0F, deltaTracker.getGameTimeDeltaTicks());
        }
        return this.replayCompatElapsedTicksFallback;
    }

    private double estimateMadeInHeavenElapsedTicks() {
        long distance = Math.max(0L, this.currentAuthoritativeDayTime - this.startTimeOfDay);
        double lo = 0.0D;
        double hi = 20000.0D;
        while (SleepAnimationState.madeInHeavenDistanceForElapsed(hi) < distance && hi < 1_000_000.0D) {
            hi *= 2.0D;
        }
        for (int i = 0; i < 20; i++) {
            double mid = (lo + hi) * 0.5D;
            if (SleepAnimationState.madeInHeavenDistanceForElapsed(mid) < distance) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    private boolean isLiveClockUsable(ClientLevel world) {
        long localGameTime = world.getGameTime();
        if (localGameTime <= 0L && this.serverStartGameTime > 20L) {
            return false;
        }
        if (localGameTime + 20L < this.serverStartGameTime) {
            return false;
        }

        long maxExpectedDrift = Math.max(200L, this.durationTicks * 2L + 40L);
        return this.serverGameTimeAtSend <= 0L
                || Math.abs(localGameTime - this.serverGameTimeAtSend) <= maxExpectedDrift;
    }

    private double computeReplayCompatProgress(DeltaTracker deltaTracker) {
        OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
        if (replayTimeline.isPresent()) {
            long replayNowMs = replayTimeline.getAsLong();

            if (this.replayCompatStartTimelineMillis < 0L) {
                long fallbackElapsedMs = (long) (this.replayCompatElapsedTicksFallback * 50.0F);
                this.replayCompatStartTimelineMillis = replayNowMs - fallbackElapsedMs;
            }

            long elapsedReplayMs = replayNowMs - this.replayCompatStartTimelineMillis;
            double totalMs = (double) this.durationTicks * 50.0;
            return totalMs <= 0.0 ? 1.0 : elapsedReplayMs / totalMs;
        }

        if (deltaTracker != null) {
            this.replayCompatElapsedTicksFallback += Math.max(0.0F, deltaTracker.getGameTimeDeltaTicks());
        }
        if (!this.loggedReplayTimelineFallback) {
            this.loggedReplayTimelineFallback = true;
            Constants.debug("Replay timeline was unavailable; using DeltaTracker fallback for sleep animation progress.");
        }

        if (this.durationTicks <= 0) {
            return 1.0;
        }

        return this.replayCompatElapsedTicksFallback / (double) this.durationTicks;
    }

    private double computeReplayCompatProgressSnapshot() {
        OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
        if (replayTimeline.isPresent()) {
            long replayNowMs = replayTimeline.getAsLong();
            long replayStartMs = this.replayCompatStartTimelineMillis;

            if (replayStartMs < 0L) {
                long fallbackElapsedMs = (long) (this.replayCompatElapsedTicksFallback * 50.0F);
                replayStartMs = replayNowMs - fallbackElapsedMs;
            }

            long elapsedReplayMs = replayNowMs - replayStartMs;
            double totalMs = (double) this.durationTicks * 50.0;
            return totalMs <= 0.0 ? 1.0 : elapsedReplayMs / totalMs;
        }

        if (this.durationTicks <= 0) {
            return 1.0;
        }

        return this.replayCompatElapsedTicksFallback / (double) this.durationTicks;
    }

    private long interpolateDayTime(double progress) {
        double eased = easeForPhase(clamp01(progress));
        long delta = this.endTimeOfDay - this.startTimeOfDay;
        return this.startTimeOfDay + (long) (delta * eased);
    }

    private double easeForPhase(double progress) {
        return this.phase == SleepAnimationPhase.BRAKING
                ? SleepAnimationState.brakeEase(progress)
                : SleepAnimationState.integralEase(progress);
    }

    private double computeProgressFromDayTime(long dayTime) {
        long delta = this.endTimeOfDay - this.startTimeOfDay;
        if (delta <= 0L) {
            return 0.0D;
        }
        return clamp01((dayTime - this.startTimeOfDay) / (double) delta);
    }

    private void clearPlaybackState(boolean keepWorldAnchor) {
        this.active = false;
        this.awaitingFinish = false;
        this.finishGraceFrames = 0;
        this.replayCompatMode = false;
        this.replayCompatStartTimelineMillis = -1L;
        this.replayCompatElapsedTicksFallback = 0.0F;
        this.loggedReplayTimelineFallback = false;
        if (!keepWorldAnchor) {
            this.activeWorldIdentity = 0;
        }
    }

    private static double clamp01(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private static boolean isNightStart(long timeOfDay) {
        long wrapped = Math.floorMod(timeOfDay, DAY_TICKS);
        return wrapped >= NIGHT_START_TICKS && wrapped < NIGHT_END_TICKS;
    }
}
