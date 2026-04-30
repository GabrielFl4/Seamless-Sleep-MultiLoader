package net.aqualoco.sec.sleep;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.minecraft.server.level.ServerLevel;

// Server driver for sleep day-time interpolation.
public final class SleepAnimationState {
    private static final long FULL_NIGHT_TICKS = 12000L;
    private static final int MIN_DURATION_TICKS = 40;   // ~2s
    private static final int MAX_DURATION_TICKS = 180;  // ~9s
    private static final long UNSET_START_GAME_TIME = Long.MIN_VALUE;

    private boolean active;
    private long nextSessionId;
    private long sessionId = -1L;
    private long sequenceId;
    private SleepAnimationPhase phase = SleepAnimationPhase.IDLE;
    private SleepAnimationMode mode = SleepAnimationMode.NORMAL_SLEEP;
    private long startTimeOfDay;
    private long endTimeOfDay;
    private int durationTicks;
    private long serverStartGameTime = UNSET_START_GAME_TIME;
    private long lastAppliedDayTime;
    private double cachedProgress;

    public boolean isActive() {
        return this.active;
    }

    public boolean start(ServerLevel world, long currentTime, long targetTime, SleepAnimationMode mode) {
        long startGameTime = world == null ? UNSET_START_GAME_TIME : world.getGameTime();
        return this.startInternal(currentTime, targetTime, startGameTime, mode);
    }

    public void start(long currentTime, long targetTime) {
        this.startInternal(currentTime, targetTime, UNSET_START_GAME_TIME, SleepAnimationMode.NORMAL_SLEEP);
    }

    private boolean startInternal(long currentTime, long targetTime, long startGameTime, SleepAnimationMode animationMode) {
        if (targetTime <= currentTime) {
            this.active = false;
            this.phase = SleepAnimationPhase.IDLE;
            this.cachedProgress = 0.0D;
            return false;
        }

        long delta = targetTime - currentTime;
        int baseDuration = computeDurationTicks(delta);
        double multiplier = SeamlessSleepServerConfigManager.get().sleepAnimationDurationMultiplier;
        this.durationTicks = applyDurationMultiplier(baseDuration, multiplier);

        this.active = true;
        this.sessionId = ++this.nextSessionId;
        this.sequenceId = 0L;
        this.phase = SleepAnimationPhase.RUNNING;
        this.mode = animationMode == null ? SleepAnimationMode.NORMAL_SLEEP : animationMode;
        this.startTimeOfDay = currentTime;
        this.endTimeOfDay = targetTime;
        this.serverStartGameTime = startGameTime;
        this.lastAppliedDayTime = currentTime;
        this.cachedProgress = 0.0D;

        Constants.debug(
                "Sleep animation started on server (session {}, {} -> {}, duration {} ticks, gameTime {})",
                this.sessionId,
                this.startTimeOfDay,
                this.endTimeOfDay,
                this.durationTicks,
                this.serverStartGameTime
        );

        return true;
    }

    public void cancel() {
        this.active = false;
        this.phase = SleepAnimationPhase.CANCELLED;
        this.cachedProgress = 0.0D;
    }

    public void tick(ServerLevel world) {
        if (!this.active) {
            return;
        }

        if (this.serverStartGameTime == UNSET_START_GAME_TIME) {
            this.serverStartGameTime = world.getGameTime();
        }

        long elapsedTicks = Math.max(0L, world.getGameTime() - this.serverStartGameTime);
        if (this.durationTicks <= 0) {
            this.finish(world);
            return;
        }

        double x = elapsedTicks / (double) this.durationTicks;
        if (x >= 1.0D) {
            this.finish(world);
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
        if (this.endTimeOfDay >= this.startTimeOfDay) {
            interpolated = Math.max(interpolated, this.lastAppliedDayTime);
        }

        world.setDayTime(interpolated);
        this.lastAppliedDayTime = interpolated;
        this.cachedProgress = x;
    }

    private void finish(ServerLevel world) {
        this.active = false;
        this.phase = SleepAnimationPhase.FINISHED;
        this.cachedProgress = 1.0D;
        this.lastAppliedDayTime = this.endTimeOfDay;
        world.setDayTime(this.endTimeOfDay);
    }

    public long getSessionId() {
        return this.sessionId;
    }

    public long getSequenceId() {
        return this.sequenceId;
    }

    public SleepAnimationPhase getPhase() {
        return this.phase;
    }

    public SleepAnimationMode getMode() {
        return this.mode;
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

    public long getServerStartGameTime() {
        return this.serverStartGameTime == UNSET_START_GAME_TIME ? 0L : this.serverStartGameTime;
    }

    public long getLastAppliedDayTime() {
        return this.lastAppliedDayTime;
    }

    public boolean isFinishedNaturally() {
        return this.phase == SleepAnimationPhase.FINISHED;
    }

    /**
     * Legacy debug key retained for callers that only need a per-session marker.
     * This is now server gameTime, not wall-clock milliseconds.
     */
    public long getStartMillis() {
        return this.getServerStartGameTime();
    }

    public double getLogicalWorldRate() {
        return getCurrentLogicalWorldRate();
    }

    public double getAverageLogicalWorldRate() {
        if (!this.active) {
            return 1.0D;
        }

        long delta = this.endTimeOfDay - this.startTimeOfDay;
        if (delta <= 0L) {
            return 1.0D;
        }

        return Math.max(1.0D, delta / (double) Math.max(1, this.durationTicks));
    }

    public double getCurrentLogicalWorldRate() {
        if (!this.active || this.durationTicks <= 0) {
            return 1.0D;
        }

        long delta = this.endTimeOfDay - this.startTimeOfDay;
        if (delta <= 0L) {
            return 1.0D;
        }

        double baseRate = delta / (double) this.durationTicks;
        return Math.max(1.0D, getEasedVelocityFactor() * baseRate);
    }

    public double getProgress() {
        if (this.phase == SleepAnimationPhase.FINISHED) {
            return 1.0D;
        }
        if (!this.active) {
            return 0.0D;
        }
        return clamp01(this.cachedProgress);
    }

    public double getEasedVelocityFactor() {
        if (!this.active) {
            return 0.0D;
        }

        double x = getProgress();
        double epsilon = 1.0D / Math.max(20.0D, this.durationTicks);
        double from = Math.max(0.0D, x - epsilon);
        double to = Math.min(1.0D, x + epsilon);
        if (to <= from) {
            return 0.0D;
        }

        double easedFrom = integralEase(from);
        double easedTo = integralEase(to);
        return Math.max(0.0D, (easedTo - easedFrom) / (to - from));
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

    private static double clamp01(double value) {
        if (value <= 0.0D) {
            return 0.0D;
        }
        if (value >= 1.0D) {
            return 1.0D;
        }
        return value;
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
