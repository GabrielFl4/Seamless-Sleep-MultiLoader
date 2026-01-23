package net.aqualoco.sec.config;

public final class SeamlessSleepClientConfig {
    public double sleepChatTextOpacityMultiplier = 0.5D;
    public double sleepChatBackgroundOpacityMultiplier = 0.4D;

    public void clamp() {
        sleepChatTextOpacityMultiplier = clamp01(sleepChatTextOpacityMultiplier, 0.5D);
        sleepChatBackgroundOpacityMultiplier = clamp01(sleepChatBackgroundOpacityMultiplier, 0.4D);
    }

    private static double clamp01(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }
}
