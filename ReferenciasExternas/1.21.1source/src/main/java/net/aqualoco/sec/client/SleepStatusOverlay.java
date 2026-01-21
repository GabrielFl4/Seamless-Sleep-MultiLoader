package net.aqualoco.sec.client;

import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public final class SleepStatusOverlay {

    private SleepStatusOverlay() {}

    public static void register(ClientSleepAnimationState state) {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) {
                return;
            }

            if (!client.world.getRegistryKey().equals(World.OVERWORLD)) {
                return;
            }

            if (!state.isActive()) {
                return;
            }

            Text text = Text.translatable("seamlesssleep.text.sleeping");

            boolean hasXaero = FabricLoader.getInstance().isModLoaded("xaerominimap")
                    || FabricLoader.getInstance().isModLoaded("xaerominimapfair");

            long now = System.currentTimeMillis();
            double pulse = 0.6D + 0.4D * Math.sin(now / 400.0D); // 0.2..1.0
            double clamped = Math.max(0.2D, Math.min(1.0D, pulse));
            int alpha = (int) (clamped * 255.0D);
            int color = (alpha << 24) | 0x00FFFFFF; // texto branco variando alpha

            int x;
            int y;
            if (hasXaero) {
                int sw = drawContext.getScaledWindowWidth();
                int sh = drawContext.getScaledWindowHeight();
                int textWidth = client.textRenderer.getWidth(text);
                x = (sw - textWidth) / 2;
                y = sh - 68; // posicao aproximada da action bar
            } else {
                x = 6;
                y = 6;
            }
            drawContext.drawTextWithShadow(client.textRenderer, text, x, y, color);
        });
    }
}
