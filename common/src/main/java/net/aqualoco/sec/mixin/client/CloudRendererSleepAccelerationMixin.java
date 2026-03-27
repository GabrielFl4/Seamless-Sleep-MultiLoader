package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.OptionalLong;

// Cloud phase boost while the sleep transition is running.
@Mixin(CloudRenderer.class)
public abstract class CloudRendererSleepAccelerationMixin {

    @Unique
    private static final float seamlesssleep$MAX_EXTRA_PHASE_SPEED_TICKS = 520.0F;

    @Unique
    private static final float seamlesssleep$PHASE_SPEED_PER_DAY_TICK = 1.65F;

    @Unique
    private static final float seamlesssleep$BOOST_REFERENCE_DAY_SPEED = 90.0F;

    @Unique
    private static final float seamlesssleep$BOOST_EXPONENT = 0.35F;

    @Unique
    private static final float seamlesssleep$BOOST_MAX_MULTIPLIER = 2.6F;

    @Unique
    private static final float seamlesssleep$SMOOTHING_PER_TICK = 6.0F;

    @Unique
    private static final float seamlesssleep$MAX_DELTA_TICKS = 4.0F;

    @Unique
    private static final float seamlesssleep$PHASE_WRAP_TICKS = 102400.0F;

    @Unique
    private static long seamlesssleep$lastUpdateMillis = -1L;

    @Unique
    private static long seamlesssleep$lastReplayTimelineMillis = -1L;

    @Unique
    private static long seamlesssleep$lastActiveLogMillis = -1L;

    @Unique
    private static long seamlesssleep$lastIdleLogMillis = -1L;

    @Unique
    private static long seamlesssleep$lastResetLogMillis = -1L;

    @Unique
    private static long seamlesssleep$lastReplayFallbackLogMillis = -1L;

    @Unique
    private static boolean seamlesssleep$loggedHookOnce;

    @Unique
    private static float seamlesssleep$extraPhaseTicks = 0.0F;

    @Unique
    private static float seamlesssleep$smoothedExtraSpeedTicks = 0.0F;

    @Unique
    private static ClientLevel seamlesssleep$lastLevel;

    @Unique
    private static double seamlesssleep$lastProgress;

    @Unique
    private static double seamlesssleep$lastVelocityFactor;

    @Unique
    private static double seamlesssleep$lastDayTimeSpeedPerTick;

    @Unique
    private static double seamlesssleep$lastBoostMultiplier = 1.0D;

    @Unique
    private static String seamlesssleep$lastZeroReason = "uninitialized";

    @ModifyVariable(
            method = "render(ILnet/minecraft/client/CloudStatus;FLnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("STORE"),
            index = 14
    )
    // local index 14 is vanilla f2 (horizontal cloud phase)
    private float seamlesssleep$injectExtraPhaseIntoF2(float vanillaPhase) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        long now = System.currentTimeMillis();

