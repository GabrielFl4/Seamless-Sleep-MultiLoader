package net.aqualoco.sec.client.sleepindicator.biomeclock;

import net.minecraft.util.Mth;

import java.util.Random;

// Handles the biome clock weather cloud entrance, exit, and subtle side drift.
public final class BiomeClockWeatherVisualState {
    private static final long ENTER_NANOS = 1_800_000_000L;
    private static final long EXIT_NANOS = 2_000_000_000L;
    private static final long DRIFT_INTERVAL_MIN_NANOS = 1_500_000_000L;
    private static final long DRIFT_INTERVAL_MAX_NANOS = 3_000_000_000L;
    private static final float DRIFT_X_RANGE = 2.5F;
    private static final float DRIFT_Y_RANGE = 1.5F;
    private static final float DRIFT_LERP_PER_SECOND = 2.2F;

    private final Random random = new Random(0x5EAD5A1L);

    private BiomeClockWeatherKind currentKind = BiomeClockWeatherKind.CLEAR;
    private BiomeClockWeatherKind previousKind = BiomeClockWeatherKind.CLEAR;
    private BiomeClockWeatherKind targetKind = BiomeClockWeatherKind.CLEAR;
    private long transitionStartNanos = -1L;
    private long transitionDurationNanos;
    private float transitionStartPresence;
    private float transitionEndPresence;
    private float presenceAlpha;
    private float slideProgress;
    private boolean transitioning;
    private boolean exiting;

    private long lastDriftUpdateNanos = -1L;
    private long nextDriftTargetNanos = -1L;
    private float leftDriftX;
    private float leftDriftY;
    private float rightDriftX;
    private float rightDriftY;
    private float leftTargetDriftX;
    private float leftTargetDriftY;
    private float rightTargetDriftX;
    private float rightTargetDriftY;

    public void update(BiomeClockWeatherKind requestedKind, long nowNanos) {
        BiomeClockWeatherKind safeKind = requestedKind == null ? BiomeClockWeatherKind.CLEAR : requestedKind;
        advanceTransition(nowNanos);
        if (safeKind != this.targetKind) {
            beginTransition(safeKind, nowNanos);
        }
        updateDrift(nowNanos);
    }

    public BiomeClockWeatherKind currentKind() {
        return this.currentKind;
    }

    public BiomeClockWeatherKind previousKind() {
        return this.previousKind;
    }

    public BiomeClockWeatherKind targetKind() {
        return this.targetKind;
    }

    public float presenceAlpha() {
        return this.presenceAlpha;
    }

    public float slideProgress() {
        return this.slideProgress;
    }

    public boolean exiting() {
        return this.exiting;
    }

    public boolean readyForLightning() {
        return this.currentKind.hasVisualWeather()
                && !this.exiting
                && this.presenceAlpha >= 0.95F
                && this.slideProgress >= 0.95F;
    }

    public float leftDriftX() {
        return this.leftDriftX;
    }

    public float leftDriftY() {
        return this.leftDriftY;
    }

    public float rightDriftX() {
        return this.rightDriftX;
    }

    public float rightDriftY() {
        return this.rightDriftY;
    }

    private void beginTransition(BiomeClockWeatherKind requestedKind, long nowNanos) {
        this.previousKind = this.currentKind;
        this.targetKind = requestedKind;
        this.transitionStartNanos = nowNanos;
        this.transitionStartPresence = this.presenceAlpha;
        this.exiting = requestedKind == BiomeClockWeatherKind.CLEAR;

        if (this.exiting) {
            this.transitionEndPresence = 0.0F;
            this.transitionDurationNanos = EXIT_NANOS;
        } else {
            this.currentKind = requestedKind;
            this.transitionEndPresence = 1.0F;
            this.transitionDurationNanos = ENTER_NANOS;
        }

        this.transitioning = Math.abs(this.transitionEndPresence - this.transitionStartPresence) > 0.001F;
        if (!this.transitioning) {
            finishTransition();
        }
    }

    private void advanceTransition(long nowNanos) {
        if (!this.transitioning) {
            this.slideProgress = this.presenceAlpha;
            return;
        }

        float rawProgress = Mth.clamp(
                (nowNanos - this.transitionStartNanos) / (float) Math.max(1L, this.transitionDurationNanos),
                0.0F,
                1.0F
        );
        float easedProgress = this.exiting ? easeInOutCubic(rawProgress) : easeOutCubic(rawProgress);
        this.presenceAlpha = Mth.lerp(easedProgress, this.transitionStartPresence, this.transitionEndPresence);
        this.slideProgress = this.presenceAlpha;

        if (rawProgress >= 1.0F) {
            finishTransition();
        }
    }

    private void finishTransition() {
        this.transitioning = false;
        this.presenceAlpha = this.transitionEndPresence;
        this.slideProgress = this.presenceAlpha;
        if (this.targetKind == BiomeClockWeatherKind.CLEAR) {
            this.currentKind = BiomeClockWeatherKind.CLEAR;
        } else {
            this.currentKind = this.targetKind;
        }
        this.exiting = false;
    }

    private void updateDrift(long nowNanos) {
        if (this.lastDriftUpdateNanos < 0L) {
            this.lastDriftUpdateNanos = nowNanos;
            chooseNextDriftTargets(nowNanos);
            return;
        }

        if (nowNanos >= this.nextDriftTargetNanos) {
            chooseNextDriftTargets(nowNanos);
        }

        float deltaSeconds = Mth.clamp((nowNanos - this.lastDriftUpdateNanos) / 1_000_000_000.0F, 0.0F, 0.25F);
        this.lastDriftUpdateNanos = nowNanos;
        float amount = Mth.clamp(deltaSeconds * DRIFT_LERP_PER_SECOND, 0.0F, 1.0F);
        this.leftDriftX = Mth.lerp(amount, this.leftDriftX, this.leftTargetDriftX);
        this.leftDriftY = Mth.lerp(amount, this.leftDriftY, this.leftTargetDriftY);
        this.rightDriftX = Mth.lerp(amount, this.rightDriftX, this.rightTargetDriftX);
        this.rightDriftY = Mth.lerp(amount, this.rightDriftY, this.rightTargetDriftY);
    }

    private void chooseNextDriftTargets(long nowNanos) {
        this.leftTargetDriftX = randomSigned(DRIFT_X_RANGE);
        this.leftTargetDriftY = randomSigned(DRIFT_Y_RANGE);
        this.rightTargetDriftX = randomSigned(DRIFT_X_RANGE);
        this.rightTargetDriftY = randomSigned(DRIFT_Y_RANGE);
        long interval = DRIFT_INTERVAL_MIN_NANOS
                + (long) (this.random.nextDouble() * (DRIFT_INTERVAL_MAX_NANOS - DRIFT_INTERVAL_MIN_NANOS));
        this.nextDriftTargetNanos = nowNanos + interval;
    }

    private float randomSigned(float range) {
        return (this.random.nextFloat() * 2.0F - 1.0F) * range;
    }

    private static float easeOutCubic(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeInOutCubic(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        if (t < 0.5F) {
            return 4.0F * t * t * t;
        }
        float inverse = -2.0F * t + 2.0F;
        return 1.0F - inverse * inverse * inverse * 0.5F;
    }
}
