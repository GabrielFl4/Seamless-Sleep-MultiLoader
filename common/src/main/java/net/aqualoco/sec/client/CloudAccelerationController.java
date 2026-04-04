package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.OptionalLong;

// Tracks a client-only additive cloud timeline so cloud motion can keep up with accelerated sleep time.
public final class CloudAccelerationController {

    private static final float MAX_EXTRA_PHASE_SPEED_TICKS = 520.0F;
    private static final float PHASE_SPEED_PER_DAY_TICK = 1.65F;
    private static final float BOOST_REFERENCE_DAY_SPEED = 90.0F;
    private static final float BOOST_EXPONENT = 0.35F;
    private static final float BOOST_MAX_MULTIPLIER = 2.6F;
    private static final float SMOOTHING_PER_TICK = 6.0F;
    private static final float MAX_DELTA_TICKS = 4.0F;
    private static final float PHASE_WRAP_TICKS = 102400.0F;

    private final String backendName;

    private long lastUpdateMillis = -1L;
    private long lastReplayTimelineMillis = -1L;
    private long lastActiveLogMillis = -1L;
    private long lastIdleLogMillis = -1L;
    private long lastResetLogMillis = -1L;
    private long lastReplayFallbackLogMillis = -1L;

    private float extraTicks = 0.0F;
    private float smoothedExtraSpeedTicks = 0.0F;
    private float lastTargetExtraSpeed = 0.0F;

    private ClientLevel lastLevel;

    private double lastProgress;
    private double lastVelocityFactor;
    private double lastDayTimeSpeedPerTick;
    private double lastBoostMultiplier = 1.0D;

    private String lastZeroReason = "uninitialized";

    public CloudAccelerationController(String backendName) {
        this.backendName = backendName;
    }

    public CloudTimeSample sample(float baseValue, ClientLevel level, long now) {
        float extra = updateAndGetExtraTicks(level, now);
        float adjustedValue = baseValue + extra;
        int wholeTicks = Mth.floor(adjustedValue);
        float partialTick = adjustedValue - wholeTicks;
        return new CloudTimeSample(extra, adjustedValue, wholeTicks, partialTick);
    }

    public void logApplied(long now, float baseValue, float adjustedValue) {
        if (lastTargetExtraSpeed > 0.0F) {
            logActive(now, baseValue, adjustedValue);
        } else {
            logIdle(now, baseValue, adjustedValue);
        }
    }

