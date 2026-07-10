package net.aqualoco.sec.acceleration;

public final class WorldSleepAccelerationGovernorSnapshot {
    public static final WorldSleepAccelerationGovernorSnapshot INACTIVE =
            new WorldSleepAccelerationGovernorSnapshot(
                    false,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    1.0D,
                    0.0D,
                    0.0D
            );

    private final boolean active;
    private final double averageMsptPressure;
    private final double p95MsptPressure;
    private final double healthPressure;
    private final double activePlayerRiskBonus;
    private final double simulationDistanceRiskBonus;
    private final double candidateAreaRiskBonus;
    private final double worldSleepRateRiskBonus;
    private final double riskFactor;
    private final double rawPressure;
    private final double smoothedPressure;

    public WorldSleepAccelerationGovernorSnapshot(boolean active,
                                                  double averageMsptPressure,
                                                  double p95MsptPressure,
                                                  double healthPressure,
                                                  double activePlayerRiskBonus,
                                                  double simulationDistanceRiskBonus,
                                                  double candidateAreaRiskBonus,
                                                  double worldSleepRateRiskBonus,
                                                  double riskFactor,
                                                  double rawPressure,
                                                  double smoothedPressure) {
        this.active = active;
        this.averageMsptPressure = averageMsptPressure;
        this.p95MsptPressure = p95MsptPressure;
        this.healthPressure = healthPressure;
        this.activePlayerRiskBonus = activePlayerRiskBonus;
        this.simulationDistanceRiskBonus = simulationDistanceRiskBonus;
        this.candidateAreaRiskBonus = candidateAreaRiskBonus;
        this.worldSleepRateRiskBonus = worldSleepRateRiskBonus;
        this.riskFactor = riskFactor;
        this.rawPressure = rawPressure;
        this.smoothedPressure = smoothedPressure;
    }

    public boolean isActive() {
        return active;
    }

    public double getAverageMsptPressure() {
        return averageMsptPressure;
    }

    public double getP95MsptPressure() {
        return p95MsptPressure;
    }

    public double getHealthPressure() {
        return healthPressure;
    }

    public double getActivePlayerRiskBonus() {
        return activePlayerRiskBonus;
    }

    public double getSimulationDistanceRiskBonus() {
        return simulationDistanceRiskBonus;
    }

    public double getCandidateAreaRiskBonus() {
        return candidateAreaRiskBonus;
    }

    public double getWorldSleepRateRiskBonus() {
        return worldSleepRateRiskBonus;
    }

    public double getRiskFactor() {
        return riskFactor;
    }

    public double getRawPressure() {
        return rawPressure;
    }

    public double getSmoothedPressure() {
        return smoothedPressure;
    }
}
