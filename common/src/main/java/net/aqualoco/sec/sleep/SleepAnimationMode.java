package net.aqualoco.sec.sleep;

public enum SleepAnimationMode {
    NORMAL_SLEEP(true, true, true, true, true, false),
    MADE_IN_HEAVEN_BED(true, true, false, true, true, true),
    COMMAND_TIMELAPSE(false, false, false, false, false, false);

    private final boolean requiresSleepers;
    private final boolean wakesPlayersOnFinish;
    private final boolean resetsWeatherOnFinish;
    private final boolean allowsWorldAcceleration;
    private final boolean bedSleepMode;
    private final boolean openEnded;

    SleepAnimationMode(boolean requiresSleepers,
                       boolean wakesPlayersOnFinish,
                       boolean resetsWeatherOnFinish,
                       boolean allowsWorldAcceleration,
                       boolean bedSleepMode,
                       boolean openEnded) {
        this.requiresSleepers = requiresSleepers;
        this.wakesPlayersOnFinish = wakesPlayersOnFinish;
        this.resetsWeatherOnFinish = resetsWeatherOnFinish;
        this.allowsWorldAcceleration = allowsWorldAcceleration;
        this.bedSleepMode = bedSleepMode;
        this.openEnded = openEnded;
    }

    public boolean requiresSleepers() {
        return this.requiresSleepers;
    }

    public boolean wakesPlayersOnFinish() {
        return this.wakesPlayersOnFinish;
    }

    public boolean resetsWeatherOnFinish() {
        return this.resetsWeatherOnFinish;
    }

    public boolean allowsWorldAcceleration() {
        return this.allowsWorldAcceleration;
    }

    public boolean isBedSleepMode() {
        return this.bedSleepMode;
    }

    public boolean isOpenEnded() {
        return this.openEnded;
    }
}
