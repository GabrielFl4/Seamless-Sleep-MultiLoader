package net.aqualoco.sec.sleep;

import net.minecraft.server.world.ServerWorld;

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
        this.durationTicks = computeDurationTicks(delta);

        this.active = true;
        this.startTimeOfDay = currentTime;
        this.endTimeOfDay = targetTime;
        this.startMillis = System.currentTimeMillis();
    }

    public void cancel() {
        this.active = false;
    }

    public void tick(ServerWorld world) {
        if (!this.active) {
            return;
        }

        long now = System.currentTimeMillis();
        double elapsedMs = (double) (now - this.startMillis);
        if (elapsedMs <= 0.0D) {
            world.setTimeOfDay(this.startTimeOfDay);
            return;
        }

        double totalMs = this.durationTicks * 50.0D;
        double x = elapsedMs / totalMs;
        if (x >= 1.0D) {
            this.active = false;
            world.setTimeOfDay(this.endTimeOfDay);
            return;
        }

        // Clamp por segurança
        if (x < 0.0D) {
            x = 0.0D;
        } else if (x > 1.0D) {
            x = 1.0D;
        }

        double eased = integralEase(x);
        long delta = this.endTimeOfDay - this.startTimeOfDay;
        long interpolated = this.startTimeOfDay + (long) (delta * eased);
        world.setTimeOfDay(interpolated);
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

    // Curva integral
    // com desaceleração mais forte no final por expoente 3.
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

