package net.aqualoco.sec.acceleration;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.WorldSleepAccelerationConfig;
import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationPlayersAffected;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class WorldSleepAccelerationManager {
    private static final Map<MinecraftServer, CachedServerState> SERVER_STATES = new WeakHashMap<>();

    private WorldSleepAccelerationManager() {
    }

    public static void prepareForLevelTick(ServerLevel level) {
        prepare(level, false);
    }

    public static void refreshForLevelTick(ServerLevel level) {
        prepare(level, true);
    }

    public static WorldSleepAccelerationStatus getStatus(ServerLevel level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return WorldSleepAccelerationStatus.INACTIVE;
        }
        return SERVER_STATES.computeIfAbsent(level.getServer(), key -> new CachedServerState())
                .prepare(level, false, false);
    }

    public static WorldSleepAccelerationStatus getDiagnosticStatus(ServerLevel level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return WorldSleepAccelerationStatus.INACTIVE;
        }
        return SERVER_STATES.computeIfAbsent(level.getServer(), key -> new CachedServerState())
                .prepare(level, false, true);
    }

    private static void prepare(ServerLevel level, boolean forceRebuild) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        SERVER_STATES.computeIfAbsent(level.getServer(), key -> new CachedServerState())
                .prepare(level, forceRebuild, false);
    }

    private static final class CachedServerState {
        private static final double METRIC_ALPHA = 0.15D;
        private static final int METRIC_UPDATE_INTERVAL_TICKS = 10;
        private static final int GOVERNOR_DEBUG_DELAY_TICKS = 10;

        private static final double PRESSURE_RISE_ALPHA = 0.35D;
        private static final double PRESSURE_FALL_ALPHA = 0.10D;
        private static final double RADIUS_RISE_ALPHA = 0.14D;
        private static final double RADIUS_FALL_ALPHA = 0.38D;
        private static final double SPEED_RISE_ALPHA = 0.16D;
        private static final double SPEED_FALL_ALPHA = 0.40D;

        private static final double AUTO_MIN_RANDOM_TICK_SPEED_PERCENT = 10.0D;
        private static final double PROCESS_SUPPRESS_PRESSURE = 0.95D;
        private static final double PROCESS_RESUME_PRESSURE = 0.75D;
        private static final int PROCESS_SUPPRESS_TICKS = 20;
        private static final int PROCESS_RESUME_TICKS = 40;

        private int lastPreparedTick = Integer.MIN_VALUE;
        private int lastDiagnosticTick = Integer.MIN_VALUE;
        private int lastMetricsUpdateTick = Integer.MIN_VALUE;
        private WorldSleepAccelerationStatus lastStatus = WorldSleepAccelerationStatus.INACTIVE;
        private WorldSleepAccelerationStatus lastDiagnosticStatus = WorldSleepAccelerationStatus.INACTIVE;
        private double averageMspt = 50.0D;
        private double p95Mspt = 50.0D;

        private double smoothedGovernorPressure;
        private double smoothedAutomaticRadiusChunks = Double.NaN;
        private double smoothedAutomaticSpeedPercent = Double.NaN;
        private boolean processTemporarilySuppressed;
        private int processSuppressionHighTicks;
        private int processSuppressionRecoveryTicks;

        private long scheduledGovernorDebugStartMillis = Long.MIN_VALUE;
        private int scheduledGovernorDebugTick = Integer.MIN_VALUE;
        private boolean governorDebugLoggedForCurrentSleep;

        private WorldSleepAccelerationStatus prepare(ServerLevel level, boolean forceRebuild, boolean includeDiagnostics) {
            int currentTick = level.getServer().getTickCount();
            updateMetrics(level.getServer(), currentTick);

            SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
            SleepAnimationState sleepState = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
            updateGovernorDebugSchedule(config.worldSleepAcceleration, sleepState, currentTick);

            if (includeDiagnostics) {
                if (!forceRebuild && currentTick == lastDiagnosticTick) {
                    return lastDiagnosticStatus;
                }

                lastDiagnosticTick = currentTick;
                lastPreparedTick = currentTick;
                lastDiagnosticStatus = buildStatus(level, averageMspt, p95Mspt, true);
                lastStatus = lastDiagnosticStatus;
                return lastDiagnosticStatus;
            }

            if (!forceRebuild && currentTick == lastPreparedTick) {
                return lastStatus;
            }

            lastPreparedTick = currentTick;
            lastStatus = buildStatus(level, averageMspt, p95Mspt, false);
            maybeLogGovernorDebug(level, config.worldSleepAcceleration, sleepState, currentTick);
            return lastStatus;
        }

        private void updateMetrics(MinecraftServer server, int currentTick) {
            if (lastMetricsUpdateTick != Integer.MIN_VALUE
                    && currentTick - lastMetricsUpdateTick < METRIC_UPDATE_INTERVAL_TICKS) {
                return;
            }

            lastMetricsUpdateTick = currentTick;
            long[] tickTimes = server.getTickTimesNanos();
            if (tickTimes == null || tickTimes.length == 0) {
                return;
            }

            double[] samples = new double[tickTimes.length];
            double sum = 0.0D;
            for (int i = 0; i < tickTimes.length; i++) {
                double sample = tickTimes[i] / 1_000_000.0D;
                samples[i] = sample;
                sum += sample;
            }

            Arrays.sort(samples);
            int p95Index = Math.min(samples.length - 1, (int) Math.floor((samples.length - 1) * 0.95D));
            double sampleAverage = sum / samples.length;
            double sampleP95 = samples[p95Index];

            averageMspt = Mth.lerp(METRIC_ALPHA, averageMspt, sampleAverage);
            p95Mspt = Mth.lerp(METRIC_ALPHA, p95Mspt, sampleP95);
        }

        private WorldSleepAccelerationStatus buildStatus(ServerLevel level,
                                                         double smoothedAverageMspt,
                                                         double smoothedP95Mspt,
                                                         boolean includeDiagnostics) {
            WorldSleepAccelerationConfig accelerationConfig = SeamlessSleepServerConfigManager.get().worldSleepAcceleration;
            SleepAnimationState sleepState = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
            double worldSleepRate = sleepState.getLogicalWorldRate();

            if (!sleepState.isActive()
                    || accelerationConfig.mode == WorldSleepAccelerationMode.OFF
                    || worldSleepRate <= 1.0D) {
                resetAutomaticRuntimeState();
                return WorldSleepAccelerationStatus.INACTIVE;
            }

            WorldSleepAccelerationPlayersAffected effectivePlayersAffected = accelerationConfig.resolveEffectivePlayersAffected();
            List<ServerPlayer> activePlayers = collectActivePlayers(level, effectivePlayersAffected);
            if (activePlayers.isEmpty()) {
                resetAutomaticRuntimeState();
                return WorldSleepAccelerationStatus.INACTIVE;
            }

            int simulationDistance = WorldSleepAccelerationConfig.clampSimulationDistance(
                    level.getServer().getPlayerList().getSimulationDistance()
            );
            int randomTickSpeed = Math.max(0, level.getGameRules().get(GameRules.RANDOM_TICK_SPEED));
            WorldSleepAccelerationFilterPolicy filterPolicy = accelerationConfig.createFilterPolicy();

            Map<Integer, LongOpenHashSet> coverageByRadius = new HashMap<>();
            int configuredRadiusChunks = accelerationConfig.resolveManualRadiusChunks(simulationDistance);
            int effectiveRadiusChunks = configuredRadiusChunks;
            int configuredNatureSpeedPercent = accelerationConfig.resolveManualAccelerationSpeedPercent();
            int effectiveNatureSpeedPercent = configuredNatureSpeedPercent;
            WorldSleepAccelerationGovernorSnapshot governorSnapshot = WorldSleepAccelerationGovernorSnapshot.INACTIVE;

            if (accelerationConfig.mode == WorldSleepAccelerationMode.AUTOMATIC) {
                AutomaticGovernorRuntime automaticRuntime = updateAutomaticGovernorRuntime(
                        accelerationConfig,
                        worldSleepRate,
                        activePlayers,
                        activePlayers.size(),
                        simulationDistance,
                        smoothedAverageMspt,
                        smoothedP95Mspt,
                        coverageByRadius
                );
                configuredRadiusChunks = automaticRuntime.configuredRadiusChunks();
                effectiveRadiusChunks = automaticRuntime.effectiveRadiusChunks();
                configuredNatureSpeedPercent = automaticRuntime.configuredSpeedPercent();
                effectiveNatureSpeedPercent = automaticRuntime.effectiveSpeedPercent();
                governorSnapshot = automaticRuntime.snapshot();
            } else {
                resetAutomaticRuntimeState();
            }
            WorldSleepAccelerationGovernorAction natureGovernorAction = determineAutomaticNatureAction(
                    accelerationConfig.mode,
                    configuredRadiusChunks,
                    effectiveRadiusChunks,
                    configuredNatureSpeedPercent,
                    effectiveNatureSpeedPercent
            );
            WorldSleepAccelerationGovernorAction processGovernorAction = determineAutomaticProcessAction(
                    accelerationConfig.mode,
                    configuredRadiusChunks,
                    effectiveRadiusChunks
            );

            WorldSleepAccelerationModuleStatus natureStatus = buildNatureStatus(
                    filterPolicy,
                    configuredRadiusChunks,
                    effectiveRadiusChunks,
                    configuredNatureSpeedPercent,
                    effectiveNatureSpeedPercent,
                    worldSleepRate,
                    activePlayers,
                    randomTickSpeed,
                    coverageByRadius,
                    natureGovernorAction
            );

            WorldSleepAccelerationModuleStatus processStatus = buildProcessStatus(
                    accelerationConfig,
                    configuredRadiusChunks,
                    effectiveRadiusChunks,
                    worldSleepRate,
                    activePlayers,
                    coverageByRadius,
                    processGovernorAction
            );

            boolean moduleActive = natureStatus.isActive() || processStatus.isActive();
            boolean exposeAutomaticRuntime = includeDiagnostics
                    && accelerationConfig.mode == WorldSleepAccelerationMode.AUTOMATIC
                    && governorSnapshot.isActive();
            if (!moduleActive && !exposeAutomaticRuntime) {
                return WorldSleepAccelerationStatus.INACTIVE;
            }

            WorldSleepAccelerationGovernorAction governorAction = WorldSleepAccelerationGovernorAction.combine(
                    natureStatus.getGovernorAction(),
                    processStatus.getGovernorAction()
            );

            return new WorldSleepAccelerationStatus(
                    moduleActive || exposeAutomaticRuntime,
                    level.dimension(),
                    activePlayers.size(),
                    simulationDistance,
                    worldSleepRate,
                    smoothedAverageMspt,
                    smoothedP95Mspt,
                    accelerationConfig.mode,
                    accelerationConfig.automaticMode,
                    effectivePlayersAffected,
                    filterPolicy,
                    governorAction,
                    governorSnapshot,
                    natureStatus,
                    processStatus,
                    processTemporarilySuppressed
            );
        }

        private List<ServerPlayer> collectActivePlayers(ServerLevel level,
                                                        WorldSleepAccelerationPlayersAffected playersAffected) {
            List<ServerPlayer> players = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !player.isAlive()) {
                    continue;
                }
                if (playersAffected == WorldSleepAccelerationPlayersAffected.SLEEPERS
                        && !BedRestingHelper.isManagedBedStateServer(player)) {
                    continue;
                }
                players.add(player);
            }
            return players;
        }

        private WorldSleepAccelerationModuleStatus buildNatureStatus(WorldSleepAccelerationFilterPolicy filterPolicy,
                                                                     int configuredRadiusChunks,
                                                                     int effectiveRadiusChunks,
                                                                     int configuredSpeedPercent,
                                                                     int effectiveSpeedPercent,
                                                                     double worldSleepRate,
                                                                     List<ServerPlayer> activePlayers,
                                                                     int randomTickSpeed,
                                                                     Map<Integer, LongOpenHashSet> coverageByRadius,
                                                                     WorldSleepAccelerationGovernorAction governorAction) {
            if (!filterPolicy.isAnyEnabled() || configuredSpeedPercent <= 0 || effectiveSpeedPercent <= 0 || randomTickSpeed <= 0) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, false);
            }

            double effectiveFraction = effectiveSpeedPercent / 100.0D;
            double effectiveTickMultiplier = computeTickMultiplier(worldSleepRate, effectiveFraction);
            double extraRandomTickAttempts = randomTickSpeed * Math.max(0.0D, worldSleepRate - 1.0D) * effectiveFraction;
            if (effectiveTickMultiplier <= 1.0D || extraRandomTickAttempts <= 0.0D) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, false);
            }

            LongOpenHashSet coveredChunks = coverageByRadius.computeIfAbsent(
                    effectiveRadiusChunks,
                    radius -> unionChunkAreas(activePlayers, radius)
            );
            if (coveredChunks.isEmpty()) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, false);
            }

            int wholeAttempts = (int) Math.floor(extraRandomTickAttempts);
            double fractionalAttempts = extraRandomTickAttempts - wholeAttempts;
            return new WorldSleepAccelerationModuleStatus(
                    true,
                    configuredRadiusChunks,
                    effectiveRadiusChunks,
                    configuredSpeedPercent,
                    effectiveSpeedPercent,
                    effectiveTickMultiplier,
                    extraRandomTickAttempts,
                    wholeAttempts,
                    fractionalAttempts,
                    coveredChunks.size(),
                    coveredChunks,
                    governorAction,
                    false
            );
        }

        private WorldSleepAccelerationModuleStatus buildProcessStatus(WorldSleepAccelerationConfig accelerationConfig,
                                                                      int configuredRadiusChunks,
                                                                      int effectiveRadiusChunks,
                                                                      double worldSleepRate,
                                                                      List<ServerPlayer> activePlayers,
                                                                      Map<Integer, LongOpenHashSet> coverageByRadius,
                                                                      WorldSleepAccelerationGovernorAction governorAction) {
            int configuredSpeedPercent = accelerationConfig.resolveProcessesSpeedPercent();
            if (!accelerationConfig.processesAccelerationEnabled || configuredSpeedPercent <= 0) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, false);
            }
            if (processTemporarilySuppressed) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, true);
            }

            double effectiveFraction = configuredSpeedPercent / 100.0D;
            double effectiveTickMultiplier = computeTickMultiplier(worldSleepRate, effectiveFraction);
            if (effectiveTickMultiplier <= 1.0D) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, false);
            }

            LongOpenHashSet coveredChunks = coverageByRadius.computeIfAbsent(
                    effectiveRadiusChunks,
                    radius -> unionChunkAreas(activePlayers, radius)
            );
            if (coveredChunks.isEmpty()) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, false);
            }

            return new WorldSleepAccelerationModuleStatus(
                    true,
                    configuredRadiusChunks,
                    effectiveRadiusChunks,
                    configuredSpeedPercent,
                    configuredSpeedPercent,
                    effectiveTickMultiplier,
                    0.0D,
                    0,
                    0.0D,
                    coveredChunks.size(),
                    coveredChunks,
                    governorAction,
                    false
            );
        }

        private WorldSleepAccelerationModuleStatus inactiveModuleStatus(int configuredRadiusChunks,
                                                                        int configuredSpeedPercent,
                                                                        boolean temporarilySuppressed) {
            return new WorldSleepAccelerationModuleStatus(
                    false,
                    configuredRadiusChunks,
                    configuredRadiusChunks,
                    configuredSpeedPercent,
                    configuredSpeedPercent,
                    1.0D,
                    0.0D,
                    0,
                    0.0D,
                    0,
                    new LongOpenHashSet(),
                    WorldSleepAccelerationGovernorAction.NONE,
                    temporarilySuppressed
            );
        }

        private AutomaticGovernorRuntime updateAutomaticGovernorRuntime(WorldSleepAccelerationConfig accelerationConfig,
                                                                        double worldSleepRate,
                                                                        List<ServerPlayer> activePlayers,
                                                                        int activePlayerCount,
                                                                        int simulationDistance,
                                                                        double averageMspt,
                                                                        double p95Mspt,
                                                                        Map<Integer, LongOpenHashSet> coverageByRadius) {
            WorldSleepAccelerationConfig.AutomaticCeiling ceiling = accelerationConfig.getAutomaticCeiling(simulationDistance);
            int governorRiskRadiusChunks = resolveAutomaticGovernorRiskRadius(ceiling.radiusChunks());
            LongOpenHashSet candidateCoverage = coverageByRadius.computeIfAbsent(
                    governorRiskRadiusChunks,
                    radius -> unionChunkAreas(activePlayers, radius)
            );
            WorldSleepAccelerationGovernorSnapshot snapshot = computeGovernorSnapshot(
                    worldSleepRate,
                    activePlayerCount,
                    simulationDistance,
                    averageMspt,
                    p95Mspt,
                    candidateCoverage.size()
            );
            int effectiveRadiusChunks = updateAutomaticEffectiveRadiusChunks(
                    ceiling.radiusChunks(),
                    snapshot.getSmoothedPressure()
            );
            int effectiveSpeedPercent = updateAutomaticEffectiveSpeedPercent(
                    ceiling.speedPercent(),
                    snapshot.getSmoothedPressure()
            );
            updateProcessSuppression(snapshot.getSmoothedPressure());
            return new AutomaticGovernorRuntime(
                    snapshot,
                    ceiling.radiusChunks(),
                    effectiveRadiusChunks,
                    ceiling.speedPercent(),
                    effectiveSpeedPercent
            );
        }

        private WorldSleepAccelerationGovernorSnapshot computeGovernorSnapshot(double worldSleepRate,
                                                                              int activePlayerCount,
                                                                              int simulationDistance,
                                                                              double averageMspt,
                                                                              double p95Mspt,
                                                                              int deduplicatedCandidateAreaChunks) {
            double averagePressure = inverseLerp(35.0D, 50.0D, averageMspt);
            double p95Pressure = inverseLerp(45.0D, 60.0D, p95Mspt);
            double healthPressure = Math.max(averagePressure, p95Pressure);

            double activePlayerRiskBonus = 0.0D;
            double simulationDistanceRiskBonus = 0.0D;
            double candidateAreaRiskBonus = 0.0D;
            double worldSleepRateRiskBonus = 0.0D;
            double riskFactor = 1.0D;
            double rawPressure = 0.0D;

            if (healthPressure > 0.01D) {
                int candidateAreaChunks = Math.max(0, deduplicatedCandidateAreaChunks);

                activePlayerRiskBonus = Mth.clamp((activePlayerCount - 1) * 0.04D, 0.0D, 0.20D);
                simulationDistanceRiskBonus = Mth.clamp((simulationDistance - 8) / 12.0D, 0.0D, 1.0D) * 0.12D;
                candidateAreaRiskBonus = Mth.clamp((candidateAreaChunks - 225) / 1000.0D, 0.0D, 1.0D) * 0.28D;
                worldSleepRateRiskBonus = Mth.clamp((worldSleepRate - 40.0D) / 220.0D, 0.0D, 1.0D) * 0.12D;
                riskFactor += activePlayerRiskBonus;
                riskFactor += simulationDistanceRiskBonus;
                riskFactor += candidateAreaRiskBonus;
                riskFactor += worldSleepRateRiskBonus;
                rawPressure = Mth.clamp(healthPressure * riskFactor, 0.0D, 1.0D);
            }

            smoothedGovernorPressure = updateSmoothedValue(
                    smoothedGovernorPressure,
                    rawPressure,
                    PRESSURE_RISE_ALPHA,
                    PRESSURE_FALL_ALPHA
            );
            if (rawPressure <= 0.0D && smoothedGovernorPressure < 0.01D) {
                smoothedGovernorPressure = 0.0D;
            }

            return new WorldSleepAccelerationGovernorSnapshot(
                    true,
                    averagePressure,
                    p95Pressure,
                    healthPressure,
                    activePlayerRiskBonus,
                    simulationDistanceRiskBonus,
                    candidateAreaRiskBonus,
                    worldSleepRateRiskBonus,
                    riskFactor,
                    rawPressure,
                    smoothedGovernorPressure
            );
        }

        private int resolveAutomaticGovernorRiskRadius(int configuredRadiusChunks) {
            int clampedConfiguredRadius = Math.max(1, configuredRadiusChunks);
            if (Double.isNaN(smoothedAutomaticRadiusChunks)) {
                return clampedConfiguredRadius;
            }
            return Mth.clamp((int) Math.round(smoothedAutomaticRadiusChunks), 1, clampedConfiguredRadius);
        }

        private int updateAutomaticEffectiveRadiusChunks(int configuredRadiusChunks, double smoothedPressure) {
            int clampedConfiguredRadius = Math.max(1, configuredRadiusChunks);
            int stageOneRadiusFloor = Math.max(1, (int) Math.floor(clampedConfiguredRadius * 0.55D));
            double stageOneProgress = Mth.clamp(smoothedPressure / 0.45D, 0.0D, 1.0D);
            double stageThreeProgress = Mth.clamp((smoothedPressure - 0.80D) / 0.20D, 0.0D, 1.0D);
            double stageOneRadius = Mth.lerp(stageOneProgress, clampedConfiguredRadius, stageOneRadiusFloor);
            double targetRadius = Mth.lerp(stageThreeProgress, stageOneRadius, 1.0D);

            if (Double.isNaN(smoothedAutomaticRadiusChunks)) {
                smoothedAutomaticRadiusChunks = clampedConfiguredRadius;
            }
            smoothedAutomaticRadiusChunks = updateSmoothedValue(
                    smoothedAutomaticRadiusChunks,
                    targetRadius,
                    RADIUS_RISE_ALPHA,
                    RADIUS_FALL_ALPHA
            );
            smoothedAutomaticRadiusChunks = Mth.clamp(smoothedAutomaticRadiusChunks, 1.0D, clampedConfiguredRadius);
            int roundedRadius = Mth.clamp((int) Math.round(smoothedAutomaticRadiusChunks), 1, clampedConfiguredRadius);
            if (Math.abs(smoothedAutomaticRadiusChunks - roundedRadius) < 0.08D) {
                smoothedAutomaticRadiusChunks = roundedRadius;
            }
            return roundedRadius;
        }

        private int updateAutomaticEffectiveSpeedPercent(int configuredSpeedPercent, double smoothedPressure) {
            int clampedConfiguredSpeed = Mth.clamp(configuredSpeedPercent, (int) AUTO_MIN_RANDOM_TICK_SPEED_PERCENT, 100);
            double stageTwoProgress = Mth.clamp((smoothedPressure - 0.45D) / 0.35D, 0.0D, 1.0D);
            double targetSpeed = Mth.lerp(
                    stageTwoProgress,
                    clampedConfiguredSpeed,
                    AUTO_MIN_RANDOM_TICK_SPEED_PERCENT
            );

            if (Double.isNaN(smoothedAutomaticSpeedPercent)) {
                smoothedAutomaticSpeedPercent = clampedConfiguredSpeed;
            }
            smoothedAutomaticSpeedPercent = updateSmoothedValue(
                    smoothedAutomaticSpeedPercent,
                    targetSpeed,
                    SPEED_RISE_ALPHA,
                    SPEED_FALL_ALPHA
            );
            smoothedAutomaticSpeedPercent = Mth.clamp(
                    smoothedAutomaticSpeedPercent,
                    AUTO_MIN_RANDOM_TICK_SPEED_PERCENT,
                    clampedConfiguredSpeed
            );
            int roundedSpeed = Mth.clamp(
                    (int) Math.round(smoothedAutomaticSpeedPercent),
                    (int) AUTO_MIN_RANDOM_TICK_SPEED_PERCENT,
                    clampedConfiguredSpeed
            );
            if (Math.abs(smoothedAutomaticSpeedPercent - roundedSpeed) < 0.15D) {
                smoothedAutomaticSpeedPercent = roundedSpeed;
            }
            return roundedSpeed;
        }

        private void updateProcessSuppression(double smoothedPressure) {
            if (smoothedPressure >= PROCESS_SUPPRESS_PRESSURE) {
                processSuppressionHighTicks++;
                processSuppressionRecoveryTicks = 0;
                if (processSuppressionHighTicks >= PROCESS_SUPPRESS_TICKS) {
                    processTemporarilySuppressed = true;
                }
                return;
            }

            processSuppressionHighTicks = 0;
            if (!processTemporarilySuppressed) {
                processSuppressionRecoveryTicks = 0;
                return;
            }

            if (smoothedPressure <= PROCESS_RESUME_PRESSURE) {
                processSuppressionRecoveryTicks++;
                if (processSuppressionRecoveryTicks >= PROCESS_RESUME_TICKS) {
                    processTemporarilySuppressed = false;
                    processSuppressionRecoveryTicks = 0;
                }
                return;
            }

            processSuppressionRecoveryTicks = 0;
        }

        private void resetAutomaticRuntimeState() {
            smoothedGovernorPressure = 0.0D;
            smoothedAutomaticRadiusChunks = Double.NaN;
            smoothedAutomaticSpeedPercent = Double.NaN;
            processTemporarilySuppressed = false;
            processSuppressionHighTicks = 0;
            processSuppressionRecoveryTicks = 0;
        }

        private double computeTickMultiplier(double worldSleepRate, double speedFraction) {
            return 1.0D + Math.max(0.0D, worldSleepRate - 1.0D) * speedFraction;
        }

        private double updateSmoothedValue(double current,
                                           double target,
                                           double riseAlpha,
                                           double fallAlpha) {
            double alpha = target > current ? riseAlpha : fallAlpha;
            return Mth.lerp(alpha, current, target);
        }

        private WorldSleepAccelerationGovernorAction determineAutomaticNatureAction(WorldSleepAccelerationMode mode,
                                                                                    int configuredRadiusChunks,
                                                                                    int effectiveRadiusChunks,
                                                                                    int configuredSpeedPercent,
                                                                                    int effectiveSpeedPercent) {
            if (mode != WorldSleepAccelerationMode.AUTOMATIC) {
                return WorldSleepAccelerationGovernorAction.NONE;
            }
            boolean areaReduced = effectiveRadiusChunks < configuredRadiusChunks;
            boolean speedReduced = effectiveSpeedPercent < configuredSpeedPercent;
            if (areaReduced && speedReduced) {
                return WorldSleepAccelerationGovernorAction.AREA_AND_SPEED;
            }
            if (areaReduced) {
                return WorldSleepAccelerationGovernorAction.AREA;
            }
            if (speedReduced) {
                return WorldSleepAccelerationGovernorAction.SPEED;
            }
            return WorldSleepAccelerationGovernorAction.NONE;
        }

        private WorldSleepAccelerationGovernorAction determineAutomaticProcessAction(WorldSleepAccelerationMode mode,
                                                                                     int configuredRadiusChunks,
                                                                                     int effectiveRadiusChunks) {
            if (mode != WorldSleepAccelerationMode.AUTOMATIC || effectiveRadiusChunks >= configuredRadiusChunks) {
                return WorldSleepAccelerationGovernorAction.NONE;
            }
            return WorldSleepAccelerationGovernorAction.AREA;
        }

        private LongOpenHashSet unionChunkAreas(List<ServerPlayer> activePlayers, int radius) {
            LongOpenHashSet chunks = new LongOpenHashSet();
            for (ServerPlayer player : activePlayers) {
                ChunkPos center = player.chunkPosition();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        chunks.add(ChunkPos.asLong(center.x + dx, center.z + dz));
                    }
                }
            }
            return chunks;
        }

        private double inverseLerp(double min, double max, double value) {
            if (value <= min) {
                return 0.0D;
            }
            if (value >= max) {
                return 1.0D;
            }
            return (value - min) / (max - min);
        }

        private void updateGovernorDebugSchedule(WorldSleepAccelerationConfig accelerationConfig,
                                                 SleepAnimationState sleepState,
                                                 int currentTick) {
            if (!Constants.isDebugLogsEnabled()
                    || accelerationConfig.mode != WorldSleepAccelerationMode.AUTOMATIC
                    || !sleepState.isActive()) {
                clearGovernorDebugSchedule();
                return;
            }

            long sleepStartMillis = sleepState.getStartMillis();
            if (sleepStartMillis <= 0L || sleepStartMillis == scheduledGovernorDebugStartMillis) {
                return;
            }

            scheduledGovernorDebugStartMillis = sleepStartMillis;
            scheduledGovernorDebugTick = currentTick + GOVERNOR_DEBUG_DELAY_TICKS;
            governorDebugLoggedForCurrentSleep = false;
        }

        private void maybeLogGovernorDebug(ServerLevel level,
                                           WorldSleepAccelerationConfig accelerationConfig,
                                           SleepAnimationState sleepState,
                                           int currentTick) {
            if (!Constants.isDebugLogsEnabled()
                    || accelerationConfig.mode != WorldSleepAccelerationMode.AUTOMATIC
                    || !sleepState.isActive()
                    || governorDebugLoggedForCurrentSleep
                    || scheduledGovernorDebugTick == Integer.MIN_VALUE
                    || currentTick < scheduledGovernorDebugTick) {
                return;
            }

            WorldSleepAccelerationStatus diagnosticStatus = buildStatus(level, averageMspt, p95Mspt, true);
            lastDiagnosticTick = currentTick;
            lastDiagnosticStatus = diagnosticStatus;

            if (!diagnosticStatus.getGovernorSnapshot().isActive()) {
                Constants.debug("Governor snapshot: no live automatic data during current sleep.");
                governorDebugLoggedForCurrentSleep = true;
                return;
            }

            WorldSleepAccelerationGovernorSnapshot governorSnapshot = diagnosticStatus.getGovernorSnapshot();
            WorldSleepAccelerationModuleStatus natureStatus = diagnosticStatus.getNature();
            WorldSleepAccelerationModuleStatus processStatus = diagnosticStatus.getProcess();

            Constants.debug(
                    "Governor snapshot: mode={}, automaticMode={}, playersAffected={}, rate={}x, avgMspt={}, p95Mspt={}, rawPressure={}, smoothedPressure={}, players={}, simDist={}, processSuppressed={}",
                    diagnosticStatus.getMode(),
                    diagnosticStatus.getAutomaticMode(),
                    diagnosticStatus.getPlayersAffected(),
                    formatDecimal(diagnosticStatus.getWorldSleepRate()),
                    formatDecimal(diagnosticStatus.getAverageMspt()),
                    formatDecimal(diagnosticStatus.getP95Mspt()),
                    formatPercent(governorSnapshot.getRawPressure()),
                    formatPercent(governorSnapshot.getSmoothedPressure()),
                    diagnosticStatus.getActivePlayerCount(),
                    diagnosticStatus.getSimulationDistance(),
                    diagnosticStatus.isProcessesTemporarilySuppressed()
            );

            Constants.debug(
                    "Governor result: nature[radius={}/{}, speed={}/{}%, extraAttempts={}, chunks={}] process[radius={}/{}, speed={}%, multiplier={}x, chunks={}, suppressed={}]",
                    natureStatus.getEffectiveRadiusChunks(),
                    natureStatus.getConfiguredRadiusChunks(),
                    natureStatus.getEffectiveSpeedPercent(),
                    natureStatus.getConfiguredSpeedPercent(),
                    formatDecimal(natureStatus.getExtraRandomTickAttemptsPerSection()),
                    natureStatus.getCoveredChunkCount(),
                    processStatus.getEffectiveRadiusChunks(),
                    processStatus.getConfiguredRadiusChunks(),
                    processStatus.getConfiguredSpeedPercent(),
                    formatDecimal(processStatus.getEffectiveTickMultiplier()),
                    processStatus.getCoveredChunkCount(),
                    processStatus.isTemporarilySuppressed()
            );

            governorDebugLoggedForCurrentSleep = true;
        }

        private void clearGovernorDebugSchedule() {
            scheduledGovernorDebugStartMillis = Long.MIN_VALUE;
            scheduledGovernorDebugTick = Integer.MIN_VALUE;
            governorDebugLoggedForCurrentSleep = false;
        }

        private String formatDecimal(double value) {
            return String.format(java.util.Locale.ROOT, "%.2f", value);
        }

        private String formatPercent(double value) {
            return String.format(java.util.Locale.ROOT, "%.0f%%", value * 100.0D);
        }
    }

    private record AutomaticGovernorRuntime(WorldSleepAccelerationGovernorSnapshot snapshot,
                                            int configuredRadiusChunks,
                                            int effectiveRadiusChunks,
                                            int configuredSpeedPercent,
                                            int effectiveSpeedPercent) {
    }
}
