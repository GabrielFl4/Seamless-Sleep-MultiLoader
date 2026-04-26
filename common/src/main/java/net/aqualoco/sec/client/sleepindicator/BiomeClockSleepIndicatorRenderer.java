package net.aqualoco.sec.client.sleepindicator;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockCategory;
import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockTransitionState;
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
    private static final int GLOW_WIDTH = 132;
    private static final int GLOW_HEIGHT = 66;
    private static final int CELESTIAL_HORIZON_CUTOFF_Y = 46;

    private static final double CLIP_CENTER_X = 33.0D;
    private static final double CLIP_CENTER_Y = 33.0D;
    private static final double CLIP_RADIUS = 31.0D;

    private static final float ORBIT_CENTER_X = 33.0F;
    private static final float ORBIT_CENTER_Y = 47.0F;
    private static final float ORBIT_RADIUS_X = 24.0F;
    private static final float ORBIT_RADIUS_Y = 28.0F;

    private static final float CLOUD_BASE_SPEED_PX_PER_TICK = 0.015F;
    private static final float CLOUD_SLEEP_SPEED_FACTOR = 0.025F;
    private static final float CLOUD_SPEED_LIMIT = 8.0F;

    private static final float SKY_TOP_DARKEN = 0.82F;
    private static final float SKY_HORIZON_LIGHTEN = 0.12F;
    private static final float SKY_SUNRISE_TINT = 0.28F;
    private static final float SUNRISE_EXTENSION_RANGE_PX = 14.0F;
    private static final float SUNRISE_EXTENSION_MAX_ALPHA = 0.22F;
    private static final int SUNRISE_FALLBACK_TINT = 0xFFFFB068;

    // Presentation values when the biome layer needs stronger or softer weather/time shading.
    private static final float BIOME_DARKEN_RAIN_ALPHA = 0.08F;
    private static final float BIOME_DARKEN_THUNDER_ALPHA = 0.24F;
    private static final float BIOME_DARKEN_NIGHT_ALPHA = 0.36F;
    private static final int BIOME_DARKEN_TINT_RGB = 0x1B2230;
    private static final float CELESTIAL_THUNDER_MIN_BRIGHTNESS = 0.76F; // remember 0.76 is 76% dumbass
    private static final float LIGHTNING_FLASH_LUMINANCE_THRESHOLD = 0.10F;
    private static final float LIGHTNING_FLASH_RESPONSE = 4.0F;
    private static final float LIGHTNING_FLASH_DECAY_PER_SECOND = 5.0F;
    private static final float LIGHTNING_FLASH_DARKENING_RELIEF = 0.85F;

    private static final int HORIZON_LEFT_X = 6;
    private static final int HORIZON_RIGHT_X = 60;
    private static final int HORIZON_GLOW_Y = 9;
    private static final float GLOW_ALPHA_MULTIPLIER = 0.92F;
    private static final float GLOW_WEATHER_ATTENUATION = 0.85F;

    private static final Identifier CLOUDS = texture("clouds.png");
    private static final Identifier SUN = texture("sun.png");
    private static final Identifier SUN_GLOW = texture("sun_glow.png");
    private static final Identifier FRAME_CIRCLE = texture("frame/circle.png");
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
    private float lastSkyLuminance = -1.0F;
    private float lightningFlashFactor;
    private long lastLightningFlashUpdateNanos = -1L;
    private final BiomeClockTransitionState biomeTransition = new BiomeClockTransitionState();

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
        renderSkyFromClient(graphics, context);
        updateLightningFlashFactor(context);

        int cloudOffset = updateCloudPhase(context);
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
                SUN_SIZE,
                SUN_SIZE,
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
                SUN_SIZE,
                SUN_SIZE,
                CELESTIAL_HORIZON_CUTOFF_Y,
                celestialColor
        );

        this.biomeTransition.update(context.biomeClockCategory(), transitionTimeMs());
        renderBiomeLayer(graphics, context);
        drawFullTexture(graphics, FRAME_CIRCLE, textureAlphaColor);
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

    private void renderBiomeLayer(GuiGraphics graphics, SleepIndicatorContext context) {
        BiomeClockCategory fromCategory = this.biomeTransition.fromCategory();
        BiomeClockCategory toCategory = this.biomeTransition.toCategory();
        float fromAlpha = this.biomeTransition.fromAlpha();
        float toAlpha = this.biomeTransition.toAlpha();

        drawBiomeTexture(graphics, fromCategory, context.alpha() * fromAlpha);
        if (this.biomeTransition.transitioning() && toCategory != fromCategory) {
            drawBiomeTexture(graphics, toCategory, context.alpha() * toAlpha);
        }

        float darkeningAlpha = computeBiomeDarkeningAlpha(context, this.lightningFlashFactor);
        if (darkeningAlpha <= 0.001F) {
            return;
        }

        drawBiomeDarkening(graphics, fromCategory, context.alpha() * fromAlpha * darkeningAlpha);
        if (this.biomeTransition.transitioning() && toCategory != fromCategory) {
            drawBiomeDarkening(graphics, toCategory, context.alpha() * toAlpha * darkeningAlpha);
        }
    }

    private static void drawBiomeTexture(GuiGraphics graphics, BiomeClockCategory category, float alpha) {
        drawFullTexture(graphics, biomeTexture(category), whiteWithAlpha(alpha));
    }

    private static void drawBiomeDarkening(GuiGraphics graphics, BiomeClockCategory category, float alpha) {
        drawFullTexture(graphics, biomeTexture(category), colorWithAlpha(alpha, BIOME_DARKEN_TINT_RGB));
    }

    private int updateCloudPhase(SleepIndicatorContext context) {
        long now = System.nanoTime();
        if (this.lastCloudUpdateNanos < 0L) {
            this.lastCloudUpdateNanos = now;
            this.cloudPhasePx = wrapCloudPhase(this.cloudPhasePx);
            return Mth.floor(this.cloudPhasePx);
        }

        float deltaTicks = Mth.clamp((now - this.lastCloudUpdateNanos) / 50_000_000.0F, 0.0F, 8.0F);
        this.lastCloudUpdateNanos = now;

        float sleepBoost = Mth.clamp(context.sleepDayTimeSpeedPerTick(), 0.0F, 240.0F) * CLOUD_SLEEP_SPEED_FACTOR;
        float speed = Mth.clamp(CLOUD_BASE_SPEED_PX_PER_TICK + sleepBoost, CLOUD_BASE_SPEED_PX_PER_TICK, CLOUD_SPEED_LIMIT);
        this.cloudPhasePx = wrapCloudPhase(this.cloudPhasePx + deltaTicks * speed);
        return Mth.floor(this.cloudPhasePx);
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

    private void updateLightningFlashFactor(SleepIndicatorContext context) {
        long now = System.nanoTime();
        float currentLuminance = luminance(context.skyColor());
        if (this.lastLightningFlashUpdateNanos < 0L || this.lastSkyLuminance < 0.0F) {
            this.lastLightningFlashUpdateNanos = now;
            this.lastSkyLuminance = currentLuminance;
            return;
        }

        float deltaSeconds = Mth.clamp((now - this.lastLightningFlashUpdateNanos) / 1_000_000_000.0F, 0.0F, 0.25F);
        this.lastLightningFlashUpdateNanos = now;
        this.lightningFlashFactor = Math.max(
                0.0F,
                this.lightningFlashFactor - LIGHTNING_FLASH_DECAY_PER_SECOND * deltaSeconds
        );

        if (context.thunderLevel() <= 0.001F) {
            this.lightningFlashFactor = 0.0F;
            this.lastSkyLuminance = currentLuminance;
            return;
        }

        float brightening = currentLuminance - this.lastSkyLuminance;
        if (brightening > LIGHTNING_FLASH_LUMINANCE_THRESHOLD) {
            float flash = Mth.clamp(
                    (brightening - LIGHTNING_FLASH_LUMINANCE_THRESHOLD) * LIGHTNING_FLASH_RESPONSE,
                    0.0F,
                    1.0F
            );
            this.lightningFlashFactor = Math.max(this.lightningFlashFactor, flash * context.thunderLevel());
        }

        this.lastSkyLuminance = currentLuminance;
    }

    private static float computeBiomeDarkeningAlpha(SleepIndicatorContext context, float lightningFlashFactor) {
        float nightFactor = computeNightFactor(context.normalizedDayTime());
        float rainDarkening = context.rainLevel() * BIOME_DARKEN_RAIN_ALPHA;
        float thunderDarkening = context.thunderLevel() * BIOME_DARKEN_THUNDER_ALPHA;
        float nightDarkening = nightFactor * BIOME_DARKEN_NIGHT_ALPHA;
        float baseDarkening = Mth.clamp(
                Math.max(nightDarkening, Math.max(thunderDarkening, rainDarkening)),
                0.0F,
                BIOME_DARKEN_NIGHT_ALPHA
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

    private static int whiteWithAlpha(float alpha) {
        return colorWithAlpha(alpha, 0xFFFFFF);
    }

    private static int celestialTintColor(SleepIndicatorContext context) {
        float brightness = 1.0F - context.thunderLevel() * (1.0F - CELESTIAL_THUNDER_MIN_BRIGHTNESS);
        int channel = Mth.clamp((int) (Mth.clamp(brightness, 0.0F, 1.0F) * 255.0F), 0, 255);
        return colorWithAlpha(context.alpha(), (channel << 16) | (channel << 8) | channel);
    }

    private static float luminance(int color) {
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        return (red * 0.2126F + green * 0.7152F + blue * 0.0722F) / 255.0F;
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
