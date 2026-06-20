package net.aqualoco.sec.bed;

import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.SleepEligibilityMode;
import net.aqualoco.sec.compat.VivecraftCompat;
import net.aqualoco.sec.sleep.SleepAnimationStates;
import net.aqualoco.sec.sleep.SleepDimensionSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
        return isManagedBedState(player) && !isCountedForSleep(player);
    }

    public static boolean isOverworldWorkflow(Player player) {
        return player != null && Level.OVERWORLD.equals(player.level().dimension());
    }

    public static boolean isManagedBedWorkflowSupported(Player player) {
        if (player == null) {
            return false;
        }
        if (Level.OVERWORLD.equals(player.level().dimension())) {
            return true;
        }
        return SleepDimensionSupport.supportsManagedBedWorkflow(player, getRestingBedPos(player));
    }

    public static boolean isManagedBedWorkflowSupported(Player player, @Nullable BlockPos bedPos) {
        return player != null && SleepDimensionSupport.supportsManagedBedWorkflow(player, bedPos);
    }

    public static boolean isManagedBedState(Player player) {
        return isManagedBedWorkflowSupported(player) && player.isSleeping();
    }

    public static boolean isPreAnimationBedStateServer(Player player) {
        if (!isManagedBedState(player)) {
            return false;
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            var state = SleepAnimationStates.getIfPresent(serverLevel);
            return state == null || !state.isActive();
        }
        return true;
    }

    public static boolean isManagedBedStateServer(Player player) {
        return isManagedBedState(player);
    }

    public static boolean isCountedForSleep(Player player) {
        return player instanceof BedRestingPlayer restingPlayer
                && restingPlayer.seamlesssleep$isCountedForSleep();
    }

    public static boolean hasSleptLongEnough(ServerPlayer player, int delayTicks) {
        int clampedDelay = SeamlessSleepServerConfig.clampInt(
                delayTicks,
                SeamlessSleepServerConfig.MIN_FALL_ASLEEP_DELAY_TICKS,
                SeamlessSleepServerConfig.MAX_FALL_ASLEEP_DELAY_TICKS
        );
        return hasSleptLongEnoughClamped(player, clampedDelay);
    }

    public static boolean hasSleptLongEnoughClamped(ServerPlayer player, int clampedDelayTicks) {
        return isCountedForSleep(player)
                && player.isSleeping()
                && player instanceof BedRestingPlayer restingPlayer
                && restingPlayer.seamlesssleep$getFallAsleepDelayCounter() >= clampedDelayTicks;
    }

    public static boolean hasMadeInHeavenSleepLongEnough(ServerPlayer player, int delayTicks) {
        int clampedDelay = SeamlessSleepServerConfig.clampInt(
                delayTicks,
                SeamlessSleepServerConfig.MIN_FALL_ASLEEP_DELAY_TICKS,
                SeamlessSleepServerConfig.MAX_FALL_ASLEEP_DELAY_TICKS
        );
        return player.isSleeping()
                && canCountForMadeInHeaven(player, player.getSleepingPos().orElse(null))
                && player instanceof BedRestingPlayer restingPlayer
                && restingPlayer.seamlesssleep$getFallAsleepDelayCounter() >= clampedDelay;
    }

    public static float getAuthoritativeBedLookYaw(Player player) {
        return player instanceof BedRestingPlayer restingPlayer
                ? restingPlayer.seamlesssleep$getBedLookYaw()
                : player.getYRot();
    }

    public static float getAuthoritativeBedLookPitch(Player player) {
        return player instanceof BedRestingPlayer restingPlayer
                ? restingPlayer.seamlesssleep$getBedLookPitch()
                : player.getXRot();
    }

    public static void initializeAuthoritativeBedLook(ServerPlayer player, @Nullable BlockPos bedPos) {
        Direction direction = getBedDirection(player.level(), bedPos);
        float yaw = direction != null
                ? clampYawToBed(player.getYRot(), direction)
                : player.getYRot();
        float pitch = clampPitch(player.getXRot());
        setAuthoritativeBedLook(player, yaw, pitch);
    }

    public static void setAuthoritativeBedLook(ServerPlayer player, float yaw, float pitch) {
        Direction direction = player.getBedOrientation();
        float clampedYaw = direction != null ? clampYawToBed(yaw, direction) : yaw;
        float clampedPitch = clampPitch(pitch);

        if (player instanceof BedRestingPlayer restingPlayer) {
            float currentYaw = restingPlayer.seamlesssleep$getBedLookYaw();
            float currentPitch = restingPlayer.seamlesssleep$getBedLookPitch();
            if (Math.abs(Mth.wrapDegrees(clampedYaw - currentYaw)) >= 0.01F
                    || Math.abs(clampedPitch - currentPitch) >= 0.01F) {
                restingPlayer.seamlesssleep$setBedLook(clampedYaw, clampedPitch);
            }
        }

        applySleepingBedLook(player, clampedYaw, clampedPitch, direction);
    }

    public static void syncManagedSleepState(ServerPlayer player, boolean countedForSleep) {
        setManagedSleepStateWithoutSleepingListUpdate(player, countedForSleep);
        ServerLevel level = (ServerLevel) player.level();
        level.updateSleepingPlayerList();
    }

    public static void setManagedSleepStateWithoutSleepingListUpdate(ServerPlayer player, boolean countedForSleep) {
        if (player instanceof BedRestingPlayer restingPlayer) {
            restingPlayer.seamlesssleep$setCountedForSleep(countedForSleep);
        }
    }

    @Nullable
    public static BlockPos getRestingBedPos(Player player) {
        return player.getSleepingPos().orElse(null);
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

    public static boolean canCountForSleep(ServerPlayer player, @Nullable BlockPos bedPos) {
        if (!isManagedBedState(player) || !player.isAlive() || bedPos == null) {
            return false;
        }

        Direction direction = getBedDirection(player.level(), bedPos);
        if (direction == null) {
            return false;
        }

        SleepEligibilityMode eligibility = SeamlessSleepServerConfigManager.get().sleepEligibility;
        if (eligibility.preventsSleepSkip()) {
            return false;
        }

        boolean bedRuleAllowsSleep = SleepDimensionSupport.canCountForSleepNow(player, bedPos);
        if (!bedRuleAllowsSleep && !eligibility.allowsDaySleep()) {
            return false;
        }

        if (!canStartResting(player, bedPos, direction)) {
            return false;
        }

        if (player.isCreative() || eligibility.ignoresMonsterCheck()) {
            return true;
        }

        Vec3 center = Vec3.atBottomCenterOf(bedPos);
        return player.level()
                .getEntitiesOfClass(
                        Monster.class,
                        new AABB(center.x() - 8.0D, center.y() - 5.0D, center.z() - 8.0D, center.x() + 8.0D, center.y() + 5.0D, center.z() + 8.0D),
                        monster -> monster.isPreventingPlayerRest(player.level(), player)
                )
                .isEmpty();
    }

    public static boolean canCountForMadeInHeaven(ServerPlayer player, @Nullable BlockPos bedPos) {
        if (!isManagedBedState(player) || !player.isAlive() || bedPos == null) {
            return false;
        }
        if (SeamlessSleepServerConfigManager.get().sleepEligibility.preventsSleepSkip()) {
            return false;
        }

        Direction direction = getBedDirection(player.level(), bedPos);
        return direction != null && canStartResting(player, bedPos, direction);
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

    public static void applySleepingBedLook(Player player, float yaw, float pitch, @Nullable Direction direction) {
        float baseYaw = direction != null ? getBedBaseYaw(direction) : yaw;
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.setYBodyRot(baseYaw);
        if (player instanceof LivingEntity livingEntity) {
            livingEntity.yHeadRotO = yaw;
            livingEntity.yBodyRotO = baseYaw;
        }
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

    public static Component getLeaveBedHintMessage(Player player) {
        if (player instanceof ServerPlayer serverPlayer && VivecraftCompat.isServerVrActive(serverPlayer)) {
            return Component.translatable("seamlesssleep.text.leave_bed_vr");
        }

        return getLeaveBedHintMessage();
    }

    public static boolean isManagedSleepFallbackProblem(Player.@Nullable BedSleepingProblem problem) {
        return problem != null
                && !Player.BedSleepingProblem.TOO_FAR_AWAY.equals(problem)
                && !Player.BedSleepingProblem.OBSTRUCTED.equals(problem)
                && !Player.BedSleepingProblem.OTHER_PROBLEM.equals(problem);
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
        player.displayClientMessage(getLeaveBedHintMessage(player), true);
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
