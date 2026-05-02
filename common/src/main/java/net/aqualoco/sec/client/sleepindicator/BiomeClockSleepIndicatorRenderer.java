package net.aqualoco.sec.client.sleepindicator;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockCategory;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockLightningSignal;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockLightningState;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockTransitionState;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockWeatherKind;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockWeatherResolver;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockWeatherVisualState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

// Draws the circular biome clock using client sky, weather, and sleep animation data.
public final class BiomeClockSleepIndicatorRenderer implements SleepIndicatorRenderer {
    private static final int CLOCK_SIZE = 66;
    private static final int CLOUD_TEXTURE_WIDTH = 462;
    private static final int CLOUD_TEXTURE_HEIGHT = 66;
    private static final int CLOUD_LOOP_DISTANCE = CLOUD_TEXTURE_WIDTH - CLOCK_SIZE;
    private static final int SUN_SIZE = 28;
    private static final int MOON_SIZE = 20;
    private static final int CELESTIAL_TEXTURE_SIZE = 36;
    private static final int GLOW_WIDTH = 132;
    private static final int GLOW_HEIGHT = 66;
    private static final int CELESTIAL_HORIZON_CUTOFF_Y = 46;
    private static final int WEATHER_CLOUD_WIDTH = 132;
    private static final int WEATHER_CLOUD_HEIGHT = 66;
    private static final int LEFT_WEATHER_CLOUD_OUT_X = -100;
    private static final int LEFT_WEATHER_CLOUD_IN_X = -68;
    private static final int RIGHT_WEATHER_CLOUD_OUT_X = 34;
    private static final int RIGHT_WEATHER_CLOUD_IN_X = 2;
    private static final int WEATHER_CLOUD_BASE_Y = 0;
    private static final int SKY_LIGHT_Y_OFFSET = -2;

    private static final double CLIP_CENTER_X = 33.0D;
    private static final double CLIP_CENTER_Y = 33.0D;
    private static final double CLIP_RADIUS = 31.0D;

    private static final float ORBIT_CENTER_X = 33.0F;
    private static final float ORBIT_CENTER_Y = 47.0F;
    private static final float ORBIT_RADIUS_X = 24.0F;
    private static final float ORBIT_RADIUS_Y = 28.0F;

    private static final float CLOUD_BASE_SPEED_PX_PER_TICK = 0.015F;
    private static final float CLOUD_SLEEP_SPEED_FACTOR = 0.025F;
    private static final float CLOUD_ABOVE_BASE_SPEED_PX_PER_TICK = 0.024F;
    private static final float CLOUD_ABOVE_SLEEP_SPEED_FACTOR = 0.018F;
    private static final float CLOUD_ABOVE_ALPHA = 0.55F;
    private static final float CLOUD_SPEED_LIMIT = 8.0F;

    private static final double STAR_ANIMATION_FPS = 6.0D;
    private static final float STAR_RAIN_VISIBILITY = 0.35F;
    private static final float STAR_THUNDER_VISIBILITY = 0.08F;
    private static final double RAIN_ANIMATION_FPS = 9.0D;
    private static final double THUNDER_RAIN_ANIMATION_FPS = 11.0D;
    private static final double SNOW_ANIMATION_FPS = 6.0D;
    private static final double SANDSTORM_ANIMATION_FPS = RAIN_ANIMATION_FPS;
    private static final double ZZZ_ANIMATION_FPS = 4.0D;
    private static final long ZZZ_FADE_NANOS = 120_000_000L;
    private static final float ZZZ_SKIP_SPEED_THRESHOLD = 1.0F;
    private static final float SANDSTORM_RAIN_ALPHA_MULTIPLIER = 0.60F;
    private static final float SANDSTORM_THUNDER_ALPHA_MULTIPLIER = 1.0F;
    private static final float SANDSTORM_NIGHT_ALPHA_REDUCTION = 0.25F;
    private static final float PRECIPITATION_DARKEN_TINT_STRENGTH = 0.55F;
    private static final float PRECIPITATION_THUNDER_DARKEN_BOOST = 0.45F;
    private static final float PRECIPITATION_SANDSTORM_NIGHT_DARKEN_BOOST = 0.35F;
    private static final float PRECIPITATION_DARKEN_OVERLAY_MULTIPLIER = 0.55F;
    private static final float PRECIPITATION_DARKEN_MAX_FACTOR = 0.82F;

    private static final float SKY_TOP_DARKEN = 0.82F;
    private static final float SKY_HORIZON_LIGHTEN = 0.12F;
    private static final float SKY_SUNRISE_TINT = 0.28F;
    private static final float SKY_LIGHT_DAY_ALPHA = 0.90F;
    private static final float SKY_LIGHT_NIGHT_ALPHA_MULTIPLIER = 0.30F;
    private static final float SUNRISE_EXTENSION_RANGE_PX = 14.0F;
    private static final float SUNRISE_EXTENSION_MAX_ALPHA = 0.22F;
    private static final int SUNRISE_FALLBACK_TINT = 0xFFFFB068;

    // Presentation values when the biome layer needs stronger or softer weather/time shading.
    private static final float BIOME_DARKEN_RAIN_ALPHA = 0.08F;
    private static final float BIOME_DARKEN_THUNDER_ALPHA = 0.24F;
    private static final float BIOME_DARKEN_NIGHT_ALPHA = 0.36F;
    private static final float BIOME_DARKEN_MAX_ALPHA = BIOME_DARKEN_NIGHT_ALPHA + BIOME_DARKEN_THUNDER_ALPHA;
    private static final int BIOME_DARKEN_TINT_RGB = 0x1B2230;
    private static final float CELESTIAL_THUNDER_MIN_BRIGHTNESS = 0.76F;
    private static final float LIGHTNING_FLASH_DARKENING_RELIEF = 0.87F;

