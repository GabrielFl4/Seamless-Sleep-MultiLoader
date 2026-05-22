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
    private static final long MILLIS_PER_TICK = 50L;
    private static final long REPLAY_SEEK_THRESHOLD_MILLIS = 1000L;
    private static final double REPLAY_WINDOW_EPSILON_TICKS = 0.5D;
    private static final long REPLAY_FINITE_ELAPSED_TOLERANCE_TICKS = 20L;

    private boolean active;
    private long sessionId = -1L;
    private long sequenceId = -1L;
    private long closedSessionId = CLOSED_SESSION_NONE;
    private SleepAnimationMode mode = SleepAnimationMode.NORMAL_SLEEP;
    private SleepAnimationPhase phase = SleepAnimationPhase.IDLE;
    private SleepAnimationVisualContext visualContext = SleepAnimationVisualContext.NIGHT;
    private SleepAnimationSoundMode soundMode = SleepAnimationSoundMode.MUTED;
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
    private long lastReplayTimelineMillis;
    private float replayCompatElapsedTicksFallback;
    private boolean loggedReplayTimelineFallback;
    private boolean replayCompatPendingWorldTimeReanchor;

    public boolean isActive() {
        return this.active;
    }

    public boolean isReplayCompatMode() {
        return this.active && this.replayCompatMode;
    }

    public boolean isVisualOverlayActive() {
        return this.isActiveForReplayTimelineSnapshot();
    }

    public boolean shouldRenderSleepIndicator() {
        return this.isVisualOverlayActive();
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
        if (this.replayCompatMode && !this.isActiveForReplayTimelineSnapshot()) {
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
        if (this.replayCompatMode && !this.isActiveForReplayTimelineSnapshot()) {
            return 0.0D;
        }

        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            double elapsedTicks = this.replayCompatMode
                    ? this.computeReplayCompatElapsedTicksSnapshot()
                    : this.estimateMadeInHeavenElapsedTicksFromVisualTime();
            return SleepAnimationState.madeInHeavenVelocityForElapsed(elapsedTicks);
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

        boolean replayCompatEnabled = SeamlessSleepClientConfigManager.get().replayCompatibilityEnabled;
        boolean replayCompatActive = replayCompatEnabled && ReplayPlaybackCompat.isReplayPlaybackActive();

        if (!replayCompatActive && sessionId < this.sessionId) {
            Constants.debug(
                    "Ignored stale sleep animation start on client (session {}, current {})",
                    sessionId,
                    this.sessionId
            );
            return;
        }

        if (!replayCompatActive && sessionId == this.sessionId && this.active && sequenceId <= this.sequenceId) {
            return;
        }

        this.sessionId = sessionId;
        this.sequenceId = sequenceId;
        this.closedSessionId = CLOSED_SESSION_NONE;
        this.mode = mode == null ? SleepAnimationMode.NORMAL_SLEEP : mode;
        this.phase = phase == null ? SleepAnimationPhase.RUNNING : phase;
        this.visualContext = visualContext == null ? SleepAnimationVisualContext.NIGHT : visualContext;
        this.soundMode = SleepAnimationSoundMode.canonical(soundMode);
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
        this.lastReplayTimelineMillis = -1L;
        this.replayCompatElapsedTicksFallback = 0.0F;
        this.loggedReplayTimelineFallback = false;
        this.replayCompatPendingWorldTimeReanchor = false;

        if (this.replayCompatMode) {
            OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
            if (replayTimeline.isPresent()) {
                long replayNowMillis = replayTimeline.getAsLong();
                this.replayCompatStartTimelineMillis = this.computeReplayStartTimelineMillis(world, replayNowMillis);
                this.lastReplayTimelineMillis = replayNowMillis;
                this.replayCompatPendingWorldTimeReanchor = true;
                double elapsedTicks = this.computeReplayCompatElapsedTicksSignedSnapshot(replayNowMillis);
                if (elapsedTicks < -REPLAY_WINDOW_EPSILON_TICKS) {
                    this.clearReplayVisualLocally("replay_start_before_window");
                    return;
                }
                if (elapsedTicks >= this.replayVisualWindowEndTicks()) {
                    this.finishReplayVisualLocally(world, this.endTimeOfDay);
                    return;
                }
            }
            this.applyReplayVisualSnapshot(world);
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
        boolean replayCompatActive = SeamlessSleepClientConfigManager.get().replayCompatibilityEnabled
                && ReplayPlaybackCompat.isReplayPlaybackActive();
        if (!replayCompatActive && sessionId == this.closedSessionId && !this.active && !this.awaitingFinish) {
            return;
        }
        if (!replayCompatActive && sessionId < this.sessionId) {
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

        ReplayTimelineStep replayStep = this.replayCompatMode
                ? this.updateReplayLifecycle(world, deltaTracker)
                : ReplayTimelineStep.unavailable();
        if (this.replayCompatMode && this.isBeforeReplayVisualWindow(replayStep)) {
            this.clearReplayVisualLocally("replay_before_start");
            return;
        }

        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            this.tickMadeInHeavenRunning(world, deltaTracker, replayStep);
            return;
        }

        double x = this.replayCompatMode
                ? this.computeReplayCompatProgress(replayStep)
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
            if (x >= 1.0D && this.shouldFinishFiniteReplayVisualLocally()) {
                this.finishReplayVisualLocally(world, this.endTimeOfDay);
            }
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

    private void tickMadeInHeavenRunning(ClientLevel world, DeltaTracker deltaTracker, ReplayTimelineStep replayStep) {
        if (this.replayCompatMode && this.isBeforeReplayVisualWindow(replayStep)) {
            this.clearReplayVisualLocally("replay_before_start");
            return;
        }
        if (this.replayCompatMode && this.isAfterReplayVisualWindow(replayStep)) {
            this.finishReplayVisualLocally(world, this.endTimeOfDay);
            return;
        }

        double elapsedTicks = this.computeMadeInHeavenElapsedTicks(world, deltaTracker, replayStep);
        double distance = SleepAnimationState.madeInHeavenDistanceForElapsed(elapsedTicks);
        long newTimeOfDay = this.startTimeOfDay + Math.max(0L, (long) distance);
        if (newTimeOfDay >= this.endTimeOfDay) {
            newTimeOfDay = this.endTimeOfDay;
            if (this.replayCompatMode) {
                this.finishReplayVisualLocally(world, newTimeOfDay);
                return;
            }
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

        if (!this.replayCompatMode) {
            newTimeOfDay = Math.max(newTimeOfDay, this.currentVisualDayTime);
        }
        world.getLevelData().setDayTime(newTimeOfDay);
        this.currentVisualDayTime = newTimeOfDay;

        long delta = this.endTimeOfDay - this.startTimeOfDay;
        this.cachedProgress = delta <= 0L ? 0.0D : clamp01((newTimeOfDay - this.startTimeOfDay) / (double) delta);
    }

    private double computeMadeInHeavenElapsedTicks(ClientLevel world,
                                                  DeltaTracker deltaTracker,
                                                  ReplayTimelineStep replayStep) {
        if (this.replayCompatMode) {
            return replayStep.elapsedTicks();
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

    private ReplayTimelineStep updateReplayLifecycle(ClientLevel world, DeltaTracker deltaTracker) {
        OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
        if (replayTimeline.isPresent()) {
            long replayNowMs = replayTimeline.getAsLong();
            if (this.replayCompatStartTimelineMillis < 0L) {
                this.replayCompatStartTimelineMillis = this.computeReplayStartTimelineMillis(world, replayNowMs);
            } else if (this.replayCompatPendingWorldTimeReanchor) {
                this.tryReanchorReplayStartFromWorldTime(world, replayNowMs);
            }

            long deltaMillis = this.lastReplayTimelineMillis < 0L
                    ? 0L
                    : replayNowMs - this.lastReplayTimelineMillis;
            boolean timelinePaused = this.lastReplayTimelineMillis >= 0L && deltaMillis == 0L;
            boolean timelineSeek = this.lastReplayTimelineMillis >= 0L
                    && (deltaMillis < 0L || deltaMillis > REPLAY_SEEK_THRESHOLD_MILLIS);
            this.lastReplayTimelineMillis = replayNowMs;
            if (timelinePaused || timelineSeek) {
                this.awaitingFinish = false;
                this.finishGraceFrames = 0;
            }

            double elapsedTicks = (replayNowMs - this.replayCompatStartTimelineMillis) / 50.0D;
            return new ReplayTimelineStep(true, replayNowMs, deltaMillis, timelinePaused, timelineSeek, elapsedTicks);
        }

        this.lastReplayTimelineMillis = -1L;
        if (deltaTracker != null) {
            this.replayCompatElapsedTicksFallback += Math.max(0.0F, deltaTracker.getGameTimeDeltaTicks());
        }
        if (!this.loggedReplayTimelineFallback) {
            this.loggedReplayTimelineFallback = true;
            Constants.debug("Replay timeline was unavailable; using DeltaTracker fallback for sleep animation progress.");
        }

        return new ReplayTimelineStep(
                false,
                -1L,
                deltaTracker == null ? 0L : (long) (Math.max(0.0F, deltaTracker.getGameTimeDeltaTicks()) * 50.0F),
                false,
                false,
                this.replayCompatElapsedTicksFallback
        );
    }

    private double computeReplayCompatElapsedTicksSnapshot() {
        return Math.max(0.0D, this.computeReplayCompatElapsedTicksSignedSnapshot());
    }

    private double computeReplayCompatElapsedTicksSignedSnapshot() {
        OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
        if (replayTimeline.isPresent()) {
            long replayNowMs = replayTimeline.getAsLong();
            return this.computeReplayCompatElapsedTicksSignedSnapshot(replayNowMs);
        }

        return this.replayCompatElapsedTicksFallback;
    }

    private double computeReplayCompatElapsedTicksSignedSnapshot(long replayNowMs) {
        long replayStartMs = this.replayCompatStartTimelineMillis >= 0L
                ? this.replayCompatStartTimelineMillis
                : this.computeReplayStartTimelineMillis(replayNowMs);
        return (replayNowMs - replayStartMs) / (double) MILLIS_PER_TICK;
    }

    private double estimateMadeInHeavenElapsedTicks() {
        return estimateMadeInHeavenElapsedTicksForDistance(
                Math.max(0L, this.currentAuthoritativeDayTime - this.startTimeOfDay)
        );
    }

    private double estimateMadeInHeavenElapsedTicksFromVisualTime() {
        return estimateMadeInHeavenElapsedTicksForDistance(
                Math.max(0L, this.currentVisualDayTime - this.startTimeOfDay)
        );
    }

    private static double estimateMadeInHeavenElapsedTicksForDistance(long distance) {
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

    private double computeReplayCompatProgress(ReplayTimelineStep replayStep) {
        if (this.durationTicks <= 0) {
            return 1.0;
        }

        return replayStep.elapsedTicks() / (double) this.durationTicks;
    }

    private double computeReplayCompatProgressSnapshot() {
        OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
        if (replayTimeline.isPresent()) {
            double totalMs = (double) this.durationTicks * 50.0;
            double elapsedTicks = this.computeReplayCompatElapsedTicksSignedSnapshot(replayTimeline.getAsLong());
            return totalMs <= 0.0 ? 1.0 : (elapsedTicks * MILLIS_PER_TICK) / totalMs;
        }

        if (this.durationTicks <= 0) {
            return 1.0;
        }

        return this.replayCompatElapsedTicksFallback / (double) this.durationTicks;
    }

    private void applyReplayVisualSnapshot(ClientLevel world) {
        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            double elapsedTicks = this.computeReplayCompatElapsedTicksSnapshot();
            double distance = SleepAnimationState.madeInHeavenDistanceForElapsed(elapsedTicks);
            long replayTimeOfDay = this.startTimeOfDay + Math.max(0L, (long) distance);
            replayTimeOfDay = Math.min(replayTimeOfDay, this.endTimeOfDay);
            world.getLevelData().setDayTime(replayTimeOfDay);
            this.currentVisualDayTime = replayTimeOfDay;
            long delta = this.endTimeOfDay - this.startTimeOfDay;
            this.cachedProgress = delta <= 0L
                    ? 0.0D
                    : clamp01((replayTimeOfDay - this.startTimeOfDay) / (double) delta);
            return;
        }

        double progress = clamp01(this.computeReplayCompatProgressSnapshot());
        long replayTimeOfDay = interpolateDayTime(progress);
        world.getLevelData().setDayTime(replayTimeOfDay);
        this.currentVisualDayTime = replayTimeOfDay;
        this.cachedProgress = progress;
    }

    private long computeReplayStartTimelineMillis(ClientLevel world, long replayNowMillis) {
        OptionalLong worldElapsedTicks = this.computeReplayWorldElapsedTicks(world);
        if (worldElapsedTicks.isPresent()) {
            return replayNowMillis - worldElapsedTicks.getAsLong() * MILLIS_PER_TICK;
        }

        return this.computeReplayStartTimelineMillis(replayNowMillis);
    }

    private long computeReplayStartTimelineMillis(long replayNowMillis) {
        if (this.serverStartGameTime > 0L && this.serverGameTimeAtSend >= this.serverStartGameTime) {
            long elapsedServerTicksAtSend = this.serverGameTimeAtSend - this.serverStartGameTime;
            return replayNowMillis - elapsedServerTicksAtSend * MILLIS_PER_TICK;
        }

        long fallbackElapsedMillis = (long) (this.replayCompatElapsedTicksFallback * MILLIS_PER_TICK);
        return replayNowMillis - fallbackElapsedMillis;
    }

    private void tryReanchorReplayStartFromWorldTime(ClientLevel world, long replayNowMillis) {
        this.replayCompatPendingWorldTimeReanchor = false;
        OptionalLong worldElapsedTicks = this.computeReplayWorldElapsedTicks(world);
        if (worldElapsedTicks.isEmpty()) {
            return;
        }

        double currentElapsedTicks = this.computeReplayCompatElapsedTicksSignedSnapshot(replayNowMillis);
        long resolvedElapsedTicks = worldElapsedTicks.getAsLong();
        if (Math.abs(resolvedElapsedTicks - currentElapsedTicks) <= REPLAY_WINDOW_EPSILON_TICKS) {
            return;
        }

        this.replayCompatStartTimelineMillis = replayNowMillis - resolvedElapsedTicks * MILLIS_PER_TICK;
        Constants.debug(
                "Client sleep animation replay reanchored from world gameTime: elapsed {} ticks (was {})",
                resolvedElapsedTicks,
                currentElapsedTicks
        );
    }

    private OptionalLong computeReplayWorldElapsedTicks(ClientLevel world) {
        if (world == null || this.serverStartGameTime <= 0L) {
            return OptionalLong.empty();
        }

        long localGameTime = world.getGameTime();
        if (localGameTime < this.serverStartGameTime) {
            return OptionalLong.empty();
        }

        long elapsedTicks = localGameTime - this.serverStartGameTime;
        return OptionalLong.of(this.normalizeReplayWorldElapsedTicks(elapsedTicks));
    }

    private long normalizeReplayWorldElapsedTicks(long elapsedTicks) {
        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            return elapsedTicks;
        }

        long finiteWindowLimit = Math.max(1L, this.durationTicks) + REPLAY_FINITE_ELAPSED_TOLERANCE_TICKS;
        return Math.min(elapsedTicks, finiteWindowLimit);
    }

    private boolean shouldFinishFiniteReplayVisualLocally() {
        return this.phase == SleepAnimationPhase.BRAKING
                || this.mode == SleepAnimationMode.NORMAL_SLEEP
                || this.mode == SleepAnimationMode.COMMAND_TIMELAPSE;
    }

    private void finishReplayVisualLocally(ClientLevel world, long finalDayTime) {
        world.getLevelData().setDayTime(finalDayTime);
        this.currentAuthoritativeDayTime = finalDayTime;
        this.currentVisualDayTime = finalDayTime;
        this.cachedProgress = 1.0D;
        this.phase = SleepAnimationPhase.FINISHED;
        this.clearPlaybackState(true);
    }

    private void clearReplayVisualLocally(String reason) {
        Constants.debug("Client sleep animation replay visual cleared: {} (session {})", reason, this.sessionId);
        this.clearPlaybackState(true);
    }

    private boolean isActiveForReplayTimelineSnapshot() {
        if (!this.active) {
            return false;
        }
        if (!this.replayCompatMode) {
            return true;
        }

        OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
        if (replayTimeline.isEmpty()) {
            return true;
        }

        double elapsedTicks = this.computeReplayCompatElapsedTicksSignedSnapshot(replayTimeline.getAsLong());
        return elapsedTicks >= -REPLAY_WINDOW_EPSILON_TICKS
                && elapsedTicks < this.replayVisualWindowEndTicks();
    }

    private boolean isBeforeReplayVisualWindow(ReplayTimelineStep replayStep) {
        return replayStep.timelineAvailable()
                && replayStep.elapsedTicks() < -REPLAY_WINDOW_EPSILON_TICKS;
    }

    private boolean isAfterReplayVisualWindow(ReplayTimelineStep replayStep) {
        return replayStep.timelineAvailable()
                && replayStep.elapsedTicks() >= this.replayVisualWindowEndTicks();
    }

    private double replayVisualWindowEndTicks() {
        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            long visualDistance = Math.max(0L, this.endTimeOfDay - this.startTimeOfDay);
            return Math.max(1.0D, estimateMadeInHeavenElapsedTicksForDistance(visualDistance));
        }
        return Math.max(1.0D, this.durationTicks);
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
        this.lastReplayTimelineMillis = -1L;
        this.replayCompatElapsedTicksFallback = 0.0F;
        this.loggedReplayTimelineFallback = false;
        this.replayCompatPendingWorldTimeReanchor = false;
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

    private record ReplayTimelineStep(
            boolean timelineAvailable,
            long nowMillis,
            long deltaMillis,
            boolean paused,
            boolean seek,
            double elapsedTicks
    ) {
        private static ReplayTimelineStep unavailable() {
            return new ReplayTimelineStep(false, -1L, 0L, false, false, 0.0D);
        }
    }
}
