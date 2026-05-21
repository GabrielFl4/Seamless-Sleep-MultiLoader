package net.aqualoco.sec.client.sleepindicator.biomeclock;

import net.aqualoco.sec.client.sleepindicator.SleepIndicatorContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;

// Resolves client-only special scenes without treating them as regular biome categories.
public final class BiomeClockSceneResolver {
    private static final int CAVERN_MAX_Y = 50;
    private static final int OCEAN_CAVERN_MAX_Y = 35;
    private static final int CAVERN_MAX_SKY_LIGHT = 0;

    private BiomeClockSceneResolver() {
    }

    public static BiomeClockSceneKind resolve(SleepIndicatorContext context) {
        if (context == null) {
            return BiomeClockSceneKind.NORMAL;
        }

        ClientLevel level = context.level();
        if (level == null) {
            return BiomeClockSceneKind.NORMAL;
        }

        try {
            if (Level.NETHER.equals(level.dimension())) {
                return BiomeClockSceneKind.NETHER;
            }
            if (Level.END.equals(level.dimension())) {
                return BiomeClockSceneKind.END;
            }
            if (!Level.OVERWORLD.equals(level.dimension())) {
                return BiomeClockSceneKind.UNKNOWN_DIMENSION;
            }

            LocalPlayer player = context.player();
            if (player == null) {
                return BiomeClockSceneKind.NORMAL;
            }
            if (!level.dimensionType().hasSkyLight()) {
                return BiomeClockSceneKind.NORMAL;
            }

            BlockPos pos = player.blockPosition();
            if (pos.getY() > cavernMaxY(context)) {
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

    private static int cavernMaxY(SleepIndicatorContext context) {
        return switch (context.biomeClockCategory()) {
            case OCEAN, OCEAN_DEEP, OCEAN_WARM, OCEAN_FROZEN -> OCEAN_CAVERN_MAX_Y;
            default -> CAVERN_MAX_Y;
        };
    }
}