    private static final int HORIZON_LEFT_X = 6;
    private static final int HORIZON_RIGHT_X = 60;
    private static final int HORIZON_GLOW_Y = 9;
    private static final float GLOW_ALPHA_MULTIPLIER = 0.92F;
    private static final float GLOW_WEATHER_ATTENUATION = 0.85F;

    private static final Identifier CLOUDS = texture("clouds.png");
    private static final Identifier SKY_LIGHT = texture("sky_light.png");
    private static final Identifier CLOUDS_ABOVE = texture("clouds_above.png");
    private static final Identifier SUN = texture("sun.png");
    private static final Identifier SUN_GLOW = texture("sun_glow.png");
    private static final Identifier FRAME_CIRCLE = texture("frame/circle.png");
    private static final Identifier WEATHER_CLOUDS_RAIN = texture("weather/clouds_rain.png");
    private static final Identifier WEATHER_CLOUDS_THUNDERSTORM = texture("weather/clouds_thunderstorm.png");
    private static final Identifier[] STARS = new Identifier[] {
            texture("stars/stars_1.png"),
            texture("stars/stars_2.png"),
            texture("stars/stars_3.png"),
            texture("stars/stars_4.png"),
            texture("stars/stars_5.png"),
            texture("stars/stars_6.png"),
            texture("stars/stars_7.png"),
            texture("stars/stars_8.png")
    };
    private static final Identifier[] ZZZ = new Identifier[] {
            texture("zzz/zzz_1.png"),
            texture("zzz/zzz_2.png"),
            texture("zzz/zzz_3.png")
    };
    private static final Identifier[] RAIN = new Identifier[] {
            texture("weather/rain/rain_1.png"),
            texture("weather/rain/rain_2.png"),
            texture("weather/rain/rain_3.png")
    };
    private static final Identifier[] THUNDERSTORM_RAIN = new Identifier[] {
            texture("weather/thunderstorm/thunderstorm_1.png"),
            texture("weather/thunderstorm/thunderstorm_2.png"),
            texture("weather/thunderstorm/thunderstorm_3.png")
    };
    private static final Identifier[] SANDSTORM = new Identifier[] {
            texture("weather/sandstorm/sand_1.png"),
            texture("weather/sandstorm/sand_2.png"),
            texture("weather/sandstorm/sand_3.png"),
            texture("weather/sandstorm/sand_4.png"),
            texture("weather/sandstorm/sand_5.png"),
            texture("weather/sandstorm/sand_6.png"),
            texture("weather/sandstorm/sand_7.png"),
            texture("weather/sandstorm/sand_8.png"),
            texture("weather/sandstorm/sand_9.png"),
            texture("weather/sandstorm/sand_10.png")
    };
    private static final Identifier[] SNOW = new Identifier[] {
            texture("weather/snow/snow_1.png"),
            texture("weather/snow/snow_2.png"),
            texture("weather/snow/snow_3.png"),
            texture("weather/snow/snow_4.png"),
            texture("weather/snow/snow_5.png"),
            texture("weather/snow/snow_6.png"),
            texture("weather/snow/snow_7.png"),
            texture("weather/snow/snow_8.png"),
            texture("weather/snow/snow_9.png"),
            texture("weather/snow/snow_10.png")
    };
    private static final Identifier[] LIGHTNING = new Identifier[] {
            texture("weather/lightning/lightning_1.png"),
            texture("weather/lightning/lightning_2.png")
    };
    private static final Map<BiomeClockCategory, Identifier> BIOME_TEXTURES = createBiomeTextures();
    private static final Identifier[] MOONS = new Identifier[] {
            texture("moons/moon_0.png"),
            texture("moons/moon_1.png"),
            texture("moons/moon_2.png"),
            texture("moons/moon_3.png"),
            texture("moons/moon_4.png"),
            texture("moons/moon_5.png"),
            texture("moons/moon_6.png"),
            texture("moons/moon_7.png")
    };

    private float cloudPhasePx;
    private long lastCloudUpdateNanos = -1L;
    private float cloudAbovePhasePx;
    private long lastCloudAboveUpdateNanos = -1L;
    private float zzzPresence;
    private long lastZzzPresenceUpdateNanos = -1L;
    private final BiomeClockTransitionState biomeTransition = new BiomeClockTransitionState();
    private final BiomeClockWeatherVisualState weatherVisualState = new BiomeClockWeatherVisualState();
    private final BiomeClockLightningState lightningState = new BiomeClockLightningState();

    @Override
    public String id() {
        return "biome_clock";
    }

    @Override
    public int width() {
        return CLOCK_SIZE;
    }

    @Override
    public int height() {
        return CLOCK_SIZE;
    }

