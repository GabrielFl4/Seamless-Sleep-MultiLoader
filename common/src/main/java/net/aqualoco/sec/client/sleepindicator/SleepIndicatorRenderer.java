package net.aqualoco.sec.client.sleepindicator;

import net.minecraft.client.gui.GuiGraphics;

// Defines the local renderer contract for sleep indicator models.
public interface SleepIndicatorRenderer {
    String id();

    int width();

    int height();

    default IndicatorSize measure(SleepIndicatorContext context) {
        return new IndicatorSize(width(), height());
    }

    void render(GuiGraphics graphics, SleepIndicatorContext context, float tickDelta);
}
