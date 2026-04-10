package net.aqualoco.sec.mixin.bed;

import com.mojang.datafixers.util.Either;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.bed.BedRestingPlayer;
import net.aqualoco.sec.network.BedHudNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

// Owns the synced resting state and the server-side promotion from resting into vanilla sleeping.
@Mixin(Player.class)
public abstract class PlayerBedRestingMixin implements BedRestingPlayer {

    @Unique
    private static final EntityDataAccessor<Boolean> seamlesssleep$RESTING =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);

    @Unique
    private static final EntityDataAccessor<Optional<BlockPos>> seamlesssleep$RESTING_BED_POS =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    @Unique
    private int seamlesssleep$restingEnterTick = Integer.MIN_VALUE;

    @Unique
    @Nullable
    private BedSleepingProblem seamlesssleep$lastRestingPromotionProblem;

    @Unique
    private boolean seamlesssleep$pendingBedHudSleepProgressSync;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void seamlesssleep$defineRestingData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(seamlesssleep$RESTING, false);
        builder.define(seamlesssleep$RESTING_BED_POS, Optional.empty());
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$keepSleepingPoseWhileResting(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!this.seamlesssleep$isResting()) {
            return;
        }

        self.setPose(Pose.SLEEPING);
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void seamlesssleep$tickRestingServer(CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (this.seamlesssleep$pendingBedHudSleepProgressSync) {
            if (serverPlayer.isSleeping()) {
                BedHudNetworking.syncSleepProgress((ServerLevel) serverPlayer.level());
            }
            this.seamlesssleep$pendingBedHudSleepProgressSync = false;
        }

        if (!this.seamlesssleep$isResting()) {
            return;
        }

        BlockPos bedPos = this.seamlesssleep$getRestingBedPos().orElse(null);
        Direction direction = BedRestingHelper.getRestingBedDirection(serverPlayer);
        if (bedPos == null || !serverPlayer.isAlive()) {
            this.seamlesssleep$stopResting(true, false);
            return;
        }

        if (direction == null) {
            this.seamlesssleep$stopResting(true, false);
            return;
        }

        if (!BedRestingHelper.isReachableBedBlock(serverPlayer, bedPos, direction)) {
            this.seamlesssleep$stopResting(true, false);
            return;
        }

        if (BedRestingHelper.isBedBlocked(serverPlayer, bedPos, direction)) {
            this.seamlesssleep$stopResting(true, true);
            return;
        }

        Vec3 restPos = BedRestingHelper.getBedRestPosition(bedPos);
        if (serverPlayer.position().distanceToSqr(restPos) > 1.0E-4D) {
            serverPlayer.snapTo(restPos.x, restPos.y, restPos.z, serverPlayer.getYRot(), serverPlayer.getXRot());
        }
        serverPlayer.setDeltaMovement(Vec3.ZERO);

        if (serverPlayer.tickCount > this.seamlesssleep$restingEnterTick + 5 && serverPlayer.isShiftKeyDown()) {
            this.seamlesssleep$stopResting(true, true);
            return;
        }

        if (serverPlayer.level().environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE, bedPos).canSleep(serverPlayer.level())) {
            Either<Player.BedSleepingProblem, Unit> sleepResult = serverPlayer.startSleepInBed(bedPos);
            if (sleepResult.right().isPresent()) {
                this.seamlesssleep$pendingBedHudSleepProgressSync = true;
                this.seamlesssleep$stopResting(false, false);
                return;
            }

            BedSleepingProblem problem = sleepResult.left().orElse(null);
            if (problem != null && problem != this.seamlesssleep$lastRestingPromotionProblem) {
                BedRestingHelper.showBedHudMessage(serverPlayer, problem.message());
                this.seamlesssleep$lastRestingPromotionProblem = problem;
            }
            return;
        }

        this.seamlesssleep$lastRestingPromotionProblem = null;
    }

    @Override
    public boolean seamlesssleep$isResting() {
        Player self = (Player) (Object) this;
        return self.getEntityData().get(seamlesssleep$RESTING);
    }

    @Override
    public Optional<BlockPos> seamlesssleep$getRestingBedPos() {
        Player self = (Player) (Object) this;
        return self.getEntityData().get(seamlesssleep$RESTING_BED_POS);
    }

    @Override
    public boolean seamlesssleep$startResting(BlockPos bedPos) {
        Player self = (Player) (Object) this;
        if (this.seamlesssleep$isResting() || self.isSleeping() || !self.isAlive() || !BedRestingHelper.isOverworldWorkflow(self)) {
            return false;
        }

        Direction direction = BedRestingHelper.getBedDirection(self.level(), bedPos);
        if (direction == null || !BedRestingHelper.canStartResting(self, bedPos, direction)) {
            return false;
        }

        float spawnYaw = self.getYRot();
        float spawnPitch = self.getXRot();
        float restYaw = BedRestingHelper.getBedBaseYaw(direction);
        Vec3 restPos = BedRestingHelper.getBedRestPosition(bedPos);

        BedRestingHelper.setBedOccupied(self.level(), bedPos, true);
        self.getEntityData().set(seamlesssleep$RESTING, true);
        self.getEntityData().set(seamlesssleep$RESTING_BED_POS, Optional.of(bedPos));
        this.seamlesssleep$restingEnterTick = self.tickCount;
        this.seamlesssleep$lastRestingPromotionProblem = null;
        this.seamlesssleep$pendingBedHudSleepProgressSync = false;

        self.snapTo(restPos.x, restPos.y, restPos.z, restYaw, 0.0F);
        self.setPose(Pose.SLEEPING);
        self.setDeltaMovement(Vec3.ZERO);
        self.setYHeadRot(restYaw);
        self.setYBodyRot(restYaw);

        if (self instanceof ServerPlayer serverPlayer) {
            serverPlayer.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(serverPlayer.level().dimension(), bedPos, spawnYaw, spawnPitch),
                            false
                    ),
                    true
            );
            if (serverPlayer.connection != null) {
                serverPlayer.connection.teleport(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot());
            }
        }

        Constants.debug("Player {} entered resting state at {}", self.getPlainTextName(), bedPos);
        return true;
    }

    @Override
    public void seamlesssleep$stopResting(boolean releaseBed, boolean syncPosition) {
        Player self = (Player) (Object) this;
        BlockPos bedPos = this.seamlesssleep$getRestingBedPos().orElse(null);
        Direction direction = BedRestingHelper.getRestingBedDirection(self);

        self.getEntityData().set(seamlesssleep$RESTING, false);
        self.getEntityData().set(seamlesssleep$RESTING_BED_POS, Optional.empty());
        this.seamlesssleep$restingEnterTick = Integer.MIN_VALUE;
        this.seamlesssleep$lastRestingPromotionProblem = null;
        if (!self.isSleeping()) {
            this.seamlesssleep$pendingBedHudSleepProgressSync = false;
        }

        if (releaseBed && bedPos != null && !self.isSleeping()) {
            BedRestingHelper.setBedOccupied(self.level(), bedPos, false);
        }

        if (!self.isSleeping()) {
            if (syncPosition && bedPos != null) {
                Vec3 standPos = BedRestingHelper.findStandUpPosition(self, bedPos, direction);
                Vec3 bedCenter = Vec3.atBottomCenterOf(bedPos);
                Vec3 lookVec = bedCenter.subtract(standPos);
                if (lookVec.lengthSqr() < 1.0E-6D) {
                    lookVec = new Vec3(0.0D, 0.0D, 1.0D);
                } else {
                    lookVec = lookVec.normalize();
                }
                float yaw = (float) Mth.wrapDegrees(Mth.atan2(lookVec.z, lookVec.x) * 180.0F / (float) Math.PI - 90.0F);
                self.snapTo(standPos.x, standPos.y, standPos.z, yaw, 0.0F);
                self.setYHeadRot(self.getYRot());
                self.setYBodyRot(self.getYRot());
            }
            self.setPose(Pose.STANDING);
            self.setDeltaMovement(Vec3.ZERO);
        }

        if (syncPosition && self instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            serverPlayer.connection.teleport(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot());
        }

        Constants.debug("Player {} left resting state", self.getPlainTextName());
    }
}
