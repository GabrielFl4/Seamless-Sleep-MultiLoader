package net.aqualoco.sec.client.sleepindicator.biomeclock;

import net.aqualoco.sec.client.sleepindicator.SleepIndicatorContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

// Resolves client-only special scenes without treating them as regular biome categories.
public final class BiomeClockSceneResolver {
    private static final int CAVERN_MAX_Y = 50;
    private static final int CAVERN_MAX_SKY_LIGHT = 0;

    private BiomeClockSceneResolver() {
    }

    public static BiomeClockSceneKind resolve(SleepIndicatorContext context) {
        if (context == null) {
            return BiomeClockSceneKind.NORMAL;
        }

        ClientLevel level = context.level();
        LocalPlayer player = context.player();
        if (level == null || player == null) {
            return BiomeClockSceneKind.NORMAL;
        }

        try {
            if (!level.dimensionType().hasSkyLight()) {
                return BiomeClockSceneKind.NORMAL;
            }

            BlockPos pos = player.blockPosition();
            if (pos.getY() > CAVERN_MAX_Y) {
                return BiomeClockSceneKind.NORMAL;
            }

            int skyLight = level.getBrightness(LightLayer.SKY, pos);
            return skyLight <= CAVERN_MAX_SKY_LIGHT
                    ? BiomeClockSceneKind.CAVERNS
                    : BiomeClockSceneKind.NORMAL;
        } catch (RuntimeException ignored) {
            return BiomeClockSceneKind.NORMAL;
        }
    }
}
