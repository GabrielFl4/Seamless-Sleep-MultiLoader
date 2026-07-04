package net.aqualoco.sec.client.sleepindicator;

import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.Locale;

// Renders the latest visual world day/time string with a small day-number transition.
public final class TimestampSleepIndicatorRenderer implements SleepIndicatorRenderer {
    private static final int FALLBACK_WIDTH = 84;
    private static final int DAY_TICKS = 24000;
    private static final int MINUTES_PER_DAY = 1440;
    private static final int DAY_ANIMATION_TRAVEL = 4;
    private static final long MIN_DAY_ANIMATION_DURATION_MS = 80L;
    private static final long MAX_DAY_ANIMATION_DURATION_MS = 300L;
    private static final double DAY_ANIMATION_BASE_MS = 300.0D;
    private static final double DIMENSION_TIME_RANDOMIZATION_FPS = 12.0D;
    private static final int DIMENSION_TIME_RANDOMIZATION_SEED = 0x51EE9;

    private String previousDayString = "";
    private String currentDayString = "";
    private boolean[] animatedDayDigits = new boolean[0];
    private long dayAnimationStartMs = -1L;
    private long dayAnimationDurationMs = MAX_DAY_ANIMATION_DURATION_MS;

    @Override
    public String id() {
        return "timestamp";
    }

    @Override
    public int width() {
        return FALLBACK_WIDTH;
    }

    @Override
    public int height() {
        return 10;
    }

    @Override
    public IndicatorSize measure(SleepIndicatorContext context) {
        TimestampRenderState state = resolveState(context);
        Font font = context.client().font;
        return new IndicatorSize(font.width(textComponent(state.text().fullText())), font.lineHeight);
    }

    @Override
    public void render(GuiGraphics graphics, SleepIndicatorContext context, float tickDelta) {
        render(new GuiSleepIndicatorDrawSurface(graphics), context, tickDelta);
    }

    public void render(SleepIndicatorDrawSurface surface, SleepIndicatorContext context, float tickDelta) {
        TimestampRenderState state = resolveState(context);
        Font font = context.client().font;
        int color = withAlpha(context.alpha(), state.rgb());
        if ((color >>> 24) <= 0) {
            return;
        }

        TimestampText text = state.text();
        int randomizationStep = state.randomizeTime() ? timeRandomizationStep(System.nanoTime()) : 0;
        int x = 0;
        x += drawString(
                surface,
                font,
                text.prefix(),
                x,
                0,
                color,
                state.shadow(),
                state.randomizeTime(),
                randomizationStep,
                DIMENSION_TIME_RANDOMIZATION_SEED
        );
        drawDayDigits(surface, font, state, x, context.alpha());
        x += font.width(textComponent(text.dayDigits()));
        drawString(
                surface,
                font,
                text.suffix(),
                x,
                0,
                color,
                state.shadow(),
                state.randomizeTime(),
                randomizationStep,
                DIMENSION_TIME_RANDOMIZATION_SEED + 97
        );
    }

    private TimestampRenderState resolveState(SleepIndicatorContext context) {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        TimestampStyle style = config.timestampStyle == null ? TimestampStyle.DAY_FIRST : config.timestampStyle;
        boolean twelveHour = TimestampHourFormatResolver.usesTwelveHourClock(context.client());
        TimestampSample latest = TimestampSample.from(context.visualDayTime(), twelveHour);
        updateDayAnimation(latest.dayString(), System.nanoTime() / 1_000_000L, context.sleepDayTimeSpeedPerTick());
        int rgb = config.timestampColor & 0x00FFFFFF;
        boolean defaultWhite = rgb == SeamlessSleepClientConfig.DEFAULT_TIMESTAMP_COLOR;
        return new TimestampRenderState(
                latest.toText(style),
                rgb,
                defaultWhite,
                shouldRandomizeTime(context)
        );
    }