        if (!seamlesssleep$loggedHookOnce) {
            seamlesssleep$loggedHookOnce = true;
            Constants.debug("Cloud acceleration hook active: CloudRenderer.render -> f2 STORE (local index 14).");
        }

        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            String reason = level == null ? "no_level" : "non_overworld";
            seamlesssleep$hardReset(level, now, reason);
            seamlesssleep$logIdle(now, vanillaPhase, vanillaPhase);
            return vanillaPhase;
        }

        if (level != seamlesssleep$lastLevel) {
            seamlesssleep$hardReset(level, now, "world_changed");
        }

        float deltaTicks = seamlesssleep$computeDeltaTicks(now);
        if (deltaTicks <= 0.0F) {
            float injectedPhase = vanillaPhase + seamlesssleep$extraPhaseTicks;
            seamlesssleep$logIdle(now, vanillaPhase, injectedPhase);
            return injectedPhase;
        }

        float targetExtraSpeed = seamlesssleep$computeTargetExtraSpeed();
        float smoothing = Mth.clamp(deltaTicks * seamlesssleep$SMOOTHING_PER_TICK, 0.0F, 1.0F);
        seamlesssleep$smoothedExtraSpeedTicks += (targetExtraSpeed - seamlesssleep$smoothedExtraSpeedTicks) * smoothing;
        seamlesssleep$extraPhaseTicks += seamlesssleep$smoothedExtraSpeedTicks * deltaTicks;

        if (seamlesssleep$extraPhaseTicks >= seamlesssleep$PHASE_WRAP_TICKS
                || seamlesssleep$extraPhaseTicks <= -seamlesssleep$PHASE_WRAP_TICKS) {
            seamlesssleep$extraPhaseTicks %= seamlesssleep$PHASE_WRAP_TICKS;
        }

        float injectedPhase = vanillaPhase + seamlesssleep$extraPhaseTicks;
        if (targetExtraSpeed > 0.0F) {
            seamlesssleep$logActive(now, targetExtraSpeed, vanillaPhase, injectedPhase);
        } else {
            seamlesssleep$logIdle(now, vanillaPhase, injectedPhase);
        }

        return injectedPhase;
    }

    @Unique
    // Derive cloud boost from the same day-time speed used by sleep interpolation.
    private static float seamlesssleep$computeTargetExtraSpeed() {
        ClientSleepAnimationState state = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (!state.isActive()) {
            seamlesssleep$lastProgress = 0.0D;
            seamlesssleep$lastVelocityFactor = 0.0D;
            seamlesssleep$lastDayTimeSpeedPerTick = 0.0D;
            seamlesssleep$lastZeroReason = "sleep_animation_inactive";
            return 0.0F;
        }

        double progress = state.getProgress();
        double velocityFactor = state.getEasedVelocityFactor();
        double dayTimeSpeedPerTick = state.getCurrentDayTimeSpeedPerTick();
        float baseTarget = (float) (dayTimeSpeedPerTick * seamlesssleep$PHASE_SPEED_PER_DAY_TICK);
        float boostMultiplier = 1.0F;
        if (dayTimeSpeedPerTick > seamlesssleep$BOOST_REFERENCE_DAY_SPEED) {
            float ratio = (float) (dayTimeSpeedPerTick / seamlesssleep$BOOST_REFERENCE_DAY_SPEED);
            boostMultiplier = Mth.clamp(
                    (float) Math.pow(ratio, seamlesssleep$BOOST_EXPONENT),
                    1.0F,
                    seamlesssleep$BOOST_MAX_MULTIPLIER
            );
        }
        float target = Mth.clamp(
                baseTarget * boostMultiplier,
                0.0F,
                seamlesssleep$MAX_EXTRA_PHASE_SPEED_TICKS
        );

        seamlesssleep$lastProgress = progress;
        seamlesssleep$lastVelocityFactor = velocityFactor;
        seamlesssleep$lastDayTimeSpeedPerTick = dayTimeSpeedPerTick;
        seamlesssleep$lastBoostMultiplier = boostMultiplier;
        if (target > 0.0F) {
            seamlesssleep$lastZeroReason = "";
        } else if (progress <= 0.0001D) {
            seamlesssleep$lastZeroReason = "progress_at_start";
        } else if (progress >= 0.9999D) {
            seamlesssleep$lastZeroReason = "progress_at_end";
        } else {
            seamlesssleep$lastZeroReason = "blend_zero";
        }
        return target;
    }

    @Unique
    // Use replay timeline delta when replay-compat is active; fallback to wall-clock in normal mode.
    private static float seamlesssleep$computeDeltaTicks(long now) {
        ClientSleepAnimationState state = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (state.isReplayCompatMode()) {
            OptionalLong replayTimeline = state.getReplayTimelineMillisSnapshot();
            if (replayTimeline.isPresent()) {
                long replayNowMillis = replayTimeline.getAsLong();
                if (seamlesssleep$lastReplayTimelineMillis < 0L) {
                    seamlesssleep$lastReplayTimelineMillis = replayNowMillis;
                    seamlesssleep$lastUpdateMillis = now;
                    seamlesssleep$lastZeroReason = "replay_timeline_seed";
                    return 0.0F;
                }

                long replayDeltaMillis = replayNowMillis - seamlesssleep$lastReplayTimelineMillis;
                seamlesssleep$lastReplayTimelineMillis = replayNowMillis;
                seamlesssleep$lastUpdateMillis = now;

                if (replayDeltaMillis <= 0L) {
                    seamlesssleep$lastZeroReason = replayDeltaMillis == 0L
                            ? "replay_timeline_paused"
                            : "replay_timeline_rewind";
                    return 0.0F;
                }

                return Mth.clamp(replayDeltaMillis / 50.0F, 0.0F, seamlesssleep$MAX_DELTA_TICKS);
            }

            if (now - seamlesssleep$lastReplayFallbackLogMillis >= 5000L) {
                seamlesssleep$lastReplayFallbackLogMillis = now;
                Constants.debug("Cloud accel replay fallback: replay timeline unavailable, using wall-clock delta.");
            }
            seamlesssleep$lastReplayTimelineMillis = -1L;
        } else {
            seamlesssleep$lastReplayTimelineMillis = -1L;
        }

        if (seamlesssleep$lastUpdateMillis < 0L) {
            seamlesssleep$lastUpdateMillis = now;
            seamlesssleep$lastZeroReason = "delta_seed";
            return 0.0F;
        }

        float deltaTicks = Mth.clamp((now - seamlesssleep$lastUpdateMillis) / 50.0F, 0.0F, seamlesssleep$MAX_DELTA_TICKS);
        seamlesssleep$lastUpdateMillis = now;
        if (deltaTicks <= 0.0F) {
            seamlesssleep$lastZeroReason = "zero_delta_ticks";
        }
        return deltaTicks;
    }

    @Unique
    // Hard reset only when world context changes.
    private static void seamlesssleep$hardReset(ClientLevel level, long now, String reason) {
        seamlesssleep$lastLevel = level;
        seamlesssleep$lastUpdateMillis = now;
        seamlesssleep$lastReplayTimelineMillis = -1L;
        seamlesssleep$extraPhaseTicks = 0.0F;
        seamlesssleep$smoothedExtraSpeedTicks = 0.0F;
        seamlesssleep$lastProgress = 0.0D;
        seamlesssleep$lastVelocityFactor = 0.0D;
        seamlesssleep$lastDayTimeSpeedPerTick = 0.0D;
        seamlesssleep$lastBoostMultiplier = 1.0D;
        seamlesssleep$lastZeroReason = reason;

        if (now - seamlesssleep$lastResetLogMillis >= 3000L) {
            seamlesssleep$lastResetLogMillis = now;
            Constants.debug(
                    "Cloud acceleration reset: reason={}, level={}",
                    reason,
                    level == null ? "null" : level.dimension()
            );
        }
    }

    @Unique
    private static void seamlesssleep$logActive(long now, float targetExtraSpeed, float vanillaPhase, float injectedPhase) {
        if (now - seamlesssleep$lastActiveLogMillis < 1500L) {
            return;
        }
        seamlesssleep$lastActiveLogMillis = now;
        Constants.debug(
                "Cloud accel active: progress={}, velocityFactor={}, dayTimeSpeedPerTick={}, boostMultiplier={}, targetExtraSpeed={}, smoothedSpeed={}, extraPhaseTicks={}, phaseIn={}, phaseOut={}",
                seamlesssleep$lastProgress,
                seamlesssleep$lastVelocityFactor,
                seamlesssleep$lastDayTimeSpeedPerTick,
                seamlesssleep$lastBoostMultiplier,
                targetExtraSpeed,
                seamlesssleep$smoothedExtraSpeedTicks,
                seamlesssleep$extraPhaseTicks,
                vanillaPhase,
                injectedPhase
        );
    }

    @Unique
    private static void seamlesssleep$logIdle(long now, float vanillaPhase, float injectedPhase) {
        if (now - seamlesssleep$lastIdleLogMillis < 5000L) {
            return;
        }
        seamlesssleep$lastIdleLogMillis = now;
        Constants.debug(
                "Cloud accel idle: reason={}, progress={}, velocityFactor={}, dayTimeSpeedPerTick={}, boostMultiplier={}, extraPhaseTicks={}, phaseIn={}, phaseOut={}",
                seamlesssleep$lastZeroReason,
                seamlesssleep$lastProgress,
                seamlesssleep$lastVelocityFactor,
                seamlesssleep$lastDayTimeSpeedPerTick,
                seamlesssleep$lastBoostMultiplier,
                seamlesssleep$extraPhaseTicks,
                vanillaPhase,
                injectedPhase
        );
    }
}
