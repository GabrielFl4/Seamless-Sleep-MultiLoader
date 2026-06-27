package net.aqualoco.sec.client.sound;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.ReplayPlaybackCompat;
import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.aqualoco.sec.sleep.SleepAnimationMode;
import net.aqualoco.sec.sleep.SleepAnimationPhase;
import net.aqualoco.sec.sleep.SleepAnimationSoundMode;
import net.aqualoco.sec.sleep.SleepAnimationStopReason;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.Locale;

public final class SleepSoundManager {
    private static final long DAY_TICKS = 24000L;
    private static final long SUN_OVERHEAD_TICKS = 6000L;
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final int WIND_FADE_TICKS = 24;
    private static final int WIND_STOP_FADE_TICKS = 34;
    private static final int MADE_IN_HEAVEN_TIME_ACCEL_DELAY_TICKS = 12;
    private static final int MADE_IN_HEAVEN_MAIN_TECH_FADE_IN_TICKS = 2;
    private static final int TIME_ACCEL_AUDIBLE_TICKS = 57;
    private static final int TIME_DECEL_AUDIBLE_TICKS = 72;
    private static final double MADE_IN_HEAVEN_BELL_SYNC_SECONDS = 3.52D;
    private static final int MADE_IN_HEAVEN_BELL_DROP_TICKS = (int) Math.round(MADE_IN_HEAVEN_BELL_SYNC_SECONDS * TICKS_PER_SECOND);
    private static final int MADE_IN_HEAVEN_CANCEL_DECEL_DELAY_TICKS = 6;
    private static final int MADE_IN_HEAVEN_MAIN_FADE_OUT_NATURAL_TICKS = 48;
    private static final int MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS = 16;
    private static final int FRESH_PAYLOAD_MAX_ELAPSED_TICKS = 5;
    private static final float MADE_IN_HEAVEN_MAIN_VOLUME = 0.72F;
    private static final float ASTRO_VOLUME_BASE_TIMELAPSE = 0.08F;
    private static final float ASTRO_VOLUME_EXTRA_TIMELAPSE = 0.18F;
    private static final float ASTRO_VOLUME_BASE_MIH = 0.16F;
    private static final float ASTRO_VOLUME_EXTRA_MIH = 0.30F;
    private static final double ASTRO_PASS_MIN_SPEED_INTENSITY_TIMELAPSE = 0.62D;
    private static final double ASTRO_PASS_MIN_SPEED_INTENSITY_MIH = 0.48D;
    private static final double WIND_SESSION_PITCH_OFFSET_RANGE = 0.058D;
    private static final double WIND_RAIN_VOLUME_MULTIPLIER = 1.35D;
    private static final double WIND_THUNDER_VOLUME_MULTIPLIER = 1.90D;
    private static final double WIND_RAIN_PITCH_OFFSET = -0.115D;
    private static final double WIND_THUNDER_PITCH_OFFSET = -0.210D;
    private static final double WIND_BRAKE_START_PROGRESS = 0.84D;
    private static final double WIND_BRAKE_END_PROGRESS = 1.0D;
    private static final double WIND_BRAKE_POWER = 1.10D;
    private static final double WIND_TAIL_RISE_START_PROGRESS = 0.62D;
    private static final double WIND_TAIL_FULL_PROGRESS = 0.80D;
    private static final double WIND_TAIL_FADE_START_PROGRESS = 0.88D;
    private static final double WIND_TAIL_FADE_END_PROGRESS = 0.995D;

    private static long activeSessionId = -1L;
    private static long activeSequenceId = -1L;
    private static int activeDurationTicks = 1;
    private static SleepAnimationMode activeMode = SleepAnimationMode.NORMAL_SLEEP;
    private static SleepAnimationPhase activePhase = SleepAnimationPhase.IDLE;
    private static SleepAnimationSoundMode activeSoundMode = SleepAnimationSoundMode.MUTED;

    private static long timeAccelPlayedSessionId = -1L;
    private static long timeDecelPlayedSessionId = -1L;
    private static long bellPlayedSessionId = -1L;
    private static long astroPassTrackingSessionId = -1L;
    private static long lastAstroPassVisualDayTime = Long.MIN_VALUE;
    private static int astroPassSunPassesSeen;
    private static long madeInHeavenStopPlayedSessionId = -1L;
    private static long madeInHeavenIntroScheduledSessionId = -1L;
    private static long madeInHeavenIntroStartedSessionId = -1L;
    private static int madeInHeavenIntroDelayTicks = -1;
    private static long madeInHeavenTimeDecelScheduledSessionId = -1L;
    private static int madeInHeavenTimeDecelDelayTicks = -1;
    private static boolean madeInHeavenDelayedDecelIsCancel;
    private static boolean madeInHeavenNaturalBrakeActive;
    private static boolean madeInHeavenCancelBrakeActive;
    private static float madeInHeavenBrakeWindStartVolume;
    private static String madeInHeavenMainFadeOutMode = "NONE";
    private static long madeInHeavenMainStartedSessionId = -1L;

    private static SleepLoopSoundInstance windLoop;
    private static SleepFadingSoundInstance madeInHeavenMainSound;
    private static SoundInstance timeAccelSound;
    private static SoundInstance timeDecelSound;
    private static SoundInstance madeInHeavenStopSound;
    private static SoundInstance bellSound;
    private static SoundInstance astroPassSound;
    private static final SleepAudioEnvironment audioEnvironment = new SleepAudioEnvironment();