    private void updateDayAnimation(String nextDayString, long nowMs, float speedPerTick) {
        if (nextDayString.equals(this.currentDayString)) {
            return;
        }

        if (this.currentDayString.isEmpty()) {
            this.previousDayString = nextDayString;
            this.currentDayString = nextDayString;
            this.animatedDayDigits = new boolean[nextDayString.length()];
            this.dayAnimationStartMs = -1L;
            return;
        }

        this.previousDayString = this.currentDayString;
        this.currentDayString = nextDayString;
        this.animatedDayDigits = changedDigitMask(this.previousDayString, this.currentDayString);
        this.dayAnimationDurationMs = adaptiveDayAnimationDurationMs(speedPerTick);
        this.dayAnimationStartMs = hasAnimatedDigit(this.animatedDayDigits) ? nowMs : -1L;
    }

    private void drawDayDigits(
            SleepIndicatorDrawSurface surface,
            Font font,
            TimestampRenderState state,
            int startX,
            float baseAlpha
    ) {
        String dayDigits = state.text().dayDigits();
        Component dayComponent = textComponent(dayDigits);
        int normalColor = withAlpha(baseAlpha, state.rgb());
        if (!isDayAnimationActive() || this.previousDayString.length() != dayDigits.length()) {
            surface.drawString(font, dayComponent, startX, 0, normalColor, state.shadow());
            return;
        }

        long nowMs = System.nanoTime() / 1_000_000L;
        float progress = Mth.clamp((nowMs - this.dayAnimationStartMs) / (float) this.dayAnimationDurationMs, 0.0F, 1.0F);
        float eased = easeOutCubic(progress);
        if (progress >= 1.0F) {
            this.dayAnimationStartMs = -1L;
            surface.drawString(font, dayComponent, startX, 0, normalColor, state.shadow());
            return;
        }

        for (int i = 0; i < dayDigits.length(); i++) {
            String currentDigit = dayDigits.substring(i, i + 1);
            int digitX = startX + font.width(textComponent(dayDigits.substring(0, i)));
            if (i >= this.animatedDayDigits.length || !this.animatedDayDigits[i]) {
                surface.drawString(font, textComponent(currentDigit), digitX, 0, normalColor, state.shadow());
                continue;
            }

            String previousDigit = this.previousDayString.substring(i, i + 1);
            int previousColor = withAlpha(baseAlpha * (1.0F - eased), state.rgb());
            int currentColor = withAlpha(baseAlpha * eased, state.rgb());
            int previousY = Math.round(eased * DAY_ANIMATION_TRAVEL);
            int currentY = -Math.round((1.0F - eased) * DAY_ANIMATION_TRAVEL);
            surface.drawString(font, textComponent(previousDigit), digitX, previousY, previousColor, state.shadow());
            surface.drawString(font, textComponent(currentDigit), digitX, currentY, currentColor, state.shadow());
        }
    }

    private boolean isDayAnimationActive() {
        return this.dayAnimationStartMs >= 0L && hasAnimatedDigit(this.animatedDayDigits);
    }

    private static boolean[] changedDigitMask(String previous, String current) {
        if (previous.length() != current.length()) {
            return new boolean[current.length()];
        }

        boolean[] mask = new boolean[current.length()];
        for (int i = 0; i < current.length(); i++) {
            mask[i] = previous.charAt(i) != current.charAt(i);
        }
        return mask;
    }

    private static boolean hasAnimatedDigit(boolean[] mask) {
        for (boolean value : mask) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    private static int drawString(
            SleepIndicatorDrawSurface surface,
            Font font,
            String text,
            int x,
            int y,
            int color,
            boolean shadow,
            boolean randomizeTime,
            int randomizationStep,
            int seed
    ) {
        if (text.isEmpty()) {
            return 0;
        }

        Component component = textComponent(randomizeTime
                ? randomizeTimeText(text, randomizationStep, seed)
                : text);
        surface.drawString(font, component, x, y, color, shadow);
        return font.width(component);
    }

    private static Component textComponent(String text) {
        return Component.literal(text);
    }

    private static String randomizeTimeText(String text, int randomizationStep, int seed) {
        StringBuilder randomized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (Character.isDigit(character)) {
                randomized.append(randomDigit(randomizationStep, seed + i * 31));
            } else if (isAmPmTokenStart(text, i)) {
                randomized.append((randomizationStep & 1) == 0 ? "AM" : "PM");
                i++;
            } else {
                randomized.append(character);
            }
        }
        return randomized.toString();
    }

