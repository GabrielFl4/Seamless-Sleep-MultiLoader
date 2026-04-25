package net.aqualoco.sec.client.sleepindicator;

// Resolves the screen position and safe scale for a sleep indicator anchor.
public record SleepIndicatorPlacement(int x, int y, float scale) {
    private static final float MIN_EFFECTIVE_SCALE = 0.10F;
    private static final float MIN_CONFIG_SCALE = 0.25F;
    private static final float MAX_CONFIG_SCALE = 4.0F;

    public static SleepIndicatorPlacement resolve(
            int screenWidth,
            int screenHeight,
            int baseWidth,
            int baseHeight,
            SleepIndicatorAnchor anchor,
            float requestedScale,
            int margin
    ) {
        int safeScreenWidth = Math.max(1, screenWidth);
        int safeScreenHeight = Math.max(1, screenHeight);
        int safeBaseWidth = Math.max(1, baseWidth);
        int safeBaseHeight = Math.max(1, baseHeight);
        int safeMargin = Math.max(0, margin);
        SleepIndicatorAnchor safeAnchor = anchor == null ? SleepIndicatorAnchor.CENTER : anchor;

        float scale = sanitizeScale(requestedScale);
        int availableWidth = Math.max(1, safeScreenWidth - safeMargin * 2);
        int availableHeight = Math.max(1, safeScreenHeight - safeMargin * 2);
        float fitScale = Math.min(
                availableWidth / (float) safeBaseWidth,
                availableHeight / (float) safeBaseHeight
        );
        if (Float.isFinite(fitScale) && fitScale > 0.0F) {
            scale = Math.min(scale, Math.max(MIN_EFFECTIVE_SCALE, fitScale));
        }

        int scaledWidth = Math.max(1, (int) Math.ceil(safeBaseWidth * scale));
        int scaledHeight = Math.max(1, (int) Math.ceil(safeBaseHeight * scale));

        int x = switch (safeAnchor) {
            case TOP_LEFT, BOTTOM_LEFT -> safeMargin;
            case TOP_CENTER, BOTTOM_CENTER, CENTER -> (safeScreenWidth - scaledWidth) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> safeScreenWidth - scaledWidth - safeMargin;
        };
        int y = switch (safeAnchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> safeMargin;
            case CENTER -> (safeScreenHeight - scaledHeight) / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> safeScreenHeight - scaledHeight - safeMargin;
        };

        int maxX = Math.max(0, safeScreenWidth - scaledWidth);
        int maxY = Math.max(0, safeScreenHeight - scaledHeight);
        return new SleepIndicatorPlacement(clamp(x, 0, maxX), clamp(y, 0, maxY), scale);
    }

    private static float sanitizeScale(float value) {
        if (!Float.isFinite(value)) {
            return 1.0F;
        }
        if (value < MIN_CONFIG_SCALE) {
            return MIN_CONFIG_SCALE;
        }
        if (value > MAX_CONFIG_SCALE) {
            return MAX_CONFIG_SCALE;
        }
        return value;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