    @Override
    public void render(GuiGraphics graphics, SleepIndicatorContext context, float tickDelta) {
        long nowNanos = System.nanoTime();
        BiomeClockWeatherKind weatherKind = BiomeClockWeatherResolver.resolve(context);
        this.weatherVisualState.update(weatherKind, nowNanos);

        BiomeClockWeatherKind visualWeatherKind = this.weatherVisualState.currentKind();
        boolean lightningWeatherAllowed = visualWeatherKind.allowsLightning()
                || (visualWeatherKind.hasVisualWeather()
                && context.rainLevel() > BiomeClockWeatherResolver.WEATHER_KIND_THRESHOLD);
        BiomeClockLightningSignal.Signal lightningSignal = BiomeClockLightningSignal.latest();
        this.lightningState.onBiomeReliefSignal(lightningSignal, nowNanos);
        this.lightningState.onGameLightningSignal(
                lightningSignal,
                lightningWeatherAllowed,
                this.weatherVisualState.readyForLightning(),
                nowNanos
        );
        int lightningFrame = this.lightningState.updateAndGetFrame(nowNanos);
        float lightningFlashFactor = this.lightningState.flashFactor(nowNanos);

        renderSkyFromClient(graphics, context);
        renderSkyLight(graphics, context);
        renderStars(graphics, context, nowNanos);

        int cloudOffset = updateCloudPhase(context, nowNanos);
        int cloudColor = ARGB.multiplyAlpha(context.cloudColor(), context.alpha());
        drawCircularTexture(
                graphics,
                CLOUDS,
                0,
                0,
                cloudOffset,
                0,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOUD_TEXTURE_WIDTH,
                CLOUD_TEXTURE_HEIGHT,
                cloudColor
        );

        int glowColor = computeGlowColor(context);
        if ((glowColor >>> 24) > 0) {
            int glowX = sunScreenX(context.sunAngleRadians()) < CLIP_CENTER_X ? HORIZON_LEFT_X : HORIZON_RIGHT_X;
            drawCircularTexture(
                    graphics,
                    SUN_GLOW,
                    glowX - GLOW_WIDTH / 2,
                    HORIZON_GLOW_Y,
                    0,
                    0,
                    GLOW_WIDTH,
                    GLOW_HEIGHT,
                    GLOW_WIDTH,
                    GLOW_HEIGHT,
                    glowColor
            );
        }

        OrbitPosition sunOrbit = orbitPosition(context.sunAngleRadians(), SUN_SIZE);
        OrbitPosition moonOrbit = orbitPosition(context.moonAngleRadians(), MOON_SIZE);
        int textureAlphaColor = whiteWithAlpha(context.alpha());
        int celestialColor = celestialTintColor(context);
        drawCircularTextureScaled(
                graphics,
                SUN,
                sunOrbit.x(),
                sunOrbit.y(),
                SUN_SIZE,
                SUN_SIZE,
                CELESTIAL_TEXTURE_SIZE,
                CELESTIAL_TEXTURE_SIZE,
                CELESTIAL_HORIZON_CUTOFF_Y,
                celestialColor
        );
        drawCircularTextureScaled(
                graphics,
                MOONS[context.moonPhase() & 7],
                moonOrbit.x(),
                moonOrbit.y(),
                MOON_SIZE,
                MOON_SIZE,
                CELESTIAL_TEXTURE_SIZE,
                CELESTIAL_TEXTURE_SIZE,
                CELESTIAL_HORIZON_CUTOFF_Y,
                celestialColor
        );

        int cloudAboveOffset = updateCloudAbovePhase(context, nowNanos);
        int cloudAboveColor = ARGB.multiplyAlpha(
                context.cloudColor(),
                context.alpha() * computeCloudAboveAlpha(context)
        );
        drawCircularTexture(
                graphics,
                CLOUDS_ABOVE,
                0,
                0,
                cloudAboveOffset,
                0,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOUD_TEXTURE_WIDTH,
                CLOUD_TEXTURE_HEIGHT,
                cloudAboveColor
        );

        this.biomeTransition.update(context.biomeClockCategory(), transitionTimeMs());
        float biomeDarkeningAlpha = computeBiomeDarkeningAlpha(context, lightningFlashFactor);
        renderBiomeLayer(graphics, context, biomeDarkeningAlpha);
        renderPrecipitation(graphics, context, visualWeatherKind, nowNanos, biomeDarkeningAlpha);
        renderWeatherClouds(graphics, context, visualWeatherKind);
        renderLightning(graphics, context, lightningFrame);
        drawFullTexture(graphics, FRAME_CIRCLE, textureAlphaColor);
        renderZzzLayer(graphics, context, nowNanos);
    }

    private void renderSkyFromClient(GuiGraphics graphics, SleepIndicatorContext context) {
        int skyBase = ARGB.opaque(context.skyColor());
        int topColor = ARGB.scaleRGB(skyBase, SKY_TOP_DARKEN);
        int horizonColor = ARGB.srgbLerp(SKY_HORIZON_LIGHTEN, skyBase, ARGB.color(255, 255, 255));
        int sunriseColor = resolveSunriseTintColor(context);
        float sunriseBlend = computeSunriseEffectAlpha(context) * SKY_SUNRISE_TINT;
        if (sunriseBlend > 0.0F) {
            horizonColor = ARGB.srgbLerp(Mth.clamp(sunriseBlend, 0.0F, 1.0F), horizonColor, sunriseColor);
        }
        final int finalTopColor = topColor;
        final int finalHorizonColor = horizonColor;

        forEachCircularSlice((sliceX, sliceY, sliceWidth, ignoredU, ignoredV) -> {
            float rowProgress = sliceY / (float) Math.max(1, CLOCK_SIZE - 1);
            int rowColor = ARGB.srgbLerp(rowProgress, finalTopColor, finalHorizonColor);
            graphics.fill(sliceX, sliceY, sliceX + sliceWidth, sliceY + 1, ARGB.multiplyAlpha(rowColor, context.alpha()));
        }, 0, 0, 0, 0, CLOCK_SIZE, CLOCK_SIZE);
    }

    private void renderSkyLight(GuiGraphics graphics, SleepIndicatorContext context) {
        float nightFactor = computeNightFactor(context.normalizedDayTime());
        float alphaMultiplier = Mth.lerp(nightFactor, SKY_LIGHT_DAY_ALPHA, SKY_LIGHT_NIGHT_ALPHA_MULTIPLIER);
        drawCircularTexture(
                graphics,
                SKY_LIGHT,
                0,
                SKY_LIGHT_Y_OFFSET,
                0,
                0,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOCK_SIZE,
                whiteWithAlpha(context.alpha() * alphaMultiplier)
        );
    }

