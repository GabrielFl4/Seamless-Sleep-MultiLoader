package net.aqualoco.sec.sleep;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.ReplayPlaybackCompat;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.OptionalLong;

// Client-side mirror of the sleep transition timing sent by the server.
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

    public void tick(ClientLevel world, float partialTick) {
        if (!this.active) {
            return;
        }

        double x = this.replayCompatMode
                ? this.computeReplayCompatProgress(partialTick)
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
        this.tick(world, 1.0F);
    }

    private double computeNormalProgress() {
        long now = System.currentTimeMillis();
        long elapsedMs = now - this.startMillis;
        double totalMs = (double) this.durationTicks * 50.0;
        return totalMs <= 0.0 ? 1.0 : Math.min(1.0, elapsedMs / totalMs);
    }

    private double computeReplayCompatProgress(float partialTick) {
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

        this.replayCompatElapsedTicksFallback += Math.max(0.0F, partialTick);
        if (!this.loggedReplayTimelineFallback) {
            this.loggedReplayTimelineFallback = true;
            Constants.debug("Replay timeline was unavailable; using partial-tick fallback for sleep animation progress.");
        }

        if (this.durationTicks <= 0) {
            return 1.0;
        }

        return Math.min(1.0, this.replayCompatElapsedTicksFallback / (double) this.durationTicks);
    }
}