    private float updateAndGetExtraTicks(ClientLevel level, long now) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            String reason = level == null ? "no_level" : "non_overworld";
            hardReset(level, now, reason);
            return 0.0F;
        }

        if (level != lastLevel) {
            hardReset(level, now, "world_changed");
        }

        float deltaTicks = computeDeltaTicks(now);
        if (deltaTicks <= 0.0F) {
            lastTargetExtraSpeed = 0.0F;
            return extraTicks;
        }

        float targetExtraSpeed = computeTargetExtraSpeed();
        float smoothing = Mth.clamp(deltaTicks * SMOOTHING_PER_TICK, 0.0F, 1.0F);
        smoothedExtraSpeedTicks += (targetExtraSpeed - smoothedExtraSpeedTicks) * smoothing;
        extraTicks += smoothedExtraSpeedTicks * deltaTicks;

        if (extraTicks >= PHASE_WRAP_TICKS || extraTicks <= -PHASE_WRAP_TICKS) {
            extraTicks %= PHASE_WRAP_TICKS;
        }

        lastTargetExtraSpeed = targetExtraSpeed;
        return extraTicks;
    }

    private float computeTargetExtraSpeed() {
        ClientSleepAnimationState state = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (!state.isActive()) {
            lastProgress = 0.0D;
            lastVelocityFactor = 0.0D;
            lastDayTimeSpeedPerTick = 0.0D;
            lastZeroReason = "sleep_animation_inactive";
            return 0.0F;
        }

        double progress = state.getProgress();
        double velocityFactor = state.getEasedVelocityFactor();
        double dayTimeSpeedPerTick = state.getCurrentDayTimeSpeedPerTick();
        float baseTarget = (float) (dayTimeSpeedPerTick * PHASE_SPEED_PER_DAY_TICK);
        float boostMultiplier = 1.0F;
        if (dayTimeSpeedPerTick > BOOST_REFERENCE_DAY_SPEED) {
            float ratio = (float) (dayTimeSpeedPerTick / BOOST_REFERENCE_DAY_SPEED);
            boostMultiplier = Mth.clamp(
                    (float) Math.pow(ratio, BOOST_EXPONENT),
                    1.0F,
                    BOOST_MAX_MULTIPLIER
            );
        }
        float target = Mth.clamp(
                baseTarget * boostMultiplier,
                0.0F,
                MAX_EXTRA_PHASE_SPEED_TICKS
        );

        lastProgress = progress;
        lastVelocityFactor = velocityFactor;
        lastDayTimeSpeedPerTick = dayTimeSpeedPerTick;
        lastBoostMultiplier = boostMultiplier;
        if (target > 0.0F) {
            lastZeroReason = "";
        } else if (progress <= 0.0001D) {
            lastZeroReason = "progress_at_start";
        } else if (progress >= 0.9999D) {
            lastZeroReason = "progress_at_end";
        } else {
            lastZeroReason = "blend_zero";
        }
        return target;
    }

    private float computeDeltaTicks(long now) {
        ClientSleepAnimationState state = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (state.isReplayCompatMode()) {
            OptionalLong replayTimeline = state.getReplayTimelineMillisSnapshot();
            if (replayTimeline.isPresent()) {
                long replayNowMillis = replayTimeline.getAsLong();
                if (lastReplayTimelineMillis < 0L) {
                    lastReplayTimelineMillis = replayNowMillis;
                    lastUpdateMillis = now;
                    lastZeroReason = "replay_timeline_seed";
                    return 0.0F;
                }

                long replayDeltaMillis = replayNowMillis - lastReplayTimelineMillis;
                lastReplayTimelineMillis = replayNowMillis;
                lastUpdateMillis = now;

                if (replayDeltaMillis <= 0L) {
                    lastZeroReason = replayDeltaMillis == 0L
                            ? "replay_timeline_paused"
                            : "replay_timeline_rewind";
                    return 0.0F;
                }

                return Mth.clamp(replayDeltaMillis / 50.0F, 0.0F, MAX_DELTA_TICKS);
            }

            if (now - lastReplayFallbackLogMillis >= 5000L) {
                lastReplayFallbackLogMillis = now;
                Constants.debug("Cloud accel [{}] replay fallback: replay timeline unavailable, using wall-clock delta.", backendName);
            }
            lastReplayTimelineMillis = -1L;
        } else {
            lastReplayTimelineMillis = -1L;
        }

        if (lastUpdateMillis < 0L) {
            lastUpdateMillis = now;
            lastZeroReason = "delta_seed";
            return 0.0F;
        }

        float deltaTicks = Mth.clamp((now - lastUpdateMillis) / 50.0F, 0.0F, MAX_DELTA_TICKS);
        lastUpdateMillis = now;
        if (deltaTicks <= 0.0F) {
            lastZeroReason = "zero_delta_ticks";
        }
        return deltaTicks;
    }

    private void hardReset(ClientLevel level, long now, String reason) {
        lastLevel = level;
        lastUpdateMillis = now;
        lastReplayTimelineMillis = -1L;
        extraTicks = 0.0F;
        smoothedExtraSpeedTicks = 0.0F;
        lastTargetExtraSpeed = 0.0F;
        lastProgress = 0.0D;
        lastVelocityFactor = 0.0D;
        lastDayTimeSpeedPerTick = 0.0D;
        lastBoostMultiplier = 1.0D;
        lastZeroReason = reason;

        if (now - lastResetLogMillis >= 3000L) {
            lastResetLogMillis = now;
            Constants.debug(
                    "Cloud accel [{}] reset: reason={}, level={}",
                    backendName,
                    reason,
                    level == null ? "null" : level.dimension()
            );
        }
    }

    private void logActive(long now, float baseValue, float adjustedValue) {
        if (now - lastActiveLogMillis < 1500L) {
            return;
        }
        lastActiveLogMillis = now;
        Constants.debug(
                "Cloud accel [{}] active: progress={}, velocityFactor={}, dayTimeSpeedPerTick={}, boostMultiplier={}, targetExtraSpeed={}, smoothedSpeed={}, extraTicks={}, baseValue={}, adjustedValue={}",
                backendName,
                lastProgress,
                lastVelocityFactor,
                lastDayTimeSpeedPerTick,
                lastBoostMultiplier,
                lastTargetExtraSpeed,
                smoothedExtraSpeedTicks,
                extraTicks,
                baseValue,
                adjustedValue
        );
    }

    private void logIdle(long now, float baseValue, float adjustedValue) {
        if (now - lastIdleLogMillis < 5000L) {
            return;
        }
        lastIdleLogMillis = now;
        Constants.debug(
                "Cloud accel [{}] idle: reason={}, progress={}, velocityFactor={}, dayTimeSpeedPerTick={}, boostMultiplier={}, extraTicks={}, baseValue={}, adjustedValue={}",
                backendName,
                lastZeroReason,
                lastProgress,
                lastVelocityFactor,
                lastDayTimeSpeedPerTick,
                lastBoostMultiplier,
                extraTicks,
                baseValue,
                adjustedValue
        );
    }

    public record CloudTimeSample(float extraTicks, float adjustedValue, int wholeTicks, float partialTick) {
    }
}
