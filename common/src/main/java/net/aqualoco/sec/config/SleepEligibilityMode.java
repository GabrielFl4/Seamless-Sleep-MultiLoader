package net.aqualoco.sec.config;

public enum SleepEligibilityMode {
    INSOMNIA,
    VANILLA,
    DAY_INCLUDED,
    ALWAYS;

    public boolean allowsDaySleep() {
        return this == DAY_INCLUDED || this == ALWAYS;
    }

    public boolean ignoresMonsterCheck() {
        return this == ALWAYS;
    }

    public boolean preventsSleepSkip() {
        return this == INSOMNIA;
    }
}
