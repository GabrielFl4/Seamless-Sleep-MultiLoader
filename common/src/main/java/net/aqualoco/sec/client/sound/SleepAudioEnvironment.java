package net.aqualoco.sec.client.sound;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;

final class SleepAudioEnvironment {
    private static final float SMOOTHING = 0.15F;
    private static final int CAVERN_MAX_Y = 50;
    private static final int CAVERN_MAX_SKY_LIGHT = 0;

    private float smoothedMain = 1.0F;
    private float smoothedWind = 1.0F;
    private boolean initialized;

    void update(ClientLevel level, LocalPlayer player) {
        Target target = resolveTarget(level, player);
        if (!this.initialized) {
            this.smoothedMain = target.main();
            this.smoothedWind = target.wind();
            this.initialized = true;
            return;
        }

        this.smoothedMain += (target.main() - this.smoothedMain) * SMOOTHING;
        this.smoothedWind += (target.wind() - this.smoothedWind) * SMOOTHING;
    }

    void updateImmediate(ClientLevel level, LocalPlayer player) {
        Target target = resolveTarget(level, player);
        this.smoothedMain = target.main();
        this.smoothedWind = target.wind();
        this.initialized = true;
    }

    void reset() {
        this.smoothedMain = 1.0F;
        this.smoothedWind = 1.0F;
        this.initialized = false;
    }

    float mainMultiplier() {
        return this.smoothedMain;
    }

    float windMultiplier() {
        return this.smoothedWind;
    }

    private static Target resolveTarget(ClientLevel level, LocalPlayer player) {
        return switch (resolveKind(level, player)) {
            case OPEN_SKY -> new Target(1.00F, 1.00F);
            case SHELTERED -> new Target(0.65F, 0.45F);
            case CAVERN -> new Target(0.22F, 0.18F);
            case SUBMERGED_WATER -> new Target(0.10F, 0.10F);
            case SUBMERGED_LAVA -> new Target(0.08F, 0.08F);
        };
    }

    private static Kind resolveKind(ClientLevel level, LocalPlayer player) {
        if (level == null || player == null) {
            return Kind.OPEN_SKY;
        }

        if (player.isEyeInFluid(FluidTags.LAVA)) {
            return Kind.SUBMERGED_LAVA;
        }
        if (player.isEyeInFluid(FluidTags.WATER) || player.isUnderWater()) {
            return Kind.SUBMERGED_WATER;
        }

        BlockPos eyePos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        boolean hasSkyLight = level.dimensionType().hasSkyLight();
        boolean canSeeSky = hasSkyLight && (level.canSeeSky(eyePos) || level.canSeeSky(player.blockPosition()));
        if (canSeeSky) {
            return Kind.OPEN_SKY;
        }

        if (hasSkyLight
                && player.getY() <= CAVERN_MAX_Y
                && level.getBrightness(LightLayer.SKY, player.blockPosition()) <= CAVERN_MAX_SKY_LIGHT) {
            return Kind.CAVERN;
        }

        return Kind.SHELTERED;
    }

    private enum Kind {
        OPEN_SKY,
        SHELTERED,
        CAVERN,
        SUBMERGED_WATER,
        SUBMERGED_LAVA
    }

    private record Target(float main,
                          float wind) {
    }
}
