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
        return SERVER_STATES.computeIfAbsent(level.getServer(), key -> new CachedServerState()).prepare(level, false);
    }

    private static void prepare(ServerLevel level, boolean forceRebuild) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        SERVER_STATES.computeIfAbsent(level.getServer(), key -> new CachedServerState()).prepare(level, forceRebuild);
    }

    private static final class CachedServerState {
        private static final double METRIC_ALPHA = 0.15D;
        private static final int METRIC_UPDATE_INTERVAL_TICKS = 10;

        private int lastPreparedTick = Integer.MIN_VALUE;
        private int lastMetricsUpdateTick = Integer.MIN_VALUE;
        private WorldSleepAccelerationStatus lastStatus = WorldSleepAccelerationStatus.INACTIVE;
        private double averageMspt = 50.0D;
        private double p95Mspt = 50.0D;

        private WorldSleepAccelerationStatus prepare(ServerLevel level, boolean forceRebuild) {
            int currentTick = level.getServer().getTickCount();
            if (!forceRebuild && currentTick == lastPreparedTick) {
                return lastStatus;
            }

            lastPreparedTick = currentTick;
            updateMetrics(level.getServer(), currentTick);
            lastStatus = buildStatus(level, averageMspt, p95Mspt, lastStatus);
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
                                                         WorldSleepAccelerationStatus previousStatus) {
            SeamlessSleepServerConfig config = SeamlessSleepServerConfigManager.get();
            WorldSleepAccelerationConfig accelerationConfig = config.worldSleepAcceleration;
            SleepAnimationState sleepState = SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION;
            double worldSleepRate = sleepState.getLogicalWorldRate();

            if (!sleepState.isActive()
                    || accelerationConfig.mode == WorldSleepAccelerationMode.OFF
                    || worldSleepRate <= 1.0D) {
                return inactive(previousStatus);
            }

            List<ServerPlayer> activePlayers = collectActivePlayers(level);
            if (activePlayers.isEmpty()) {
                return inactive(previousStatus);
            }

            int simulationDistance = Math.max(0, level.getServer().getPlayerList().getSimulationDistance());
            double governorPressure = accelerationConfig.mode == WorldSleepAccelerationMode.AUTO
                    ? computeGovernorPressure(
                    smoothedAverageMspt,
                    smoothedP95Mspt,
                    worldSleepRate,
                    activePlayers.size(),
                    simulationDistance,
                    accelerationConfig.governorAggressiveness
            )
                    : 0.0D;

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
                return inactive(previousStatus);
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
                    natureStatus,
                    processStatus
            );

            logStateChanges(previousStatus, nextStatus);
            return nextStatus;
        }

        private WorldSleepAccelerationStatus inactive(WorldSleepAccelerationStatus previousStatus) {
            logStateChanges(previousStatus, WorldSleepAccelerationStatus.INACTIVE);
            return WorldSleepAccelerationStatus.INACTIVE;
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

        private double computeGovernorPressure(double averageMspt,
                                               double p95Mspt,
                                               double worldSleepRate,
                                               int activePlayers,
                                               int simulationDistance,
                                               WorldSleepAccelerationGovernorAggressiveness aggressiveness) {
            double averagePressure = inverseLerp(35.0D, 50.0D, averageMspt);
            double p95Pressure = inverseLerp(45.0D, 60.0D, p95Mspt);
            double perfPressure = Math.max(averagePressure, p95Pressure);

            double riskFactor = 1.0D;
            riskFactor += Mth.clamp((activePlayers - 1) * 0.06D, 0.0D, 0.40D);
            riskFactor += Mth.clamp(simulationDistance / 16.0D, 0.0D, 1.0D) * 0.12D;
            riskFactor += Mth.clamp(worldSleepRate / 300.0D, 0.0D, 1.0D) * 0.18D;

            double aggressionMultiplier = switch (aggressiveness) {
                case CONSERVATIVE -> 0.85D;
                case AGGRESSIVE -> 1.20D;
                case BALANCED -> 1.0D;
            };
            return Mth.clamp(perfPressure * riskFactor * aggressionMultiplier, 0.0D, 1.0D);
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

        private void logStateChanges(WorldSleepAccelerationStatus previous, WorldSleepAccelerationStatus next) {
            if (previous.isActive() == next.isActive()
                    && previous.getGovernorAction() == next.getGovernorAction()) {
                return;
            }

            if (!next.isActive()) {
                Constants.debug("World sleep acceleration inactive.");
                return;
            }

            Constants.debug(
                    "World sleep acceleration active. rate={}, avgMspt={}, p95Mspt={}, governor={}, natureRate={}, processRate={}",
                    String.format(java.util.Locale.ROOT, "%.2f", next.getWorldSleepRate()),
                    String.format(java.util.Locale.ROOT, "%.2f", next.getAverageMspt()),
                    String.format(java.util.Locale.ROOT, "%.2f", next.getP95Mspt()),
                    next.getGovernorAction(),
                    String.format(java.util.Locale.ROOT, "%.2f", next.getNature().getEffectiveRateFraction()),
                    String.format(java.util.Locale.ROOT, "%.2f", next.getProcess().getEffectiveRateFraction())
            );
        }
    }
}
