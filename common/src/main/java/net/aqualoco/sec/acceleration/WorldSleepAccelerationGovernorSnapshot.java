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
                    1.0D,
                    1.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );

    private final boolean active;
    private final double averageMsptPressure;
    private final double p95MsptPressure;
    private final double performancePressure;
    private final double activePlayerRiskBonus;
    private final double simulationDistanceRiskBonus;
    private final double worldSleepRateRiskBonus;
    private final double riskFactor;
    private final double aggressivenessMultiplier;
    private final double pressure;
    private final double areaStageOne;
    private final double intensityStage;
    private final double areaStageTwo;

    public WorldSleepAccelerationGovernorSnapshot(boolean active,
                                                  double averageMsptPressure,
                                                  double p95MsptPressure,
                                                  double performancePressure,
                                                  double activePlayerRiskBonus,
                                                  double simulationDistanceRiskBonus,
                                                  double worldSleepRateRiskBonus,
                                                  double riskFactor,
                                                  double aggressivenessMultiplier,
                                                  double pressure,
                                                  double areaStageOne,
                                                  double intensityStage,
                                                  double areaStageTwo) {
        this.active = active;
        this.averageMsptPressure = averageMsptPressure;
        this.p95MsptPressure = p95MsptPressure;
        this.performancePressure = performancePressure;
        this.activePlayerRiskBonus = activePlayerRiskBonus;
        this.simulationDistanceRiskBonus = simulationDistanceRiskBonus;
        this.worldSleepRateRiskBonus = worldSleepRateRiskBonus;
        this.riskFactor = riskFactor;
        this.aggressivenessMultiplier = aggressivenessMultiplier;
        this.pressure = pressure;
        this.areaStageOne = areaStageOne;
        this.intensityStage = intensityStage;
        this.areaStageTwo = areaStageTwo;
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

    public double getPerformancePressure() {
        return performancePressure;
    }

    public double getActivePlayerRiskBonus() {
        return activePlayerRiskBonus;
    }

    public double getSimulationDistanceRiskBonus() {
        return simulationDistanceRiskBonus;
    }

    public double getWorldSleepRateRiskBonus() {
        return worldSleepRateRiskBonus;
    }

    public double getRiskFactor() {
        return riskFactor;
    }

    public double getAggressivenessMultiplier() {
        return aggressivenessMultiplier;
    }

    public double getPressure() {
        return pressure;
    }

    public double getAreaStageOne() {
        return areaStageOne;
    }

    public double getIntensityStage() {
        return intensityStage;
    }

    public double getAreaStageTwo() {
        return areaStageTwo;
    }
}