    private SleepSoundManager() {
    }

    public static void onSleepStart(SleepAnimationStartPayload payload) {
        if (payload == null) {
            return;
        }

        boolean newSession = payload.sessionId() != activeSessionId;
        Minecraft client = Minecraft.getInstance();
        if (newSession) {
            stopWindLoop(WIND_STOP_FADE_TICKS);
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
            stopOneShotSounds(client);
            clearPlaybackMarkers();
        }

        activeSessionId = payload.sessionId();
        activeSequenceId = payload.sequenceId();
        activeDurationTicks = Math.max(1, payload.durationTicks());
        activeMode = payload.mode() == null ? SleepAnimationMode.NORMAL_SLEEP : payload.mode();
        activePhase = payload.phase() == null ? SleepAnimationPhase.RUNNING : payload.phase();
        activeSoundMode = SleepAnimationSoundMode.canonical(payload.soundMode());
        boolean freshPayload = isFreshPayload(payload);
        MadeInHeavenMusicSuppression.update(shouldSuppressMadeInHeavenMusic(client));

        if (shouldSuppressAllAudio(client)) {
            stopWindLoop(WIND_STOP_FADE_TICKS);
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
            stopOneShotSounds(client);
            cancelMadeInHeavenDelayedSounds();
            audioEnvironment.reset();
            return;
        }

        if (activeMode == SleepAnimationMode.NORMAL_SLEEP
                && activePhase == SleepAnimationPhase.CANCEL_BRAKING) {
            handleStopSounds(client, false);
            stopWindLoop(WIND_STOP_FADE_TICKS);
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
            MadeInHeavenMusicSuppression.update(false);
            resetSessionState("normal_sleep_cancel_braking");
            audioEnvironment.reset();
            return;
        }

        primeAudioEnvironment(client);

        if (activeMode == SleepAnimationMode.COMMAND_TIMELAPSE) {
            handleTimelapsePayload(client, freshPayload);
        } else if (activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED) {
            handleMadeInHeavenPayload(client, freshPayload);
        } else {
            stopWindLoop(WIND_STOP_FADE_TICKS);
        }

        Constants.debug(
                "Sleep sound session updated: mode={}, phase={}, soundMode={}, session={}, sequence={}.",
                activeMode,
                activePhase,
                activeSoundMode,
                activeSessionId,
                activeSequenceId
        );
    }

    public static void onSleepStop(SleepAnimationStopPayload payload) {
        if (payload == null || payload.sessionId() != activeSessionId) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        boolean finished = payload.reason() == SleepAnimationStopReason.FINISHED;
        if (!shouldSuppressAllAudio(client)) {
            handleStopSounds(client, finished);
        }

        stopWindLoop(WIND_STOP_FADE_TICKS);
        stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
        MadeInHeavenMusicSuppression.update(false);
        resetSessionState("sleep_stop_" + payload.reason().name().toLowerCase(Locale.ROOT));
        audioEnvironment.reset();
    }

    public static void tick(Minecraft client) {
        if (client == null) {
            MadeInHeavenMusicSuppression.update(false);
            stopAllLoops();
            audioEnvironment.reset();
            return;
        }

        pruneStoppedSounds(client);
        MadeInHeavenMusicSuppression.update(shouldSuppressMadeInHeavenMusic(client));
        if (shouldSuppressAllAudio(client)) {
            stopAllLoops();
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
            stopOneShotSounds(client);
            cancelMadeInHeavenDelayedSounds();
            audioEnvironment.reset();
            return;
        }

        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            stopAllLoops();
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
            stopOneShotSounds(client);
            cancelMadeInHeavenDelayedSounds();
            audioEnvironment.reset();
            return;
        }

        ClientSleepAnimationState sleepState = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (activeSessionId < 0L || !sleepState.isActive()) {
            stopAllLoops();
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
            cancelMadeInHeavenDelayedSounds();
            audioEnvironment.reset();
            return;
        }

