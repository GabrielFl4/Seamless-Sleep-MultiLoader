package net.aqualoco.sec.client.sleepindicator;

import net.minecraft.client.gui.GuiGraphics;

// Defines the fixed-size local renderer contract for sleep indicator models.
public interface SleepIndicatorRenderer {
    String id();

    int width();

    int height();

    void render(GuiGraphics graphics, SleepIndicatorContext context, float tickDelta);
}