    private void renderStars(GuiGraphics graphics, SleepIndicatorContext context, long nowNanos) {
        float nightAlpha = smoothstepRange(0.02F, 0.55F, context.starBrightness());
        float rainDim = Mth.lerp(Mth.clamp(context.rainLevel(), 0.0F, 1.0F), 1.0F, STAR_RAIN_VISIBILITY);
        float thunderDim = Mth.lerp(Mth.clamp(context.thunderLevel(), 0.0F, 1.0F), 1.0F, STAR_THUNDER_VISIBILITY);
        float finalAlpha = nightAlpha * Math.min(rainDim, thunderDim) * context.alpha();
        if (finalAlpha <= 0.001F) {
            return;
        }

        int frameIndex = animationFrame(nowNanos, STAR_ANIMATION_FPS, STARS.length);
        drawCircularFullTexture(graphics, STARS[frameIndex], whiteWithAlpha(finalAlpha));
    }

    private static float computeCloudAboveAlpha(SleepIndicatorContext context) {
        float weatherLift = Mth.clamp(Math.max(context.rainLevel(), context.thunderLevel()) * 0.10F, 0.0F, 0.10F);
        return Mth.clamp(CLOUD_ABOVE_ALPHA + weatherLift, 0.0F, 0.65F);
    }

    private void renderPrecipitation(
            GuiGraphics graphics,
            SleepIndicatorContext context,
            BiomeClockWeatherKind weatherKind,
            long nowNanos,
            float biomeDarkeningAlpha
    ) {
        if (!weatherKind.hasVisualWeather()) {
            return;
        }

        Identifier[] frames;
        double fps;
        float alphaMultiplier = 1.0F;
        BiomeClockCategory category = context.biomeClockCategory();
        if (category == BiomeClockCategory.SAVANNA) {
            return;
        }
        if (usesSandstormPrecipitation(category)) {
            frames = SANDSTORM;
            fps = SANDSTORM_ANIMATION_FPS;
            alphaMultiplier = weatherKind.usesThunderClouds()
                    ? SANDSTORM_THUNDER_ALPHA_MULTIPLIER
                    : SANDSTORM_RAIN_ALPHA_MULTIPLIER;
            alphaMultiplier *= sandstormNightAlphaMultiplier(context, biomeDarkeningAlpha);
        } else if (weatherKind.usesSnowPrecipitation()) {
            frames = SNOW;
            fps = SNOW_ANIMATION_FPS;
        } else if (weatherKind.usesThunderPrecipitation()) {
            frames = THUNDERSTORM_RAIN;
            fps = THUNDER_RAIN_ANIMATION_FPS;
        } else {
            frames = RAIN;
            fps = RAIN_ANIMATION_FPS;
        }

        float alpha = weatherIntensity(context, weatherKind)
                * this.weatherVisualState.presenceAlpha()
                * context.alpha()
                * alphaMultiplier;
        if (alpha <= 0.001F) {
            return;
        }

        int frameIndex = animationFrame(nowNanos, fps, frames.length);
        float precipitationDarkening = precipitationDarkeningFactor(context, weatherKind, category, biomeDarkeningAlpha);
        drawCircularFullTexture(
                graphics,
                frames[frameIndex],
                precipitationTintColor(alpha, precipitationDarkening)
        );
        if (precipitationDarkening > 0.001F) {
            drawCircularFullTexture(
                    graphics,
                    frames[frameIndex],
                    colorWithAlpha(
                            alpha * precipitationDarkening * PRECIPITATION_DARKEN_OVERLAY_MULTIPLIER,
                            BIOME_DARKEN_TINT_RGB
                    )
            );
        }
    }

    private void renderWeatherClouds(
            GuiGraphics graphics,
            SleepIndicatorContext context,
            BiomeClockWeatherKind weatherKind
    ) {
        if (!weatherKind.hasVisualWeather()) {
            return;
        }

        float alpha = weatherIntensity(context, weatherKind) * this.weatherVisualState.presenceAlpha() * context.alpha();
        if (alpha <= 0.001F) {
            return;
        }

        Identifier texture = weatherKind.usesThunderClouds() ? WEATHER_CLOUDS_THUNDERSTORM : WEATHER_CLOUDS_RAIN;
        int color = weatherCloudTint(context, weatherKind, alpha);
        float slide = this.weatherVisualState.slideProgress();
        int leftX = Math.round(Mth.lerp(slide, LEFT_WEATHER_CLOUD_OUT_X, LEFT_WEATHER_CLOUD_IN_X)
                + this.weatherVisualState.leftDriftX());
        int rightX = Math.round(Mth.lerp(slide, RIGHT_WEATHER_CLOUD_OUT_X, RIGHT_WEATHER_CLOUD_IN_X)
                + this.weatherVisualState.rightDriftX());
        int leftY = Math.round(WEATHER_CLOUD_BASE_Y + this.weatherVisualState.leftDriftY());
        int rightY = Math.round(WEATHER_CLOUD_BASE_Y + this.weatherVisualState.rightDriftY());

        drawCircularTexture(
                graphics,
                texture,
                leftX,
                leftY,
                0,
                0,
                WEATHER_CLOUD_WIDTH,
                WEATHER_CLOUD_HEIGHT,
                WEATHER_CLOUD_WIDTH,
                WEATHER_CLOUD_HEIGHT,
                color
        );
        drawCircularTexture(
                graphics,
                texture,
                rightX,
                rightY,
                0,
                0,
                WEATHER_CLOUD_WIDTH,
                WEATHER_CLOUD_HEIGHT,
                WEATHER_CLOUD_WIDTH,
                WEATHER_CLOUD_HEIGHT,
                color
        );
    }

