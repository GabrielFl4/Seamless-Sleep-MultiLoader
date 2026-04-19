package net.aqualoco.sec.acceleration;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.SeamlessSleepCommon;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.WorldSleepAccelerationConfig;
import net.aqualoco.sec.config.WorldSleepAccelerationGovernorAggressiveness;
import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAccelerationModuleConfig;
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
        return SERVER_STATES.computeIfAbsent(level.getServer(), key -> new CachedServerState()).prepare(level, false, false);
    }

    public static WorldSleepAccelerationStatus getDiagnosticStatus(ServerLevel level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return WorldSleepAccelerationStatus.INACTIVE;
        }
        return SERVER_STATES.computeIfAbsent(level.getServer(), key -> new CachedServerState()).prepare(level, false, true);
    }

    private static void prepare(ServerLevel level, boolean forceRebuild) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        SERVER_STATES.computeIfAbsent(level.getServer(), key -> new CachedServerState()).prepare(level, forceRebuild, false);
    }

    private static final class CachedServerState {
        private static final double METRIC_ALPHA = 0.15D;
        private static final int METRIC_UPDATE_INTERVAL_TICKS = 10;
        private static final int GOVERNOR_DEBUG_DELAY_TICKS = 10;

        private int lastPreparedTick = Integer.MIN_VALUE;
        private int lastDiagnosticTick = Integer.MIN_VALUE;
        private int lastMetricsUpdateTick = Integer.MIN_VALUE;
        private WorldSleepAccelerationStatus lastStatus = WorldSleepAccelerationStatus.INACTIVE;
        private WorldSleepAccelerationStatus lastDiagnosticStatus = WorldSleepAccelerationStatus.INACTIVE;
        private double averageMspt = 50.0D;
        private double p95Mspt = 50.0D;
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
                return WorldSleepAccelerationStatus.INACTIVE;
            }

            List<ServerPlayer> activePlayers = collectActivePlayers(level);
            if (activePlayers.isEmpty()) {
                return WorldSleepAccelerationStatus.INACTIVE;
            }

            int simulationDistance = Math.max(0, level.getServer().getPlayerList().getSimulationDistance());
            double governorPressure = 0.0D;
            WorldSleepAccelerationGovernorSnapshot governorSnapshot = WorldSleepAccelerationGovernorSnapshot.INACTIVE;
            if (accelerationConfig.mode == WorldSleepAccelerationMode.AUTO) {
                if (includeDiagnostics) {
                    governorSnapshot = computeGovernorSnapshot(
                            smoothedAverageMspt,
                            smoothedP95Mspt,
                            worldSleepRate,
                            activePlayers.size(),
                            simulationDistance,
                            accelerationConfig.governorAggressiveness
                    );
                    governorPressure = governorSnapshot.getPressure();
                } else {
                    governorPressure = computeGovernorPressure(
                            smoothedAverageMspt,
                            smoothedP95Mspt,
                            worldSleepRate,
                            activePlayers.size(),
                            simulationDistance,
                            accelerationConfig.governorAggressiveness
                    );
                }
            }

            Map<Integer, LongOpenHashSet> coverageByRadius = new HashMap<>();
            int randomTickSpeed = Math.max(0, level.getGameRules().get(GameRules.RANDOM_TICK_SPEED));
            WorldSleepAccelerationModuleStatus natureStatus = buildNatureStatus(
                    accelerationConfig,
                    simulationDistance,
                    worldSleepRate,
                    governorPressure,
                    activePlayers,
                    randomTickSpeed,
                    coverageByRadius
            );
            WorldSleepAccelerationModuleStatus processStatus = buildProcessStatus(
                    accelerationConfig,
                    simulationDistance,
                    worldSleepRate,
                    governorPressure,
                    activePlayers,
                    coverageByRadius
            );

            boolean active = natureStatus.isActive() || processStatus.isActive();
            if (!active) {
                return WorldSleepAccelerationStatus.INACTIVE;
            }

            WorldSleepAccelerationGovernorAction governorAction = WorldSleepAccelerationGovernorAction.combine(
                    natureStatus.getGovernorAction(),
                    processStatus.getGovernorAction()
            );

            WorldSleepAccelerationStatus nextStatus = new WorldSleepAccelerationStatus(
                    true,
                    level.dimension(),
                    activePlayers.size(),
                    worldSleepRate,
                    smoothedAverageMspt,
                    smoothedP95Mspt,
                    governorPressure,
                    accelerationConfig.mode,
                    accelerationConfig.preset,
                    accelerationConfig.natureFilterProfile,
                    governorAction,
                    governorSnapshot,
                    natureStatus,
                    processStatus
            );
            return nextStatus;
        }

        private List<ServerPlayer> collectActivePlayers(ServerLevel level) {
            List<ServerPlayer> players = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !player.isAlive()) {
                    continue;
                }
                players.add(player);
            }
            return players;
        }

        private WorldSleepAccelerationModuleStatus buildNatureStatus(WorldSleepAccelerationConfig accelerationConfig,
                                                                     int simulationDistance,
                                                                     double worldSleepRate,
                                                                     double governorPressure,
                                                                     List<ServerPlayer> activePlayers,
                                                                     int randomTickSpeed,
                                                                     Map<Integer, LongOpenHashSet> coverageByRadius) {
            if (!accelerationConfig.randomTickAccelerationEnabled || randomTickSpeed <= 0) {
                return WorldSleepAccelerationModuleStatus.INACTIVE;
            }

            return buildModuleStatus(
                    accelerationConfig.mode,
                    accelerationConfig.nature,
                    simulationDistance,
                    worldSleepRate,
                    governorPressure,
                    activePlayers,
                    randomTickSpeed,
                    coverageByRadius
            );
        }

        private WorldSleepAccelerationModuleStatus buildProcessStatus(WorldSleepAccelerationConfig accelerationConfig,
                                                                      int simulationDistance,
                                                                      double worldSleepRate,
                                                                      double governorPressure,
                                                                      List<ServerPlayer> activePlayers,
                                                                      Map<Integer, LongOpenHashSet> coverageByRadius) {
            if (!accelerationConfig.processAccelerationEnabled) {
                return WorldSleepAccelerationModuleStatus.INACTIVE;
            }

            return buildModuleStatus(
                    accelerationConfig.mode,
                    accelerationConfig.process,
                    simulationDistance,
                    worldSleepRate,
                    governorPressure,
                    activePlayers,
                    0,
                    coverageByRadius
            );
        }

        private WorldSleepAccelerationModuleStatus buildModuleStatus(WorldSleepAccelerationMode mode,
                                                                     WorldSleepAccelerationModuleConfig moduleConfig,
                                                                     int simulationDistance,
                                                                     double worldSleepRate,
                                                                     double governorPressure,
                                                                     List<ServerPlayer> activePlayers,
                                                                     int randomTickSpeed,
                                                                     Map<Integer, LongOpenHashSet> coverageByRadius) {
            int baseRadius = resolveRadius(moduleConfig.baseRadiusChunks, simulationDistance);
            int minRadius = Math.min(baseRadius, resolveRadius(moduleConfig.autoMinRadiusChunks, simulationDistance));
            double baseFraction = moduleConfig.baseRateFraction;
            double minFraction = Math.min(baseFraction, moduleConfig.autoMinRateFraction);

            if (baseFraction <= 0.0D) {
                return WorldSleepAccelerationModuleStatus.INACTIVE;
            }

            int effectiveRadius = baseRadius;
            double effectiveFraction = baseFraction;
            WorldSleepAccelerationGovernorAction governorAction = WorldSleepAccelerationGovernorAction.NONE;

            if (mode == WorldSleepAccelerationMode.AUTO) {
                double areaStageOne = Mth.clamp(governorPressure / 0.45D, 0.0D, 1.0D);
                double intensityStage = Mth.clamp((governorPressure - 0.45D) / 0.35D, 0.0D, 1.0D);
                double areaStageTwo = Mth.clamp((governorPressure - 0.80D) / 0.20D, 0.0D, 1.0D);

                int midRadius = Math.max(minRadius, Math.max(0, (int) Math.round(baseRadius * 0.70D)));
                double firstRadiusTarget = Mth.lerp(areaStageOne, baseRadius, midRadius);
                double finalRadiusTarget = Mth.lerp(areaStageTwo, firstRadiusTarget, minRadius);
                effectiveRadius = Mth.clamp((int) Math.round(finalRadiusTarget), minRadius, baseRadius);
                effectiveFraction = Mth.lerp(intensityStage, baseFraction, minFraction);

                boolean areaReduced = effectiveRadius < baseRadius;
                boolean intensityReduced = effectiveFraction + 0.0001D < baseFraction;
                if (areaReduced && intensityReduced) {
                    governorAction = WorldSleepAccelerationGovernorAction.AREA_AND_INTENSITY;
                } else if (areaReduced) {
                    governorAction = WorldSleepAccelerationGovernorAction.AREA;
                } else if (intensityReduced) {
                    governorAction = WorldSleepAccelerationGovernorAction.INTENSITY;
                }
            }

            double effectiveTickMultiplier = 1.0D + Math.max(0.0D, worldSleepRate - 1.0D) * effectiveFraction;
            double extraRandomTickAttempts = randomTickSpeed <= 0
                    ? 0.0D
                    : randomTickSpeed * Math.max(0.0D, worldSleepRate - 1.0D) * effectiveFraction;

            if (effectiveTickMultiplier <= 1.0D && extraRandomTickAttempts <= 0.0D) {
                return WorldSleepAccelerationModuleStatus.INACTIVE;
            }

            LongOpenHashSet coveredChunks = coverageByRadius.computeIfAbsent(
                    effectiveRadius,
                    radius -> unionChunkAreas(activePlayers, radius)
            );
            if (coveredChunks.isEmpty()) {
                return WorldSleepAccelerationModuleStatus.INACTIVE;
            }

            int wholeAttempts = (int) Math.floor(extraRandomTickAttempts);
            double fractionalAttempts = extraRandomTickAttempts - wholeAttempts;

            return new WorldSleepAccelerationModuleStatus(
                    true,
                    baseRadius,
                    minRadius,
                    effectiveRadius,
                    baseFraction,
                    minFraction,
                    effectiveFraction,
                    effectiveTickMultiplier,
                    extraRandomTickAttempts,
                    wholeAttempts,
                    fractionalAttempts,
                    coveredChunks.size(),
                    coveredChunks,
                    governorAction
            );
        }

        private WorldSleepAccelerationGovernorSnapshot computeGovernorSnapshot(double averageMspt,
                                                                              double p95Mspt,
                                                                              double worldSleepRate,
                                                                              int activePlayers,
                                                                              int simulationDistance,
                                                                              WorldSleepAccelerationGovernorAggressiveness aggressiveness) {
            double averagePressure = inverseLerp(35.0D, 50.0D, averageMspt);
            double p95Pressure = inverseLerp(45.0D, 60.0D, p95Mspt);
            double perfPressure = Math.max(averagePressure, p95Pressure);

            double riskFactor = 1.0D;
            double activePlayerRiskBonus = Mth.clamp((activePlayers - 1) * 0.06D, 0.0D, 0.40D);
            double simulationDistanceRiskBonus = Mth.clamp(simulationDistance / 16.0D, 0.0D, 1.0D) * 0.12D;
            double worldSleepRateRiskBonus = Mth.clamp(worldSleepRate / 300.0D, 0.0D, 1.0D) * 0.18D;
            riskFactor += activePlayerRiskBonus;
            riskFactor += simulationDistanceRiskBonus;
            riskFactor += worldSleepRateRiskBonus;

            double aggressionMultiplier = switch (aggressiveness) {
                case CONSERVATIVE -> 0.85D;
                case AGGRESSIVE -> 1.20D;
                case BALANCED -> 1.0D;
            };
            double pressure = Mth.clamp(perfPressure * riskFactor * aggressionMultiplier, 0.0D, 1.0D);
            double areaStageOne = Mth.clamp(pressure / 0.45D, 0.0D, 1.0D);
            double intensityStage = Mth.clamp((pressure - 0.45D) / 0.35D, 0.0D, 1.0D);
            double areaStageTwo = Mth.clamp((pressure - 0.80D) / 0.20D, 0.0D, 1.0D);

            return new WorldSleepAccelerationGovernorSnapshot(
                    true,
                    averagePressure,
                    p95Pressure,
                    perfPressure,
                    activePlayerRiskBonus,
                    simulationDistanceRiskBonus,
                    worldSleepRateRiskBonus,
                    riskFactor,
                    aggressionMultiplier,
                    pressure,
                    areaStageOne,
                    intensityStage,
                    areaStageTwo
            );
        }

        private double computeGovernorPressure(double averageMspt,
                                               double p95Mspt,
                                               double worldSleepRate,
                                               int activePlayers,
                                               int simulationDistance,
                                               WorldSleepAccelerationGovernorAggressiveness aggressiveness) {
            return computeGovernorSnapshot(
                    averageMspt,
                    p95Mspt,
                    worldSleepRate,
                    activePlayers,
                    simulationDistance,
                    aggressiveness
            ).getPressure();
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

        private int resolveRadius(int configuredRadius, int simulationDistance) {
            if (configuredRadius <= 0) {
                return Math.max(0, simulationDistance);
            }
            return Math.min(configuredRadius, Math.max(0, simulationDistance));
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
                    || accelerationConfig.mode != WorldSleepAccelerationMode.AUTO
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
                    || accelerationConfig.mode != WorldSleepAccelerationMode.AUTO
                    || !sleepState.isActive()
                    || governorDebugLoggedForCurrentSleep
                    || scheduledGovernorDebugTick == Integer.MIN_VALUE
                    || currentTick < scheduledGovernorDebugTick) {
                return;
            }

            WorldSleepAccelerationStatus diagnosticStatus = buildStatus(level, averageMspt, p95Mspt, true);
            lastDiagnosticTick = currentTick;
            lastDiagnosticStatus = diagnosticStatus;

            if (!diagnosticStatus.isActive() || !diagnosticStatus.getGovernorSnapshot().isActive()) {
                Constants.debug(
                        "Governor snapshot: acceleration inactive during sleep. mode={}, preset={}",
                        accelerationConfig.mode,
                        accelerationConfig.preset
                );
                governorDebugLoggedForCurrentSleep = true;
                return;
            }

            WorldSleepAccelerationGovernorSnapshot governorSnapshot = diagnosticStatus.getGovernorSnapshot();
            WorldSleepAccelerationModuleStatus natureStatus = diagnosticStatus.getNature();
            WorldSleepAccelerationModuleStatus processStatus = diagnosticStatus.getProcess();
            int randomTickSpeed = Math.max(0, level.getGameRules().get(GameRules.RANDOM_TICK_SPEED));
            double totalRandomTickAttempts = randomTickSpeed + natureStatus.getExtraRandomTickAttemptsPerSection();

            Constants.debug(
                    "Governor snapshot: preset={}, rate={}x, avgMspt={}, p95Mspt={}, players={}, simDist={}, pressure={}, stages=[area1={}, intensity={}, area2={}], action={}",
                    accelerationConfig.preset,
                    formatDecimal(diagnosticStatus.getWorldSleepRate()),
                    formatDecimal(diagnosticStatus.getAverageMspt()),
                    formatDecimal(diagnosticStatus.getP95Mspt()),
                    diagnosticStatus.getActivePlayerCount(),
                    level.getServer().getPlayerList().getSimulationDistance(),
                    formatPercent(governorSnapshot.getPressure()),
                    formatPercent(governorSnapshot.getAreaStageOne()),
                    formatPercent(governorSnapshot.getIntensityStage()),
                    formatPercent(governorSnapshot.getAreaStageTwo()),
                    diagnosticStatus.getGovernorAction()
            );

            Constants.debug(
                    "Governor result: nature[radius={}/{}, min={}, rate={}/{}, extraAttempts={}, totalAttempts={}, chunks={}] process[radius={}/{}, min={}, rate={}/{}, multiplier={}x, chunks={}]",
                    natureStatus.getEffectiveRadiusChunks(),
                    natureStatus.getBaseRadiusChunks(),
                    natureStatus.getMinRadiusChunks(),
                    formatPercent(natureStatus.getEffectiveRateFraction()),
                    formatPercent(natureStatus.getMinRateFraction()),
                    formatDecimal(natureStatus.getExtraRandomTickAttemptsPerSection()),
                    formatDecimal(totalRandomTickAttempts),
                    natureStatus.getCoveredChunkCount(),
                    processStatus.getEffectiveRadiusChunks(),
                    processStatus.getBaseRadiusChunks(),
                    processStatus.getMinRadiusChunks(),
                    formatPercent(processStatus.getEffectiveRateFraction()),
                    formatPercent(processStatus.getMinRateFraction()),
                    formatDecimal(processStatus.getEffectiveTickMultiplier()),
                    processStatus.getCoveredChunkCount()
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
}
