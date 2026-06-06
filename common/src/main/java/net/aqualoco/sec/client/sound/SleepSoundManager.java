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
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class SleepSoundManager {
    private static final long DAY_TICKS = 24000L;
    private static final long SUN_OVERHEAD_TICKS = 6000L;
    private static final int WIND_FADE_TICKS = 24;
    private static final int WIND_STOP_FADE_TICKS = 34;
    private static final int MADE_IN_HEAVEN_TIME_ACCEL_DELAY_TICKS = 12;
    private static final int MADE_IN_HEAVEN_TIME_DECEL_DELAY_TICKS = 40;
    private static final int MADE_IN_HEAVEN_MAIN_DELAY_TICKS = 42;
    private static final int MADE_IN_HEAVEN_MAIN_FADE_IN_TICKS = 130;
    private static final int MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS = 160;
    private static final int TIME_DECEL_AUDIBLE_TICKS = 72;
    private static final int FRESH_PAYLOAD_MAX_ELAPSED_TICKS = 5;
    private static final double MADE_IN_HEAVEN_BELL_PROGRESS = 0.88D;
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
    private static final double ASTRO_PASS_MIN_SPEED_INTENSITY = 0.52D;
    private static final boolean AUDIO_DEBUG_LOG_ENABLED = true; // TODO audio-debug-remove: remove temporary audio runtime logger.
    private static final int AUDIO_DEBUG_LOG_INTERVAL_TICKS = 5; // TODO audio-debug-remove: remove temporary audio runtime logger.
    private static final Path AUDIO_DEBUG_LOG_PATH = Path.of("seamless_sleep_audio_runtime_debug.txt"); // TODO audio-debug-remove: remove temporary audio runtime logger.

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
    private static long audioDebugTickCounter; // TODO audio-debug-remove: remove temporary audio runtime logger.
    private static long audioDebugLastSessionId = Long.MIN_VALUE; // TODO audio-debug-remove: remove temporary audio runtime logger.
    private static long audioDebugLastSequenceId = Long.MIN_VALUE; // TODO audio-debug-remove: remove temporary audio runtime logger.
    private static long madeInHeavenBellEligibleSessionId = -1L;
    private static long madeInHeavenStopPlayedSessionId = -1L;
    private static long madeInHeavenTimeAccelScheduledSessionId = -1L;
    private static int madeInHeavenTimeAccelDelayTicks = -1;
    private static long madeInHeavenTimeDecelScheduledSessionId = -1L;
    private static int madeInHeavenTimeDecelDelayTicks = -1;
    private static boolean madeInHeavenDelayedDecelNeedsStop;
    private static long madeInHeavenMainStartedSessionId = -1L;
    private static long madeInHeavenMainScheduledSessionId = -1L;
    private static int madeInHeavenMainDelayTicks = -1;

    private static SleepLoopSoundInstance windLoop;
    private static SleepFadingSoundInstance madeInHeavenMainSound;
    private static SoundInstance timeAccelSound;
    private static SoundInstance timeDecelSound;
    private static SoundInstance madeInHeavenStopSound;
    private static SoundInstance bellSound;
    private static SoundInstance astroPassSound;

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
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS);
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

        if (shouldSuppressAllAudio(client)) {
            stopWindLoop(WIND_STOP_FADE_TICKS);
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS);
            stopOneShotSounds(client);
            cancelMadeInHeavenDelayedSounds();
            return;
        }

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
        stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS);
        resetSessionState("sleep_stop_" + payload.reason().name().toLowerCase(Locale.ROOT));
    }

    public static void tick(Minecraft client) {
        pruneStoppedSounds();
        if (client == null) {
            stopAllLoops();
            return;
        }

        if (shouldSuppressAllAudio(client)) {
            stopAllLoops();
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS);
            stopOneShotSounds(client);
            cancelMadeInHeavenDelayedSounds();
            return;
        }

        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            stopAllLoops();
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS);
            stopOneShotSounds(client);
            cancelMadeInHeavenDelayedSounds();
            return;
        }

        ClientSleepAnimationState sleepState = SeamlessSleepClientState.SLEEP_ANIMATION;
        if (activeSessionId < 0L || !sleepState.isActive()) {
            stopAllLoops();
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS);
            cancelMadeInHeavenDelayedSounds();
            return;
        }

        updateMadeInHeavenDelayedTimeAccel(client);
        updateMadeInHeavenDelayedTimeDecel(client);
        updateMadeInHeavenDelayedStart(client);
        updateMadeInHeavenNaturalBell(client, sleepState);
        updateWindLoop(client, level, player, sleepState);
        updateAstroPassSound(client, sleepState);
        updateTimelapseEpicDecel(client, sleepState);
        debugAudioRuntime(client, level, player, sleepState); // TODO audio-debug-remove: remove temporary audio runtime logger.
    }

    public static void reset(String reason) {
        if (activeSessionId >= 0L) {
            Constants.debug("Sleep sound state reset: {}.", reason);
        }
        stopAllLoops();
        Minecraft client = Minecraft.getInstance();
        stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS);
        stopOneShotSounds(client);
        resetSessionState(reason);
    }

    private static void handleTimelapsePayload(Minecraft client, boolean freshPayload) {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent <= 0 || activeSoundMode.isMuted()) {
            stopWindLoop(WIND_STOP_FADE_TICKS);
            return;
        }

        if (activePhase == SleepAnimationPhase.BRAKING) {
            stopWindLoop(WIND_STOP_FADE_TICKS);
            if (activeSoundMode == SleepAnimationSoundMode.EPIC && freshPayload) {
                playTimeDecelOnce(client, config);
            }
            return;
        }

        if (activeSoundMode == SleepAnimationSoundMode.EPIC && freshPayload) {
            playTimeAccelOnce(client, config);
        }
    }

    private static void handleMadeInHeavenPayload(Minecraft client, boolean freshPayload) {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent <= 0) {
            return;
        }

        if (activePhase == SleepAnimationPhase.BRAKING) {
            if (freshPayload) {
                boolean naturalAutoBrake = activeDurationTicks >= 100;
                if (naturalAutoBrake) {
                    madeInHeavenBellEligibleSessionId = activeSessionId;
                }
                scheduleMadeInHeavenTimeDecelStart(!naturalAutoBrake);
            }
            madeInHeavenMainScheduledSessionId = -1L;
            madeInHeavenMainDelayTicks = -1;
            return;
        }

        if (activePhase == SleepAnimationPhase.RUNNING && freshPayload) {
            scheduleMadeInHeavenTimeAccelStart();
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
            if (madeInHeavenBellEligibleSessionId == activeSessionId) {
                playBellOnce(client, config);
            }
            return;
        }

        playTimeDecelOnce(client, config);
        playMadeInHeavenStopOnce(client, config);
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
        if (speedIntensity < ASTRO_PASS_MIN_SPEED_INTENSITY) {
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        playAstroPassOnce(client, config, sleepState, speedIntensity);
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

    private static void updateMadeInHeavenNaturalBell(Minecraft client, ClientSleepAnimationState sleepState) {
        if (activeMode != SleepAnimationMode.MADE_IN_HEAVEN_BED
                || activePhase != SleepAnimationPhase.BRAKING
                || madeInHeavenBellEligibleSessionId != activeSessionId
                || bellPlayedSessionId == activeSessionId
                || sleepState.getProgress() < MADE_IN_HEAVEN_BELL_PROGRESS) {
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent > 0) {
            playBellOnce(client, config);
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

        double environment = computeSkyExposureMultiplier(level, player) * computeWeatherMultiplier(level);
        double velocityBlend = 0.70D + 0.30D * speedIntensity;
        double motionVariation = computeWindMotionVariation(level, progress, speedIntensity);
        double configuredVolume = config.sleepWindVolumePercent / 100.0D;
        float targetVolume = (float) clamp01(base * curve * environment * velocityBlend * motionVariation * configuredVolume);
        float pitch = computeWindPitch(level, sleepState, speedIntensity);
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

            double fadeStart = clamp01(MADE_IN_HEAVEN_TIME_DECEL_DELAY_TICKS / (double) Math.max(1, activeDurationTicks));
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

    private static double computeSkyExposureMultiplier(ClientLevel level, LocalPlayer player) {
        if (level.dimension() != Level.OVERWORLD) {
            return 0.45D;
        }

        BlockPos eyePos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        if (level.canSeeSky(eyePos) || level.canSeeSky(player.blockPosition())) {
            return 1.0D;
        }

        return player.getY() <= 45.0D ? 0.18D : 0.45D;
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
        if (windLoop != null && !windLoop.isStopped()) {
            return;
        }

        windLoop = new SleepLoopSoundInstance(SleepSoundIds.SLEEP_WIND, SoundSource.AMBIENT, WIND_FADE_TICKS);
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

    private static void pruneStoppedSounds() {
        if (windLoop != null && windLoop.isStopped()) {
            windLoop = null;
        }
        if (madeInHeavenMainSound != null && madeInHeavenMainSound.isStopped()) {
            madeInHeavenMainSound = null;
        }
    }

    private static SoundInstance playOneShot(Minecraft client,
                                             net.minecraft.resources.Identifier soundId,
                                             float volume,
                                             float pitch) {
        if (client == null || volume <= 0.0F) {
            return null;
        }

        SoundInstance sound = new SimpleSoundInstance(
                soundId,
                SoundSource.AMBIENT,
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
                                          double speedIntensity) {
        double passIntensity = smoothstep(ASTRO_PASS_MIN_SPEED_INTENSITY, 1.0D, speedIntensity);
        double speed = Math.max(0.0D, sleepState.getCurrentDayTimeSpeedPerTick());
        double pitchIntensity = smoothstep(0.80D, 0.98D, speedIntensity);
        double extremeSpeed = smoothstep(1200.0D, 3600.0D, speed);
        float volume = soundtrackVolume(config, (float) (0.08D + 0.18D * passIntensity));
        float pitch = clamp((float) (0.95D + 0.42D * pitchIntensity + 0.95D * extremeSpeed), 0.85F, 2.50F);
        astroPassSound = playOneShot(client, SleepSoundIds.TIME_ASTRO, volume, pitch);
    }

    private static void scheduleMadeInHeavenTimeAccelStart() {
        if (timeAccelPlayedSessionId == activeSessionId
                || madeInHeavenTimeAccelScheduledSessionId == activeSessionId) {
            return;
        }

        madeInHeavenTimeAccelScheduledSessionId = activeSessionId;
        madeInHeavenTimeAccelDelayTicks = MADE_IN_HEAVEN_TIME_ACCEL_DELAY_TICKS;
    }

    private static void updateMadeInHeavenDelayedTimeAccel(Minecraft client) {
        if (madeInHeavenTimeAccelScheduledSessionId != activeSessionId) {
            return;
        }

        if (activeMode != SleepAnimationMode.MADE_IN_HEAVEN_BED
                || activePhase != SleepAnimationPhase.RUNNING) {
            madeInHeavenTimeAccelScheduledSessionId = -1L;
            madeInHeavenTimeAccelDelayTicks = -1;
            return;
        }

        if (madeInHeavenTimeAccelDelayTicks > 0) {
            madeInHeavenTimeAccelDelayTicks--;
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent > 0) {
            playTimeAccelOnce(client, config);
            scheduleMadeInHeavenMainStart();
        }
        madeInHeavenTimeAccelScheduledSessionId = -1L;
        madeInHeavenTimeAccelDelayTicks = -1;
    }

    private static void scheduleMadeInHeavenTimeDecelStart(boolean playStopSound) {
        if (timeDecelPlayedSessionId == activeSessionId
                || madeInHeavenTimeDecelScheduledSessionId == activeSessionId) {
            return;
        }

        madeInHeavenTimeDecelScheduledSessionId = activeSessionId;
        madeInHeavenTimeDecelDelayTicks = MADE_IN_HEAVEN_TIME_DECEL_DELAY_TICKS;
        madeInHeavenDelayedDecelNeedsStop = playStopSound;
    }

    private static void updateMadeInHeavenDelayedTimeDecel(Minecraft client) {
        if (madeInHeavenTimeDecelScheduledSessionId != activeSessionId) {
            return;
        }

        if (activeMode != SleepAnimationMode.MADE_IN_HEAVEN_BED
                || activePhase != SleepAnimationPhase.BRAKING) {
            madeInHeavenTimeDecelScheduledSessionId = -1L;
            madeInHeavenTimeDecelDelayTicks = -1;
            madeInHeavenDelayedDecelNeedsStop = false;
            return;
        }

        if (madeInHeavenTimeDecelDelayTicks > 0) {
            madeInHeavenTimeDecelDelayTicks--;
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent > 0) {
            stopMadeInHeavenMainSound(MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS);
            playTimeDecelOnce(client, config);
            if (madeInHeavenDelayedDecelNeedsStop) {
                playMadeInHeavenStopOnce(client, config);
            }
        }
        madeInHeavenTimeDecelScheduledSessionId = -1L;
        madeInHeavenTimeDecelDelayTicks = -1;
        madeInHeavenDelayedDecelNeedsStop = false;
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

    private static void scheduleMadeInHeavenMainStart() {
        if (madeInHeavenMainStartedSessionId == activeSessionId
                || madeInHeavenMainScheduledSessionId == activeSessionId) {
            return;
        }

        madeInHeavenMainScheduledSessionId = activeSessionId;
        madeInHeavenMainDelayTicks = MADE_IN_HEAVEN_MAIN_DELAY_TICKS;
    }

    private static void updateMadeInHeavenDelayedStart(Minecraft client) {
        if (madeInHeavenMainScheduledSessionId != activeSessionId) {
            return;
        }

        if (activeMode != SleepAnimationMode.MADE_IN_HEAVEN_BED
                || activePhase != SleepAnimationPhase.RUNNING) {
            madeInHeavenMainScheduledSessionId = -1L;
            madeInHeavenMainDelayTicks = -1;
            return;
        }

        if (madeInHeavenMainDelayTicks > 0) {
            madeInHeavenMainDelayTicks--;
            return;
        }

        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        if (config.soundtrackVolumePercent <= 0 || madeInHeavenMainStartedSessionId == activeSessionId) {
            madeInHeavenMainScheduledSessionId = -1L;
            madeInHeavenMainDelayTicks = -1;
            return;
        }

        madeInHeavenMainSound = new SleepFadingSoundInstance(
                SleepSoundIds.MADE_IN_HEAVEN_MAIN,
                SoundSource.AMBIENT,
                soundtrackVolume(config, 0.72F),
                MADE_IN_HEAVEN_MAIN_FADE_IN_TICKS
        );
        client.getSoundManager().play(madeInHeavenMainSound);
        madeInHeavenMainStartedSessionId = activeSessionId;
        madeInHeavenMainScheduledSessionId = -1L;
        madeInHeavenMainDelayTicks = -1;
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

    private static float soundtrackVolume(SeamlessSleepClientConfig config, float multiplier) {
        return (float) clamp01((config.soundtrackVolumePercent / 100.0D) * multiplier);
    }

    private static void debugAudioRuntime(Minecraft client, ClientLevel level, LocalPlayer player, ClientSleepAnimationState sleepState) { // TODO audio-debug-remove: remove temporary audio runtime logger.
        if (!AUDIO_DEBUG_LOG_ENABLED) { // TODO audio-debug-remove: remove temporary audio runtime logger.
            return; // TODO audio-debug-remove: remove temporary audio runtime logger.
        } // TODO audio-debug-remove: remove temporary audio runtime logger.
        audioDebugTickCounter++; // TODO audio-debug-remove: remove temporary audio runtime logger.
        boolean force = activeSessionId != audioDebugLastSessionId || activeSequenceId != audioDebugLastSequenceId; // TODO audio-debug-remove: remove temporary audio runtime logger.
        if (!force && audioDebugTickCounter % AUDIO_DEBUG_LOG_INTERVAL_TICKS != 0L) { // TODO audio-debug-remove: remove temporary audio runtime logger.
            return; // TODO audio-debug-remove: remove temporary audio runtime logger.
        } // TODO audio-debug-remove: remove temporary audio runtime logger.
        audioDebugLastSessionId = activeSessionId; // TODO audio-debug-remove: remove temporary audio runtime logger.
        audioDebugLastSequenceId = activeSequenceId; // TODO audio-debug-remove: remove temporary audio runtime logger.
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get(); // TODO audio-debug-remove: remove temporary audio runtime logger.
        double progress = sleepState.getProgress(); // TODO audio-debug-remove: remove temporary audio runtime logger.
        double easedVelocity = sleepState.getEasedVelocityFactor(); // TODO audio-debug-remove: remove temporary audio runtime logger.
        double daySpeed = sleepState.getCurrentDayTimeSpeedPerTick(); // TODO audio-debug-remove: remove temporary audio runtime logger.
        double speedIntensity = computeSpeedIntensity(sleepState); // TODO audio-debug-remove: remove temporary audio runtime logger.
        boolean windEligible = shouldPlayWind(); // TODO audio-debug-remove: remove temporary audio runtime logger.
        WindSoundFrame windFrame = windEligible ? computeWindSoundFrame(level, player, sleepState) : null; // TODO audio-debug-remove: remove temporary audio runtime logger.
        double skyMultiplier = computeSkyExposureMultiplier(level, player); // TODO audio-debug-remove: remove temporary audio runtime logger.
        double weatherMultiplier = computeWeatherMultiplier(level); // TODO audio-debug-remove: remove temporary audio runtime logger.
        double madeInHeavenWindFadeStart = activeMode == SleepAnimationMode.MADE_IN_HEAVEN_BED ? clamp01(MADE_IN_HEAVEN_TIME_DECEL_DELAY_TICKS / (double) Math.max(1, activeDurationTicks)) : -1.0D; // TODO audio-debug-remove: remove temporary audio runtime logger.
        StringBuilder line = new StringBuilder(1024); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append("wallMs=").append(System.currentTimeMillis()); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("gameTime=").append(level.getGameTime()); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("session=").append(activeSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("sequence=").append(activeSequenceId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mode=").append(activeMode); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("phase=").append(activePhase); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("soundMode=").append(activeSoundMode); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("durationTicks=").append(activeDurationTicks); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("progress=").append(String.format(Locale.ROOT, "%.5f", progress)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("visualDayTime=").append(sleepState.getCurrentVisualDayTime()); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("worldDayTime=").append(level.getDayTime()); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("easedVelocity=").append(String.format(Locale.ROOT, "%.5f", easedVelocity)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("daySpeed=").append(String.format(Locale.ROOT, "%.3f", daySpeed)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("speedIntensity=").append(String.format(Locale.ROOT, "%.5f", speedIntensity)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("sleepWindConfig=").append(config.sleepWindVolumePercent); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("soundtrackConfig=").append(config.soundtrackVolumePercent); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("disableSoundsDuringReplay=").append(config.disableSoundsDuringReplay); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("suppressAllAudio=").append(shouldSuppressAllAudio(client)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("windEligible=").append(windEligible); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("windTarget=").append(windFrame == null ? "NA" : String.format(Locale.ROOT, "%.5f", windFrame.targetVolume())); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("windPitch=").append(windFrame == null ? "NA" : String.format(Locale.ROOT, "%.5f", windFrame.pitch())); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("windCurrent=").append(windLoop == null ? "NA" : String.format(Locale.ROOT, "%.5f", windLoop.getCurrentVolume())); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("windStopped=").append(windLoop == null || windLoop.isStopped()); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("skyMultiplier=").append(String.format(Locale.ROOT, "%.5f", skyMultiplier)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("weatherMultiplier=").append(String.format(Locale.ROOT, "%.5f", weatherMultiplier)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("raining=").append(level.isRaining()); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("thundering=").append(level.isThundering()); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mainSoundCurrent=").append(madeInHeavenMainSound == null ? "NA" : String.format(Locale.ROOT, "%.5f", madeInHeavenMainSound.getCurrentVolume())); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mainSoundStopped=").append(madeInHeavenMainSound == null || madeInHeavenMainSound.isStopped()); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("timeAccelPlayed=").append(timeAccelPlayedSessionId == activeSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("timeDecelPlayed=").append(timeDecelPlayedSessionId == activeSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("bellPlayed=").append(bellPlayedSessionId == activeSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("madeInStopPlayed=").append(madeInHeavenStopPlayedSessionId == activeSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("astroSunPassesSeen=").append(astroPassSunPassesSeen); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("astroTrackingSession=").append(astroPassTrackingSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("lastAstroVisualDayTime=").append(lastAstroPassVisualDayTime); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mihAccelScheduled=").append(madeInHeavenTimeAccelScheduledSessionId == activeSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mihAccelDelayTicks=").append(madeInHeavenTimeAccelDelayTicks); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mihMainScheduled=").append(madeInHeavenMainScheduledSessionId == activeSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mihMainDelayTicks=").append(madeInHeavenMainDelayTicks); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mihDecelScheduled=").append(madeInHeavenTimeDecelScheduledSessionId == activeSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mihDecelDelayTicks=").append(madeInHeavenTimeDecelDelayTicks); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mihDelayedDecelNeedsStop=").append(madeInHeavenDelayedDecelNeedsStop); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mihBellEligible=").append(madeInHeavenBellEligibleSessionId == activeSessionId); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("mihWindFadeStartProgress=").append(String.format(Locale.ROOT, "%.5f", madeInHeavenWindFadeStart)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("constAccelDelay=").append(MADE_IN_HEAVEN_TIME_ACCEL_DELAY_TICKS); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("constDecelDelay=").append(MADE_IN_HEAVEN_TIME_DECEL_DELAY_TICKS); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("constMainDelay=").append(MADE_IN_HEAVEN_MAIN_DELAY_TICKS); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("constMainFadeIn=").append(MADE_IN_HEAVEN_MAIN_FADE_IN_TICKS); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("constMainFadeOut=").append(MADE_IN_HEAVEN_MAIN_FADE_OUT_TICKS); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("constBellProgress=").append(String.format(Locale.ROOT, "%.5f", MADE_IN_HEAVEN_BELL_PROGRESS)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        line.append('\t').append("constAstroMinSpeedIntensity=").append(String.format(Locale.ROOT, "%.5f", ASTRO_PASS_MIN_SPEED_INTENSITY)); // TODO audio-debug-remove: remove temporary audio runtime logger.
        try { // TODO audio-debug-remove: remove temporary audio runtime logger.
            Files.writeString(AUDIO_DEBUG_LOG_PATH, line.append(System.lineSeparator()).toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); // TODO audio-debug-remove: remove temporary audio runtime logger.
        } catch (IOException ignored) { // TODO audio-debug-remove: remove temporary audio runtime logger.
            // TODO audio-debug-remove: remove temporary audio runtime logger.
        } // TODO audio-debug-remove: remove temporary audio runtime logger.
    } // TODO audio-debug-remove: remove temporary audio runtime logger.

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

    private static void clearPlaybackMarkers() {
        timeAccelPlayedSessionId = -1L;
        timeDecelPlayedSessionId = -1L;
        bellPlayedSessionId = -1L;
        resetAstroPassTracking();
        madeInHeavenBellEligibleSessionId = -1L;
        madeInHeavenStopPlayedSessionId = -1L;
        madeInHeavenTimeAccelScheduledSessionId = -1L;
        madeInHeavenTimeAccelDelayTicks = -1;
        madeInHeavenTimeDecelScheduledSessionId = -1L;
        madeInHeavenTimeDecelDelayTicks = -1;
        madeInHeavenDelayedDecelNeedsStop = false;
        madeInHeavenMainStartedSessionId = -1L;
        madeInHeavenMainScheduledSessionId = -1L;
        madeInHeavenMainDelayTicks = -1;
    }

    private static void resetAstroPassTracking() {
        astroPassTrackingSessionId = -1L;
        lastAstroPassVisualDayTime = Long.MIN_VALUE;
        astroPassSunPassesSeen = 0;
    }

    private static void cancelMadeInHeavenDelayedSounds() {
        madeInHeavenTimeAccelScheduledSessionId = -1L;
        madeInHeavenTimeAccelDelayTicks = -1;
        madeInHeavenTimeDecelScheduledSessionId = -1L;
        madeInHeavenTimeDecelDelayTicks = -1;
        madeInHeavenDelayedDecelNeedsStop = false;
        madeInHeavenMainScheduledSessionId = -1L;
        madeInHeavenMainDelayTicks = -1;
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
