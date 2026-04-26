package net.aqualoco.sec.client.sleepindicator.biomeclock;

import net.aqualoco.sec.client.sleepindicator.SleepIndicatorContext;

// Centralizes biome and vanilla weather rules for the clock's visual weather.
public final class BiomeClockWeatherResolver {
    public static final float WEATHER_KIND_THRESHOLD = 0.05F;
    public static final float WEATHER_THUNDER_THRESHOLD = 0.15F;

    private BiomeClockWeatherResolver() {
    }

    public static BiomeClockWeatherKind resolve(SleepIndicatorContext context) {
        if (context == null) {
            return BiomeClockWeatherKind.CLEAR;
        }

        float rainLevel = sanitize(context.rainLevel());
        float thunderLevel = sanitize(context.thunderLevel());
        if (rainLevel <= WEATHER_KIND_THRESHOLD) {
            return BiomeClockWeatherKind.CLEAR;
        }
        boolean thundering = thunderLevel > WEATHER_THUNDER_THRESHOLD;

        if (context.biomeClockCategory() == BiomeClockCategory.SNOW) {
            return thundering ? BiomeClockWeatherKind.SNOW_THUNDER : BiomeClockWeatherKind.SNOW;
        }

        if (thundering) {
            return BiomeClockWeatherKind.THUNDER;
        }

        return BiomeClockWeatherKind.RAIN;
    }

    private static float sanitize(float value) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            return 0.0F;
        }
        if (value >= 1.0F) {
            return 1.0F;
        }
        return value;
    }
}
