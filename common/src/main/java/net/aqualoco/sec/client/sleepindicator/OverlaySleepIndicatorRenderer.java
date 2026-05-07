package net.aqualoco.sec.client.sleepindicator;

import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.sleep.SleepAnimationVisualContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// Renders the legacy text sleep indicator through the shared indicator pipeline.
public final class OverlaySleepIndicatorRenderer implements SleepIndicatorRenderer {
    private static final int WIDTH = 180;
    private static final int HEIGHT = 10;

    @Override
    public String id() {
        return "overlay";
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public IndicatorSize measure(SleepIndicatorContext context) {
        Minecraft client = context.client();
        Component text = resolveText(context.visualContext());
        return new IndicatorSize(client.font.width(text), HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, SleepIndicatorContext context, float tickDelta) {
        Minecraft client = context.client();
        Component text = resolveText(context.visualContext());

        long now = System.currentTimeMillis();
        double pulse = 0.6D + 0.4D * Math.sin(now / 400.0D);
        double clamped = Math.max(0.2D, Math.min(1.0D, pulse));
        int alpha = (int) (clamped * context.alpha() * 255.0D);
        if (alpha <= 0) {
            return;
        }
        int color = (alpha << 24) | 0x00FFFFFF;
        graphics.drawString(client.font, text, 0, 0, color, true);
    }

    private static Component resolveText(SleepAnimationVisualContext visualContext) {
        SleepAnimationVisualContext resolved = visualContext == null
                ? SleepAnimationVisualContext.NIGHT
                : visualContext;
        if (SeamlessSleepServerConfigSnapshot.isOverrideOverlayText()
                && resolved != SleepAnimationVisualContext.MADE_IN_HEAVEN) {
            return Component.literal(SeamlessSleepServerConfigSnapshot.getOverlayCustomText());
        }

        return switch (resolved) {
            case DAY -> Component.translatable("seamlesssleep.text.sleeping_day");
            case STORM -> Component.translatable("seamlesssleep.text.sleeping_storm");
            case MADE_IN_HEAVEN -> Component.translatable("seamlesssleep.text.made_in_heaven");
            case NIGHT -> Component.translatable("seamlesssleep.text.sleeping");
        };
    }
}
