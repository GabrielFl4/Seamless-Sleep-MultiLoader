package net.aqualoco.sec.client;

import net.aqualoco.sec.client.sleepindicator.SleepIndicatorSystem;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

// Compatibility bridge for platform HUD hooks that still call the old overlay entrypoint.
public final class SleepStatusOverlay {

    private SleepStatusOverlay() {
    }

    public static void render(GuiGraphics graphics, ClientSleepAnimationState state) {
        SleepIndicatorSystem.render(graphics, DeltaTracker.ONE, state);
    }
}
