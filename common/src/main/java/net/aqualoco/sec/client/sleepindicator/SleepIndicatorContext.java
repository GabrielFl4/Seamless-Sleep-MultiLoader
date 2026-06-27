package net.aqualoco.sec.client.sleepindicator;

import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockBiomeResolver;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockCategory;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.aqualoco.sec.sleep.SleepAnimationVisualContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;

// Carries the sampled client-side data that every sleep indicator renderer needs.
public record SleepIndicatorContext(
        Minecraft client,
        ClientLevel level,
        LocalPlayer player,
        float tickDelta,
        long visualDayTime,
        float normalizedDayTime,
        int moonPhase,
        float sunAngleRadians,
        float moonAngleRadians,
        float starBrightness,
        int skyColor,
        int cloudColor,
        int sunriseColor,
        float rainLevel,
        float thunderLevel,
        boolean raining,
        boolean thundering,
        String rawBiomeId,
        BiomeClockCategory biomeClockCategory,
        float sleepProgress,
        float sleepDayTimeSpeedPerTick,
        float alpha,
        boolean sleepAnimationActive,
        boolean startedDuringDay,
        SleepAnimationVisualContext visualContext
) {
    private static final long DAY_TICKS = 24000L;

    public static SleepIndicatorContext create(
            Minecraft client,
            ClientLevel level,
            LocalPlayer player,
            ClientSleepAnimationState sleepAnimation,
            float tickDelta
    ) {
        long visualDayTime = level.getDayTime();
        long wrappedDayTime = Math.floorMod(visualDayTime, DAY_TICKS);
        float normalizedDayTime = wrappedDayTime / (float) DAY_TICKS;
        boolean animationActive = sleepAnimation != null && sleepAnimation.isVisualOverlayActive();
        float rainLevel = level.getRainLevel(tickDelta);
        float thunderLevel = level.getThunderLevel(tickDelta);
        Camera camera = client.gameRenderer == null ? null : client.gameRenderer.getMainCamera();

        int skyColor = sampleSkyColor(level, camera, player, tickDelta);
        int cloudColor = sampleCloudColor(level, camera, player, tickDelta);
        int sunriseColor = sampleSunriseColor(level, camera, player, tickDelta);
        float sunAngleRadians = sampleAngleRadians(level, camera, player, tickDelta, true);
        float moonAngleRadians = sampleAngleRadians(level, camera, player, tickDelta, false);
        float starBrightness = sampleStarBrightness(level, camera, player, tickDelta);
        int moonPhase = sampleMoonPhase(level, tickDelta);
        BiomeClockBiomeResolver.ResolvedBiome resolvedBiome = BiomeClockBiomeResolver.resolve(level, player);

        return new SleepIndicatorContext(
                client,
                level,
                player,
                tickDelta,
                visualDayTime,
                normalizedDayTime,
                moonPhase,
                sunAngleRadians,
                moonAngleRadians,
                starBrightness,
                skyColor,
                cloudColor,
                sunriseColor,
                rainLevel,
                thunderLevel,
                level.isRaining(),
                level.isThundering(),
                resolvedBiome.rawBiomeId(),
                resolvedBiome.category(),
                animationActive ? (float) sleepAnimation.getProgress() : 0.0F,
                animationActive ? (float) sleepAnimation.getCurrentDayTimeSpeedPerTick() : 0.0F,
                1.0F,
                animationActive,
                sleepAnimation != null && sleepAnimation.startedDuringDay(),
                sleepAnimation != null ? sleepAnimation.getVisualContext() : SleepAnimationVisualContext.NIGHT
        );
    }

    public SleepIndicatorContext withAlphaMultiplier(float alphaMultiplier) {
        float multipliedAlpha = clamp01(this.alpha * clamp01(alphaMultiplier));
        return new SleepIndicatorContext(
                this.client,
                this.level,
                this.player,
                this.tickDelta,
                this.visualDayTime,
                this.normalizedDayTime,
                this.moonPhase,
                this.sunAngleRadians,
                this.moonAngleRadians,
                this.starBrightness,
                this.skyColor,
                this.cloudColor,
                this.sunriseColor,
                this.rainLevel,
                this.thunderLevel,
                this.raining,
                this.thundering,
                this.rawBiomeId,
                this.biomeClockCategory,
                this.sleepProgress,
                this.sleepDayTimeSpeedPerTick,
                multipliedAlpha,
                this.sleepAnimationActive,
                this.startedDuringDay,
                this.visualContext
        );
    }

    private static int sampleSkyColor(ClientLevel level, Camera camera, LocalPlayer player, float tickDelta) {
        return ARGB.opaque(level.getSkyColor(player.position(), tickDelta));
    }

    private static int sampleCloudColor(ClientLevel level, Camera camera, LocalPlayer player, float tickDelta) {
        return level.getCloudColor(tickDelta);
    }

    private static int sampleSunriseColor(ClientLevel level, Camera camera, LocalPlayer player, float tickDelta) {
        float timeOfDay = level.getTimeOfDay(tickDelta);
        if (!level.effects().isSunriseOrSunset(timeOfDay)) {
            return 0;
        }
        return level.effects().getSunriseOrSunsetColor(timeOfDay);
    }

    private static float sampleAngleRadians(
            ClientLevel level,
            Camera camera,
            LocalPlayer player,
            float tickDelta,
            boolean sunAngle
    ) {
        float sunRadians = level.getSunAngle(tickDelta);
        return sunAngle ? sunRadians : sunRadians + (float) Math.PI;
    }

    private static float sampleStarBrightness(ClientLevel level, Camera camera, LocalPlayer player, float tickDelta) {
        return level.getStarBrightness(tickDelta);
    }

    private static int sampleMoonPhase(ClientLevel level, float tickDelta) {
        return level.getMoonPhase();
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            return 0.0F;
        }
        if (value >= 1.0F) {
            return 1.0F;
        }
        return value;
    }
}
