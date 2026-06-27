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
import net.aqualoco.sec.config.WorldSleepAutomaticMode;
import net.aqualoco.sec.sleep.SleepAnimationMode;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;

import java.util.Arrays;
import java.util.LinkedHashMap;
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
        return getOrCreateServerState(level.getServer()).prepare(level, false, false);
    }

    public static WorldSleepAccelerationStatus getDiagnosticStatus(ServerLevel level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return WorldSleepAccelerationStatus.INACTIVE;
        }
        return getOrCreateServerState(level.getServer()).prepare(level, false, true);
    }

    public static WorldSleepAccelerationTelemetry.Snapshot getTelemetrySnapshot(ServerLevel level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return new WorldSleepAccelerationTelemetry().snapshot(0);
        }
        CachedServerState state = getOrCreateServerState(level.getServer());
        return state.telemetry.snapshot(level.getServer().getTickCount());
    }

    public static WorldSleepNatureSessionCache.Snapshot getNatureSessionCacheSnapshot(ServerLevel level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return new WorldSleepNatureSessionCache().snapshot();
        }
        CachedServerState state = getOrCreateServerState(level.getServer());
        return state.natureSessionCache.snapshot();
    }

    private static void prepare(ServerLevel level, boolean forceRebuild) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        getOrCreateServerState(level.getServer()).prepare(level, forceRebuild, false);
    }

    private static CachedServerState getOrCreateServerState(MinecraftServer server) {
        CachedServerState state = SERVER_STATES.get(server);
        if (state != null) {
            return state;
        }
        CachedServerState created = new CachedServerState();
        SERVER_STATES.put(server, created);
        return created;
    }

    private static final class CachedServerState {
        private static final double METRIC_ALPHA = 0.15D;
        private static final int METRIC_UPDATE_INTERVAL_TICKS = 10;
        private static final int GOVERNOR_DEBUG_DELAY_TICKS = 10;
        private static final int MAX_CACHED_COVERAGE_RADII = 4;
        private static final int MAX_COVERAGE_PREALLOCATION_CHUNKS = 262_144;
        private static final long[] EMPTY_CHUNK_CENTERS = new long[0];
        private static final LongOpenHashSet EMPTY_COVERED_CHUNKS = new LongOpenHashSet(0);

        private static final double PRESSURE_RISE_ALPHA = 0.35D;
        private static final double PRESSURE_FALL_ALPHA = 0.10D;
        private static final double RADIUS_RISE_ALPHA = 0.14D;
        private static final double RADIUS_FALL_ALPHA = 0.38D;
        private static final double SPEED_RISE_ALPHA = 0.16D;
        private static final double SPEED_FALL_ALPHA = 0.40D;

        private static final double AUTO_MIN_RANDOM_TICK_SPEED_PERCENT = 10.0D;
        private static final double NATURE_WORKLOAD_KNEE_ATTEMPTS_PER_SECTION = 400.0D;
        private static final double NATURE_WORKLOAD_COMPRESSION_SCALE = 400.0D;
        private static final double NATURE_EMERGENCY_SUPPRESS_PRESSURE = 0.95D;
        private static final double NATURE_EMERGENCY_SUPPRESS_AVERAGE_MSPT = 55.0D;
        private static final double NATURE_EMERGENCY_SUPPRESS_P95_MSPT = 70.0D;
        private static final double NATURE_EMERGENCY_CRITICAL_AVERAGE_MSPT = 75.0D;
        private static final double NATURE_EMERGENCY_CRITICAL_P95_MSPT = 100.0D;
        private static final int NATURE_EMERGENCY_SUPPRESS_TICKS = 20;
        private static final int NATURE_EMERGENCY_CRITICAL_TICKS = 5;
        private static final int NATURE_EMERGENCY_MIN_SUPPRESSION_TICKS = 40;
        private static final double NATURE_EMERGENCY_RESUME_PRESSURE = 0.70D;
        private static final double NATURE_EMERGENCY_RESUME_AVERAGE_MSPT = 45.0D;
        private static final double NATURE_EMERGENCY_RESUME_P95_MSPT = 55.0D;
        private static final int NATURE_EMERGENCY_RESUME_TICKS = 60;
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
        private double[] metricSamples = new double[0];

        private double smoothedGovernorPressure;
        private double smoothedAutomaticRadiusChunks = Double.NaN;
        private double smoothedAutomaticSpeedPercent = Double.NaN;
        private boolean natureTemporarilySuppressed;
        private int natureSuppressionSevereTicks;
        private int natureSuppressionCriticalTicks;
        private int natureSuppressionMinimumTicks;
        private int natureSuppressionRecoveryTicks;
        private int lastNatureSuppressionUpdateTick = Integer.MIN_VALUE;
        private boolean processTemporarilySuppressed;
        private int processSuppressionHighTicks;
        private int processSuppressionRecoveryTicks;
        private int lastProcessSuppressionUpdateTick = Integer.MIN_VALUE;
        private final WorldSleepAccelerationTelemetry telemetry = new WorldSleepAccelerationTelemetry();
        private final WorldSleepNatureSessionCache natureSessionCache = new WorldSleepNatureSessionCache();
        private final Map<Integer, LongOpenHashSet> coverageByRadius =
                new LinkedHashMap<>(MAX_CACHED_COVERAGE_RADII, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Integer, LongOpenHashSet> eldest) {
                        return size() > MAX_CACHED_COVERAGE_RADII;
                    }
                };
        private long[] coverageCenterScratch = EMPTY_CHUNK_CENTERS;
        private long[] lastObservedCoverageCenters = EMPTY_CHUNK_CENTERS;
        private int lastObservedCoverageCenterCount = -1;
        private long[] cachedCoverageCenters = EMPTY_CHUNK_CENTERS;
        private ResourceKey<Level> cachedCoverageDimension;
        private WorldSleepAccelerationMode cachedCoverageMode;
        private WorldSleepAccelerationPlayersAffected cachedCoveragePlayersAffected;
        private int cachedFilterPolicyKey = Integer.MIN_VALUE;
        private WorldSleepAccelerationFilterPolicy cachedFilterPolicy =
                WorldSleepAccelerationFilterPolicy.DISABLED;
        private boolean dormantOffStabilized;

        private long scheduledGovernorDebugStartGameTime = Long.MIN_VALUE;
        private int scheduledGovernorDebugTick = Integer.MIN_VALUE;
        private boolean governorDebugLoggedForCurrentSleep;

        private WorldSleepAccelerationStatus prepare(ServerLevel level, boolean forceRebuild, boolean includeDiagnostics) {
            int currentTick = level.getServer().getTickCount();
            SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
            telemetry.setEnabled(config.worldSleepAcceleration.accelerationTelemetryEnabled);
            if (telemetry.isEnabled()) {
                telemetry.beginTick(currentTick);
            }
            updateMetrics(level.getServer(), currentTick);

            if (config.worldSleepAcceleration.mode == WorldSleepAccelerationMode.OFF
                    && !config.worldSleepAcceleration.accelerationTelemetryEnabled) {
                return prepareDormantOff(currentTick);
            }
            dormantOffStabilized = false;
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
                updateTelemetrySession(
                        level,
                        currentTick,
                        sleepState,
                        lastDiagnosticStatus,
                        config.worldSleepAcceleration
                );
                return lastDiagnosticStatus;
            }

            if (!forceRebuild && currentTick == lastPreparedTick) {
                return lastStatus;
            }

            lastPreparedTick = currentTick;
            lastStatus = buildStatus(level, averageMspt, p95Mspt, false);
            updateTelemetrySession(
                    level,
                    currentTick,
                    sleepState,
                    lastStatus,
                    config.worldSleepAcceleration
            );
            maybeLogGovernorDebug(level, config.worldSleepAcceleration, sleepState, currentTick);
            return lastStatus;
        }

        private WorldSleepAccelerationStatus prepareDormantOff(int currentTick) {
            if (!dormantOffStabilized) {
                resetAutomaticRuntimeState();
                clearCoverageCache();
                natureSessionCache.updateSession(false, 0L, 0, false, 0, false);
                clearGovernorDebugSchedule();
                lastStatus = WorldSleepAccelerationStatus.INACTIVE;
                lastDiagnosticStatus = WorldSleepAccelerationStatus.INACTIVE;
                dormantOffStabilized = true;
            }
            lastPreparedTick = currentTick;
            lastDiagnosticTick = currentTick;
            return WorldSleepAccelerationStatus.INACTIVE;
        }

        private void updateTelemetrySession(ServerLevel level,
                                            int currentTick,
                                            SleepAnimationState sleepState,
                                            WorldSleepAccelerationStatus status,
                                            WorldSleepAccelerationConfig accelerationConfig) {
            WorldSleepAccelerationModuleStatus natureStatus = status.getNature();
            boolean sessionActive = natureStatus.isActive() || natureStatus.isTemporarilySuppressed();
            long sessionId = sleepState.getServerStartGameTime();
            if (sessionId <= 0L) {
                sessionId = currentTick;
            }
            natureSessionCache.updateSession(
                    sessionActive,
                    sessionId,
                    status.getFilterPolicy().getCacheKey(),
                    accelerationConfig.recheckIrrelevantNatureSectionsDuringAcceleration,
                    status.getActivePlayerCount(),
                    accelerationConfig.accelerationTelemetryEnabled
            );
            if (!accelerationConfig.accelerationTelemetryEnabled) {
                return;
            }
            telemetry.updateSession(
                    currentTick,
                    sessionActive,
                    sessionId,
                    sleepState.isActive() ? sleepState.getMode() : null,
                    status.getMode()
            );
            if (!sessionActive) {
                return;
            }

            WorldSleepAccelerationGovernorSnapshot governorSnapshot = status.getGovernorSnapshot();
            telemetry.updateRuntimeDetails(
                    status.getAutomaticMode(),
                    natureStatus.getEffectiveSpeedPercent(),
                    natureStatus.getGovernorAction(),
                    Math.max(0, level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING)),
                    status.getWorldSleepRate(),
                    natureStatus.getRawNatureExtraAttemptsPerSection(),
                    natureStatus.getExtraRandomTickAttemptsPerSection(),
                    natureStatus.getNatureWorkloadKnee(),
                    natureStatus.getNatureWorkloadCompressionScale(),
                    natureStatus.isNatureWorkloadNormalizationApplied(),
                    natureStatus.getEffectiveRadiusChunks(),
                    natureStatus.getCoveredChunkCount(),
                    status.getActivePlayerCount(),
                    status.getAverageMspt(),
                    status.getP95Mspt(),
                    governorSnapshot.getHealthPressure(),
                    governorSnapshot.getSmoothedPressure(),
                    natureStatus.isTemporarilySuppressed()
            );
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

            if (metricSamples.length != tickTimes.length) {
                metricSamples = new double[tickTimes.length];
            }
            double sum = 0.0D;
            for (int i = 0; i < tickTimes.length; i++) {
                double sample = tickTimes[i] / 1_000_000.0D;
                metricSamples[i] = sample;
                sum += sample;
            }

            Arrays.sort(metricSamples);
            int p95Index = Math.min(
                    metricSamples.length - 1,
                    (int) Math.floor((metricSamples.length - 1) * 0.95D)
            );
            double sampleAverage = sum / metricSamples.length;
            double sampleP95 = metricSamples[p95Index];

            averageMspt = Mth.lerp(METRIC_ALPHA, averageMspt, sampleAverage);
            p95Mspt = Mth.lerp(METRIC_ALPHA, p95Mspt, sampleP95);
        }

        private WorldSleepAccelerationStatus buildStatus(ServerLevel level,
                                                         double smoothedAverageMspt,
                                                         double smoothedP95Mspt,
                                                         boolean includeDiagnostics) {
            SleepAnimationState sleepState = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
            WorldSleepAccelerationConfig accelerationConfig = resolveRuntimeAccelerationConfig(
                    SeamlessSleepServerConfigManager.get().worldSleepAcceleration,
                    sleepState
            );
            // Nature follows the instantaneous sleep curve, while processes stay on the
            // original average-per-night rate so all furnaces remain stable together.
            double natureWorldSleepRate = sleepState.getCurrentLogicalWorldRate();
            double processWorldSleepRate = sleepState.getAverageLogicalWorldRate();
            double worldSleepRate = natureWorldSleepRate;

            if (!sleepState.isActive()) {
                resetAutomaticRuntimeState();
                clearCoverageCache();
                return WorldSleepAccelerationStatus.INACTIVE;
            }
            if (!sleepState.getMode().allowsWorldAcceleration()
                    || accelerationConfig.mode == WorldSleepAccelerationMode.OFF) {
                resetAutomaticRuntimeState();
                clearCoverageCache();
                return WorldSleepAccelerationStatus.INACTIVE;
            }
            if (natureWorldSleepRate <= 1.0D && processWorldSleepRate <= 1.0D) {
                resetAutomaticRuntimeState();
                clearCoverageCache();
                return WorldSleepAccelerationStatus.INACTIVE;
            }

            WorldSleepAccelerationPlayersAffected effectivePlayersAffected = accelerationConfig.resolveEffectivePlayersAffected();
            int activePlayerCount = collectActivePlayerCenters(level, effectivePlayersAffected);
            if (activePlayerCount == 0) {
                resetAutomaticRuntimeState();
                clearCoverageCache();
                return WorldSleepAccelerationStatus.INACTIVE;
            }

            int simulationDistance = WorldSleepAccelerationConfig.clampSimulationDistance(
                    level.getServer().getPlayerList().getSimulationDistance()
            );
            int randomTickSpeed = Math.max(0, level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING));
            WorldSleepAccelerationFilterPolicy filterPolicy = resolveFilterPolicy(accelerationConfig);
            prepareCoverageCache(
                    level.dimension(),
                    accelerationConfig.mode,
                    effectivePlayersAffected,
                    activePlayerCount
            );

            int configuredRadiusChunks = accelerationConfig.resolveManualRadiusChunks(simulationDistance);
            int effectiveRadiusChunks = configuredRadiusChunks;
            int configuredNatureSpeedPercent = accelerationConfig.resolveManualAccelerationSpeedPercent();
            int effectiveNatureSpeedPercent = configuredNatureSpeedPercent;
            WorldSleepAccelerationGovernorSnapshot governorSnapshot = WorldSleepAccelerationGovernorSnapshot.INACTIVE;

            if (accelerationConfig.mode == WorldSleepAccelerationMode.AUTOMATIC) {
                AutomaticGovernorRuntime automaticRuntime = updateAutomaticGovernorRuntime(
                        accelerationConfig,
                        level.getServer().getTickCount(),
                        worldSleepRate,
                        activePlayerCount,
                        simulationDistance,
                        smoothedAverageMspt,
                        smoothedP95Mspt
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
                    natureWorldSleepRate,
                    randomTickSpeed,
                    natureGovernorAction,
                    natureTemporarilySuppressed
            );

            WorldSleepAccelerationModuleStatus processStatus = buildProcessStatus(
                    accelerationConfig,
                    configuredRadiusChunks,
                    effectiveRadiusChunks,
                    processWorldSleepRate,
                    processGovernorAction
            );

            boolean moduleActive = natureStatus.isActive() || processStatus.isActive();
            boolean exposeTemporarySuppression = natureStatus.isTemporarilySuppressed()
                    || processStatus.isTemporarilySuppressed();
            boolean exposeAutomaticRuntime = includeDiagnostics
                    && accelerationConfig.mode == WorldSleepAccelerationMode.AUTOMATIC
                    && governorSnapshot.isActive();
            if (!moduleActive && !exposeTemporarySuppression && !exposeAutomaticRuntime) {
                return WorldSleepAccelerationStatus.INACTIVE;
            }

            WorldSleepAccelerationGovernorAction governorAction = WorldSleepAccelerationGovernorAction.combine(
                    natureStatus.getGovernorAction(),
                    processStatus.getGovernorAction()
            );

            return new WorldSleepAccelerationStatus(
                    moduleActive || exposeTemporarySuppression || exposeAutomaticRuntime,
                    level.dimension(),
                    activePlayerCount,
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
                    processTemporarilySuppressed,
                    telemetry,
                    natureSessionCache
            );
        }

        private int collectActivePlayerCenters(ServerLevel level,
                                               WorldSleepAccelerationPlayersAffected playersAffected) {
            ensureCoverageCenterScratchCapacity(level.players().size());
            int activePlayerCount = 0;
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !player.isAlive()) {
                    continue;
                }
                if (playersAffected == WorldSleepAccelerationPlayersAffected.SLEEPERS
                        && !BedRestingHelper.isManagedBedStateServer(player)) {
                    continue;
                }
                ChunkPos center = player.chunkPosition();
                coverageCenterScratch[activePlayerCount++] = ChunkPos.asLong(center.x, center.z);
            }
            return activePlayerCount;
        }

        private WorldSleepAccelerationFilterPolicy resolveFilterPolicy(
                WorldSleepAccelerationConfig accelerationConfig) {
            int policyKey = WorldSleepAccelerationFilterPolicy.createCacheKey(
                    accelerationConfig.grassAndFoliageAccelerationEnabled,
                    accelerationConfig.cropsAndSaplingsAccelerationEnabled,
                    accelerationConfig.vinesAndBambooAccelerationEnabled,
                    accelerationConfig.kelpAccelerationEnabled,
                    accelerationConfig.vanillaOnlyAcceleration
            );
            if (policyKey == cachedFilterPolicyKey) {
                return cachedFilterPolicy;
            }
            cachedFilterPolicyKey = policyKey;
            cachedFilterPolicy = accelerationConfig.createFilterPolicy();
            return cachedFilterPolicy;
        }

        private WorldSleepAccelerationConfig resolveRuntimeAccelerationConfig(WorldSleepAccelerationConfig baseConfig,
                                                                              SleepAnimationState sleepState) {
            if (sleepState.getMode() != SleepAnimationMode.MADE_IN_HEAVEN_BED
                    || baseConfig.mode == WorldSleepAccelerationMode.OFF) {
                return baseConfig;
            }

            WorldSleepAccelerationConfig runtimeConfig = new WorldSleepAccelerationConfig();
            runtimeConfig.mode = WorldSleepAccelerationMode.AUTOMATIC;
            runtimeConfig.automaticMode = WorldSleepAutomaticMode.BALANCED;
            runtimeConfig.playersAffected = WorldSleepAccelerationPlayersAffected.ALL_PLAYERS;
            runtimeConfig.manualAccelerationRadiusChunks = baseConfig.manualAccelerationRadiusChunks;
            runtimeConfig.manualAccelerationSpeedPercent = baseConfig.manualAccelerationSpeedPercent;
            runtimeConfig.grassAndFoliageAccelerationEnabled = baseConfig.grassAndFoliageAccelerationEnabled;
            runtimeConfig.cropsAndSaplingsAccelerationEnabled = baseConfig.cropsAndSaplingsAccelerationEnabled;
            runtimeConfig.vinesAndBambooAccelerationEnabled =
                    baseConfig.vinesAndBambooAccelerationEnabled;
            runtimeConfig.kelpAccelerationEnabled = baseConfig.kelpAccelerationEnabled;
            runtimeConfig.vanillaOnlyAcceleration = baseConfig.vanillaOnlyAcceleration;
            runtimeConfig.recheckIrrelevantNatureSectionsDuringAcceleration =
                    baseConfig.recheckIrrelevantNatureSectionsDuringAcceleration;
            runtimeConfig.accelerationTelemetryEnabled = baseConfig.accelerationTelemetryEnabled;
            runtimeConfig.processesAccelerationEnabled = baseConfig.processesAccelerationEnabled;
            runtimeConfig.processesSpeedPercent = baseConfig.processesSpeedPercent;
            runtimeConfig.clamp();
            return runtimeConfig;
        }

        private WorldSleepAccelerationModuleStatus buildNatureStatus(WorldSleepAccelerationFilterPolicy filterPolicy,
                                                                     int configuredRadiusChunks,
                                                                     int effectiveRadiusChunks,
                                                                     int configuredSpeedPercent,
                                                                     int effectiveSpeedPercent,
                                                                     double worldSleepRate,
                                                                     int randomTickSpeed,
                                                                     WorldSleepAccelerationGovernorAction governorAction,
                                                                     boolean temporarilySuppressed) {
            if (temporarilySuppressed) {
                return inactiveModuleStatus(
                        configuredRadiusChunks,
                        effectiveRadiusChunks,
                        configuredSpeedPercent,
                        0,
                        governorAction,
                        true
                );
            }
            if (!filterPolicy.isAnyEnabled() || configuredSpeedPercent <= 0 || effectiveSpeedPercent <= 0 || randomTickSpeed <= 0) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, false);
            }

            double effectiveFraction = effectiveSpeedPercent / 100.0D;
            double rawNatureExtraAttemptsPerSection =
                    randomTickSpeed * Math.max(0.0D, worldSleepRate - 1.0D) * effectiveFraction;
            double normalizedNatureExtraAttemptsPerSection =
                    normalizeNatureExtraAttempts(rawNatureExtraAttemptsPerSection);
            if (normalizedNatureExtraAttemptsPerSection <= 0.0D) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, false);
            }
            double effectiveTickMultiplier =
                    1.0D + normalizedNatureExtraAttemptsPerSection / randomTickSpeed;

            LongOpenHashSet coveredChunks = getCoverageForRadius(effectiveRadiusChunks);
            if (coveredChunks.isEmpty()) {
                return inactiveModuleStatus(configuredRadiusChunks, configuredSpeedPercent, false);
            }

            int wholeAttempts = (int) Math.floor(normalizedNatureExtraAttemptsPerSection);
            double fractionalAttempts = normalizedNatureExtraAttemptsPerSection - wholeAttempts;
            return new WorldSleepAccelerationModuleStatus(
                    true,
                    configuredRadiusChunks,
                    effectiveRadiusChunks,
                    configuredSpeedPercent,
                    effectiveSpeedPercent,
                    effectiveTickMultiplier,
                    rawNatureExtraAttemptsPerSection,
                    normalizedNatureExtraAttemptsPerSection,
                    NATURE_WORKLOAD_KNEE_ATTEMPTS_PER_SECTION,
                    NATURE_WORKLOAD_COMPRESSION_SCALE,
                    rawNatureExtraAttemptsPerSection > NATURE_WORKLOAD_KNEE_ATTEMPTS_PER_SECTION,
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

            LongOpenHashSet coveredChunks = getCoverageForRadius(effectiveRadiusChunks);
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
                    0.0D,
                    0.0D,
                    0.0D,
                    false,
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
            return inactiveModuleStatus(
                    configuredRadiusChunks,
                    configuredRadiusChunks,
                    configuredSpeedPercent,
                    configuredSpeedPercent,
                    WorldSleepAccelerationGovernorAction.NONE,
                    temporarilySuppressed
            );
        }

        private WorldSleepAccelerationModuleStatus inactiveModuleStatus(int configuredRadiusChunks,
                                                                        int effectiveRadiusChunks,
                                                                        int configuredSpeedPercent,
                                                                        int effectiveSpeedPercent,
                                                                        WorldSleepAccelerationGovernorAction governorAction,
                                                                        boolean temporarilySuppressed) {
            return new WorldSleepAccelerationModuleStatus(
                    false,
                    configuredRadiusChunks,
                    effectiveRadiusChunks,
                    configuredSpeedPercent,
                    effectiveSpeedPercent,
                    1.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    false,
                    0,
                    0.0D,
                    0,
                    EMPTY_COVERED_CHUNKS,
                    governorAction,
                    temporarilySuppressed
            );
        }

        private AutomaticGovernorRuntime updateAutomaticGovernorRuntime(WorldSleepAccelerationConfig accelerationConfig,
                                                                        int currentTick,
                                                                        double worldSleepRate,
                                                                        int activePlayerCount,
                                                                        int simulationDistance,
                                                                        double averageMspt,
                                                                        double p95Mspt) {
            WorldSleepAccelerationConfig.AutomaticCeiling ceiling = accelerationConfig.getAutomaticCeiling(simulationDistance);
            int governorRiskRadiusChunks = resolveAutomaticGovernorRiskRadius(ceiling.radiusChunks());
            LongOpenHashSet candidateCoverage = getCoverageForRadius(governorRiskRadiusChunks);
            WorldSleepAccelerationGovernorSnapshot snapshot = computeGovernorSnapshot(
                    worldSleepRate,
                    activePlayerCount,
                    simulationDistance,
                    averageMspt,
                    p95Mspt,
                    candidateCoverage.size()
            );
            updateNatureEmergencySuppression(
                    currentTick,
                    snapshot,
                    averageMspt,
                    p95Mspt
            );
            int effectiveRadiusChunks = updateAutomaticEffectiveRadiusChunks(
                    ceiling.radiusChunks(),
                    snapshot.getSmoothedPressure()
            );
            int effectiveSpeedPercent;
            if (natureTemporarilySuppressed) {
                holdAutomaticNatureSpeedAtMinimum(ceiling.speedPercent());
                effectiveSpeedPercent = 0;
            } else {
                effectiveSpeedPercent = updateAutomaticEffectiveSpeedPercent(
                        ceiling.speedPercent(),
                        snapshot.getSmoothedPressure()
                );
            }
            updateProcessSuppression(currentTick, snapshot.getSmoothedPressure());
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

        private void holdAutomaticNatureSpeedAtMinimum(int configuredSpeedPercent) {
            smoothedAutomaticSpeedPercent = Math.min(
                    Math.max(0, configuredSpeedPercent),
                    AUTO_MIN_RANDOM_TICK_SPEED_PERCENT
            );
        }

        private void updateNatureEmergencySuppression(int currentTick,
                                                      WorldSleepAccelerationGovernorSnapshot snapshot,
                                                      double averageMspt,
                                                      double p95Mspt) {
            if (currentTick == lastNatureSuppressionUpdateTick) {
                return;
            }
            lastNatureSuppressionUpdateTick = currentTick;

            // Only server-health signals may suspend nature acceleration.
            boolean severeHealthPressure = snapshot.getSmoothedPressure() >= NATURE_EMERGENCY_SUPPRESS_PRESSURE
                    && (averageMspt >= NATURE_EMERGENCY_SUPPRESS_AVERAGE_MSPT
                    || p95Mspt >= NATURE_EMERGENCY_SUPPRESS_P95_MSPT);
            boolean criticalHealthPressure = averageMspt >= NATURE_EMERGENCY_CRITICAL_AVERAGE_MSPT
                    || p95Mspt >= NATURE_EMERGENCY_CRITICAL_P95_MSPT;

            if (!natureTemporarilySuppressed) {
                natureSuppressionSevereTicks = severeHealthPressure
                        ? natureSuppressionSevereTicks + 1
                        : 0;
                natureSuppressionCriticalTicks = criticalHealthPressure
                        ? natureSuppressionCriticalTicks + 1
                        : 0;
                natureSuppressionRecoveryTicks = 0;

                if (natureSuppressionSevereTicks >= NATURE_EMERGENCY_SUPPRESS_TICKS
                        || natureSuppressionCriticalTicks >= NATURE_EMERGENCY_CRITICAL_TICKS) {
                    natureTemporarilySuppressed = true;
                    natureSuppressionMinimumTicks = NATURE_EMERGENCY_MIN_SUPPRESSION_TICKS;
                    natureSuppressionRecoveryTicks = 0;
                }
                return;
            }

            if (natureSuppressionMinimumTicks > 0) {
                natureSuppressionMinimumTicks--;
                natureSuppressionRecoveryTicks = 0;
                return;
            }

            boolean healthStable = snapshot.getSmoothedPressure() <= NATURE_EMERGENCY_RESUME_PRESSURE
                    && averageMspt <= NATURE_EMERGENCY_RESUME_AVERAGE_MSPT
                    && p95Mspt <= NATURE_EMERGENCY_RESUME_P95_MSPT;
            if (!healthStable) {
                natureSuppressionRecoveryTicks = 0;
                return;
            }

            natureSuppressionRecoveryTicks++;
            if (natureSuppressionRecoveryTicks >= NATURE_EMERGENCY_RESUME_TICKS) {
                natureTemporarilySuppressed = false;
                natureSuppressionSevereTicks = 0;
                natureSuppressionCriticalTicks = 0;
                natureSuppressionMinimumTicks = 0;
                natureSuppressionRecoveryTicks = 0;
            }
        }

        private void updateProcessSuppression(int currentTick, double smoothedPressure) {
            if (currentTick == lastProcessSuppressionUpdateTick) {
                return;
            }
            lastProcessSuppressionUpdateTick = currentTick;

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
            natureTemporarilySuppressed = false;
            natureSuppressionSevereTicks = 0;
            natureSuppressionCriticalTicks = 0;
            natureSuppressionMinimumTicks = 0;
            natureSuppressionRecoveryTicks = 0;
            lastNatureSuppressionUpdateTick = Integer.MIN_VALUE;
            processTemporarilySuppressed = false;
            processSuppressionHighTicks = 0;
            processSuppressionRecoveryTicks = 0;
            lastProcessSuppressionUpdateTick = Integer.MIN_VALUE;
        }

        private double computeTickMultiplier(double worldSleepRate, double speedFraction) {
            return 1.0D + Math.max(0.0D, worldSleepRate - 1.0D) * speedFraction;
        }

        private double normalizeNatureExtraAttempts(double rawNatureExtraAttemptsPerSection) {
            if (rawNatureExtraAttemptsPerSection <= NATURE_WORKLOAD_KNEE_ATTEMPTS_PER_SECTION) {
                return rawNatureExtraAttemptsPerSection;
            }

            double excess =
                    rawNatureExtraAttemptsPerSection - NATURE_WORKLOAD_KNEE_ATTEMPTS_PER_SECTION;
            double compressedExcess = NATURE_WORKLOAD_COMPRESSION_SCALE
                    * Math.log1p(excess / NATURE_WORKLOAD_COMPRESSION_SCALE);
            return NATURE_WORKLOAD_KNEE_ATTEMPTS_PER_SECTION + compressedExcess;
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

        private void prepareCoverageCache(ResourceKey<Level> dimension,
                                          WorldSleepAccelerationMode mode,
                                          WorldSleepAccelerationPlayersAffected playersAffected,
                                          int centerCount) {
            boolean metadataUnchanged = dimension.equals(cachedCoverageDimension)
                    && mode == cachedCoverageMode
                    && playersAffected == cachedCoveragePlayersAffected;
            if (metadataUnchanged && observedCoverageCentersMatch(centerCount)) {
                return;
            }
            rememberObservedCoverageCenters(centerCount);

            int uniqueCenterCount = 0;
            if (centerCount == 1) {
                uniqueCenterCount = 1;
            } else if (centerCount > 1) {
                Arrays.sort(coverageCenterScratch, 0, centerCount);
                for (int index = 0; index < centerCount; index++) {
                    long center = coverageCenterScratch[index];
                    if (uniqueCenterCount == 0 || coverageCenterScratch[uniqueCenterCount - 1] != center) {
                        coverageCenterScratch[uniqueCenterCount++] = center;
                    }
                }
            }

            boolean inputsUnchanged = metadataUnchanged
                    && coverageCentersMatch(uniqueCenterCount);
            if (inputsUnchanged) {
                return;
            }

            // Sets already published through older statuses are never mutated. Clearing this
            // map only drops the manager's references; old statuses remain valid.
            coverageByRadius.clear();
            cachedCoverageCenters = Arrays.copyOf(coverageCenterScratch, uniqueCenterCount);
            cachedCoverageDimension = dimension;
            cachedCoverageMode = mode;
            cachedCoveragePlayersAffected = playersAffected;
        }

        private void ensureCoverageCenterScratchCapacity(int requiredCapacity) {
            if (coverageCenterScratch.length >= requiredCapacity) {
                return;
            }
            int grownCapacity = Math.max(requiredCapacity, Math.max(4, coverageCenterScratch.length * 2));
            coverageCenterScratch = new long[grownCapacity];
        }

        private void ensureObservedCoverageCenterCapacity(int requiredCapacity) {
            if (lastObservedCoverageCenters.length >= requiredCapacity) {
                return;
            }
            int grownCapacity = Math.max(requiredCapacity, Math.max(4, lastObservedCoverageCenters.length * 2));
            lastObservedCoverageCenters = new long[grownCapacity];
        }

        private boolean observedCoverageCentersMatch(int centerCount) {
            if (lastObservedCoverageCenterCount != centerCount) {
                return false;
            }
            for (int index = 0; index < centerCount; index++) {
                if (lastObservedCoverageCenters[index] != coverageCenterScratch[index]) {
                    return false;
                }
            }
            return true;
        }

        private void rememberObservedCoverageCenters(int centerCount) {
            ensureObservedCoverageCenterCapacity(centerCount);
            System.arraycopy(
                    coverageCenterScratch,
                    0,
                    lastObservedCoverageCenters,
                    0,
                    centerCount
            );
            lastObservedCoverageCenterCount = centerCount;
        }

        private boolean coverageCentersMatch(int centerCount) {
            if (cachedCoverageCenters.length != centerCount) {
                return false;
            }
            for (int index = 0; index < centerCount; index++) {
                if (cachedCoverageCenters[index] != coverageCenterScratch[index]) {
                    return false;
                }
            }
            return true;
        }

        private boolean hasCoverageCacheState() {
            return !coverageByRadius.isEmpty()
                    || cachedCoverageCenters.length != 0
                    || cachedCoverageDimension != null
                    || cachedCoverageMode != null
                    || cachedCoveragePlayersAffected != null;
        }

        private void clearCoverageCache() {
            boolean effectiveClear = hasCoverageCacheState();
            if (!effectiveClear && lastObservedCoverageCenterCount < 0) {
                return;
            }
            coverageByRadius.clear();
            cachedCoverageCenters = EMPTY_CHUNK_CENTERS;
            cachedCoverageDimension = null;
            cachedCoverageMode = null;
            cachedCoveragePlayersAffected = null;
            lastObservedCoverageCenterCount = -1;
        }

        private LongOpenHashSet getCoverageForRadius(int radius) {
            LongOpenHashSet cached = coverageByRadius.get(radius);
            if (cached != null) {
                return cached;
            }
            LongOpenHashSet created = unionChunkAreas(radius);
            coverageByRadius.put(radius, created);
            return created;
        }

        private LongOpenHashSet unionChunkAreas(int radius) {
            LongOpenHashSet chunks = new LongOpenHashSet(estimateCoverageCapacity(radius));
            for (long packedCenter : cachedCoverageCenters) {
                int centerX = (int) packedCenter;
                int centerZ = (int) (packedCenter >>> 32);
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        chunks.add(ChunkPos.asLong(centerX + dx, centerZ + dz));
                    }
                }
            }
            return chunks;
        }

        private int estimateCoverageCapacity(int radius) {
            if (cachedCoverageCenters.length == 0 || radius < 0) {
                return 0;
            }
            long sideLength = 2L * radius + 1L;
            long chunksPerCenter = saturatedMultiply(sideLength, sideLength);
            long disjointAreaEstimate = saturatedMultiply(
                    chunksPerCenter,
                    cachedCoverageCenters.length
            );

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (long packedCenter : cachedCoverageCenters) {
                int centerX = (int) packedCenter;
                int centerZ = (int) (packedCenter >>> 32);
                minX = Math.min(minX, centerX);
                maxX = Math.max(maxX, centerX);
                minZ = Math.min(minZ, centerZ);
                maxZ = Math.max(maxZ, centerZ);
            }
            long boundingWidth = (long) maxX - minX + sideLength;
            long boundingHeight = (long) maxZ - minZ + sideLength;
            long boundingAreaEstimate = saturatedMultiply(boundingWidth, boundingHeight);
            long estimatedChunks = Math.min(disjointAreaEstimate, boundingAreaEstimate);
            return (int) Math.min(estimatedChunks, MAX_COVERAGE_PREALLOCATION_CHUNKS);
        }

        private long saturatedMultiply(long left, long right) {
            if (left <= 0L || right <= 0L) {
                return 0L;
            }
            if (left > Long.MAX_VALUE / right) {
                return Long.MAX_VALUE;
            }
            return left * right;
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

            long sleepStartGameTime = sleepState.getServerStartGameTime();
            if (sleepStartGameTime <= 0L || sleepStartGameTime == scheduledGovernorDebugStartGameTime) {
                return;
            }

            scheduledGovernorDebugStartGameTime = sleepStartGameTime;
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
                    "Governor snapshot: mode={}, automaticMode={}, playersAffected={}, rate={}x, avgMspt={}, p95Mspt={}, rawPressure={}, smoothedPressure={}, players={}, simDist={}, natureSuppressed={}, processSuppressed={}",
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
                    diagnosticStatus.getNature().isTemporarilySuppressed(),
                    diagnosticStatus.isProcessesTemporarilySuppressed()
            );

            Constants.debug(
                    "Governor result: nature[radius={}/{}, speed={}/{}%, rawExtraAttempts={}, normalizedExtraAttempts={}, normalizerApplied={}, chunks={}, suppressed={}] process[radius={}/{}, speed={}%, multiplier={}x, chunks={}, suppressed={}]",
                    natureStatus.getEffectiveRadiusChunks(),
                    natureStatus.getConfiguredRadiusChunks(),
                    natureStatus.getEffectiveSpeedPercent(),
                    natureStatus.getConfiguredSpeedPercent(),
                    formatDecimal(natureStatus.getRawNatureExtraAttemptsPerSection()),
                    formatDecimal(natureStatus.getExtraRandomTickAttemptsPerSection()),
                    natureStatus.isNatureWorkloadNormalizationApplied(),
                    natureStatus.getCoveredChunkCount(),
                    natureStatus.isTemporarilySuppressed(),
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
            scheduledGovernorDebugStartGameTime = Long.MIN_VALUE;
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
