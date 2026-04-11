package net.aqualoco.sec.bed;

import net.aqualoco.sec.SeamlessSleepCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// Centralizes the bed workflow rules shared by mixins and client helpers.
// This class is where the "feel" of resting in bed is tuned and where the
// temporary resting state is mapped onto vanilla bed assumptions.
public final class BedRestingHelper {

    // Positional tuning for the custom resting pose and first-person camera.
    public static final double BED_REST_Y = 0.6875D;
    public static final double REST_CAMERA_Y_OFFSET = 0.3D;

    // Mouse response while only resting in bed.
    public static final float REST_LOOK_SCALE = 0.30F;
    public static final double REST_LOOK_SMOOTH_FACTOR = 0.15D;

    // Final mouse response after the Seamless skip has fully ramped in.
    public static final double ANIMATION_LOOK_SCALE = 0.15D;
    public static final double ANIMATION_LOOK_SMOOTH_FACTOR = 0.08D;

    // Higher values make the skip damping bite earlier in the animation.
    public static final double ANIMATION_LOOK_BLEND_EXPONENT = 2.0D;

    // Bed look limits relative to the vanilla bed forward direction.
    public static final float REST_MAX_YAW = 100.0F;
    public static final float REST_MIN_CAMERA_PITCH = -100.0F;

    // Max camera pitch (looking down) is -0.1 and not 0 to avoid visual issue mine has (weirdos)
    public static final float REST_MAX_CAMERA_PITCH = -0.1F;

    private BedRestingHelper() {
    }

    public static boolean isResting(Player player) {
        return player instanceof BedRestingPlayer restingPlayer && restingPlayer.seamlesssleep$isResting();
    }

    public static boolean isOverworldWorkflow(Player player) {
        return player.level().dimension().equals(Level.OVERWORLD);
    }

    public static boolean isPreAnimationBedStateServer(Player player) {
        if (!isOverworldWorkflow(player)) {
            return false;
        }
        return isResting(player)
                || (player.isSleeping() && !SeamlessSleepCommon.OVERWORLD_SLEEP_ANIMATION.isActive());
    }

    public static boolean isManagedBedStateServer(Player player) {
        return isOverworldWorkflow(player) && (isResting(player) || player.isSleeping());
    }

    @Nullable
    public static BlockPos getRestingBedPos(Player player) {
        if (player instanceof BedRestingPlayer restingPlayer) {
            return restingPlayer.seamlesssleep$getRestingBedPos().orElse(null);
        }
        return null;
    }

    @Nullable
    public static Direction getRestingBedDirection(Player player) {
        return getBedDirection(player.level(), getRestingBedPos(player));
    }

    @Nullable
    public static Direction getBedDirection(Level level, @Nullable BlockPos bedPos) {
        if (bedPos == null) {
            return null;
        }

        BlockState state = level.getBlockState(bedPos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return null;
        }
        return state.getValue(BedBlock.FACING);
    }

    public static boolean canStartResting(Player player, BlockPos bedPos, Direction direction) {
        return isReachableBedBlock(player, bedPos, direction) && !isBedBlocked(player, bedPos, direction);
    }

    public static boolean isReachableBedBlock(Player player, BlockPos bedPos, Direction direction) {
        return isReachableBedBlock(player, bedPos) || isReachableBedBlock(player, bedPos.relative(direction.getOpposite()));
    }

    public static boolean isReachableBedBlock(Player player, BlockPos pos) {
        Vec3 center = Vec3.atBottomCenterOf(pos);
        return Math.abs(player.getX() - center.x()) <= 3.0D
                && Math.abs(player.getY() - center.y()) <= 2.0D
                && Math.abs(player.getZ() - center.z()) <= 3.0D;
    }

    public static boolean isBedBlocked(Player player, BlockPos bedPos, Direction direction) {
        BlockPos above = bedPos.above();
        return isSuffocating(player.level(), above) || isSuffocating(player.level(), above.relative(direction.getOpposite()));
    }

    private static boolean isSuffocating(Level level, BlockPos pos) {
        return level.getBlockState(pos).isSuffocating(level, pos);
    }

    public static void setBedOccupied(Level level, BlockPos bedPos, boolean occupied) {
        BlockState state = level.getBlockState(bedPos);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.OCCUPIED) == occupied) {
            return;
        }
        level.setBlock(bedPos, state.setValue(BedBlock.OCCUPIED, occupied), 3);
    }

    public static Vec3 getBedRestPosition(BlockPos bedPos) {
        return new Vec3(
                bedPos.getX() + 0.5D,
                bedPos.getY() + BED_REST_Y,
                bedPos.getZ() + 0.5D
        );
    }

    public static float getBedBaseYaw(Direction direction) {
        return direction.toYRot() - 180.0F;
    }

    public static float clampYawToBed(float yaw, Direction direction) {
        float baseYaw = getBedBaseYaw(direction);
        float relativeYaw = Mth.wrapDegrees(yaw - baseYaw);
        return Mth.wrapDegrees(baseYaw + Mth.clamp(relativeYaw, -REST_MAX_YAW, REST_MAX_YAW));
    }

    public static float clampPitch(float pitch) {
        return Mth.clamp(pitch, REST_MIN_CAMERA_PITCH, REST_MAX_CAMERA_PITCH);
    }

    // Converts raw animation progress into a feel-oriented blend for camera damping.
    public static double getAnimationLookBlend(double animationProgress) {
        double clamped = Mth.clamp(animationProgress, 0.0D, 1.0D);
        return 1.0D - Math.pow(1.0D - clamped, ANIMATION_LOOK_BLEND_EXPONENT);
    }

    public static double getLookScaleForAnimationProgress(double animationProgress) {
        return Mth.lerp(
                getAnimationLookBlend(animationProgress),
                REST_LOOK_SCALE,
                ANIMATION_LOOK_SCALE
        );
    }

    public static double getLookSmoothingForAnimationProgress(double animationProgress) {
        return Mth.lerp(
                getAnimationLookBlend(animationProgress),
                REST_LOOK_SMOOTH_FACTOR,
                ANIMATION_LOOK_SMOOTH_FACTOR
        );
    }

    public static Vec3 getRestingCameraOffset() {
        return new Vec3(0.0D, REST_CAMERA_Y_OFFSET, 0.0D);
    }

    public static Component getLeaveBedHintMessage() {
        return Component.translatable(
                "seamlesssleep.text.leave_bed",
                Component.keybind("key.sneak").withStyle(ChatFormatting.BOLD)
        );
    }

    @Nullable
    public static Component getCurrentBedProblemMessage(Level level, BlockPos bedPos) {
        return level.environmentAttributes()
                .getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE, bedPos)
                .asProblem()
                .message();
    }

    public static void showBedHudMessage(ServerPlayer player, @Nullable Component message) {
        if (message != null) {
            player.displayClientMessage(message, true);
        }
    }

    public static void showLeaveBedHint(ServerPlayer player) {
        player.displayClientMessage(getLeaveBedHintMessage(), true);
    }

    // Falls back to a simple safe spot above the bed if vanilla cannot resolve a stand-up position.
    public static Vec3 findStandUpPosition(LivingEntity entity, BlockPos bedPos, @Nullable Direction direction) {
        Direction resolvedDirection = direction != null ? direction : Direction.NORTH;
        return BedBlock.findStandUpPosition(entity.getType(), entity.level(), bedPos, resolvedDirection, entity.getYRot())
                .orElseGet(() -> {
                    BlockPos fallback = bedPos.above();
                    return new Vec3(fallback.getX() + 0.5D, fallback.getY() + 0.1D, fallback.getZ() + 0.5D);
                });
    }
}
