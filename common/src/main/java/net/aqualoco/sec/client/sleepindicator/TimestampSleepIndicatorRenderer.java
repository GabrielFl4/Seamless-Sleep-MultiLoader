package net.aqualoco.sec.client.sleepindicator;

import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
        TimestampRenderState state = resolveState(context);
        Font font = context.client().font;
        int color = withAlpha(context.alpha(), state.rgb());
        if ((color >>> 24) <= 0) {
            return;
        }

        TimestampText text = state.text();
        int x = 0;
        x += drawString(graphics, font, text.prefix(), x, 0, color, state.shadow());
        drawDayDigits(graphics, font, state, x, context.alpha());
        x += font.width(textComponent(text.dayDigits()));
        drawString(graphics, font, text.suffix(), x, 0, color, state.shadow());
    }

    private TimestampRenderState resolveState(SleepIndicatorContext context) {
        SeamlessSleepClientConfig config = SeamlessSleepClientConfigManager.get();
        TimestampStyle style = config.timestampStyle == null ? TimestampStyle.DAY_FIRST : config.timestampStyle;
        boolean twelveHour = TimestampHourFormatResolver.usesTwelveHourClock(context.client());
        TimestampSample latest = TimestampSample.from(context.visualDayTime(), twelveHour);
        updateDayAnimation(latest.dayString(), System.nanoTime() / 1_000_000L, context.sleepDayTimeSpeedPerTick());
        int rgb = config.timestampColor & 0x00FFFFFF;
        boolean defaultWhite = rgb == SeamlessSleepClientConfig.DEFAULT_TIMESTAMP_COLOR;
        return new TimestampRenderState(latest.toText(style), rgb, defaultWhite);
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
            GuiGraphics graphics,
            Font font,
            TimestampRenderState state,
            int startX,
            float baseAlpha
    ) {
        String dayDigits = state.text().dayDigits();
        Component dayComponent = textComponent(dayDigits);
        int normalColor = withAlpha(baseAlpha, state.rgb());
        if (!isDayAnimationActive() || this.previousDayString.length() != dayDigits.length()) {
            graphics.drawString(font, dayComponent, startX, 0, normalColor, state.shadow());
            return;
        }

        long nowMs = System.nanoTime() / 1_000_000L;
        float progress = Mth.clamp((nowMs - this.dayAnimationStartMs) / (float) this.dayAnimationDurationMs, 0.0F, 1.0F);
        float eased = easeOutCubic(progress);
        if (progress >= 1.0F) {
            this.dayAnimationStartMs = -1L;
            graphics.drawString(font, dayComponent, startX, 0, normalColor, state.shadow());
            return;
        }

        for (int i = 0; i < dayDigits.length(); i++) {
            String currentDigit = dayDigits.substring(i, i + 1);
            int digitX = startX + font.width(textComponent(dayDigits.substring(0, i)));
            if (i >= this.animatedDayDigits.length || !this.animatedDayDigits[i]) {
                graphics.drawString(font, textComponent(currentDigit), digitX, 0, normalColor, state.shadow());
                continue;
            }

            String previousDigit = this.previousDayString.substring(i, i + 1);
            int previousColor = withAlpha(baseAlpha * (1.0F - eased), state.rgb());
            int currentColor = withAlpha(baseAlpha * eased, state.rgb());
            int previousY = Math.round(eased * DAY_ANIMATION_TRAVEL);
            int currentY = -Math.round((1.0F - eased) * DAY_ANIMATION_TRAVEL);
            graphics.drawString(font, textComponent(previousDigit), digitX, previousY, previousColor, state.shadow());
            graphics.drawString(font, textComponent(currentDigit), digitX, currentY, currentColor, state.shadow());
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
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int color,
            boolean shadow
    ) {
        if (text.isEmpty()) {
            return 0;
        }

        Component component = textComponent(text);
        graphics.drawString(font, component, x, y, color, shadow);
        return font.width(component);
    }

    private static Component textComponent(String text) {
        return Component.literal(text);
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

    private record TimestampRenderState(TimestampText text, int rgb, boolean shadow) {
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
