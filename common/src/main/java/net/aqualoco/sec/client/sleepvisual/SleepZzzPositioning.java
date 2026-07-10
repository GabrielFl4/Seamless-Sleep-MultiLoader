package net.aqualoco.sec.client.sleepvisual;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.bed.BedRestingPlayer;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

// Resolves a stable bed/head anchor and the current bed-look direction for new Z spawns.
public final class SleepZzzPositioning {

    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final double HEAD_ANCHOR_UP_FROM_BED = 0.82D;
    private static final double HEAD_ANCHOR_FORWARD_FROM_BED = -0.18D;
    private static final double MOUTH_OFFSET_FORWARD = 0.22D;
    private static final double MOUTH_OFFSET_UP = 0.05D;
    private static final double SPAWN_RANDOM_RADIUS = 0.035D;

    private SleepZzzPositioning() {
    }

    public static Spawn resolve(Player player, float partialTick, Random random) {
        Direction bedDirection = BedRestingHelper.getRestingBedDirection(player);
        Vec3 fallbackForward = bedDirection == null
                ? horizontalForward(player.getYRot())
                : horizontalForward(BedRestingHelper.getBedBaseYaw(bedDirection));
        Vec3 headForward = resolveHeadForward(player, partialTick, fallbackForward);
        Vec3 horizontalForward = flatten(headForward, fallbackForward);
        Vec3 side = new Vec3(-horizontalForward.z(), 0.0D, horizontalForward.x()).normalize();
        Vec3 baseHeadPos = resolveHeadAnchor(player, partialTick, bedDirection, fallbackForward);

        double sideJitter = (random.nextDouble() - 0.5D) * SPAWN_RANDOM_RADIUS;
        double upJitter = (random.nextDouble() - 0.5D) * SPAWN_RANDOM_RADIUS;
        double forwardJitter = (random.nextDouble() - 0.5D) * SPAWN_RANDOM_RADIUS;
        Vec3 origin = baseHeadPos
                .add(headForward.scale(MOUTH_OFFSET_FORWARD + forwardJitter))
                .add(side.scale(sideJitter))
                .add(WORLD_UP.scale(MOUTH_OFFSET_UP + upJitter));

        return new Spawn(origin, horizontalForward, side);
    }

    private static Vec3 resolveHeadAnchor(Player player, float partialTick, Direction bedDirection, Vec3 fallbackForward) {
        BlockPos bedPos = BedRestingHelper.getRestingBedPos(player);
        if (bedPos != null && bedDirection != null) {
            BlockPos headPos = resolveHeadBlock(player, bedPos, bedDirection);
            return Vec3.atBottomCenterOf(headPos)
                    .add(0.0D, HEAD_ANCHOR_UP_FROM_BED, 0.0D)
                    .add(fallbackForward.scale(HEAD_ANCHOR_FORWARD_FROM_BED));
        }

        double x = lerp(partialTick, player.xOld, player.getX());
        double y = lerp(partialTick, player.yOld, player.getY());
        double z = lerp(partialTick, player.zOld, player.getZ());
        return new Vec3(x, y + player.getEyeHeight() * 0.82D, z);
    }

    private static BlockPos resolveHeadBlock(Player player, BlockPos bedPos, Direction bedDirection) {
        BlockState state = player.level().getBlockState(bedPos);
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)) {
            return state.getValue(BedBlock.PART) == BedPart.HEAD ? bedPos : bedPos.relative(bedDirection);
        }
        return bedPos;
    }

    private static Vec3 resolveHeadForward(Player player, float partialTick, Vec3 fallbackForward) {
        Minecraft client = Minecraft.getInstance();
        if (player instanceof LocalPlayer localPlayer && client.player == localPlayer && ClientBedWorkflow.isManagedBedState(localPlayer)) {
            return Vec3.directionFromRotation(ClientBedWorkflow.getCameraPitch(localPlayer), ClientBedWorkflow.getCameraYaw(localPlayer));
        }

        if (player instanceof BedRestingPlayer restingPlayer) {
            return Vec3.directionFromRotation(
                    restingPlayer.seamlesssleep$getVisualBedLookPitch(partialTick),
                    restingPlayer.seamlesssleep$getVisualBedLookYaw(partialTick)
            );
        }

        Vec3 vanillaLook = player.getLookAngle();
        return vanillaLook.lengthSqr() > 1.0E-5D ? vanillaLook : fallbackForward;
    }

    private static Vec3 horizontalForward(float yaw) {
        return flatten(Vec3.directionFromRotation(0.0F, yaw), new Vec3(0.0D, 0.0D, 1.0D));
    }

    private static Vec3 flatten(Vec3 vector, Vec3 fallback) {
        Vec3 flattened = new Vec3(vector.x(), 0.0D, vector.z());
        return flattened.lengthSqr() > 1.0E-5D ? flattened.normalize() : fallback.normalize();
    }

    private static double lerp(float partialTick, double previous, double current) {
        return previous + (current - previous) * partialTick;
    }

    public record Spawn(Vec3 origin, Vec3 forward, Vec3 side) {
    }
}