    private void renderLightning(GuiGraphics graphics, SleepIndicatorContext context, int lightningFrame) {
        if (lightningFrame < 0 || lightningFrame >= LIGHTNING.length) {
            return;
        }

        drawCircularTexture(
                graphics,
                LIGHTNING[lightningFrame],
                this.lightningState.offsetX(),
                this.lightningState.offsetY(),
                0,
                0,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOCK_SIZE,
                whiteWithAlpha(context.alpha())
        );
    }

    private void renderZzzLayer(GuiGraphics graphics, SleepIndicatorContext context, long nowNanos) {
        float presence = updateZzzPresence(context, nowNanos);
        float alpha = presence * context.alpha();
        if (alpha <= 0.001F) {
            return;
        }

        int frameIndex = animationFrame(nowNanos, ZZZ_ANIMATION_FPS, ZZZ.length);
        drawFullTexture(graphics, ZZZ[frameIndex], whiteWithAlpha(alpha));
    }

    private void renderBiomeLayer(GuiGraphics graphics, SleepIndicatorContext context, float darkeningAlpha) {
        BiomeClockCategory fromCategory = this.biomeTransition.fromCategory();
        BiomeClockCategory toCategory = this.biomeTransition.toCategory();
        float fromAlpha = this.biomeTransition.fromAlpha();
        float toAlpha = this.biomeTransition.toAlpha();

        drawBiomeTexture(graphics, fromCategory, context.alpha() * fromAlpha);
        if (this.biomeTransition.transitioning() && toCategory != fromCategory) {
            drawBiomeTexture(graphics, toCategory, context.alpha() * toAlpha);
        }

        if (darkeningAlpha <= 0.001F) {
            return;
        }

        drawBiomeDarkening(graphics, fromCategory, context.alpha() * fromAlpha * darkeningAlpha);
        if (this.biomeTransition.transitioning() && toCategory != fromCategory) {
            drawBiomeDarkening(graphics, toCategory, context.alpha() * toAlpha * darkeningAlpha);
        }
    }

    private static void drawBiomeTexture(GuiGraphics graphics, BiomeClockCategory category, float alpha) {
        drawCircularFullTexture(graphics, biomeTexture(category), whiteWithAlpha(alpha));
    }

    private static void drawBiomeDarkening(GuiGraphics graphics, BiomeClockCategory category, float alpha) {
        drawCircularFullTexture(graphics, biomeTexture(category), colorWithAlpha(alpha, BIOME_DARKEN_TINT_RGB));
    }

    private int updateCloudPhase(SleepIndicatorContext context, long nowNanos) {
        if (this.lastCloudUpdateNanos < 0L) {
            this.lastCloudUpdateNanos = nowNanos;
            this.cloudPhasePx = wrapCloudPhase(this.cloudPhasePx);
            return Mth.floor(this.cloudPhasePx);
        }

        float deltaTicks = Mth.clamp((nowNanos - this.lastCloudUpdateNanos) / 50_000_000.0F, 0.0F, 8.0F);
        this.lastCloudUpdateNanos = nowNanos;

        float sleepBoost = Mth.clamp(context.sleepDayTimeSpeedPerTick(), 0.0F, 240.0F) * CLOUD_SLEEP_SPEED_FACTOR;
        float speed = Mth.clamp(CLOUD_BASE_SPEED_PX_PER_TICK + sleepBoost, CLOUD_BASE_SPEED_PX_PER_TICK, CLOUD_SPEED_LIMIT);
        this.cloudPhasePx = wrapCloudPhase(this.cloudPhasePx + deltaTicks * speed);
        return Mth.floor(this.cloudPhasePx);
    }

    private int updateCloudAbovePhase(SleepIndicatorContext context, long nowNanos) {
        if (this.lastCloudAboveUpdateNanos < 0L) {
            this.lastCloudAboveUpdateNanos = nowNanos;
            this.cloudAbovePhasePx = wrapCloudPhase(this.cloudAbovePhasePx);
            return Mth.floor(this.cloudAbovePhasePx);
        }

        float deltaTicks = Mth.clamp((nowNanos - this.lastCloudAboveUpdateNanos) / 50_000_000.0F, 0.0F, 8.0F);
        this.lastCloudAboveUpdateNanos = nowNanos;

        float sleepBoost = Mth.clamp(context.sleepDayTimeSpeedPerTick(), 0.0F, 240.0F) * CLOUD_ABOVE_SLEEP_SPEED_FACTOR;
        float speed = Mth.clamp(
                CLOUD_ABOVE_BASE_SPEED_PX_PER_TICK + sleepBoost,
                CLOUD_ABOVE_BASE_SPEED_PX_PER_TICK,
                CLOUD_SPEED_LIMIT
        );
        this.cloudAbovePhasePx = wrapCloudPhase(this.cloudAbovePhasePx + deltaTicks * speed);
        return Mth.floor(this.cloudAbovePhasePx);
    }

    private static float wrapCloudPhase(float phase) {
        if (phase < 0.0F || phase >= CLOUD_LOOP_DISTANCE) {
            phase = phase % CLOUD_LOOP_DISTANCE;
            if (phase < 0.0F) {
                phase += CLOUD_LOOP_DISTANCE;
            }
        }
        return phase;
    }

