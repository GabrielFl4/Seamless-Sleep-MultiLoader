package net.aqualoco.sec.client.sleepindicator.biomeclock;

// Names the visual weather families used by the biome clock overlay.
public enum BiomeClockWeatherKind {
    CLEAR,
    RAIN,
    THUNDER,
    SNOW,
    SNOW_THUNDER;

    public boolean hasVisualWeather() {
        return this != CLEAR;
    }

    public boolean usesThunderClouds() {
        return this == THUNDER || this == SNOW_THUNDER;
    }

    public boolean usesSnowPrecipitation() {
        return this == SNOW || this == SNOW_THUNDER;
    }

    public boolean usesThunderPrecipitation() {
        return this == THUNDER;
    }

    public boolean allowsLightning() {
        return this == THUNDER || this == SNOW_THUNDER;
    }
}
