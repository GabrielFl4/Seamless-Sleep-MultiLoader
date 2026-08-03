package net.aqualoco.sec.acceleration;

import net.aqualoco.sec.config.WorldSleepAccelerationMode;
import net.aqualoco.sec.config.WorldSleepAutomaticMode;
import net.aqualoco.sec.sleep.SleepAnimationMode;

public final class WorldSleepAccelerationTelemetry {
    private boolean enabled;
    private int currentTick = Integer.MIN_VALUE;
    private int lastTick = Integer.MIN_VALUE;

    private long currentRequestedAttempts;
    private long currentExecutedCoordinateAttempts;
    private long currentRejectedByFilter;
    private long currentAcceptedRandomTicks;
    private long currentBlockStateReads;
    private long currentKnownNegativeSkips;
    private long currentKnownPositiveHits;
    private long currentUnknownClassifications;
    private long currentChunksVisited;
    private long currentSectionsVisited;
    private long currentRandomTickingSections;
    private long currentSkippedSections;
    private long currentSectionRelevantSkips;
    private int currentPeakAttemptsPerSection;

    private long lastRequestedAttempts;
    private long lastExecutedCoordinateAttempts;
    private long lastRejectedByFilter;
    private long lastAcceptedRandomTicks;
    private long lastBlockStateReads;
    private long lastKnownNegativeSkips;
    private long lastKnownPositiveHits;
    private long lastUnknownClassifications;
    private long lastChunksVisited;
    private long lastSectionsVisited;
    private long lastRandomTickingSections;
    private long lastSkippedSections;
    private long lastSectionRelevantSkips;
    private int lastPeakAttemptsPerSection;