    private static int computeGlowColor(SleepIndicatorContext context) {
        float sunriseAlpha = computeSunriseEffectAlpha(context);
        if (sunriseAlpha <= 0.001F) {
            return 0;
        }

        float weatherFactor = 1.0F - Mth.clamp(
                Math.max(context.rainLevel(), context.thunderLevel()) * GLOW_WEATHER_ATTENUATION,
                0.0F,
                0.92F
        );
        float finalAlpha = Mth.clamp(sunriseAlpha * weatherFactor * GLOW_ALPHA_MULTIPLIER * context.alpha(), 0.0F, 1.0F);
        if (finalAlpha <= 0.001F) {
            return 0;
        }

        return ARGB.color(finalAlpha, resolveSunriseTintColor(context));
    }

    private static float computeSunriseEffectAlpha(SleepIndicatorContext context) {
        float vanillaAlpha = ARGB.alphaFloat(context.sunriseColor());
        float sunBottomY = sunBottomY(context.sunAngleRadians(), SUN_SIZE);
        float horizonDistance = Math.abs(sunBottomY - CELESTIAL_HORIZON_CUTOFF_Y);
        float extension = 1.0F - Mth.clamp(horizonDistance / SUNRISE_EXTENSION_RANGE_PX, 0.0F, 1.0F);
        float extendedAlpha = extension * SUNRISE_EXTENSION_MAX_ALPHA;
        return Mth.clamp(Math.max(vanillaAlpha, extendedAlpha), 0.0F, 1.0F);
    }

    private static int resolveSunriseTintColor(SleepIndicatorContext context) {
        int sunriseColor = ARGB.opaque(context.sunriseColor());
        if ((sunriseColor & 0x00FFFFFF) == 0) {
            return SUNRISE_FALLBACK_TINT;
        }
        return sunriseColor;
    }

    private float updateZzzPresence(SleepIndicatorContext context, long nowNanos) {
        boolean targetVisible = context.sleepAnimationActive()
                && context.sleepDayTimeSpeedPerTick() > ZZZ_SKIP_SPEED_THRESHOLD;
        if (this.lastZzzPresenceUpdateNanos < 0L) {
            this.lastZzzPresenceUpdateNanos = nowNanos;
        }

        float delta = Mth.clamp((nowNanos - this.lastZzzPresenceUpdateNanos) / (float) ZZZ_FADE_NANOS, 0.0F, 1.0F);
        this.lastZzzPresenceUpdateNanos = nowNanos;
        if (targetVisible) {
            this.zzzPresence = Mth.clamp(this.zzzPresence + delta, 0.0F, 1.0F);
        } else {
            this.zzzPresence = Mth.clamp(this.zzzPresence - delta, 0.0F, 1.0F);
        }
        return this.zzzPresence;
    }

    private static float weatherIntensity(SleepIndicatorContext context, BiomeClockWeatherKind weatherKind) {
        float rain = Mth.clamp(context.rainLevel(), 0.0F, 1.0F);
        float thunder = Mth.clamp(context.thunderLevel(), 0.0F, 1.0F);
        return weatherKind.usesThunderClouds() ? Math.max(rain, thunder) : rain;
    }

    private static float sandstormNightAlphaMultiplier(SleepIndicatorContext context, float biomeDarkeningAlpha) {
        float nightFactor = computeNightFactor(context.normalizedDayTime());
        float darkeningFactor = Mth.clamp(biomeDarkeningAlpha / BIOME_DARKEN_MAX_ALPHA, 0.0F, 1.0F);
        float reductionFactor = nightFactor * darkeningFactor;
        return Mth.lerp(reductionFactor, 1.0F, 1.0F - SANDSTORM_NIGHT_ALPHA_REDUCTION);
    }

    private static int precipitationTintColor(float alpha, float darkeningFactor) {
        int tintColor = ARGB.srgbLerp(
                darkeningFactor,
                ARGB.opaque(0xFFFFFF),
                ARGB.opaque(BIOME_DARKEN_TINT_RGB)
        );
        return ARGB.multiplyAlpha(tintColor, alpha);
    }

    private static float precipitationDarkeningFactor(
            SleepIndicatorContext context,
            BiomeClockWeatherKind weatherKind,
            BiomeClockCategory category,
            float biomeDarkeningAlpha
    ) {
        float thunderFactor = weatherKind.usesThunderClouds()
                ? Mth.clamp(context.thunderLevel(), 0.0F, 1.0F)
                : 0.0F;
        float nightFactor = computeNightFactor(context.normalizedDayTime());
        float darkeningFactor = biomeDarkeningAlpha * PRECIPITATION_DARKEN_TINT_STRENGTH;
        darkeningFactor += biomeDarkeningAlpha * thunderFactor * PRECIPITATION_THUNDER_DARKEN_BOOST;
        if (usesSandstormPrecipitation(category)) {
            darkeningFactor += biomeDarkeningAlpha * nightFactor * PRECIPITATION_SANDSTORM_NIGHT_DARKEN_BOOST;
        }
        return Mth.clamp(darkeningFactor, 0.0F, PRECIPITATION_DARKEN_MAX_FACTOR);
    }

    private static boolean usesSandstormPrecipitation(BiomeClockCategory category) {
        return category == BiomeClockCategory.DESERT || category == BiomeClockCategory.BADLANDS;
    }

    private static int weatherCloudTint(SleepIndicatorContext context, BiomeClockWeatherKind weatherKind, float alpha) {
        int baseColor = ARGB.opaque(context.cloudColor());
        if (weatherKind.usesThunderClouds()) {
            baseColor = ARGB.scaleRGB(baseColor, 0.72F);
        }
        return ARGB.multiplyAlpha(baseColor, alpha);
    }

