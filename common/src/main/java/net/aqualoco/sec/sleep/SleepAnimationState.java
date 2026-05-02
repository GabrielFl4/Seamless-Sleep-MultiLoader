package net.aqualoco.sec.sleep;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.minecraft.server.level.ServerLevel;

// Server driver for sleep day-time interpolation.
public final class SleepAnimationState {
    private static final long DAY_TICKS = 24000L;
    private static final long FULL_NIGHT_TICKS = 12000L;
    private static final int MIN_DURATION_TICKS = 40;   // ~2s
    private static final int MAX_DURATION_TICKS = 180;  // ~9s
    private static final long VISUAL_FINISH_DAY_TIME_THRESHOLD = 2L;
    private static final long UNSET_START_GAME_TIME = Long.MIN_VALUE;
    private static final int MADE_IN_HEAVEN_MAX_CYCLES = 21;
    private static final int MADE_IN_HEAVEN_MANUAL_BRAKE_TICKS = 60;
    private static final int MADE_IN_HEAVEN_AUTO_BRAKE_TICKS = 120;
    private static final int COMMAND_TIMELAPSE_STOP_BRAKE_TICKS = 60;
    private static final int MADE_IN_HEAVEN_ACCELERATION_TICKS = 400;
    private static final double MADE_IN_HEAVEN_MAX_SPEED_DAY_TIME_PER_TICK = 2400.0D;

    private boolean active;
    private long nextSessionId;
    private long sessionId = -1L;
    private long sequenceId;
    private SleepAnimationPhase phase = SleepAnimationPhase.IDLE;
    private SleepAnimationMode mode = SleepAnimationMode.NORMAL_SLEEP;
    private SleepAnimationVisualContext visualContext = SleepAnimationVisualContext.NIGHT;
    private SleepAnimationSoundMode soundMode = SleepAnimationSoundMode.NONE;
    private long startTimeOfDay;
    private long endTimeOfDay;
    private int durationTicks;
    private long serverStartGameTime = UNSET_START_GAME_TIME;
    private long lastAppliedDayTime;
    private double cachedProgress;
    private boolean wakePlayersOnFinish;

    public boolean isActive() {
        return this.active;
    }

    public boolean start(ServerLevel world, long currentTime, long targetTime, SleepAnimationMode mode) {
        return this.start(world, currentTime, targetTime, mode, SleepAnimationVisualContext.NIGHT);
    }

    public boolean start(ServerLevel world,
                         long currentTime,
                         long targetTime,
                         SleepAnimationMode mode,
                         SleepAnimationVisualContext visualContext) {
        long startGameTime = world == null ? UNSET_START_GAME_TIME : world.getGameTime();
        return this.startFiniteInternal(
                currentTime,
                targetTime,
                startGameTime,
                mode,
                visualContext,
                SleepAnimationSoundMode.NONE,
                -1,
                true
        );
    }

    public void start(long currentTime, long targetTime) {
        this.startFiniteInternal(
                currentTime,
                targetTime,
                UNSET_START_GAME_TIME,
                SleepAnimationMode.NORMAL_SLEEP,
                SleepAnimationVisualContext.NIGHT,
                SleepAnimationSoundMode.NONE,
                -1,
                true
        );
    }

    public boolean startExplicit(ServerLevel world,
                                 long currentTime,
                                 long targetTime,
                                 int durationTicks,
                                 SleepAnimationMode mode,
                                 SleepAnimationVisualContext visualContext,
                                 SleepAnimationSoundMode soundMode) {
        long startGameTime = world == null ? UNSET_START_GAME_TIME : world.getGameTime();
        return this.startFiniteInternal(
                currentTime,
                targetTime,
                startGameTime,
                mode,
                visualContext,
                soundMode,
                durationTicks,
                false
        );
    }

    public boolean startMadeInHeavenBed(ServerLevel world, long currentTime) {
        long targetTime = computeMadeInHeavenAutoEndTime(currentTime);
        if (targetTime <= currentTime) {
            return false;
        }

        long startGameTime = world == null ? UNSET_START_GAME_TIME : world.getGameTime();
        this.active = true;
        this.sessionId = ++this.nextSessionId;
        this.sequenceId = 0L;
        this.phase = SleepAnimationPhase.RUNNING;
        this.mode = SleepAnimationMode.MADE_IN_HEAVEN_BED;
        this.visualContext = SleepAnimationVisualContext.MADE_IN_HEAVEN;
        this.soundMode = SleepAnimationSoundMode.NONE;
        this.startTimeOfDay = currentTime;
        this.endTimeOfDay = targetTime;
        this.durationTicks = MADE_IN_HEAVEN_ACCELERATION_TICKS;
        this.serverStartGameTime = startGameTime;
        this.lastAppliedDayTime = currentTime;
        this.cachedProgress = 0.0D;
        this.wakePlayersOnFinish = true;

        Constants.debug(
                "Made In Heaven bed animation started on server (session {}, {} -> {}, gameTime {})",
                this.sessionId,
                this.startTimeOfDay,
                this.endTimeOfDay,
                this.serverStartGameTime
        );

        return true;
    }

