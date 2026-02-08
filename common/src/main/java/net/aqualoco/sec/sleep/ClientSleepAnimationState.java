package net.aqualoco.sec.sleep;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.ReplayModCompat;
import net.minecraft.client.multiplayer.ClientLevel;

// Client-side mirror of the sleep transition timing sent by the server.
public final class ClientSleepAnimationState {

    private boolean active;
    private long startTimeOfDay;
    private long endTimeOfDay;
    private int durationTicks;
    private long startMillis;

    public boolean isActive() {
        return this.active;
    }

    public void reset() {
        this.active = false;
    }

    public void start(long startTimeOfDay, long endTimeOfDay, int durationTicks, long serverStartMillis) {
        long now = System.currentTimeMillis();
        boolean replayCompatActive = ReplayModCompat.isReplayPlaybackActive();
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

        Constants.LOG.info(
                "Sleep animation started on client [{}] ({} -> {}, duration {} ticks, server delta {} ms)",
                replayCompatActive ? "REPLAY_COMPAT" : "NORMAL",
                startTimeOfDay,
                endTimeOfDay,
                this.durationTicks,
                elapsedSinceServerStart
        );
    }

    public void tick(ClientLevel world) {
        if (!this.active) {
            return;
        }

        long now = System.currentTimeMillis();
        long elapsedMs = now - this.startMillis;
        double totalMs = (double) this.durationTicks * 50.0;

        double x = totalMs <= 0.0 ? 1.0 : Math.min(1.0, elapsedMs / totalMs);
        double eased = SleepAnimationState.integralEase(x);

        long delta = this.endTimeOfDay - this.startTimeOfDay;
        long newTimeOfDay = this.startTimeOfDay + (long) (delta * eased);

        world.getLevelData().setDayTime(newTimeOfDay);

        if (x >= 1.0) {
            this.active = false;
        }
    }
}