    private static float computeBiomeDarkeningAlpha(SleepIndicatorContext context, float lightningFlashFactor) {
        float nightFactor = computeNightFactor(context.normalizedDayTime());
        float rainDarkening = context.rainLevel() * BIOME_DARKEN_RAIN_ALPHA;
        float thunderDarkening = context.thunderLevel() * BIOME_DARKEN_THUNDER_ALPHA;
        float nightDarkening = nightFactor * BIOME_DARKEN_NIGHT_ALPHA;
        float weatherDarkening = Math.max(thunderDarkening, rainDarkening);
        float baseDarkening = Mth.clamp(
                nightDarkening + weatherDarkening,
                0.0F,
                BIOME_DARKEN_MAX_ALPHA
        );
        return baseDarkening * (1.0F - Mth.clamp(lightningFlashFactor, 0.0F, 1.0F) * LIGHTNING_FLASH_DARKENING_RELIEF);
    }

    private static float computeNightFactor(float normalizedDayTime) {
        float time = normalizedDayTime - Mth.floor(normalizedDayTime);
        if (time < 0.50F) {
            return 0.0F;
        }
        if (time < 0.58F) {
            return smoothstep((time - 0.50F) / 0.08F);
        }
        if (time < 0.92F) {
            return 1.0F;
        }
        if (time < 0.99F) {
            return 1.0F - smoothstep((time - 0.92F) / 0.07F);
        }
        return 0.0F;
    }

    private static float smoothstep(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float smoothstepRange(float start, float end, float value) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        return smoothstep((value - start) / (end - start));
    }

    private static int animationFrame(long nowNanos, double fps, int frameCount) {
        if (frameCount <= 1 || fps <= 0.0D) {
            return 0;
        }
        long frame = (long) Math.floor(nowNanos / 1_000_000_000.0D * fps);
        return Math.floorMod(frame, frameCount);
    }

    private static int whiteWithAlpha(float alpha) {
        return colorWithAlpha(alpha, 0xFFFFFF);
    }

    private static int celestialTintColor(SleepIndicatorContext context) {
        float brightness = 1.0F - context.thunderLevel() * (1.0F - CELESTIAL_THUNDER_MIN_BRIGHTNESS);
        int channel = Mth.clamp((int) (Mth.clamp(brightness, 0.0F, 1.0F) * 255.0F), 0, 255);
        return colorWithAlpha(context.alpha(), (channel << 16) | (channel << 8) | channel);
    }

    private static int colorWithAlpha(float alpha, int rgb) {
        int alphaByte = Mth.clamp((int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F), 0, 255);
        return (alphaByte << 24) | (rgb & 0x00FFFFFF);
    }

    private static OrbitPosition orbitPosition(float angleRadians, int size) {
        float centerX = ORBIT_CENTER_X + Mth.sin(angleRadians) * ORBIT_RADIUS_X;
        float centerY = ORBIT_CENTER_Y - Mth.cos(angleRadians) * ORBIT_RADIUS_Y;
        return new OrbitPosition(
                Math.round(centerX - size * 0.5F),
                Math.round(centerY - size * 0.5F)
        );
    }

    private static float sunScreenX(float angleRadians) {
        return ORBIT_CENTER_X + Mth.sin(angleRadians) * ORBIT_RADIUS_X;
    }

    private static float sunBottomY(float angleRadians, int size) {
        float centerY = ORBIT_CENTER_Y - Mth.cos(angleRadians) * ORBIT_RADIUS_Y;
        return centerY + size * 0.5F;
    }

    private static void drawCircularFullTexture(GuiGraphics graphics, Identifier texture, int color) {
        drawCircularTexture(
                graphics,
                texture,
                0,
                0,
                0,
                0,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOCK_SIZE,
                color
        );
    }

