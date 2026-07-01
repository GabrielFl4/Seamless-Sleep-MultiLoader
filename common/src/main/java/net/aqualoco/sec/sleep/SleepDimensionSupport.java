package net.aqualoco.sec.sleep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;

// Separates "this bed can use the managed workflow" from "this sleep can advance time now".
public final class SleepDimensionSupport {

    private SleepDimensionSupport() {
    }

    public static boolean supportsManagedBedWorkflow(Player player, @Nullable BlockPos bedPos) {
        return player != null && supportsManagedBedWorkflow(player.level(), bedPos);
    }

    public static boolean supportsManagedBedWorkflow(Level level, @Nullable BlockPos bedPos) {
        if (level == null || bedPos == null) {
            return false;
        }

        return level.dimensionType().bedWorks();
    }

    public static boolean canCountForSleepNow(ServerPlayer player, @Nullable BlockPos bedPos) {
        if (player == null || bedPos == null || !supportsManagedBedWorkflow(player, bedPos)) {
            return false;
        }

        Level level = player.level();
        return level.dimensionType().natural() && !level.isBrightOutside();
    }

    public static boolean supportsSleepAnimation(ServerLevel level) {
        if (level == null) {
            return false;
        }
        if (Level.OVERWORLD.equals(level.dimension())) {
            return true;
        }
        return level.dimensionType().bedWorks()
                && level.canSleepThroughNights()
                && hasManagedBedWorkflowPlayer(level);
    }

    public static boolean supportsClientSleepAnimation(Level level) {
        return level != null && level.dimensionType().bedWorks();
    }

    public static boolean shouldUseManagedSleepStatus(List<ServerPlayer> players) {
        ServerLevel level = inferLevel(players);
        if (level == null) {
            return false;
        }
        if (Level.OVERWORLD.equals(level.dimension())) {
            return true;
        }
        return supportsSleepAnimation(level);
    }

    public static boolean supportsWorldAcceleration(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }

    private static boolean hasManagedBedWorkflowPlayer(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.isSleeping()
                    && supportsManagedBedWorkflow(player, player.getSleepingPos().orElse(null))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ServerLevel inferLevel(List<ServerPlayer> players) {
        if (players == null) {
            return null;
        }
        for (ServerPlayer player : players) {
            if (player != null && player.level() instanceof ServerLevel level) {
                return level;
            }
        }
        return null;
    }

    private static boolean isKnownVanillaExplodingDimension(Level level) {
        return Level.NETHER.equals(level.dimension()) || Level.END.equals(level.dimension());
    }
}
