package net.aqualoco.sec.acceleration;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class WorldSleepAccelerationModuleStatus {
    public static final WorldSleepAccelerationModuleStatus INACTIVE =
            new WorldSleepAccelerationModuleStatus(
                    false,
                    0,
                    0,
                    0,
                    0,
                    1.0D,
                    0.0D,
                    0,
                    0.0D,
                    0,
                    new LongOpenHashSet(),
                    WorldSleepAccelerationGovernorAction.NONE,
                    false
            );

    private final boolean active;
    private final int configuredRadiusChunks;
    private final int effectiveRadiusChunks;
    private final int configuredSpeedPercent;
    private final int effectiveSpeedPercent;
    private final double effectiveTickMultiplier;
    private final double extraRandomTickAttemptsPerSection;
    private final int extraRandomTickWholeAttemptsPerSection;
    private final double extraRandomTickFractionalAttemptsPerSection;
    private final int coveredChunkCount;
    private final LongOpenHashSet coveredChunks;
    private final WorldSleepAccelerationGovernorAction governorAction;
    private final boolean temporarilySuppressed;

    public WorldSleepAccelerationModuleStatus(boolean active,
                                              int configuredRadiusChunks,
                                              int effectiveRadiusChunks,
                                              int configuredSpeedPercent,
                                              int effectiveSpeedPercent,
                                              double effectiveTickMultiplier,
                                              double extraRandomTickAttemptsPerSection,
                                              int extraRandomTickWholeAttemptsPerSection,
                                              double extraRandomTickFractionalAttemptsPerSection,
                                              int coveredChunkCount,
                                              LongOpenHashSet coveredChunks,
                                              WorldSleepAccelerationGovernorAction governorAction,
                                              boolean temporarilySuppressed) {
        this.active = active;
        this.configuredRadiusChunks = configuredRadiusChunks;
        this.effectiveRadiusChunks = effectiveRadiusChunks;
        this.configuredSpeedPercent = configuredSpeedPercent;
        this.effectiveSpeedPercent = effectiveSpeedPercent;
        this.effectiveTickMultiplier = effectiveTickMultiplier;
        this.extraRandomTickAttemptsPerSection = extraRandomTickAttemptsPerSection;
        this.extraRandomTickWholeAttemptsPerSection = extraRandomTickWholeAttemptsPerSection;
        this.extraRandomTickFractionalAttemptsPerSection = extraRandomTickFractionalAttemptsPerSection;
        this.coveredChunkCount = coveredChunkCount;
        this.coveredChunks = coveredChunks;
        this.governorAction = governorAction;
        this.temporarilySuppressed = temporarilySuppressed;
    }

    public boolean isActive() {
        return active;
    }

    public int getConfiguredRadiusChunks() {
        return configuredRadiusChunks;
    }

    public int getEffectiveRadiusChunks() {
        return effectiveRadiusChunks;
    }

    public int getConfiguredSpeedPercent() {
        return configuredSpeedPercent;
    }

    public int getEffectiveSpeedPercent() {
        return effectiveSpeedPercent;
    }

    public double getEffectiveTickMultiplier() {
        return effectiveTickMultiplier;
    }

    public double getExtraRandomTickAttemptsPerSection() {
        return extraRandomTickAttemptsPerSection;
    }

    public int getExtraRandomTickWholeAttemptsPerSection() {
        return extraRandomTickWholeAttemptsPerSection;
    }

    public double getExtraRandomTickFractionalAttemptsPerSection() {
        return extraRandomTickFractionalAttemptsPerSection;
    }

    public int getCoveredChunkCount() {
        return coveredChunkCount;
    }

    public WorldSleepAccelerationGovernorAction getGovernorAction() {
        return governorAction;
    }

    public boolean isTemporarilySuppressed() {
        return temporarilySuppressed;
    }

    public boolean coversChunk(long chunkPos) {
        return active && coveredChunks.contains(chunkPos);
    }
}