    private static char randomDigit(int randomizationStep, int seed) {
        return (char) ('0' + Math.floorMod(hash(randomizationStep ^ seed), 10));
    }

    private static boolean isAmPmTokenStart(String text, int index) {
        if (index < 0 || index + 1 >= text.length()) {
            return false;
        }

        char current = Character.toUpperCase(text.charAt(index));
        return (current == 'A' || current == 'P')
                && Character.toUpperCase(text.charAt(index + 1)) == 'M'
                && isTokenBoundary(text, index - 1)
                && isTokenBoundary(text, index + 2);
    }

    private static boolean isTokenBoundary(String text, int index) {
        return index < 0
                || index >= text.length()
                || !Character.isLetterOrDigit(text.charAt(index));
    }

    private static boolean shouldRandomizeTime(SleepIndicatorContext context) {
        return context.level() != null
                && (Level.NETHER.equals(context.level().dimension())
                        || Level.END.equals(context.level().dimension()));
    }

    private static int withAlpha(float alpha, int rgb) {
        int alphaByte = Mth.clamp((int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F), 0, 255);
        return (alphaByte << 24) | (rgb & 0x00FFFFFF);
    }

    private static float easeOutCubic(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    private static long adaptiveDayAnimationDurationMs(float speedPerTick) {
        double daysPerSecond = Math.max(0.0D, Math.abs(speedPerTick) * 20.0D / DAY_TICKS);
        double duration = DAY_ANIMATION_BASE_MS / Math.sqrt(daysPerSecond + 1.0D);
        return Mth.clamp(Math.round(duration), MIN_DAY_ANIMATION_DURATION_MS, MAX_DAY_ANIMATION_DURATION_MS);
    }

    private static int timeRandomizationStep(long nowNanos) {
        if (DIMENSION_TIME_RANDOMIZATION_FPS <= 0.0D) {
            return 0;
        }

        long step = (long) Math.floor(nowNanos / 1_000_000_000.0D * DIMENSION_TIME_RANDOMIZATION_FPS);
        return (int) step;
    }

    private static int hash(int value) {
        int mixed = value;
        mixed ^= mixed >>> 16;
        mixed *= 0x7FEB352D;
        mixed ^= mixed >>> 15;
        mixed *= 0x846CA68B;
        mixed ^= mixed >>> 16;
        return mixed;
    }

    private record TimestampRenderState(TimestampText text, int rgb, boolean shadow, boolean randomizeTime) {
    }

    private record TimestampText(String prefix, String dayDigits, String suffix) {
        String fullText() {
            return prefix + dayDigits + suffix;
        }
    }

    private record TimestampSample(String dayString, String timeString) {
        static TimestampSample from(long visualDayTime, boolean twelveHour) {
            long day = Math.floorDiv(visualDayTime, DAY_TICKS) + 1L;
            long wrapped = Math.floorMod(visualDayTime, DAY_TICKS);
            long clockTicks = Math.floorMod(wrapped + 6000L, DAY_TICKS);
            int minuteOfDay = (int) (clockTicks * MINUTES_PER_DAY / DAY_TICKS);
            int hour24 = minuteOfDay / 60;
            int minute = minuteOfDay % 60;
            return new TimestampSample(Long.toString(day), formatTime(hour24, minute, twelveHour));
        }

        TimestampText toText(TimestampStyle style) {
            if (style == TimestampStyle.TIME_FIRST) {
                return new TimestampText(this.timeString + " | DAY: ", this.dayString, "");
            }
            return new TimestampText("DAY: ", this.dayString, " | " + this.timeString);
        }

        private static String formatTime(int hour24, int minute, boolean twelveHour) {
            if (!twelveHour) {
                return String.format(Locale.ROOT, "%02d:%02d", hour24, minute);
            }

            int hour12 = hour24 % 12;
            if (hour12 == 0) {
                hour12 = 12;
            }
            String suffix = hour24 < 12 ? "AM" : "PM";
            return String.format(Locale.ROOT, "%d:%02d %s", hour12, minute, suffix);
        }
    }
}