    public boolean startBraking(ServerLevel world) {
        if (!this.active || this.mode != SleepAnimationMode.MADE_IN_HEAVEN_BED || this.phase != SleepAnimationPhase.RUNNING) {
            return false;
        }

        long currentTime = Math.max(world.getDayTime(), this.lastAppliedDayTime);
        double velocity = this.getCurrentLogicalWorldRate();
        long brakeDistance = computeBrakeDistance(velocity, MADE_IN_HEAVEN_MANUAL_BRAKE_TICKS);
        return this.startBrakingInternal(
                world,
                currentTime + brakeDistance,
                MADE_IN_HEAVEN_MANUAL_BRAKE_TICKS,
                false,
                "Made In Heaven bed animation braking"
        );
    }

    public boolean shouldStartMadeInHeavenAutoBrake(ServerLevel world) {
        if (!this.active
                || this.mode != SleepAnimationMode.MADE_IN_HEAVEN_BED
                || this.phase != SleepAnimationPhase.RUNNING
                || !this.wakePlayersOnFinish) {
            return false;
        }

        long currentTime = Math.max(world.getDayTime(), this.lastAppliedDayTime);
        long remaining = this.endTimeOfDay - currentTime;
        if (remaining <= 0L) {
            return false;
        }

        long brakeDistance = computeBrakeDistance(this.getCurrentLogicalWorldRate(), MADE_IN_HEAVEN_AUTO_BRAKE_TICKS);
        return remaining <= brakeDistance;
    }

    public boolean startMadeInHeavenAutoBraking(ServerLevel world) {
        if (!this.shouldStartMadeInHeavenAutoBrake(world)) {
            return false;
        }

        return this.startBrakingInternal(
                world,
                this.endTimeOfDay,
                MADE_IN_HEAVEN_AUTO_BRAKE_TICKS,
                true,
                "Made In Heaven bed animation auto braking"
        );
    }

    public boolean startCommandTimelapseStopBraking(ServerLevel world) {
        if (!this.active || this.mode != SleepAnimationMode.COMMAND_TIMELAPSE || this.phase != SleepAnimationPhase.RUNNING) {
            return false;
        }

        long currentTime = Math.max(world.getDayTime(), this.lastAppliedDayTime);
        double velocity = this.getCurrentLogicalWorldRate();
        long brakeDistance = computeBrakeDistance(velocity, COMMAND_TIMELAPSE_STOP_BRAKE_TICKS);
        return this.startBrakingInternal(
                world,
                currentTime + brakeDistance,
                COMMAND_TIMELAPSE_STOP_BRAKE_TICKS,
                false,
                "Command timelapse braking"
        );
    }

    private boolean startBrakingInternal(ServerLevel world,
                                         long targetTime,
                                         int brakeDurationTicks,
                                         boolean wakePlayersOnFinish,
                                         String debugLabel) {
        long currentTime = Math.max(world.getDayTime(), this.lastAppliedDayTime);
        if (!this.active || this.phase != SleepAnimationPhase.RUNNING || targetTime <= currentTime) {
            return false;
        }

        this.sequenceId++;
        this.phase = SleepAnimationPhase.BRAKING;
        this.visualContext = SleepAnimationVisualContext.MADE_IN_HEAVEN;
        this.startTimeOfDay = currentTime;
        this.endTimeOfDay = targetTime;
        this.durationTicks = Math.max(1, brakeDurationTicks);
        this.serverStartGameTime = world.getGameTime();
        this.lastAppliedDayTime = currentTime;
        this.cachedProgress = 0.0D;
        this.wakePlayersOnFinish = wakePlayersOnFinish;

        Constants.debug(
                "{} (session {}, {} -> {}, duration {} ticks)",
                debugLabel,
                this.sessionId,
                this.startTimeOfDay,
                this.endTimeOfDay,
                this.durationTicks
        );

        return true;
    }

