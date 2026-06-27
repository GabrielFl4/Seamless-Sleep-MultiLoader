package net.aqualoco.sec.client.sleepindicator;

// Pixel size reported by a sleep indicator renderer for placement and animation pivots.
public record IndicatorSize(int width, int height) {
    public IndicatorSize {
        width = Math.max(1, width);
        height = Math.max(1, height);
    }
}
