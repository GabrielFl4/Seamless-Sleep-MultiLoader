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
    private static final long CLOSED_SESSION_NONE = -1L;
    private static final long MILLIS_PER_TICK = 50L;
    private static final long NANOS_PER_TICK = MILLIS_PER_TICK * 1_000_000L;
    private static final double MAX_PRESENTATION_DELTA_TICKS = 4.0D;
    private static final long MAX_CANCEL_AUTHORITY_HOLD_DISTANCE = 200L;
    private static final long MAX_CANCEL_AUTHORITY_HOLD_NANOS = 10_000_000_000L;
    private static final long FINISHED_LOCK_POST_WAKE_NANOS = 250_000_000L;
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
    private double presentationElapsedTicks;
    private long livePresentationLastAdvanceNanos;
    private PresentationState presentationState = PresentationState.INACTIVE;
    private long cancelHoldVisualDayTime;
    private long lastObservedAuthoritativeDayTime;
    private long cancelHoldDeadlineNanos;
    private int activeWorldIdentity;
    private boolean replayCompatMode;
    private long replayCompatStartTimelineMillis;
    private long lastReplayTimelineMillis;
    private float replayCompatElapsedTicksFallback;
    private boolean loggedReplayTimelineFallback;
    private boolean replayCompatPendingWorldTimeReanchor;
    private boolean finishedDayTimeLockActive;
    private long finishedDayTimeLockDayTime;
    private int finishedDayTimeLockWorldIdentity;
    private long finishedDayTimeLockPostWakeDeadlineNanos;

    public boolean isActive() {
        return this.active;
    }

    public boolean needsFrameTick() {
        return this.active || this.presentationState == PresentationState.CANCEL_AUTHORITY_HOLD;
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
        if (this.presentationState == PresentationState.HOLDING_AT_TARGET
                || this.presentationState == PresentationState.FINISHED_LOCK) {
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

    public long getCurrentVisualDayTime() {
        return this.currentVisualDayTime;
    }

    public double getEasedVelocityFactor() {
        if (!this.active || this.presentationState != PresentationState.RUNNING) {
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
        if (!this.active
                || this.presentationState != PresentationState.RUNNING
                || this.durationTicks <= 0) {
            return 0.0D;
        }
        if (this.replayCompatMode && !this.isActiveForReplayTimelineSnapshot()) {
            return 0.0D;
        }

        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            double elapsedTicks = this.replayCompatMode
                    ? this.computeReplayCompatElapsedTicksSnapshot()
                    : this.presentationElapsedTicks;
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
        this.clearFinishedDayTimeLock();
    }

    public void resetForWorldExit(String reason) {
        if (this.needsFrameTick()) {
            Constants.debug("Client sleep animation reset for world exit: {}", reason);
        }
        this.sessionId = -1L;
        this.sequenceId = -1L;
        this.closedSessionId = CLOSED_SESSION_NONE;
        this.activeWorldIdentity = 0;
        this.clearPlaybackState(false);
        this.clearFinishedDayTimeLock();
    }

    public void resetIfWorldMismatch(ClientLevel world, String reason) {
        int worldIdentity = System.identityHashCode(world);
        boolean playbackMismatch = this.needsFrameTick()
                && this.activeWorldIdentity != 0
                && this.activeWorldIdentity != worldIdentity;
        boolean finishedLockMismatch = this.finishedDayTimeLockActive
                && this.finishedDayTimeLockWorldIdentity != worldIdentity;
        if (playbackMismatch || finishedLockMismatch) {
            this.resetForWorldExit(reason);
        }
    }

    public boolean start(ClientLevel world,
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
            return false;
        }
        this.resetIfWorldMismatch(world, "start_world_changed");

        boolean replayCompatEnabled = SeamlessSleepClientConfigManager.get().replayCompatibilityEnabled;
        boolean replayCompatActive = replayCompatEnabled && ReplayPlaybackCompat.isReplayPlaybackActive();

        if (!replayCompatActive && sessionId <= this.closedSessionId) {
            Constants.debug(
                    "Ignored start for terminally closed sleep animation session {} (closed through {})",
                    sessionId,
                    this.closedSessionId
            );
            return false;
        }

        if (!replayCompatActive && sessionId < this.sessionId) {
            Constants.debug(
                    "Ignored stale sleep animation start on client (session {}, current {})",
                    sessionId,
                    this.sessionId
            );
            return false;
        }

        if (!replayCompatActive && sessionId == this.sessionId && this.active && sequenceId <= this.sequenceId) {
            return false;
        }

        boolean continuingLiveSession = !replayCompatActive
                && this.active
                && sessionId == this.sessionId;
        long previousVisualDayTime = this.currentVisualDayTime;
        double previousSpeed = this.getCurrentDayTimeSpeedPerTick();
        SleepAnimationPhase resolvedPhase = phase == null ? SleepAnimationPhase.RUNNING : phase;

        this.clearFinishedDayTimeLock();
        this.sessionId = sessionId;
        this.sequenceId = sequenceId;
        this.mode = mode == null ? SleepAnimationMode.NORMAL_SLEEP : mode;
        this.phase = resolvedPhase;
        this.visualContext = visualContext == null ? SleepAnimationVisualContext.NIGHT : visualContext;
        this.soundMode = SleepAnimationSoundMode.canonical(soundMode);
        this.endTimeOfDay = endTimeOfDay;
        this.serverStartGameTime = serverStartGameTime;
        this.serverGameTimeAtSend = serverGameTimeAtSend;
        this.currentAuthoritativeDayTime = currentAuthoritativeDayTime;
        this.activeWorldIdentity = System.identityHashCode(world);
        this.active = true;
        this.replayCompatMode = replayCompatActive;
        this.presentationState = PresentationState.RUNNING;
        this.cancelHoldDeadlineNanos = 0L;
        this.replayCompatStartTimelineMillis = -1L;
        this.lastReplayTimelineMillis = -1L;
        this.replayCompatElapsedTicksFallback = 0.0F;
        this.loggedReplayTimelineFallback = false;
        this.replayCompatPendingWorldTimeReanchor = false;

        if (this.replayCompatMode) {
            this.startTimeOfDay = startTimeOfDay;
            this.durationTicks = Math.max(1, durationTicks);
            this.currentVisualDayTime = currentAuthoritativeDayTime;
            this.cachedProgress = computeCurveProgressFromDayTime(
                    currentAuthoritativeDayTime,
                    this.startTimeOfDay,
                    this.endTimeOfDay,
                    this.phase
            );
            this.presentationElapsedTicks = this.cachedProgress * this.durationTicks;
            OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
            if (replayTimeline.isPresent()) {
                long replayNowMillis = replayTimeline.getAsLong();
                this.replayCompatStartTimelineMillis = this.computeReplayStartTimelineMillis(world, replayNowMillis);
                this.lastReplayTimelineMillis = replayNowMillis;
                this.replayCompatPendingWorldTimeReanchor = true;
                double elapsedTicks = this.computeReplayCompatElapsedTicksSignedSnapshot(replayNowMillis);
                if (elapsedTicks < -REPLAY_WINDOW_EPSILON_TICKS) {
                    this.clearReplayVisualLocally("replay_start_before_window");
                    return true;
                }
                if (elapsedTicks >= this.replayVisualWindowEndTicks()) {
                    this.finishReplayVisualLocally(world, this.endTimeOfDay);
                    return true;
                }
            }
            this.applyReplayVisualSnapshot(world);
        } else if (continuingLiveSession) {
            this.startTimeOfDay = previousVisualDayTime;
            this.currentVisualDayTime = previousVisualDayTime;
            this.durationTicks = computeCheckpointDurationTicks(
                    startTimeOfDay,
                    endTimeOfDay,
                    durationTicks,
                    currentAuthoritativeDayTime,
                    resolvedPhase,
                    previousVisualDayTime,
                    previousSpeed
            );
            this.presentationElapsedTicks = 0.0D;
            this.cachedProgress = this.currentVisualDayTime >= this.endTimeOfDay ? 1.0D : 0.0D;
            if (this.currentVisualDayTime >= this.endTimeOfDay) {
                this.presentationState = PresentationState.HOLDING_AT_TARGET;
            }
            world.getLevelData().setDayTime(this.currentVisualDayTime);
        } else {
            this.startTimeOfDay = startTimeOfDay;
            this.durationTicks = Math.max(1, durationTicks);
            this.currentVisualDayTime = clampLiveAnchor(
                    currentAuthoritativeDayTime,
                    this.startTimeOfDay,
                    this.endTimeOfDay
            );
            this.cachedProgress = computeCurveProgressFromDayTime(
                    this.currentVisualDayTime,
                    this.startTimeOfDay,
                    this.endTimeOfDay,
                    this.phase
            );
            this.presentationElapsedTicks = this.cachedProgress * this.durationTicks;
            if (this.cachedProgress >= 1.0D || this.currentVisualDayTime >= this.endTimeOfDay) {
                this.currentVisualDayTime = this.endTimeOfDay;
                this.cachedProgress = 1.0D;
                this.presentationState = PresentationState.HOLDING_AT_TARGET;
            }
            world.getLevelData().setDayTime(this.currentVisualDayTime);
        }
        this.livePresentationLastAdvanceNanos = this.replayCompatMode ? 0L : System.nanoTime();

        Constants.debug(
                "Client sleep animation mode: {} (session {}, sequence {}, state {}, duration {} ticks)",
                replayCompatActive ? "REPLAY_COMPAT" : "NORMAL",
                this.sessionId,
                this.sequenceId,
                this.presentationState,
                this.durationTicks
        );
        return true;
    }

    public boolean finish(ClientLevel world, long sessionId, long finalDayTime, SleepAnimationStopReason reason) {
        boolean replayCompatActive = SeamlessSleepClientConfigManager.get().replayCompatibilityEnabled
                && ReplayPlaybackCompat.isReplayPlaybackActive();
        if (!replayCompatActive && sessionId <= this.closedSessionId) {
            return false;
        }
        if (!replayCompatActive && sessionId < this.sessionId) {
            return false;
        }

        if (this.needsFrameTick()
                && this.activeWorldIdentity != 0
                && world != null
                && this.activeWorldIdentity != System.identityHashCode(world)) {
            this.resetForWorldExit("finish_world_mismatch");
            return false;
        }

        boolean shouldLockFinishedDayTime = reason == SleepAnimationStopReason.FINISHED
                && !replayCompatActive
                && world != null;
        long previousVisualDayTime = this.currentVisualDayTime;
        SleepAnimationMode previousMode = this.mode;
        SleepAnimationPhase previousPhase = this.phase;
        long acceptedFinalDayTime = shouldLockFinishedDayTime
                ? Math.max(previousVisualDayTime, finalDayTime)
                : finalDayTime;

        this.sessionId = sessionId;
        if (!replayCompatActive) {
            this.closedSessionId = Math.max(this.closedSessionId, sessionId);
        }
        this.currentAuthoritativeDayTime = finalDayTime;
        this.phase = reason == SleepAnimationStopReason.FINISHED
                ? SleepAnimationPhase.FINISHED
                : SleepAnimationPhase.CANCELLED;
        this.clearPlaybackState(false);

        if (shouldLockFinishedDayTime) {
            this.currentVisualDayTime = acceptedFinalDayTime;
            this.cachedProgress = 1.0D;
            world.getLevelData().setDayTime(acceptedFinalDayTime);
            this.startFinishedDayTimeLock(world, acceptedFinalDayTime);
        } else {
            this.clearFinishedDayTimeLock();
            long backwardDistance = chronologicalAheadDifference(previousVisualDayTime, finalDayTime);
            boolean canHoldNormalCancellation = !replayCompatActive
                    && world != null
                    && previousMode == SleepAnimationMode.NORMAL_SLEEP
                    && previousPhase == SleepAnimationPhase.CANCEL_BRAKING
                    && backwardDistance > 1L
                    && backwardDistance <= MAX_CANCEL_AUTHORITY_HOLD_DISTANCE;
            if (canHoldNormalCancellation) {
                this.startCancelAuthorityHold(world, previousVisualDayTime, finalDayTime);
            } else {
                this.currentVisualDayTime = finalDayTime;
                this.cachedProgress = 0.0D;
                if (world != null) {
                    world.getLevelData().setDayTime(finalDayTime);
                }
            }
        }

        Constants.debug("Client sleep animation finished/stopped: {} (session {})", reason, sessionId);
        return true;
    }

    public void tickFinishedDayTimeLock(ClientLevel world, boolean playerStillSleeping) {
        if (!this.finishedDayTimeLockActive) {
            return;
        }
        if (world == null || this.finishedDayTimeLockWorldIdentity != System.identityHashCode(world)) {
            this.clearFinishedDayTimeLock();
            return;
        }

        if (playerStillSleeping) {
            this.finishedDayTimeLockPostWakeDeadlineNanos = 0L;
        } else {
            long now = System.nanoTime();
            if (this.finishedDayTimeLockPostWakeDeadlineNanos == 0L) {
                this.finishedDayTimeLockPostWakeDeadlineNanos = now + FINISHED_LOCK_POST_WAKE_NANOS;
            } else if (now - this.finishedDayTimeLockPostWakeDeadlineNanos >= 0L) {
                this.clearFinishedDayTimeLock();
                return;
            }
        }

        world.getLevelData().setDayTime(this.finishedDayTimeLockDayTime);
        this.currentAuthoritativeDayTime = this.finishedDayTimeLockDayTime;
        this.currentVisualDayTime = this.finishedDayTimeLockDayTime;
    }

    public void tick(ClientLevel world, DeltaTracker deltaTracker) {
        if (!this.needsFrameTick()) {
            return;
        }

        this.resetIfWorldMismatch(world, "world_changed");
        if (!this.needsFrameTick()) {
            return;
        }

        if (this.presentationState == PresentationState.CANCEL_AUTHORITY_HOLD) {
            this.tickCancelAuthorityHold(world);
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

        if (!this.replayCompatMode && this.presentationState == PresentationState.HOLDING_AT_TARGET) {
            this.cachedProgress = 1.0D;
            this.currentVisualDayTime = Math.max(this.currentVisualDayTime, this.endTimeOfDay);
            world.getLevelData().setDayTime(this.currentVisualDayTime);
            return;
        }

        double x = this.replayCompatMode
                ? this.computeReplayCompatProgress(replayStep)
                : this.advanceLivePresentation(deltaTracker) / this.durationTicks;
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
            this.presentationState = PresentationState.HOLDING_AT_TARGET;
        }
    }

    public void tick(ClientLevel world) {
        this.tick(world, DeltaTracker.ONE);
    }

    private double advanceLivePresentation(DeltaTracker deltaTracker) {
        this.presentationElapsedTicks += sampleLivePresentationDeltaTicks(deltaTracker);
        return this.presentationElapsedTicks;
    }

    public void observeAuthoritativeDayTime(long authoritativeDayTime) {
        if (this.presentationState == PresentationState.CANCEL_AUTHORITY_HOLD) {
            this.lastObservedAuthoritativeDayTime = authoritativeDayTime;
        }
    }

    private void startCancelAuthorityHold(ClientLevel world, long visualDayTime, long finalDayTime) {
        this.active = false;
        this.presentationState = PresentationState.CANCEL_AUTHORITY_HOLD;
        this.cancelHoldVisualDayTime = visualDayTime;
        this.lastObservedAuthoritativeDayTime = finalDayTime;
        this.cancelHoldDeadlineNanos = System.nanoTime() + MAX_CANCEL_AUTHORITY_HOLD_NANOS;
        this.currentVisualDayTime = visualDayTime;
        this.activeWorldIdentity = System.identityHashCode(world);
        world.getLevelData().setDayTime(visualDayTime);
    }

    private void tickCancelAuthorityHold(ClientLevel world) {
        long now = System.nanoTime();
        boolean authoritativeCaughtUp = hasAuthoritativeReached(
                this.lastObservedAuthoritativeDayTime,
                this.cancelHoldVisualDayTime
        );
        boolean timedOut = now - this.cancelHoldDeadlineNanos >= 0L;
        if (authoritativeCaughtUp || timedOut) {
            long synchronizedDayTime = this.lastObservedAuthoritativeDayTime;
            world.getLevelData().setDayTime(synchronizedDayTime);
            this.currentAuthoritativeDayTime = synchronizedDayTime;
            this.currentVisualDayTime = synchronizedDayTime;
            this.presentationState = PresentationState.INACTIVE;
            this.activeWorldIdentity = 0;
            this.cancelHoldDeadlineNanos = 0L;
            return;
        }

        world.getLevelData().setDayTime(this.cancelHoldVisualDayTime);
        this.currentVisualDayTime = this.cancelHoldVisualDayTime;
        this.cachedProgress = 0.0D;
    }

    private static double samplePresentationDeltaTicks(DeltaTracker deltaTracker) {
        double deltaTicks = deltaTracker == null ? 1.0D : deltaTracker.getGameTimeDeltaTicks();
        if (!Double.isFinite(deltaTicks) || deltaTicks <= 0.0D) {
            return 0.0D;
        }
        return Math.min(MAX_PRESENTATION_DELTA_TICKS, deltaTicks);
    }

    private double sampleLivePresentationDeltaTicks(DeltaTracker deltaTracker) {
        long nowNanos = System.nanoTime();
        if (this.livePresentationLastAdvanceNanos <= 0L) {
            this.livePresentationLastAdvanceNanos = nowNanos;
            return samplePresentationDeltaTicks(deltaTracker);
        }

        long elapsedNanos = nowNanos - this.livePresentationLastAdvanceNanos;
        if (elapsedNanos <= 0L) {
            return 0.0D;
        }
        this.livePresentationLastAdvanceNanos = nowNanos;

        double deltaTicks = elapsedNanos / (double) NANOS_PER_TICK;
        if (!Double.isFinite(deltaTicks) || deltaTicks <= 0.0D) {
            return 0.0D;
        }
        return Math.min(MAX_PRESENTATION_DELTA_TICKS, deltaTicks);
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

        if (!this.replayCompatMode && this.presentationState == PresentationState.HOLDING_AT_TARGET) {
            this.cachedProgress = 1.0D;
            this.currentVisualDayTime = Math.max(this.currentVisualDayTime, this.endTimeOfDay);
            world.getLevelData().setDayTime(this.currentVisualDayTime);
            return;
        }

        double elapsedTicks = this.replayCompatMode
                ? replayStep.elapsedTicks()
                : this.advanceLivePresentation(deltaTracker);
        double distance = SleepAnimationState.madeInHeavenDistanceForElapsed(elapsedTicks);
        long newTimeOfDay = this.startTimeOfDay + Math.max(0L, (long) distance);
        if (newTimeOfDay >= this.endTimeOfDay) {
            newTimeOfDay = this.endTimeOfDay;
            if (this.replayCompatMode) {
                this.finishReplayVisualLocally(world, newTimeOfDay);
                return;
            }
            this.presentationState = PresentationState.HOLDING_AT_TARGET;
        }

        if (!this.replayCompatMode) {
            newTimeOfDay = Math.max(newTimeOfDay, this.currentVisualDayTime);
        }
        world.getLevelData().setDayTime(newTimeOfDay);
        this.currentVisualDayTime = newTimeOfDay;

        long delta = this.endTimeOfDay - this.startTimeOfDay;
        this.cachedProgress = delta <= 0L ? 0.0D : clamp01((newTimeOfDay - this.startTimeOfDay) / (double) delta);
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
        return this.phase == SleepAnimationPhase.BRAKING || this.phase == SleepAnimationPhase.CANCEL_BRAKING
                ? SleepAnimationState.brakeEase(progress)
                : SleepAnimationState.integralEase(progress);
    }

    private void clearPlaybackState(boolean keepWorldAnchor) {
        this.active = false;
        this.presentationState = PresentationState.INACTIVE;
        this.presentationElapsedTicks = 0.0D;
        this.livePresentationLastAdvanceNanos = 0L;
        this.cancelHoldDeadlineNanos = 0L;
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

    private void startFinishedDayTimeLock(ClientLevel world, long finalDayTime) {
        this.finishedDayTimeLockActive = true;
        this.finishedDayTimeLockDayTime = finalDayTime;
        this.finishedDayTimeLockWorldIdentity = System.identityHashCode(world);
        this.finishedDayTimeLockPostWakeDeadlineNanos = 0L;
        this.presentationState = PresentationState.FINISHED_LOCK;
    }

    private void clearFinishedDayTimeLock() {
        this.finishedDayTimeLockActive = false;
        this.finishedDayTimeLockDayTime = 0L;
        this.finishedDayTimeLockWorldIdentity = 0;
        this.finishedDayTimeLockPostWakeDeadlineNanos = 0L;
        if (!this.active && this.presentationState == PresentationState.FINISHED_LOCK) {
            this.presentationState = PresentationState.INACTIVE;
        }
    }

    private static int computeCheckpointDurationTicks(long payloadStartTime,
                                                      long payloadEndTime,
                                                      int payloadDurationTicks,
                                                      long payloadCurrentDayTime,
                                                      SleepAnimationPhase phase,
                                                      long visualStartTime,
                                                      double previousSpeed) {
        int fullDuration = Math.max(1, payloadDurationTicks);
        double payloadProgress = computeCurveProgressFromDayTime(
                payloadCurrentDayTime,
                payloadStartTime,
                payloadEndTime,
                phase
        );
        int nominalRemaining = Math.max(1, (int) Math.ceil(fullDuration * (1.0D - payloadProgress)));
        long visualRemaining = Math.max(0L, payloadEndTime - visualStartTime);
        if ((phase != SleepAnimationPhase.BRAKING && phase != SleepAnimationPhase.CANCEL_BRAKING)
                || visualRemaining <= 0L
                || !Double.isFinite(previousSpeed)
                || previousSpeed <= 0.0D) {
            return nominalRemaining;
        }

        int continuityDuration = Math.max(1, (int) Math.round(3.0D * visualRemaining / previousSpeed));
        int minimumPlausible = Math.max(1, nominalRemaining / 2);
        int maximumPlausible = Math.max(minimumPlausible, nominalRemaining * 2);
        if (continuityDuration < minimumPlausible || continuityDuration > maximumPlausible) {
            return nominalRemaining;
        }
        return continuityDuration;
    }

    private static long clampLiveAnchor(long dayTime, long startTime, long endTime) {
        if (endTime < startTime) {
            return dayTime;
        }
        return Math.max(startTime, Math.min(dayTime, endTime));
    }

    private static double computeCurveProgressFromDayTime(long dayTime,
                                                          long startTime,
                                                          long endTime,
                                                          SleepAnimationPhase phase) {
        long delta = endTime - startTime;
        if (delta <= 0L) {
            return dayTime >= endTime ? 1.0D : 0.0D;
        }

        double easedProgress = clamp01((dayTime - startTime) / (double) delta);
        if (easedProgress <= 0.0D || easedProgress >= 1.0D) {
            return easedProgress;
        }

        double low = 0.0D;
        double high = 1.0D;
        for (int i = 0; i < 24; i++) {
            double mid = (low + high) * 0.5D;
            if (curveEase(mid, phase) < easedProgress) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5D;
    }

    private static double curveEase(double progress, SleepAnimationPhase phase) {
        return phase == SleepAnimationPhase.BRAKING || phase == SleepAnimationPhase.CANCEL_BRAKING
                ? SleepAnimationState.brakeEase(progress)
                : SleepAnimationState.integralEase(progress);
    }

    private static long chronologicalAheadDifference(long visualDayTime, long authoritativeDayTime) {
        long directDifference = visualDayTime - authoritativeDayTime;
        if (directDifference > 0L) {
            return directDifference;
        }

        long wrappedDifference = Math.floorMod(directDifference, DAY_TICKS);
        return wrappedDifference <= DAY_TICKS / 2L ? wrappedDifference : 0L;
    }

    private static boolean hasAuthoritativeReached(long authoritativeDayTime, long visualDayTime) {
        if (authoritativeDayTime >= visualDayTime - 1L) {
            return true;
        }
        long remainingWrapped = Math.floorMod(visualDayTime - authoritativeDayTime, DAY_TICKS);
        return remainingWrapped <= 1L || remainingWrapped > DAY_TICKS / 2L;
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

    private enum PresentationState {
        INACTIVE,
        RUNNING,
        HOLDING_AT_TARGET,
        CANCEL_AUTHORITY_HOLD,
        FINISHED_LOCK
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