    private boolean startFiniteInternal(long currentTime,
                                        long targetTime,
                                        long startGameTime,
                                        SleepAnimationMode animationMode,
                                        SleepAnimationVisualContext animationVisualContext,
                                        SleepAnimationSoundMode animationSoundMode,
                                        int explicitDurationTicks,
                                        boolean applyConfiguredMultiplier) {
        if (targetTime <= currentTime) {
            this.active = false;
            this.phase = SleepAnimationPhase.IDLE;
            this.cachedProgress = 0.0D;
            this.wakePlayersOnFinish = false;
            return false;
        }

        long delta = targetTime - currentTime;
        if (explicitDurationTicks > 0) {
            this.durationTicks = Math.max(1, explicitDurationTicks);
        } else {
            int baseDuration = computeDurationTicks(delta);
            double multiplier = applyConfiguredMultiplier
                    ? SeamlessSleepServerConfigManager.get().sleepAnimationDurationMultiplier
                    : 1.0D;
            this.durationTicks = applyDurationMultiplier(baseDuration, multiplier);
        }

        this.active = true;
        this.sessionId = ++this.nextSessionId;
        this.sequenceId = 0L;
        this.phase = SleepAnimationPhase.RUNNING;
        this.mode = animationMode == null ? SleepAnimationMode.NORMAL_SLEEP : animationMode;
        this.visualContext = resolveVisualContext(this.mode, animationVisualContext);
        this.soundMode = animationSoundMode == null ? SleepAnimationSoundMode.NONE : animationSoundMode;
        this.startTimeOfDay = currentTime;
        this.endTimeOfDay = targetTime;
        this.serverStartGameTime = startGameTime;
        this.lastAppliedDayTime = currentTime;
        this.cachedProgress = 0.0D;
        this.wakePlayersOnFinish = this.mode.wakesPlayersOnFinish();

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
        this.wakePlayersOnFinish = false;
    }

    public void tick(ServerLevel world) {
        if (!this.active) {
            return;
        }

        if (this.serverStartGameTime == UNSET_START_GAME_TIME) {
            this.serverStartGameTime = world.getGameTime();
        }

        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            this.tickMadeInHeavenRunning(world);
            return;
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

        double eased = easeForPhase(x, this.phase);
        long delta = this.endTimeOfDay - this.startTimeOfDay;
        long interpolated = this.startTimeOfDay + (long) (delta * eased);
        if (this.endTimeOfDay >= this.startTimeOfDay) {
            interpolated = Math.max(interpolated, this.lastAppliedDayTime);
            if (this.endTimeOfDay - interpolated <= VISUAL_FINISH_DAY_TIME_THRESHOLD) {
                this.finish(world);
                return;
            }
        }

        world.setDayTime(interpolated);
        this.lastAppliedDayTime = interpolated;
        this.cachedProgress = x;
    }

