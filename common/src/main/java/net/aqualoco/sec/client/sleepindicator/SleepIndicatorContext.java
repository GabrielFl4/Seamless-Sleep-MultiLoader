package net.aqualoco.sec.client.sleepindicator;

import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockBiomeResolver;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockCategory;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;

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
        boolean startedDuringDay
) {
    private static final long DAY_TICKS = 24000L;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

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
        boolean animationActive = sleepAnimation != null && sleepAnimation.isActive();
        float rainLevel = level.getRainLevel(tickDelta);
        float thunderLevel = level.getThunderLevel(tickDelta);
        Camera camera = client.gameRenderer == null ? null : client.gameRenderer.getMainCamera();

        int skyColor = sampleSkyColor(level, camera, player, tickDelta);
        int cloudColor = sampleCloudColor(level, camera, player, tickDelta);
        int sunriseColor = sampleSunriseColor(level, camera, player, tickDelta);
        float sunAngleRadians = sampleAngleRadians(level, camera, player, tickDelta, true);
        float moonAngleRadians = sampleAngleRadians(level, camera, player, tickDelta, false);
        float starBrightness = sampleStarBrightness(level, camera, player, tickDelta);
        MoonPhase moonPhase = sampleMoonPhase(level, camera, player, tickDelta);
        BiomeClockBiomeResolver.ResolvedBiome resolvedBiome = BiomeClockBiomeResolver.resolve(level, player);

        return new SleepIndicatorContext(
                client,
                level,
                player,
                tickDelta,
                visualDayTime,
                normalizedDayTime,
                moonPhase.index(),
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
                sleepAnimation != null && sleepAnimation.startedDuringDay()
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
                this.startedDuringDay
        );
    }

    private static int sampleSkyColor(ClientLevel level, Camera camera, LocalPlayer player, float tickDelta) {
        if (camera != null) {
            return ARGB.opaque(camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, tickDelta));
        }
        return ARGB.opaque(level.environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, player.position()));
    }

    private static int sampleCloudColor(ClientLevel level, Camera camera, LocalPlayer player, float tickDelta) {
        if (camera != null) {
            return camera.attributeProbe().getValue(EnvironmentAttributes.CLOUD_COLOR, tickDelta);
        }
        return level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_COLOR, player.position());
    }

    private static int sampleSunriseColor(ClientLevel level, Camera camera, LocalPlayer player, float tickDelta) {
        if (camera != null) {
            return camera.attributeProbe().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, tickDelta);
        }
        return level.environmentAttributes().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, player.position());
    }

    private static float sampleAngleRadians(
            ClientLevel level,
            Camera camera,
            LocalPlayer player,
            float tickDelta,
            boolean sunAngle
    ) {
        float angleDegrees;
        if (camera != null) {
            angleDegrees = camera.attributeProbe().getValue(
                    sunAngle ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE,
                    tickDelta
            );
        } else {
            angleDegrees = level.environmentAttributes().getValue(
                    sunAngle ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE,
                    player.position()
            );
        }
        return angleDegrees * DEG_TO_RAD;
    }

    private static float sampleStarBrightness(ClientLevel level, Camera camera, LocalPlayer player, float tickDelta) {
        if (camera != null) {
            return camera.attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, tickDelta);
        }
        return level.environmentAttributes().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, player.position());
    }

    private static MoonPhase sampleMoonPhase(ClientLevel level, Camera camera, LocalPlayer player, float tickDelta) {
        if (camera != null) {
            return camera.attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, tickDelta);
        }
        return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, player.position());
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