    private boolean sessionPresent;
    private boolean sessionActive;
    private long sessionId = Long.MIN_VALUE;
    private int sessionStartTick = Integer.MIN_VALUE;
    private int sessionLastActiveTick = Integer.MIN_VALUE;
    private SleepAnimationMode sessionAnimationMode;
    private WorldSleepAccelerationMode sessionRuntimeMode = WorldSleepAccelerationMode.OFF;
    private WorldSleepAutomaticMode sessionAutomaticMode = WorldSleepAutomaticMode.AGGRESSIVE;
    private int sessionLastEffectiveSpeedPercent;
    private WorldSleepAccelerationGovernorAction sessionLastGovernorAction =
            WorldSleepAccelerationGovernorAction.NONE;
    private int sessionLastRandomTickSpeed;
    private double sessionLastWorldSleepRate = 1.0D;
    private double sessionLastEffectiveRandomTickRate;
    private double sessionLastRawNatureExtraAttemptsPerSection;
    private double sessionLastNormalizedNatureExtraAttemptsPerSection;
    private double sessionLastNatureWorkloadReductionPercent;
    private double sessionNatureWorkloadKnee;
    private double sessionNatureWorkloadCompressionScale;
    private boolean sessionLastNatureWorkloadNormalizationApplied;
    private boolean sessionNatureWorkloadNormalizationEverApplied;
    private double sessionPeakRawNatureExtraAttemptsPerSection;
    private double sessionNormalizedNatureExtraAttemptsAtRawPeak;
    private double sessionPeakNatureWorkloadReductionPercent;
    private int sessionLastEffectiveRadiusChunks;
    private int sessionLastCoveredChunkCount;
    private int sessionLastAffectedPlayerCount;
    private double sessionLastAverageMspt = 50.0D;
    private double sessionLastP95Mspt = 50.0D;
    private double sessionLastHealthPressure;
    private double sessionLastSmoothedPressure;
    private boolean sessionLastEmergencySuppressionActive;
    private long sessionRequestedAttempts;
    private long sessionExecutedCoordinateAttempts;
    private long sessionRejectedByFilter;
    private long sessionAcceptedRandomTicks;
    private long sessionBlockStateReads;
    private long sessionKnownNegativeSkips;
    private long sessionKnownPositiveHits;
    private long sessionUnknownClassifications;
    private long sessionChunksVisited;
    private long sessionSectionsVisited;
    private long sessionRandomTickingSections;
    private long sessionSkippedSections;
    private long sessionSectionRelevantSkips;
    private long sessionCurrentTickRequestedAttempts;
    private long sessionPeakRequestedAttemptsPerTick;
    private int sessionPeakAttemptsPerSection;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        resetCollectionState();
    }

    public void beginTick(int serverTick) {
        if (!enabled) {
            return;
        }
        if (currentTick == serverTick) {
            return;
        }
        if (currentTick != Integer.MIN_VALUE) {
            lastTick = currentTick;
            lastRequestedAttempts = currentRequestedAttempts;
            lastExecutedCoordinateAttempts = currentExecutedCoordinateAttempts;
            lastRejectedByFilter = currentRejectedByFilter;
            lastAcceptedRandomTicks = currentAcceptedRandomTicks;
            lastBlockStateReads = currentBlockStateReads;
            lastKnownNegativeSkips = currentKnownNegativeSkips;
            lastKnownPositiveHits = currentKnownPositiveHits;
            lastUnknownClassifications = currentUnknownClassifications;
            lastChunksVisited = currentChunksVisited;
            lastSectionsVisited = currentSectionsVisited;
            lastRandomTickingSections = currentRandomTickingSections;
            lastSkippedSections = currentSkippedSections;
            lastSectionRelevantSkips = currentSectionRelevantSkips;
            lastPeakAttemptsPerSection = currentPeakAttemptsPerSection;
        }

        currentTick = serverTick;
        currentRequestedAttempts = 0L;
        currentExecutedCoordinateAttempts = 0L;
        currentRejectedByFilter = 0L;
        currentAcceptedRandomTicks = 0L;
        currentBlockStateReads = 0L;
        currentKnownNegativeSkips = 0L;
        currentKnownPositiveHits = 0L;
        currentUnknownClassifications = 0L;
        currentChunksVisited = 0L;
        currentSectionsVisited = 0L;
        currentRandomTickingSections = 0L;
        currentSkippedSections = 0L;
        currentSectionRelevantSkips = 0L;
        currentPeakAttemptsPerSection = 0;
        sessionCurrentTickRequestedAttempts = 0L;
    }

    public void updateSession(int serverTick,
                              boolean accelerationSessionActive,
                              long accelerationSessionId,
                              SleepAnimationMode animationMode,
                              WorldSleepAccelerationMode runtimeMode) {
        if (!enabled) {
            return;
        }
        beginTick(serverTick);
        if (!accelerationSessionActive) {
            sessionActive = false;
            return;
        }

        boolean newSession = !sessionPresent
                || sessionId != accelerationSessionId
                || sessionAnimationMode != animationMode;
        if (newSession) {
            resetSession(serverTick, accelerationSessionId, animationMode, runtimeMode);
        }

        sessionActive = true;
        sessionLastActiveTick = serverTick;
        sessionRuntimeMode = runtimeMode;
    }

    public void updateRuntimeDetails(WorldSleepAutomaticMode automaticMode,
                                     int effectiveSpeedPercent,
                                     WorldSleepAccelerationGovernorAction governorAction,
                                     int randomTickSpeed,
                                     double worldSleepRate,
                                     double rawNatureExtraAttemptsPerSection,
                                     double normalizedNatureExtraAttemptsPerSection,
                                     double natureWorkloadKnee,
                                     double natureWorkloadCompressionScale,
                                     boolean natureWorkloadNormalizationApplied,
                                     int effectiveRadiusChunks,
                                     int coveredChunkCount,
                                     int affectedPlayerCount,
                                     double averageMspt,
                                     double p95Mspt,
                                     double healthPressure,
                                     double smoothedPressure,
                                     boolean emergencySuppressionActive) {
        if (!enabled || !sessionActive) {
            return;
        }
        sessionAutomaticMode = automaticMode;
        sessionLastEffectiveSpeedPercent = effectiveSpeedPercent;
        sessionLastGovernorAction = governorAction;
        sessionLastRandomTickSpeed = randomTickSpeed;
        sessionLastWorldSleepRate = worldSleepRate;
        sessionLastEffectiveRandomTickRate =
                randomTickSpeed + normalizedNatureExtraAttemptsPerSection;
        sessionLastRawNatureExtraAttemptsPerSection = rawNatureExtraAttemptsPerSection;
        sessionLastNormalizedNatureExtraAttemptsPerSection =
                normalizedNatureExtraAttemptsPerSection;
        sessionLastNatureWorkloadReductionPercent = rawNatureExtraAttemptsPerSection <= 0.0D
                ? 0.0D
                : Math.max(
                        0.0D,
                        (1.0D - normalizedNatureExtraAttemptsPerSection
                                / rawNatureExtraAttemptsPerSection) * 100.0D
                );
        sessionNatureWorkloadKnee = natureWorkloadKnee;
        sessionNatureWorkloadCompressionScale = natureWorkloadCompressionScale;
        sessionLastNatureWorkloadNormalizationApplied = natureWorkloadNormalizationApplied;
        sessionNatureWorkloadNormalizationEverApplied |= natureWorkloadNormalizationApplied;
        if (rawNatureExtraAttemptsPerSection >= sessionPeakRawNatureExtraAttemptsPerSection) {
            sessionPeakRawNatureExtraAttemptsPerSection = rawNatureExtraAttemptsPerSection;
            sessionNormalizedNatureExtraAttemptsAtRawPeak =
                    normalizedNatureExtraAttemptsPerSection;
            sessionPeakNatureWorkloadReductionPercent =
                    sessionLastNatureWorkloadReductionPercent;
        }
        sessionLastEffectiveRadiusChunks = effectiveRadiusChunks;
        sessionLastCoveredChunkCount = coveredChunkCount;
        sessionLastAffectedPlayerCount = affectedPlayerCount;
        sessionLastAverageMspt = averageMspt;
        sessionLastP95Mspt = p95Mspt;
        sessionLastHealthPressure = healthPressure;
        sessionLastSmoothedPressure = smoothedPressure;
        sessionLastEmergencySuppressionActive = emergencySuppressionActive;
    }

    public void recordChunkVisited() {
        if (!enabled) {
            return;
        }
        currentChunksVisited++;
        if (sessionActive) {
            sessionChunksVisited++;
        }
    }

    public void recordSkippedSection() {
        if (!enabled) {
            return;
        }
        currentSectionsVisited++;
        currentSkippedSections++;
        if (sessionActive) {
            sessionSectionsVisited++;
            sessionSkippedSections++;
        }
    }

    public void recordSectionRelevantSkip() {
        if (!enabled) {
            return;
        }
        currentSectionsVisited++;
        currentSectionRelevantSkips++;
        if (sessionActive) {
            sessionSectionsVisited++;
            sessionSectionRelevantSkips++;
        }
    }

    public void recordRandomTickingSection(int attempts) {
        if (!enabled) {
            return;
        }
        int nonNegativeAttempts = Math.max(0, attempts);
        currentSectionsVisited++;
        currentRandomTickingSections++;
        currentRequestedAttempts += nonNegativeAttempts;
        currentExecutedCoordinateAttempts += nonNegativeAttempts;
        currentPeakAttemptsPerSection = Math.max(currentPeakAttemptsPerSection, nonNegativeAttempts);

        if (sessionActive) {
            sessionSectionsVisited++;
            sessionRandomTickingSections++;
            sessionRequestedAttempts += nonNegativeAttempts;
            sessionExecutedCoordinateAttempts += nonNegativeAttempts;
            sessionCurrentTickRequestedAttempts += nonNegativeAttempts;
            sessionPeakAttemptsPerSection = Math.max(sessionPeakAttemptsPerSection, nonNegativeAttempts);
            sessionPeakRequestedAttemptsPerTick = Math.max(
                    sessionPeakRequestedAttemptsPerTick,
                    sessionCurrentTickRequestedAttempts
            );
        }
    }

    public void recordAttemptResults(int rejectedByFilter,
                                     int acceptedRandomTicks,
                                     int blockStateReads,
                                     int knownNegativeSkips,
                                     int knownPositiveHits,
                                     int unknownClassifications) {
        if (!enabled) {
            return;
        }
        int nonNegativeRejected = Math.max(0, rejectedByFilter);
        int nonNegativeAccepted = Math.max(0, acceptedRandomTicks);
        int nonNegativeReads = Math.max(0, blockStateReads);
        int nonNegativeKnownNegative = Math.max(0, knownNegativeSkips);
        int nonNegativeKnownPositive = Math.max(0, knownPositiveHits);
        int nonNegativeUnknown = Math.max(0, unknownClassifications);
        currentRejectedByFilter += nonNegativeRejected;
        currentAcceptedRandomTicks += nonNegativeAccepted;
        currentBlockStateReads += nonNegativeReads;
        currentKnownNegativeSkips += nonNegativeKnownNegative;
        currentKnownPositiveHits += nonNegativeKnownPositive;
        currentUnknownClassifications += nonNegativeUnknown;
        if (sessionActive) {
            sessionRejectedByFilter += nonNegativeRejected;
            sessionAcceptedRandomTicks += nonNegativeAccepted;
            sessionBlockStateReads += nonNegativeReads;
            sessionKnownNegativeSkips += nonNegativeKnownNegative;
            sessionKnownPositiveHits += nonNegativeKnownPositive;
            sessionUnknownClassifications += nonNegativeUnknown;
        }
    }

    public Snapshot snapshot(int serverTick) {
        if (enabled) {
            beginTick(serverTick);
        }
        boolean useLastCompletedTick = lastTick != Integer.MIN_VALUE;
        TickSnapshot tickSnapshot = new TickSnapshot(
                useLastCompletedTick ? lastTick : currentTick,
                useLastCompletedTick ? lastRequestedAttempts : currentRequestedAttempts,
                useLastCompletedTick ? lastExecutedCoordinateAttempts : currentExecutedCoordinateAttempts,
                useLastCompletedTick ? lastRejectedByFilter : currentRejectedByFilter,
                useLastCompletedTick ? lastAcceptedRandomTicks : currentAcceptedRandomTicks,
                useLastCompletedTick ? lastBlockStateReads : currentBlockStateReads,
                useLastCompletedTick ? lastKnownNegativeSkips : currentKnownNegativeSkips,
                useLastCompletedTick ? lastKnownPositiveHits : currentKnownPositiveHits,
                useLastCompletedTick ? lastUnknownClassifications : currentUnknownClassifications,
                useLastCompletedTick ? lastChunksVisited : currentChunksVisited,
                useLastCompletedTick ? lastSectionsVisited : currentSectionsVisited,
                useLastCompletedTick ? lastRandomTickingSections : currentRandomTickingSections,
                useLastCompletedTick ? lastSkippedSections : currentSkippedSections,
                useLastCompletedTick ? lastSectionRelevantSkips : currentSectionRelevantSkips,
                useLastCompletedTick ? lastPeakAttemptsPerSection : currentPeakAttemptsPerSection
        );
        int sessionDurationTicks = sessionPresent
                ? Math.max(0, sessionLastActiveTick - sessionStartTick + 1)
                : 0;
        return new Snapshot(
                tickSnapshot,
                sessionPresent,
                sessionActive,
                sessionId,
                sessionStartTick,
                sessionLastActiveTick,
                sessionDurationTicks,
                sessionAnimationMode,
                sessionRuntimeMode,
                sessionAutomaticMode,
                sessionLastEffectiveSpeedPercent,
                sessionLastGovernorAction,
                sessionLastRandomTickSpeed,
                sessionLastWorldSleepRate,
                sessionLastEffectiveRandomTickRate,
                sessionLastRawNatureExtraAttemptsPerSection,
                sessionLastNormalizedNatureExtraAttemptsPerSection,
                sessionLastNatureWorkloadReductionPercent,
                sessionNatureWorkloadKnee,
                sessionNatureWorkloadCompressionScale,
                sessionLastNatureWorkloadNormalizationApplied,
                sessionNatureWorkloadNormalizationEverApplied,
                sessionPeakRawNatureExtraAttemptsPerSection,
                sessionNormalizedNatureExtraAttemptsAtRawPeak,
                sessionPeakNatureWorkloadReductionPercent,
                sessionLastEffectiveRadiusChunks,
                sessionLastCoveredChunkCount,
                sessionLastAffectedPlayerCount,
                sessionLastAverageMspt,
                sessionLastP95Mspt,
                sessionLastHealthPressure,
                sessionLastSmoothedPressure,
                sessionLastEmergencySuppressionActive,
                sessionRequestedAttempts,
                sessionExecutedCoordinateAttempts,
                sessionRejectedByFilter,
                sessionAcceptedRandomTicks,
                sessionBlockStateReads,
                sessionKnownNegativeSkips,
                sessionKnownPositiveHits,
                sessionUnknownClassifications,
                sessionChunksVisited,
                sessionSectionsVisited,
                sessionRandomTickingSections,
                sessionSkippedSections,
                sessionSectionRelevantSkips,
                sessionPeakRequestedAttemptsPerTick,
                sessionPeakAttemptsPerSection
        );
    }

    private void resetCollectionState() {
        currentTick = Integer.MIN_VALUE;
        lastTick = Integer.MIN_VALUE;
        currentRequestedAttempts = 0L;
        currentExecutedCoordinateAttempts = 0L;
        currentRejectedByFilter = 0L;
        currentAcceptedRandomTicks = 0L;
        currentBlockStateReads = 0L;
        currentKnownNegativeSkips = 0L;
        currentKnownPositiveHits = 0L;
        currentUnknownClassifications = 0L;
        currentChunksVisited = 0L;
        currentSectionsVisited = 0L;
        currentRandomTickingSections = 0L;
        currentSkippedSections = 0L;
        currentSectionRelevantSkips = 0L;
        currentPeakAttemptsPerSection = 0;
        lastRequestedAttempts = 0L;
        lastExecutedCoordinateAttempts = 0L;
        lastRejectedByFilter = 0L;
        lastAcceptedRandomTicks = 0L;
        lastBlockStateReads = 0L;
        lastKnownNegativeSkips = 0L;
        lastKnownPositiveHits = 0L;
        lastUnknownClassifications = 0L;
        lastChunksVisited = 0L;
        lastSectionsVisited = 0L;
        lastRandomTickingSections = 0L;
        lastSkippedSections = 0L;
        lastSectionRelevantSkips = 0L;
        lastPeakAttemptsPerSection = 0;
        sessionPresent = false;
        sessionActive = false;
        sessionId = Long.MIN_VALUE;
        sessionCurrentTickRequestedAttempts = 0L;
    }

    private void resetSession(int serverTick,
                              long accelerationSessionId,
                              SleepAnimationMode animationMode,
                              WorldSleepAccelerationMode runtimeMode) {
        sessionPresent = true;
        sessionActive = true;
        sessionId = accelerationSessionId;
        sessionStartTick = serverTick;
        sessionLastActiveTick = serverTick;
        sessionAnimationMode = animationMode;
        sessionRuntimeMode = runtimeMode;
        sessionAutomaticMode = WorldSleepAutomaticMode.AGGRESSIVE;
        sessionLastEffectiveSpeedPercent = 0;
        sessionLastGovernorAction = WorldSleepAccelerationGovernorAction.NONE;
        sessionLastRandomTickSpeed = 0;
        sessionLastWorldSleepRate = 1.0D;
        sessionLastEffectiveRandomTickRate = 0.0D;
        sessionLastRawNatureExtraAttemptsPerSection = 0.0D;
        sessionLastNormalizedNatureExtraAttemptsPerSection = 0.0D;
        sessionLastNatureWorkloadReductionPercent = 0.0D;
        sessionNatureWorkloadKnee = 0.0D;
        sessionNatureWorkloadCompressionScale = 0.0D;
        sessionLastNatureWorkloadNormalizationApplied = false;
        sessionNatureWorkloadNormalizationEverApplied = false;
        sessionPeakRawNatureExtraAttemptsPerSection = 0.0D;
        sessionNormalizedNatureExtraAttemptsAtRawPeak = 0.0D;
        sessionPeakNatureWorkloadReductionPercent = 0.0D;
        sessionLastEffectiveRadiusChunks = 0;
        sessionLastCoveredChunkCount = 0;
        sessionLastAffectedPlayerCount = 0;
        sessionLastAverageMspt = 50.0D;
        sessionLastP95Mspt = 50.0D;
        sessionLastHealthPressure = 0.0D;
        sessionLastSmoothedPressure = 0.0D;
        sessionLastEmergencySuppressionActive = false;
        sessionRequestedAttempts = 0L;
        sessionExecutedCoordinateAttempts = 0L;
        sessionRejectedByFilter = 0L;
        sessionAcceptedRandomTicks = 0L;
        sessionBlockStateReads = 0L;
        sessionKnownNegativeSkips = 0L;
        sessionKnownPositiveHits = 0L;
        sessionUnknownClassifications = 0L;
        sessionChunksVisited = 0L;
        sessionSectionsVisited = 0L;
        sessionRandomTickingSections = 0L;
        sessionSkippedSections = 0L;
        sessionSectionRelevantSkips = 0L;
        sessionCurrentTickRequestedAttempts = 0L;
        sessionPeakRequestedAttemptsPerTick = 0L;
        sessionPeakAttemptsPerSection = 0;
    }

    public record TickSnapshot(int serverTick,
                               long requestedAttempts,
                               long executedCoordinateAttempts,
                               long rejectedByFilter,
                               long acceptedRandomTicks,
                               long blockStateReads,
                               long knownNegativeSkips,
                               long knownPositiveHits,
                               long unknownClassifications,
                               long chunksVisited,
                               long sectionsVisited,
                               long randomTickingSections,
                               long skippedSections,
                               long sectionRelevantSkips,
                               int peakAttemptsPerSection) {
        public double averageAttemptsPerRandomTickingSection() {
            return randomTickingSections <= 0L
                    ? 0.0D
                    : requestedAttempts / (double) randomTickingSections;
        }
    }

    public record Snapshot(TickSnapshot lastTick,
                           boolean sessionPresent,
                           boolean sessionActive,
                           long sessionId,
                           int sessionStartTick,
                           int sessionLastActiveTick,
                           int sessionDurationTicks,
                           SleepAnimationMode sessionAnimationMode,
                           WorldSleepAccelerationMode sessionRuntimeMode,
                           WorldSleepAutomaticMode sessionAutomaticMode,
                           int sessionLastEffectiveSpeedPercent,
                           WorldSleepAccelerationGovernorAction sessionLastGovernorAction,
                           int sessionLastRandomTickSpeed,
                           double sessionLastWorldSleepRate,
                           double sessionLastEffectiveRandomTickRate,
                           double sessionLastRawNatureExtraAttemptsPerSection,
                           double sessionLastNormalizedNatureExtraAttemptsPerSection,
                           double sessionLastNatureWorkloadReductionPercent,
                           double sessionNatureWorkloadKnee,
                           double sessionNatureWorkloadCompressionScale,
                           boolean sessionLastNatureWorkloadNormalizationApplied,
                           boolean sessionNatureWorkloadNormalizationEverApplied,
                           double sessionPeakRawNatureExtraAttemptsPerSection,
                           double sessionNormalizedNatureExtraAttemptsAtRawPeak,
                           double sessionPeakNatureWorkloadReductionPercent,
                           int sessionLastEffectiveRadiusChunks,
                           int sessionLastCoveredChunkCount,
                           int sessionLastAffectedPlayerCount,
                           double sessionLastAverageMspt,
                           double sessionLastP95Mspt,
                           double sessionLastHealthPressure,
                           double sessionLastSmoothedPressure,
                           boolean sessionLastEmergencySuppressionActive,
                           long sessionRequestedAttempts,
                           long sessionExecutedCoordinateAttempts,
                           long sessionRejectedByFilter,
                           long sessionAcceptedRandomTicks,
                           long sessionBlockStateReads,
                           long sessionKnownNegativeSkips,
                           long sessionKnownPositiveHits,
                           long sessionUnknownClassifications,
                           long sessionChunksVisited,
                           long sessionSectionsVisited,
                           long sessionRandomTickingSections,
                           long sessionSkippedSections,
                           long sessionSectionRelevantSkips,
                           long sessionPeakRequestedAttemptsPerTick,
                           int sessionPeakAttemptsPerSection) {
        public double averageAttemptsPerTick() {
            return sessionDurationTicks <= 0
                    ? 0.0D
                    : sessionRequestedAttempts / (double) sessionDurationTicks;
        }

        public double averageAcceptedRandomTicksPerTick() {
            return sessionDurationTicks <= 0
                    ? 0.0D
                    : sessionAcceptedRandomTicks / (double) sessionDurationTicks;
        }

        public double averageAttemptsPerRandomTickingSection() {
            return sessionRandomTickingSections <= 0L
                    ? 0.0D
                    : sessionRequestedAttempts / (double) sessionRandomTickingSections;
        }

        public double acceptanceRate() {
            return sessionRequestedAttempts <= 0L
                    ? 0.0D
                    : sessionAcceptedRandomTicks / (double) sessionRequestedAttempts;
        }

        public double avoidedBlockStateReadRate() {
            return sessionExecutedCoordinateAttempts <= 0L
                    ? 0.0D
                    : sessionKnownNegativeSkips / (double) sessionExecutedCoordinateAttempts;
        }
    }
}