    private void tickMadeInHeavenRunning(ServerLevel world) {
        long elapsedTicks = Math.max(0L, world.getGameTime() - this.serverStartGameTime);
        double distance = madeInHeavenDistanceForElapsed(elapsedTicks);
        long interpolated = this.startTimeOfDay + Math.max(0L, (long) distance);
        if (interpolated >= this.endTimeOfDay) {
            this.finish(world);
            return;
        }

        interpolated = Math.max(interpolated, this.lastAppliedDayTime);
        world.setDayTime(interpolated);
        this.lastAppliedDayTime = interpolated;

        long delta = this.endTimeOfDay - this.startTimeOfDay;
        this.cachedProgress = delta <= 0L ? 0.0D : clamp01((interpolated - this.startTimeOfDay) / (double) delta);
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

    public SleepAnimationVisualContext getVisualContext() {
        return this.visualContext;
    }

    public SleepAnimationSoundMode getSoundMode() {
        return this.soundMode;
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

    public boolean shouldWakePlayersOnFinish() {
        return this.wakePlayersOnFinish;
    }

    public void suppressWakePlayersOnFinish() {
        this.wakePlayersOnFinish = false;
    }

    public double getLogicalWorldRate() {
        return getCurrentLogicalWorldRate();
    }

    public double getAverageLogicalWorldRate() {
        if (!this.active) {
            return 1.0D;
        }

        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            return this.getCurrentLogicalWorldRate();
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

        if (this.mode == SleepAnimationMode.MADE_IN_HEAVEN_BED && this.phase == SleepAnimationPhase.RUNNING) {
            long elapsedTicks = 0L;
            if (this.serverStartGameTime != UNSET_START_GAME_TIME && this.lastAppliedDayTime > this.startTimeOfDay) {
                double distance = this.lastAppliedDayTime - this.startTimeOfDay;
                elapsedTicks = estimateMadeInHeavenElapsedTicks(distance);
            }
            return Math.max(1.0D, madeInHeavenVelocityForElapsed(elapsedTicks));
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

        double easedFrom = easeForPhase(from, this.phase);
        double easedTo = easeForPhase(to, this.phase);
        return Math.max(0.0D, (easedTo - easedFrom) / (to - from));
    }

    private static SleepAnimationVisualContext resolveVisualContext(SleepAnimationMode mode,
                                                                    SleepAnimationVisualContext visualContext) {
        SleepAnimationMode resolvedMode = mode == null ? SleepAnimationMode.NORMAL_SLEEP : mode;
        if (resolvedMode == SleepAnimationMode.MADE_IN_HEAVEN_BED
                || resolvedMode == SleepAnimationMode.COMMAND_TIMELAPSE) {
            return SleepAnimationVisualContext.MADE_IN_HEAVEN;
        }
        return visualContext == null ? SleepAnimationVisualContext.NIGHT : visualContext;
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

    public static long computeMadeInHeavenAutoEndTime(long currentTime) {
        long currentCycle = Math.floorDiv(currentTime, DAY_TICKS);
        long target = (currentCycle + MADE_IN_HEAVEN_MAX_CYCLES) * DAY_TICKS;
        if (target <= currentTime) {
            target += DAY_TICKS;
        }
        return target;
    }

    public static double madeInHeavenVelocityForElapsed(double elapsedTicks) {
        double elapsed = Math.max(0.0D, elapsedTicks);
        double x = Math.min(1.0D, elapsed / MADE_IN_HEAVEN_ACCELERATION_TICKS);
        double factor = x * x;
        return 1.0D + (MADE_IN_HEAVEN_MAX_SPEED_DAY_TIME_PER_TICK - 1.0D) * factor;
    }

    public static double madeInHeavenDistanceForElapsed(double elapsedTicks) {
        double elapsed = Math.max(0.0D, elapsedTicks);
        double maxExtra = MADE_IN_HEAVEN_MAX_SPEED_DAY_TIME_PER_TICK - 1.0D;
        double accelerationTicks = MADE_IN_HEAVEN_ACCELERATION_TICKS;
        if (elapsed <= accelerationTicks) {
            return elapsed + maxExtra * elapsed * elapsed * elapsed / (3.0D * accelerationTicks * accelerationTicks);
        }

        double accelerationDistance = accelerationTicks + maxExtra * accelerationTicks / 3.0D;
        return accelerationDistance + (elapsed - accelerationTicks) * MADE_IN_HEAVEN_MAX_SPEED_DAY_TIME_PER_TICK;
    }

    private static long estimateMadeInHeavenElapsedTicks(double distance) {
        double accelerationDistance = madeInHeavenDistanceForElapsed(MADE_IN_HEAVEN_ACCELERATION_TICKS);
        if (distance <= 0.0D) {
            return 0L;
        }
        if (distance >= accelerationDistance) {
            return MADE_IN_HEAVEN_ACCELERATION_TICKS
                    + (long) ((distance - accelerationDistance) / MADE_IN_HEAVEN_MAX_SPEED_DAY_TIME_PER_TICK);
        }

        double lo = 0.0D;
        double hi = MADE_IN_HEAVEN_ACCELERATION_TICKS;
        for (int i = 0; i < 16; i++) {
            double mid = (lo + hi) * 0.5D;
            if (madeInHeavenDistanceForElapsed(mid) < distance) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (long) hi;
    }

    private static long computeBrakeDistance(double velocity, int durationTicks) {
        double safeVelocity = Double.isFinite(velocity) ? Math.max(1.0D, velocity) : 1.0D;
        return Math.max(1L, Math.round(safeVelocity * Math.max(1, durationTicks) / 3.0D));
    }

    private static double easeForPhase(double x, SleepAnimationPhase phase) {
        if (phase == SleepAnimationPhase.BRAKING) {
            return brakeEase(x);
        }
        return integralEase(x);
    }

    public static double brakeEase(double x) {
        double clamped = clamp01(x);
        double inverse = 1.0D - clamped;
        return 1.0D - inverse * inverse * inverse;
    }
}
