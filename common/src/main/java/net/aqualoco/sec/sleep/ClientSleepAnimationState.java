package net.aqualoco.sec.sleep;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.ReplayPlaybackCompat;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.OptionalLong;

// Client mirror for the active sleep transition.
public final class ClientSleepAnimationState {

    private boolean active;
    private long startTimeOfDay;
    private long endTimeOfDay;
    private int durationTicks;
    private long startMillis;
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

    public OptionalLong getReplayTimelineMillisSnapshot() {
        if (!this.isReplayCompatMode()) {
            return OptionalLong.empty();
        }
        return ReplayPlaybackCompat.getReplayTimelineMillis();
    }

    public double getProgress() {
        if (!this.active) {
            return 0.0D;
        }

        double progress = this.replayCompatMode
                ? this.computeReplayCompatProgressSnapshot()
                : this.computeNormalProgress();
        return clamp01(progress);
    }

    public double getEasedVelocityFactor() {
        if (!this.active) {
            return 0.0D;
        }

        double x = this.getProgress();
        double epsilon = 1.0D / Math.max(20.0D, this.durationTicks);
        double from = Math.max(0.0D, x - epsilon);
        double to = Math.min(1.0D, x + epsilon);
        if (to <= from) {
            return 0.0D;
        }

        double easedFrom = SleepAnimationState.integralEase(from);
        double easedTo = SleepAnimationState.integralEase(to);
        return Math.max(0.0D, (easedTo - easedFrom) / (to - from));
    }

    public double getCurrentDayTimeSpeedPerTick() {
        if (!this.active || this.durationTicks <= 0) {
            return 0.0D;
        }

        long deltaTime = Math.max(0L, this.endTimeOfDay - this.startTimeOfDay);
        if (deltaTime <= 0L) {
            return 0.0D;
        }

        double baseSpeedPerTick = deltaTime / (double) this.durationTicks;
        return Math.max(0.0D, this.getEasedVelocityFactor() * baseSpeedPerTick);
    }

    public void reset() {
        this.active = false;
        this.replayCompatMode = false;
        this.replayCompatStartTimelineMillis = -1L;
        this.replayCompatElapsedTicksFallback = 0.0F;
        this.loggedReplayTimelineFallback = false;
    }

    public void start(long startTimeOfDay, long endTimeOfDay, int durationTicks, long serverStartMillis) {
        long now = System.currentTimeMillis();
        boolean replayCompatEnabled = SeamlessSleepClientConfigManager.get().replayCompatibilityEnabled;
        boolean replayCompatActive = replayCompatEnabled && ReplayPlaybackCompat.isReplayPlaybackActive();
        long elapsedSinceServerStart = Math.max(0L, now - serverStartMillis);
        int adjustedDuration;
        if (replayCompatActive) {
            adjustedDuration = Math.max(1, durationTicks);
        } else {
            // In normal mode, compensate packet delay so the client can catch up.
            adjustedDuration = (int) Math.max(1L, durationTicks - elapsedSinceServerStart / 50L);
        }

        this.startTimeOfDay = startTimeOfDay;
        this.endTimeOfDay = endTimeOfDay;
        this.durationTicks = adjustedDuration;
        this.startMillis = now;
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
        }

        Constants.debug(
                "Client sleep animation mode: {}",
                replayCompatActive ? "REPLAY_COMPAT" : "NORMAL"
        );
    }

    public void tick(ClientLevel world, DeltaTracker deltaTracker) {
        if (!this.active) {
            return;
        }

        double x = this.replayCompatMode
                ? this.computeReplayCompatProgress(deltaTracker)
                : this.computeNormalProgress();
        double eased = SleepAnimationState.integralEase(x);

        long delta = this.endTimeOfDay - this.startTimeOfDay;
        long newTimeOfDay = this.startTimeOfDay + (long) (delta * eased);

        world.getLevelData().setDayTime(newTimeOfDay);

        if (x >= 1.0) {
            this.reset();
        }
    }

    public void tick(ClientLevel world) {
        this.tick(world, DeltaTracker.ONE);
    }

    private double computeNormalProgress() {
        long now = System.currentTimeMillis();
        long elapsedMs = now - this.startMillis;
        double totalMs = (double) this.durationTicks * 50.0;
        return totalMs <= 0.0 ? 1.0 : Math.min(1.0, elapsedMs / totalMs);
    }

    private double computeReplayCompatProgress(DeltaTracker deltaTracker) {
        OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
        if (replayTimeline.isPresent()) {
            long replayNowMs = replayTimeline.getAsLong();

            if (this.replayCompatStartTimelineMillis < 0L) {
                long fallbackElapsedMs = (long) (this.replayCompatElapsedTicksFallback * 50.0F);
                this.replayCompatStartTimelineMillis = replayNowMs - fallbackElapsedMs;
            }

            long elapsedReplayMs = Math.max(0L, replayNowMs - this.replayCompatStartTimelineMillis);
            double totalMs = (double) this.durationTicks * 50.0;
            return totalMs <= 0.0 ? 1.0 : Math.min(1.0, elapsedReplayMs / totalMs);
        }

        this.replayCompatElapsedTicksFallback += Math.max(0.0F, deltaTracker.getGameTimeDeltaTicks());
        if (!this.loggedReplayTimelineFallback) {
            this.loggedReplayTimelineFallback = true;
            Constants.debug("Replay timeline was unavailable; using DeltaTracker fallback for sleep animation progress.");
        }

        if (this.durationTicks <= 0) {
            return 1.0;
        }

        return Math.min(1.0, this.replayCompatElapsedTicksFallback / (double) this.durationTicks);
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

            long elapsedReplayMs = Math.max(0L, replayNowMs - replayStartMs);
            double totalMs = (double) this.durationTicks * 50.0;
            return totalMs <= 0.0 ? 1.0 : Math.min(1.0, elapsedReplayMs / totalMs);
        }

        if (this.durationTicks <= 0) {
            return 1.0;
        }

        return Math.min(1.0, this.replayCompatElapsedTicksFallback / (double) this.durationTicks);
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
}