        audioEnvironment.update(level, player);
        updateMadeInHeavenDelayedIntroStart(client);
        updateMadeInHeavenDelayedTimeDecel(client);
        updateMadeInHeavenMainVolume(client);
        updateWindLoop(client, level, player, sleepState);
        updateAstroPassSound(client, sleepState);
        updateTimelapseEpicDecel(client, sleepState);
    }

    public static void reset(String reason) {
        if (isResetStateIdle()) {
            MadeInHeavenMusicSuppression.update(false);
            return;
        }

        if (activeSessionId >= 0L) {
            Constants.debug("Sleep sound state reset: {}.", reason);
        }

        stopAllLoops();
        Minecraft client = Minecraft.getInstance();
        stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
        MadeInHeavenMusicSuppression.update(false);
        stopOneShotSounds(client);
        clearLoopSoundReferences();
        resetSessionState(reason);
        audioEnvironment.reset();
    }

    private static void handleTimelapsePayload(Minecraft client, boolean freshPayload) {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (activeSoundMode.isMuted()) {
            stopWindLoop(WIND_STOP_FADE_TICKS);
            return;
        }

        if (activePhase == SleepAnimationPhase.BRAKING) {
            stopWindLoop(WIND_STOP_FADE_TICKS);
            if (activeSoundMode == SleepAnimationSoundMode.EPIC && freshPayload && config.soundtrackVolumePercent > 0) {
                playTimeDecelOnce(client, config);
            }
            return;
        }

        if (activeSoundMode == SleepAnimationSoundMode.EPIC && freshPayload && config.soundtrackVolumePercent > 0) {
            playTimeAccelOnce(client, config);
        }
    }

    private static void handleMadeInHeavenPayload(Minecraft client, boolean freshPayload) {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent <= 0) {
            cancelMadeInHeavenDelayedSounds();
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
            return;
        }

        if (activePhase == SleepAnimationPhase.BRAKING) {
            if (freshPayload) {
                boolean naturalAutoBrake = activeDurationTicks >= 100;
                startMadeInHeavenBrake(client, config, naturalAutoBrake);
            }
            cancelMadeInHeavenPendingIntro();
            return;
        }

        if (activePhase == SleepAnimationPhase.RUNNING && freshPayload) {
            scheduleMadeInHeavenIntroStart();
        }
    }

    private static void handleStopSounds(Minecraft client, boolean finished) {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent <= 0) {
            return;
        }

        if (activeMode == SleepAnimationMode.COMMAND_TIMELAPSE) {
            if (activeSoundMode == SleepAnimationSoundMode.EPIC) {
                playTimeDecelOnce(client, config);
            }
            return;
        }

        if (activeMode != SleepAnimationMode.MADE_IN_HEAVEN_BED) {
            return;
        }

        if (finished) {
            if (madeInHeavenNaturalBrakeActive && bellPlayedSessionId != activeSessionId) {
                playBellOnce(client, config);
            }
            return;
        }

        if (!madeInHeavenCancelBrakeActive) {
            madeInHeavenCancelBrakeActive = true;
            madeInHeavenNaturalBrakeActive = false;
            madeInHeavenBrakeWindStartVolume = getCurrentWindVolumeSafely(client);
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
            madeInHeavenMainFadeOutMode = "CANCEL";
            playMadeInHeavenStopOnce(client, config);
        }
        playTimeDecelOnce(client, config);
    }

    private static void updateWindLoop(Minecraft client,
                                       ClientLevel level,
                                       LocalPlayer player,
                                       ClientSleepAnimationState sleepState) {
        if (!shouldPlayWind()) {
            stopWindLoop(WIND_STOP_FADE_TICKS);
            return;
        }

        ensureWindLoop(client);
        if (windLoop != null && !windLoop.isStopped()) {
            WindSoundFrame frame = computeWindSoundFrame(level, player, sleepState);
            windLoop.setFadeTicks(WIND_FADE_TICKS);
            windLoop.setTargetVolume(frame.targetVolume());
            windLoop.setPitch(frame.pitch());
        }
    }

    private static void updateAstroPassSound(Minecraft client, ClientSleepAnimationState sleepState) {
        if (!shouldPlayAstroPassSound()) {
            resetAstroPassTracking();
            return;
        }

        long currentVisualDayTime = sleepState.getCurrentVisualDayTime();
        if (astroPassTrackingSessionId != activeSessionId || currentVisualDayTime < lastAstroPassVisualDayTime) {
            astroPassTrackingSessionId = activeSessionId;
            lastAstroPassVisualDayTime = currentVisualDayTime;
            return;
        }

        long previousVisualDayTime = lastAstroPassVisualDayTime;
        lastAstroPassVisualDayTime = currentVisualDayTime;
        if (currentVisualDayTime <= previousVisualDayTime) {
            return;
        }

        if (!crossedAstroOverhead(previousVisualDayTime, currentVisualDayTime, SUN_OVERHEAD_TICKS)) {
            return;
        }

        astroPassSunPassesSeen++;
        if (astroPassSunPassesSeen <= 1) {
            return;
        }

        double speedIntensity = computeSpeedIntensity(sleepState);
        double minSpeedIntensity = resolveAstroPassMinSpeedIntensity();
        if (speedIntensity < minSpeedIntensity) {
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        playAstroPassOnce(client, config, sleepState, speedIntensity, minSpeedIntensity);
    }

    private static void updateTimelapseEpicDecel(Minecraft client, ClientSleepAnimationState sleepState) {
        if (activeMode != SleepAnimationMode.COMMAND_TIMELAPSE
                || activeSoundMode != SleepAnimationSoundMode.EPIC
                || activePhase != SleepAnimationPhase.RUNNING
                || timeDecelPlayedSessionId == activeSessionId) {
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent <= 0) {
            return;
        }

        double threshold = Math.max(0.0D, 1.0D - TIME_DECEL_AUDIBLE_TICKS / (double) Math.max(1, activeDurationTicks));
        if (sleepState.getProgress() >= threshold) {
            playTimeDecelOnce(client, config);
        }
    }

    private static boolean shouldPlayWind() {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.sleepWindVolumePercent <= 0) {
            return false;
        }

        if (activeMode == SleepAnimationMode.NORMAL_SLEEP) {
            return activePhase == SleepAnimationPhase.RUNNING;
        }

        if (activeMode == SleepAnimationMode.COMMAND_TIMELAPSE) {
            return activePhase == SleepAnimationPhase.RUNNING
                    && (activeSoundMode == SleepAnimationSoundMode.DEFAULT
                    || activeSoundMode == SleepAnimationSoundMode.EPIC);
        }

        return activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED
                && (activePhase == SleepAnimationPhase.RUNNING
                || activePhase == SleepAnimationPhase.BRAKING);
    }

    private static boolean shouldPlayAstroPassSound() {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent <= 0) {
            return false;
        }

        if (activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED) {
            return activePhase == SleepAnimationPhase.RUNNING
                    || activePhase == SleepAnimationPhase.BRAKING;
        }

        return activeMode == SleepAnimationMode.COMMAND_TIMELAPSE
                && activeSoundMode == SleepAnimationSoundMode.EPIC
                && (activePhase == SleepAnimationPhase.RUNNING
                || activePhase == SleepAnimationPhase.BRAKING);
    }

    private static WindSoundFrame computeWindSoundFrame(ClientLevel level,
                                                        LocalPlayer player,
                                                        ClientSleepAnimationState sleepState) {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        double progress = sleepState.getProgress();
        double easedVelocity = Math.max(0.0D, sleepState.getEasedVelocityFactor());
        double astroMotion = smoothstep(0.025D, 1.15D, easedVelocity);
        double startGate = computeWindStartGate(progress);
        double brakeEnvelope = computeWindBrakeEnvelope(progress);
        double speedIntensity = computeSpeedIntensity(sleepState);
        double velocityCurve = Math.pow(astroMotion, 0.82D);
        double brakeTail = computeWindBrakeTail(progress, speedIntensity);
        double curve = startGate * brakeEnvelope * Math.max(velocityCurve, brakeTail);
        double base = activeMode == SleepAnimationMode.NORMAL_SLEEP ? 0.28D : 0.34D;

        double environment = audioEnvironment.windMultiplier() * computeWeatherMultiplier(level);
        double velocityBlend = 0.70D + 0.30D * speedIntensity;
        double motionVariation = computeWindMotionVariation(level, progress, speedIntensity);
        double configuredVolume = config.sleepWindVolumePercent / 100.0D;
        float targetVolume = (float) clamp01(base * curve * environment * velocityBlend * motionVariation * configuredVolume);
        float pitch = computeWindPitch(level, sleepState, speedIntensity);
        if (activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED
                && activePhase == SleepAnimationPhase.BRAKING
                && madeInHeavenCancelBrakeActive) {
            double tail = 1.0D - smoothstep(0.0D, 1.0D, progress);
            targetVolume = (float) clamp01(madeInHeavenBrakeWindStartVolume * tail);
        }
        return new WindSoundFrame(
                targetVolume,
                pitch
        );
    }

    private static float computeWindPitch(ClientLevel level,
                                          ClientSleepAnimationState sleepState,
                                          double speedIntensity) {
        double motion = Math.sin(level.getGameTime() * 0.021D + activeSessionId * 0.11D) * 0.012D * speedIntensity;
        double pitch = 1.0D + computeWindSessionPitchOffset() + 0.025D * speedIntensity + motion;
        pitch += computeWeatherPitchOffset(level);
        return clamp((float) pitch, 0.88F, 1.10F);
    }

    private static double computeWindSessionPitchOffset() {
        if (activeSessionId < 0L) {
            return 0.0D;
        }

        long seed = activeSessionId * 73_428_767L + (activeMode.ordinal() + 1L) * 912_931L;
        seed ^= seed >>> 16;
        seed *= 0x7FEB352DL;
        seed ^= seed >>> 15;
        seed *= 0x846CA68BL;
        seed ^= seed >>> 16;

        double unit = (seed & 0xFFFFFFL) / (double) 0xFFFFFFL;
        return (unit * 2.0D - 1.0D) * WIND_SESSION_PITCH_OFFSET_RANGE;
    }

    private static double computeWindBrakeEnvelope(double progress) {
        if (activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED) {
            if (activePhase != SleepAnimationPhase.BRAKING) {
                return 1.0D;
            }

            if (madeInHeavenCancelBrakeActive) {
                return 1.0D;
            }

            double fadeStart = computeMadeInHeavenNaturalWindFadeStartProgress();
            double fade = 1.0D - smoothstep(fadeStart, 1.0D, progress);
            return Math.pow(fade, WIND_BRAKE_POWER);
        }

        double fade = 1.0D - smoothstep(WIND_BRAKE_START_PROGRESS, WIND_BRAKE_END_PROGRESS, progress);
        return Math.pow(fade, WIND_BRAKE_POWER);
    }

    private static double computeWindStartGate(double progress) {
        if (activePhase == SleepAnimationPhase.BRAKING) {
            return 1.0D;
        }
        if (activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED) {
            return smoothstep(0.0D, 0.025D, progress);
        }
        return smoothstep(0.01D, 0.10D, progress);
    }

    private static double computeWindBrakeTail(double progress, double speedIntensity) {
        return smoothstep(WIND_TAIL_RISE_START_PROGRESS, WIND_TAIL_FULL_PROGRESS, progress)
                * (1.0D - smoothstep(WIND_TAIL_FADE_START_PROGRESS, WIND_TAIL_FADE_END_PROGRESS, progress))
                * (0.070D + 0.030D * speedIntensity);
    }

    private static double computeSpeedIntensity(ClientSleepAnimationState sleepState) {
        double speed = Math.max(0.0D, sleepState.getCurrentDayTimeSpeedPerTick());
        return Math.log1p(Math.min(speed, 2400.0D)) / Math.log1p(2400.0D);
    }

    private static double computeWindMotionVariation(ClientLevel level, double progress, double speedIntensity) {
        double activeWindow = smoothstep(0.10D, 0.32D, progress) * (1.0D - smoothstep(0.82D, 1.0D, progress));
        double highSpeed = smoothstep(0.35D, 0.85D, speedIntensity);
        double amplitude = (0.014D + 0.028D * highSpeed) * activeWindow;
        if (amplitude <= 0.0D) {
            return 1.0D;
        }

        double time = level.getGameTime();
        double slow = Math.sin(time * 0.043D + activeSessionId * 0.17D);
        double slower = Math.sin(time * 0.017D + 1.9D + activeSequenceId * 0.07D);
        return 1.0D + (slow * 0.65D + slower * 0.35D) * amplitude;
    }

    private static double computeWeatherMultiplier(ClientLevel level) {
        if (level.isThundering()) {
            return WIND_THUNDER_VOLUME_MULTIPLIER;
        }
        if (level.isRaining()) {
            return WIND_RAIN_VOLUME_MULTIPLIER;
        }
        return 1.0D;
    }

    private static double computeWeatherPitchOffset(ClientLevel level) {
        if (level.isThundering()) {
            return WIND_THUNDER_PITCH_OFFSET;
        }
        if (level.isRaining()) {
            return WIND_RAIN_PITCH_OFFSET;
        }
        return 0.0D;
    }

    private static void ensureWindLoop(Minecraft client) {
        if (isSoundReferenceAlive(client, windLoop)) {
            return;
        }

        windLoop = null;
        if (client == null) {
            return;
        }
        windLoop = new SleepLoopSoundInstance(SleepSoundIds.SLEEP_WIND, SoundSource.MASTER, WIND_FADE_TICKS);
        client.getSoundManager().play(windLoop);
    }

    private static void stopAllLoops() {
        stopWindLoop(WIND_STOP_FADE_TICKS);
    }

    private static void stopWindLoop(int fadeTicks) {
        if (windLoop != null && !windLoop.isStopped()) {
            windLoop.stopWithFade(fadeTicks);
        }
    }

    private static void stopMadeInHeavenMainSound(int fadeTicks) {
        if (madeInHeavenMainSound != null && !madeInHeavenMainSound.isStopped()) {
            madeInHeavenMainSound.stopWithFade(fadeTicks);
        }
    }

    private static void clearLoopSoundReferences() {
        windLoop = null;
        madeInHeavenMainSound = null;
    }

    private static void pruneStoppedSounds(Minecraft client) {
        if (windLoop != null && !isSoundReferenceAlive(client, windLoop)) {
            windLoop = null;
        }
        if (madeInHeavenMainSound != null && !isSoundReferenceAlive(client, madeInHeavenMainSound)) {
            madeInHeavenMainSound = null;
        }
        if (timeAccelSound != null && !isSoundReferenceAlive(client, timeAccelSound)) {
            timeAccelSound = null;
        }
        if (timeDecelSound != null && !isSoundReferenceAlive(client, timeDecelSound)) {
            timeDecelSound = null;
        }
        if (madeInHeavenStopSound != null && !isSoundReferenceAlive(client, madeInHeavenStopSound)) {
            madeInHeavenStopSound = null;
        }
        if (bellSound != null && !isSoundReferenceAlive(client, bellSound)) {
            bellSound = null;
        }
        if (astroPassSound != null && !isSoundReferenceAlive(client, astroPassSound)) {
            astroPassSound = null;
        }
    }

    private static boolean isSoundReferenceAlive(Minecraft client, SoundInstance instance) {
        if (client == null || instance == null) {
            return false;
        }
        if (instance instanceof TickableSoundInstance tickableSound && tickableSound.isStopped()) {
            return false;
        }
        return client.getSoundManager().isActive(instance);
    }

    private static SoundInstance playOneShot(Minecraft client,
                                             net.minecraft.resources.ResourceLocation soundId,
                                             float volume,
                                             float pitch) {
        if (client == null || volume <= 0.0F) {
            return null;
        }

        SoundInstance sound = new SimpleSoundInstance(
                soundId,
                SoundSource.MASTER,
                clamp01(volume),
                clamp(pitch, 0.5F, 2.0F),
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
        client.getSoundManager().play(sound);
        return sound;
    }

    private static boolean isFreshPayload(SleepAnimationStartPayload payload) {
        if (payload.currentDayTime() > payload.startTimeOfDay()) {
            return false;
        }
        long startGameTime = payload.serverStartGameTime();
        long sentGameTime = payload.serverGameTimeAtSend();
        if (startGameTime < 0L || sentGameTime < startGameTime) {
            return true;
        }
        return sentGameTime - startGameTime <= FRESH_PAYLOAD_MAX_ELAPSED_TICKS;
    }

    private static boolean crossedAstroOverhead(long previousVisualDayTime, long currentVisualDayTime, long overheadOffset) {
        if (currentVisualDayTime <= previousVisualDayTime) {
            return false;
        }

        long previousCycle = Math.floorDiv(previousVisualDayTime - overheadOffset, DAY_TICKS);
        long currentCycle = Math.floorDiv(currentVisualDayTime - overheadOffset, DAY_TICKS);
        return currentCycle > previousCycle;
    }

    private static void playAstroPassOnce(Minecraft client,
                                          SeamlessSleepClientConfig config,
                                          ClientSleepAnimationState sleepState,
                                          double speedIntensity,
                                          double minSpeedIntensity) {
        double speed = Math.max(0.0D, sleepState.getCurrentDayTimeSpeedPerTick());
        double pitchIntensity = smoothstep(0.80D, 0.98D, speedIntensity);
        double extremeSpeed = smoothstep(1200.0D, 3600.0D, speed);
        float volume = resolveAstroPassVolume(config, speedIntensity, minSpeedIntensity);
        float pitch = clamp((float) (0.95D + 0.42D * pitchIntensity + 0.95D * extremeSpeed), 0.85F, 2.50F);
        astroPassSound = playOneShot(client, SleepSoundIds.TIME_ASTRO, volume, pitch);
    }

    private static double resolveAstroPassMinSpeedIntensity() {
        return activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED
                ? ASTRO_PASS_MIN_SPEED_INTENSITY_MIH
                : ASTRO_PASS_MIN_SPEED_INTENSITY_TIMELAPSE;
    }

    private static float resolveAstroPassVolume(SeamlessSleepClientConfig config,
                                                double speedIntensity,
                                                double minSpeedIntensity) {
        double passIntensity = smoothstep(minSpeedIntensity, 1.0D, speedIntensity);
        float base = activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED
                ? ASTRO_VOLUME_BASE_MIH
                : ASTRO_VOLUME_BASE_TIMELAPSE;
        float extra = activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED
                ? ASTRO_VOLUME_EXTRA_MIH
                : ASTRO_VOLUME_EXTRA_TIMELAPSE;
        return soundtrackVolume(config, (float) (base + extra * passIntensity));
    }

    private static void scheduleMadeInHeavenIntroStart() {
        if (madeInHeavenIntroStartedSessionId == activeSessionId
                || madeInHeavenIntroScheduledSessionId == activeSessionId) {
            return;
        }

        madeInHeavenIntroScheduledSessionId = activeSessionId;
        madeInHeavenIntroDelayTicks = MADE_IN_HEAVEN_TIME_ACCEL_DELAY_TICKS;
    }

    private static void updateMadeInHeavenDelayedIntroStart(Minecraft client) {
        if (madeInHeavenIntroScheduledSessionId != activeSessionId) {
            return;
        }

        if (activeMode != SleepAnimationMode.MADE_IN_HEAVEN_BED
                || activePhase != SleepAnimationPhase.RUNNING) {
            cancelMadeInHeavenPendingIntro();
            return;
        }

        if (madeInHeavenIntroDelayTicks > 0) {
            madeInHeavenIntroDelayTicks--;
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent > 0) {
            playMadeInHeavenIntroNow(client, config);
        }
        cancelMadeInHeavenPendingIntro();
    }

    private static void playMadeInHeavenIntroNow(Minecraft client, SeamlessSleepClientConfig config) {
        if (madeInHeavenIntroStartedSessionId == activeSessionId) {
            return;
        }

        madeInHeavenIntroStartedSessionId = activeSessionId;
        playTimeAccelOnce(client, config);
        startMadeInHeavenMainNow(client, config);
    }

    private static void startMadeInHeavenMainNow(Minecraft client, SeamlessSleepClientConfig config) {
        if (isSoundReferenceAlive(client, madeInHeavenMainSound)) {
            return;
        }

        madeInHeavenMainSound = null;
        if (client == null) {
            return;
        }
        madeInHeavenMainSound = new SleepFadingSoundInstance(
                SleepSoundIds.MADE_IN_HEAVEN_MAIN,
                SoundSource.MASTER,
                soundtrackVolume(config, MADE_IN_HEAVEN_MAIN_VOLUME),
                MADE_IN_HEAVEN_MAIN_TECH_FADE_IN_TICKS
        );
        client.getSoundManager().play(madeInHeavenMainSound);
        madeInHeavenMainStartedSessionId = activeSessionId;
        madeInHeavenMainFadeOutMode = "NONE";
    }

    private static void updateMadeInHeavenMainVolume(Minecraft client) {
        if (activeMode != SleepAnimationMode.MADE_IN_HEAVEN_BED
                || madeInHeavenMainStartedSessionId != activeSessionId
                || !"NONE".equals(madeInHeavenMainFadeOutMode)) {
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent <= 0) {
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
            return;
        }

        if (!isSoundReferenceAlive(client, madeInHeavenMainSound)) {
            madeInHeavenMainSound = null;
            if (activePhase == SleepAnimationPhase.RUNNING
                    || (activePhase == SleepAnimationPhase.BRAKING && madeInHeavenNaturalBrakeActive)) {
                startMadeInHeavenMainNow(client, config);
            }
            return;
        }

        madeInHeavenMainSound.setTargetVolume(soundtrackVolume(config, MADE_IN_HEAVEN_MAIN_VOLUME));
    }

    private static void startMadeInHeavenBrake(Minecraft client,
                                               SeamlessSleepClientConfig config,
                                               boolean naturalAutoBrake) {
        if (madeInHeavenNaturalBrakeActive
                || madeInHeavenCancelBrakeActive
                || madeInHeavenTimeDecelScheduledSessionId == activeSessionId
                || timeDecelPlayedSessionId == activeSessionId) {
            return;
        }

        if (naturalAutoBrake) {
            startMadeInHeavenNaturalBrake();
        } else {
            startMadeInHeavenCancelBrake(client, config);
        }
    }

    private static void startMadeInHeavenNaturalBrake() {
        madeInHeavenNaturalBrakeActive = true;
        madeInHeavenCancelBrakeActive = false;
        madeInHeavenDelayedDecelIsCancel = false;
        madeInHeavenBrakeWindStartVolume = 0.0F;
        madeInHeavenMainFadeOutMode = "NONE";
        madeInHeavenTimeDecelScheduledSessionId = activeSessionId;
        madeInHeavenTimeDecelDelayTicks = computeMadeInHeavenNaturalDecelDelayTicks();
    }

    private static void startMadeInHeavenCancelBrake(Minecraft client, SeamlessSleepClientConfig config) {
        madeInHeavenNaturalBrakeActive = false;
        madeInHeavenCancelBrakeActive = true;
        madeInHeavenDelayedDecelIsCancel = true;
        madeInHeavenBrakeWindStartVolume = getCurrentWindVolumeSafely(client);
        stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_CANCEL_TICKS);
        madeInHeavenMainFadeOutMode = "CANCEL";
        playMadeInHeavenStopOnce(client, config);
        madeInHeavenTimeDecelScheduledSessionId = activeSessionId;
        madeInHeavenTimeDecelDelayTicks = MADE_IN_HEAVEN_CANCEL_DECEL_DELAY_TICKS;
    }

    private static int computeMadeInHeavenNaturalDecelDelayTicks() {
        return Math.max(0, Math.max(1, activeDurationTicks) - MADE_IN_HEAVEN_BELL_DROP_TICKS);
    }

    private static double computeMadeInHeavenNaturalWindFadeStartProgress() {
        return clamp01(computeMadeInHeavenNaturalDecelDelayTicks() / (double) Math.max(1, activeDurationTicks));
    }

    private static float getCurrentWindVolumeSafely(Minecraft client) {
        return isSoundReferenceAlive(client, windLoop) ? windLoop.getCurrentVolume() : 0.0F;
    }

    private static void updateMadeInHeavenDelayedTimeDecel(Minecraft client) {
        if (madeInHeavenTimeDecelScheduledSessionId != activeSessionId) {
            return;
        }

        if (activeMode != SleepAnimationMode.MADE_IN_HEAVEN_BED
                || activePhase != SleepAnimationPhase.BRAKING) {
            madeInHeavenTimeDecelScheduledSessionId = -1L;
            madeInHeavenTimeDecelDelayTicks = -1;
            madeInHeavenDelayedDecelIsCancel = false;
            return;
        }

        if (madeInHeavenTimeDecelDelayTicks > 0) {
            madeInHeavenTimeDecelDelayTicks--;
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent > 0) {
            if (madeInHeavenDelayedDecelIsCancel) {
                playMadeInHeavenCancelDecelNow(client, config);
            } else {
                playMadeInHeavenNaturalDecelNow(client, config);
            }
        }
        madeInHeavenTimeDecelScheduledSessionId = -1L;
        madeInHeavenTimeDecelDelayTicks = -1;
        madeInHeavenDelayedDecelIsCancel = false;
    }

    private static void playMadeInHeavenNaturalDecelNow(Minecraft client, SeamlessSleepClientConfig config) {
        stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_NATURAL_TICKS);
        madeInHeavenMainFadeOutMode = "NATURAL";
        playTimeDecelOnce(client, config);
        playBellOnce(client, config);
    }

    private static void playMadeInHeavenCancelDecelNow(Minecraft client, SeamlessSleepClientConfig config) {
        playTimeDecelOnce(client, config);
    }

    private static void playTimeAccelOnce(Minecraft client, SeamlessSleepClientConfig config) {
        if (timeAccelPlayedSessionId == activeSessionId) {
            return;
        }

        timeAccelSound = playOneShot(client, SleepSoundIds.TIME_ACCEL, soundtrackVolume(config, 1.0F), 1.0F);
        timeAccelPlayedSessionId = activeSessionId;
    }

    private static void playTimeDecelOnce(Minecraft client, SeamlessSleepClientConfig config) {
        if (timeDecelPlayedSessionId == activeSessionId) {
            return;
        }

        timeDecelSound = playOneShot(client, SleepSoundIds.TIME_DECEL, soundtrackVolume(config, 1.0F), 1.0F);
        timeDecelPlayedSessionId = activeSessionId;
    }

    private static void playMadeInHeavenStopOnce(Minecraft client, SeamlessSleepClientConfig config) {
        if (madeInHeavenStopPlayedSessionId == activeSessionId) {
            return;
        }

        madeInHeavenStopSound = playOneShot(
                client,
                SleepSoundIds.MADE_IN_HEAVEN_STOP,
                soundtrackVolume(config, 0.72F),
                1.0F
        );
        madeInHeavenStopPlayedSessionId = activeSessionId;
    }

    private static void playBellOnce(Minecraft client, SeamlessSleepClientConfig config) {
        if (bellPlayedSessionId == activeSessionId) {
            return;
        }

        bellSound = playOneShot(client, SleepSoundIds.TIME_BELL, soundtrackVolume(config, 0.80F), 1.0F);
        bellPlayedSessionId = activeSessionId;
    }

    private static void stopOneShotSounds(Minecraft client) {
        if (client != null) {
            if (timeAccelSound != null) {
                client.getSoundManager().stop(timeAccelSound);
            }
            if (timeDecelSound != null) {
                client.getSoundManager().stop(timeDecelSound);
            }
            if (madeInHeavenStopSound != null) {
                client.getSoundManager().stop(madeInHeavenStopSound);
            }
            if (bellSound != null) {
                client.getSoundManager().stop(bellSound);
            }
            if (astroPassSound != null) {
                client.getSoundManager().stop(astroPassSound);
            }
        }
        timeAccelSound = null;
        timeDecelSound = null;
        madeInHeavenStopSound = null;
        bellSound = null;
        astroPassSound = null;
    }

    private static boolean shouldSuppressAllAudio(Minecraft client) {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        return client != null
                && config.disableSoundsDuringReplay
                && ReplayPlaybackCompat.isReplayPlaybackActive();
    }

    private static boolean shouldSuppressMadeInHeavenMusic(Minecraft client) {
        if (client == null
                || client.level == null
                || client.player == null
                || activeSessionId < 0L
                || activeMode != SleepAnimationMode.MADE_IN_HEAVEN_BED
                || shouldSuppressAllAudio(client)) {
            return false;
        }

        if (activePhase != SleepAnimationPhase.RUNNING && activePhase != SleepAnimationPhase.BRAKING) {
            return false;
        }

        ClientSleepAnimationState sleepState = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (sleepState == null || !sleepState.isActive()) {
            return false;
        }

        return SeamlessSleepClientConfigManager.get().soundtrackVolumePercent > 0;
    }

    private static float soundtrackVolume(SeamlessSleepClientConfig config, float multiplier) {
        return (float) clamp01((config.soundtrackVolumePercent / 100.0D) * multiplier * audioEnvironment.mainMultiplier());
    }

    private static void primeAudioEnvironment(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            audioEnvironment.reset();
            return;
        }
        audioEnvironment.updateImmediate(client.level, client.player);
    }

    private static void resetSessionState(String reason) {
        activeSessionId = -1L;
        activeSequenceId = -1L;
        activeDurationTicks = 1;
        activeMode = SleepAnimationMode.NORMAL_SLEEP;
        activePhase = SleepAnimationPhase.IDLE;
        activeSoundMode = SleepAnimationSoundMode.MUTED;
        clearPlaybackMarkers();
        Constants.debug("Sleep sound session cleared: {}.", reason);
    }

    private static boolean isResetStateIdle() {
        return activeSessionId < 0L
                && activeSequenceId < 0L
                && activeMode == SleepAnimationMode.NORMAL_SLEEP
                && activePhase == SleepAnimationPhase.IDLE
                && activeSoundMode == SleepAnimationSoundMode.MUTED
                && !hasSoundReferences()
                && !hasPendingMadeInHeavenState();
    }

    private static boolean hasSoundReferences() {
        return windLoop != null
                || madeInHeavenMainSound != null
                || timeAccelSound != null
                || timeDecelSound != null
                || madeInHeavenStopSound != null
                || bellSound != null
                || astroPassSound != null;
    }

    private static boolean hasPendingMadeInHeavenState() {
        return madeInHeavenIntroScheduledSessionId >= 0L
                || madeInHeavenIntroStartedSessionId >= 0L
                || madeInHeavenTimeDecelScheduledSessionId >= 0L
                || madeInHeavenNaturalBrakeActive
                || madeInHeavenCancelBrakeActive
                || !"NONE".equals(madeInHeavenMainFadeOutMode)
                || madeInHeavenMainStartedSessionId >= 0L;
    }

    private static void clearPlaybackMarkers() {
        timeAccelPlayedSessionId = -1L;
        timeDecelPlayedSessionId = -1L;
        bellPlayedSessionId = -1L;
        resetAstroPassTracking();
        madeInHeavenStopPlayedSessionId = -1L;
        madeInHeavenIntroScheduledSessionId = -1L;
        madeInHeavenIntroStartedSessionId = -1L;
        madeInHeavenIntroDelayTicks = -1;
        madeInHeavenTimeDecelScheduledSessionId = -1L;
        madeInHeavenTimeDecelDelayTicks = -1;
        madeInHeavenDelayedDecelIsCancel = false;
        madeInHeavenNaturalBrakeActive = false;
        madeInHeavenCancelBrakeActive = false;
        madeInHeavenBrakeWindStartVolume = 0.0F;
        madeInHeavenMainFadeOutMode = "NONE";
        madeInHeavenMainStartedSessionId = -1L;
    }

    private static void resetAstroPassTracking() {
        astroPassTrackingSessionId = -1L;
        lastAstroPassVisualDayTime = Long.MIN_VALUE;
        astroPassSunPassesSeen = 0;
    }

    private static void cancelMadeInHeavenDelayedSounds() {
        cancelMadeInHeavenPendingIntro();
        madeInHeavenIntroStartedSessionId = -1L;
        madeInHeavenTimeDecelScheduledSessionId = -1L;
        madeInHeavenTimeDecelDelayTicks = -1;
        madeInHeavenDelayedDecelIsCancel = false;
        madeInHeavenNaturalBrakeActive = false;
        madeInHeavenCancelBrakeActive = false;
        madeInHeavenBrakeWindStartVolume = 0.0F;
        madeInHeavenMainStartedSessionId = -1L;
        madeInHeavenMainFadeOutMode = "NONE";
    }

    private static void cancelMadeInHeavenPendingIntro() {
        madeInHeavenIntroScheduledSessionId = -1L;
        madeInHeavenIntroDelayTicks = -1;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge0 == edge1) {
            return value < edge0 ? 0.0D : 1.0D;
        }

        double x = clamp01((value - edge0) / (edge1 - edge0));
        return x * x * (3.0D - 2.0D * x);
    }

    private static float clamp01(float value) {
        return (float) clamp01((double) value);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private record WindSoundFrame(float targetVolume,
                                  float pitch) {
    }
}
