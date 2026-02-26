package net.aqualoco.sec.sleep;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.minecraft.server.level.ServerLevel;

// Server-side state machine that eases world time during the sleep transition.
public final class SleepAnimationState {
    private static final long FULL_NIGHT_TICKS = 12000L;
    private static final int MIN_DURATION_TICKS = 40;   // ~2s
    private static final int MAX_DURATION_TICKS = 180;  // ~9s

    private boolean active;
    private long startTimeOfDay;
    private long endTimeOfDay;
    private int durationTicks;
    private long startMillis;

    public boolean isActive() {
        return this.active;
    }

    public void start(long currentTime, long targetTime) {
        if (targetTime <= currentTime) {
            this.active = false;
            return;
        }

        long delta = targetTime - currentTime;
        int baseDuration = computeDurationTicks(delta);
        double multiplier = SeamlessSleepServerConfigManager.get().sleepAnimationDurationMultiplier;
        this.durationTicks = applyDurationMultiplier(baseDuration, multiplier);

        this.active = true;
        this.startTimeOfDay = currentTime;
        this.endTimeOfDay = targetTime;
        this.startMillis = System.currentTimeMillis();

        Constants.debug(
                "Sleep animation started on server ({} -> {}, duration {} ticks)",
                this.startTimeOfDay,
                this.endTimeOfDay,
                this.durationTicks
        );
    }

    public void cancel() {
        this.active = false;
    }

    public void tick(ServerLevel world) {
        if (!this.active) {
            return;
        }

        long now = System.currentTimeMillis();
        double elapsedMs = (double) (now - this.startMillis);
        if (elapsedMs <= 0.0D) {
            world.setDayTime(this.startTimeOfDay);
            return;
        }

        double totalMs = this.durationTicks * 50.0D;
        double x = elapsedMs / totalMs;
        if (x >= 1.0D) {
            this.active = false;
            world.setDayTime(this.endTimeOfDay);
            return;
        }

        if (x < 0.0D) {
            x = 0.0D;
        } else if (x > 1.0D) {
            x = 1.0D;
        }

        double eased = integralEase(x);
        long delta = this.endTimeOfDay - this.startTimeOfDay;
        long interpolated = this.startTimeOfDay + (long) (delta * eased);
        world.setDayTime(interpolated);
    }

    public long getStartTimeOfDay() {
        return this.startTimeOfDay;
    }

    public long getEndTimeOfDay() {
        return this.endTimeOfDay;
    }

    public int getDurationTicks() {
        return this.durationTicks;
    }

    public long getStartMillis() {
        return this.startMillis;
    }

    private static int computeDurationTicks(long delta) {
        double fraction = delta / (double) FULL_NIGHT_TICKS;
        if (fraction < 0.0D) {
            fraction = 0.0D;
        } else if (fraction > 1.0D) {
            fraction = 1.0D;
        }

        int durationRange = MAX_DURATION_TICKS - MIN_DURATION_TICKS;
        int duration = MIN_DURATION_TICKS + (int) Math.round(durationRange * fraction);

        if (duration < MIN_DURATION_TICKS) {
            duration = MIN_DURATION_TICKS;
        } else if (duration > MAX_DURATION_TICKS) {
            duration = MAX_DURATION_TICKS;
        }

        return duration;
    }

    private static int applyDurationMultiplier(int baseDuration, double multiplier) {
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) {
            multiplier = 1.0D;
        }
        if (multiplier < 0.25D) {
            multiplier = 0.25D;
        } else if (multiplier > 8.0D) {
            multiplier = 8.0D;
        }

        int scaled = (int) Math.round(baseDuration * multiplier);
        int min = Math.max(1, (int) Math.round(MIN_DURATION_TICKS * multiplier));
        int max = Math.max(min, (int) Math.round(MAX_DURATION_TICKS * multiplier));

        if (scaled < min) {
            return min;
        }
        if (scaled > max) {
            return max;
        }
        return scaled;
    }

    public static double integralEase(double x) {
        if (x <= 0.0D) {
            return 0.0D;
        }
        if (x >= 1.0D) {
            return 1.0D;
        }

        double x2 = x * x;
        double base = (x2 - 1.0D) * Math.sqrt(1.0D - x2) + 1.0D;
        double oneMinus = 1.0D - base;
        return 1.0D - Math.pow(oneMinus, 3.0D);
    }
}
