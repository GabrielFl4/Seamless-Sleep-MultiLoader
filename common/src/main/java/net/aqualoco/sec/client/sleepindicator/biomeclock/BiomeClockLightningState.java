package net.aqualoco.sec.client.sleepindicator.biomeclock;

import net.minecraft.util.Mth;

import java.util.Random;

// Drives the biome clock lightning sprite and the biome darkening relief from real lightning.
public final class BiomeClockLightningState {
    private static final long DURATION_NANOS = 120_000_000L;
    private static final long COOLDOWN_NANOS = 250_000_000L;
    private static final long SIGNAL_MAX_AGE_NANOS = 350_000_000L;
    private static final float FRAME_SPLIT = 0.5F;
    private static final int OFFSET_X_RANGE = 9;
    private static final int OFFSET_Y_MIN = -4;
    private static final int OFFSET_Y_MAX = 5;

    private final Random random = new Random(0xB1014EL);

    private boolean active;
    private long startNanos;
    private int randomOffsetX;
    private int randomOffsetY;
    private int lastAcceptedEntityId = Integer.MIN_VALUE;
    private long lastAcceptedFirstSeenNanos = Long.MIN_VALUE;
    private long lastAcceptedSignalNanos = Long.MIN_VALUE;
    private long lastAcceptedNanos = Long.MIN_VALUE;
    private boolean reliefActive;
    private long reliefStartNanos;
    private int lastReliefAcceptedEntityId = Integer.MIN_VALUE;
    private long lastReliefAcceptedFirstSeenNanos = Long.MIN_VALUE;
    private long lastReliefAcceptedSignalNanos = Long.MIN_VALUE;

    public void onBiomeReliefSignal(BiomeClockLightningSignal.Signal signal, long nowNanos) {
        if (!validSignal(signal, nowNanos)) {
            return;
        }
        if (signal.entityId() == this.lastReliefAcceptedEntityId
                && signal.firstSeenNanos() == this.lastReliefAcceptedFirstSeenNanos) {
            return;
        }
        if (signal.recordedNanos() <= this.lastReliefAcceptedSignalNanos) {
            return;
        }

        this.reliefActive = true;
        this.reliefStartNanos = nowNanos;
        this.lastReliefAcceptedEntityId = signal.entityId();
        this.lastReliefAcceptedFirstSeenNanos = signal.firstSeenNanos();
        this.lastReliefAcceptedSignalNanos = signal.recordedNanos();
    }

    public void onGameLightningSignal(
            BiomeClockLightningSignal.Signal signal,
            boolean weatherAllowsLightning,
            boolean weatherCloudsReady,
            long nowNanos
    ) {
        if (!validSignal(signal, nowNanos) || !weatherAllowsLightning || !weatherCloudsReady) {
            return;
        }
        if (signal.entityId() == this.lastAcceptedEntityId
                && signal.firstSeenNanos() == this.lastAcceptedFirstSeenNanos) {
            return;
        }
        if (signal.recordedNanos() <= this.lastAcceptedSignalNanos) {
            return;
        }
        if (this.lastAcceptedNanos != Long.MIN_VALUE && nowNanos - this.lastAcceptedNanos < COOLDOWN_NANOS) {
            return;
        }

        this.active = true;
        this.startNanos = nowNanos;
        this.randomOffsetX = this.random.nextInt(OFFSET_X_RANGE * 2 + 1) - OFFSET_X_RANGE;
        this.randomOffsetY = OFFSET_Y_MIN + this.random.nextInt(OFFSET_Y_MAX - OFFSET_Y_MIN + 1);
        this.lastAcceptedEntityId = signal.entityId();
        this.lastAcceptedFirstSeenNanos = signal.firstSeenNanos();
        this.lastAcceptedSignalNanos = signal.recordedNanos();
        this.lastAcceptedNanos = nowNanos;
    }

    public int updateAndGetFrame(long nowNanos) {
        if (!this.active) {
            return -1;
        }

        float progress = progress(nowNanos);
        if (progress >= 1.0F) {
            this.active = false;
            return -1;
        }
        return progress < FRAME_SPLIT ? 0 : 1;
    }

    public float flashFactor(long nowNanos) {
        if (!this.reliefActive) {
            return 0.0F;
        }
        float progress = progress(nowNanos, this.reliefStartNanos);
        if (progress >= 1.0F) {
            this.reliefActive = false;
            return 0.0F;
        }
        return 1.0F - Mth.clamp(progress, 0.0F, 1.0F);
    }

    public boolean isActive() {
        return this.active;
    }

    public int offsetX() {
        return this.randomOffsetX;
    }

    public int offsetY() {
        return this.randomOffsetY;
    }

    private float progress(long nowNanos) {
        return progress(nowNanos, this.startNanos);
    }

    private static boolean validSignal(BiomeClockLightningSignal.Signal signal, long nowNanos) {
        return signal != null
                && signal.present()
                && nowNanos - signal.firstSeenNanos() <= SIGNAL_MAX_AGE_NANOS;
    }

    private static float progress(long nowNanos, long startNanos) {
        return Mth.clamp((nowNanos - startNanos) / (float) DURATION_NANOS, 0.0F, 1.0F);
    }
}