    private static void drawFullTexture(GuiGraphics graphics, Identifier texture, int color) {
        if ((color >>> 24) <= 0) {
            return;
        }

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                0,
                0,
                0.0F,
                0.0F,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOCK_SIZE,
                CLOCK_SIZE,
                color
        );
    }

    private static void drawCircularTexture(
            GuiGraphics graphics,
            Identifier texture,
            int destX,
            int destY,
            int sourceU,
            int sourceV,
            int drawWidth,
            int drawHeight,
            int textureWidth,
            int textureHeight
    ) {
        forEachCircularSlice((sliceX, sliceY, sliceWidth, u, v) -> graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                sliceX,
                sliceY,
                (float) u,
                (float) v,
                sliceWidth,
                1,
                textureWidth,
                textureHeight
        ), destX, destY, sourceU, sourceV, drawWidth, drawHeight);
    }

    private static void drawCircularTextureClippedToHorizon(
            GuiGraphics graphics,
            Identifier texture,
            int destX,
            int destY,
            int sourceU,
            int sourceV,
            int drawWidth,
            int drawHeight,
            int textureWidth,
            int textureHeight,
            int maxVisibleYExclusive
    ) {
        forEachCircularSlice((sliceX, sliceY, sliceWidth, u, v) -> graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                sliceX,
                sliceY,
                (float) u,
                (float) v,
                sliceWidth,
                1,
                textureWidth,
                textureHeight
        ), destX, destY, sourceU, sourceV, drawWidth, drawHeight, maxVisibleYExclusive);
    }

    private static void drawCircularTextureScaled(
            GuiGraphics graphics,
            Identifier texture,
            int destX,
            int destY,
            int drawWidth,
            int drawHeight,
            int sourceWidth,
            int sourceHeight
    ) {
        drawCircularTextureScaled(
                graphics,
                texture,
                destX,
                destY,
                drawWidth,
                drawHeight,
                sourceWidth,
                sourceHeight,
                CLOCK_SIZE,
                -1
        );
    }

    private static void drawCircularTextureScaled(
            GuiGraphics graphics,
            Identifier texture,
            int destX,
            int destY,
            int drawWidth,
            int drawHeight,
            int sourceWidth,
            int sourceHeight,
            int maxVisibleYExclusive
    ) {
        drawCircularTextureScaled(
                graphics,
                texture,
                destX,
                destY,
                drawWidth,
                drawHeight,
                sourceWidth,
                sourceHeight,
                maxVisibleYExclusive,
                -1
        );
    }

    private static void drawCircularTextureScaled(
            GuiGraphics graphics,
            Identifier texture,
            int destX,
            int destY,
            int drawWidth,
            int drawHeight,
            int sourceWidth,
            int sourceHeight,
            int maxVisibleYExclusive,
            int color
    ) {
        if (drawWidth <= 0 || drawHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        if (color != -1 && (color >>> 24) <= 0) {
            return;
        }

        forEachCircularSlice((sliceX, sliceY, sliceWidth, ignoredU, ignoredV) -> {
            int rowOffset = sliceY - destY;
            int sourceY = Math.min(sourceHeight - 1, Math.max(0, rowOffset * sourceHeight / drawHeight));
            int horizontalOffset = sliceX - destX;
            int sourceX = Math.min(sourceWidth - 1, Math.max(0, horizontalOffset * sourceWidth / drawWidth));
            int sourceSliceWidth = Math.max(1, Math.min(
                    sourceWidth - sourceX,
                    (int) Math.ceil(sliceWidth * sourceWidth / (double) drawWidth)
            ));

            if (color == -1) {
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        texture,
                        sliceX,
                        sliceY,
                        (float) sourceX,
                        (float) sourceY,
                        sliceWidth,
                        1,
                        sourceSliceWidth,
                        1,
                        sourceWidth,
                        sourceHeight
                );
            } else {
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        texture,
                        sliceX,
                        sliceY,
                        (float) sourceX,
                        (float) sourceY,
                        sliceWidth,
                        1,
                        sourceSliceWidth,
                        1,
                        sourceWidth,
                        sourceHeight,
                        color
                );
            }
        }, destX, destY, 0, 0, drawWidth, drawHeight, maxVisibleYExclusive);
    }

    private static void drawCircularTexture(
            GuiGraphics graphics,
            Identifier texture,
            int destX,
            int destY,
            int sourceU,
            int sourceV,
            int drawWidth,
            int drawHeight,
            int textureWidth,
            int textureHeight,
            int color
    ) {
        if ((color >>> 24) <= 0) {
            return;
        }

        forEachCircularSlice((sliceX, sliceY, sliceWidth, u, v) -> graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                sliceX,
                sliceY,
                (float) u,
                (float) v,
                sliceWidth,
                1,
                textureWidth,
                textureHeight,
                color
        ), destX, destY, sourceU, sourceV, drawWidth, drawHeight);
    }

    private static void forEachCircularSlice(
            SliceConsumer consumer,
            int destX,
            int destY,
            int sourceU,
            int sourceV,
            int drawWidth,
            int drawHeight
    ) {
        forEachCircularSlice(consumer, destX, destY, sourceU, sourceV, drawWidth, drawHeight, CLOCK_SIZE);
    }

    private static void forEachCircularSlice(
            SliceConsumer consumer,
            int destX,
            int destY,
            int sourceU,
            int sourceV,
            int drawWidth,
            int drawHeight,
            int maxVisibleYExclusive
    ) {
        if (drawWidth <= 0 || drawHeight <= 0) {
            return;
        }

        int yLimit = Math.max(0, Math.min(CLOCK_SIZE, maxVisibleYExclusive));
        for (int y = 0; y < yLimit; y++) {
            int rowOffset = y - destY;
            if (rowOffset < 0 || rowOffset >= drawHeight) {
                continue;
            }

            double dy = y + 0.5D - CLIP_CENTER_Y;
            double inside = CLIP_RADIUS * CLIP_RADIUS - dy * dy;
            if (inside < 0.0D) {
                continue;
            }

            double halfWidth = Math.sqrt(inside);
            int maskMinX = Math.max(0, (int) Math.ceil(CLIP_CENTER_X - halfWidth));
            int maskMaxExclusive = Math.min(CLOCK_SIZE, (int) Math.floor(CLIP_CENTER_X + halfWidth) + 1);
            int sliceX = Math.max(maskMinX, destX);
            int sliceMaxExclusive = Math.min(maskMaxExclusive, destX + drawWidth);
            int sliceWidth = sliceMaxExclusive - sliceX;
            if (sliceWidth <= 0) {
                continue;
            }

            int u = sourceU + (sliceX - destX);
            int v = sourceV + rowOffset;
            consumer.accept(sliceX, y, sliceWidth, u, v);
        }
    }

    private static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath(
                Constants.MOD_ID,
                "textures/gui/sleep_indicator/biome_clock/" + path
        );
    }

    private static Identifier biomeTexture(BiomeClockCategory category) {
        Identifier fallback = BIOME_TEXTURES.get(BiomeClockCategory.DEFAULT);
        return BIOME_TEXTURES.getOrDefault(category == null ? BiomeClockCategory.DEFAULT : category, fallback);
    }

    private static Map<BiomeClockCategory, Identifier> createBiomeTextures() {
        EnumMap<BiomeClockCategory, Identifier> textures = new EnumMap<>(BiomeClockCategory.class);
        for (BiomeClockCategory category : BiomeClockCategory.values()) {
            textures.put(category, texture("biomes/" + category.textureId() + ".png"));
        }
        return textures;
    }

    private static long transitionTimeMs() {
        return System.nanoTime() / 1_000_000L;
    }

    @FunctionalInterface
    private interface SliceConsumer {
        void accept(int sliceX, int sliceY, int sliceWidth, int u, int v);
    }

    private record OrbitPosition(int x, int y) {
    }
}
