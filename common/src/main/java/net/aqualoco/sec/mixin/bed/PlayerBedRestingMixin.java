package net.aqualoco.sec.mixin.bed;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.bed.BedRestingPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Owns the synced managed-sleep count state while vanilla sleeping remains the only physical bed pose.
@Mixin(Player.class)
public abstract class PlayerBedRestingMixin implements BedRestingPlayer {

    @Unique
    private static final float seamlesssleep$REMOTE_BED_LOOK_LERP_TICKS = 3.0F;

    @Shadow
    private int sleepCounter;

    @Unique
    private static final EntityDataAccessor<Boolean> seamlesssleep$COUNTED_FOR_SLEEP =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Float> seamlesssleep$BED_LOOK_YAW =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
    @Unique
    private static final EntityDataAccessor<Float> seamlesssleep$BED_LOOK_PITCH =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);

    @Unique
    private boolean seamlesssleep$hasClientBedLookVisualState;

    @Unique
    private float seamlesssleep$clientPrevBedLookYaw;

    @Unique
    private float seamlesssleep$clientPrevBedLookPitch;

    @Unique
    private float seamlesssleep$clientTargetBedLookYaw;

    @Unique
    private float seamlesssleep$clientTargetBedLookPitch;

    @Unique
    private int seamlesssleep$clientBedLookUpdateTick;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void seamlesssleep$defineManagedSleepData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(seamlesssleep$COUNTED_FOR_SLEEP, false);
        builder.define(seamlesssleep$BED_LOOK_YAW, 0.0F);
        builder.define(seamlesssleep$BED_LOOK_PITCH, 0.0F);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void seamlesssleep$syncManagedSleepCountState(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) {
            this.seamlesssleep$tickClientBedLookVisualState(self);
        }

        if (BedRestingHelper.isManagedBedState(self) && !this.seamlesssleep$isCountedForSleep()) {
            this.sleepCounter = 0;
        }

        if (!((Object) this instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!BedRestingHelper.isManagedBedStateServer(serverPlayer)) {
            if (this.seamlesssleep$isCountedForSleep()) {
                this.seamlesssleep$setCountedForSleep(false);
            }
            return;
        }

        BlockPos bedPos = serverPlayer.getSleepingPos().orElse(null);
        boolean shouldCountForSleep = BedRestingHelper.canCountForSleep(serverPlayer, bedPos);

        if (this.seamlesssleep$isCountedForSleep() == shouldCountForSleep) {
            return;
        }

        this.sleepCounter = 0;
        BedRestingHelper.syncManagedSleepState(serverPlayer, shouldCountForSleep);
    }

    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void seamlesssleep$clearManagedSleepCountState(boolean wakeImmediately, boolean updateLevelForSleepingPlayers, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer serverPlayer)
                || !BedRestingHelper.isManagedBedStateServer(serverPlayer)) {
            return;
        }

        BedRestingHelper.syncManagedSleepState(serverPlayer, false);
    }

    @Override
    public boolean seamlesssleep$isCountedForSleep() {
        Player self = (Player) (Object) this;
        return self.getEntityData().get(seamlesssleep$COUNTED_FOR_SLEEP);
    }

    @Override
    public void seamlesssleep$setCountedForSleep(boolean countedForSleep) {
        Player self = (Player) (Object) this;
        self.getEntityData().set(seamlesssleep$COUNTED_FOR_SLEEP, countedForSleep);
    }

    @Override
    public float seamlesssleep$getBedLookYaw() {
        Player self = (Player) (Object) this;
        return self.getEntityData().get(seamlesssleep$BED_LOOK_YAW);
    }

    @Override
    public float seamlesssleep$getBedLookPitch() {
        Player self = (Player) (Object) this;
        return self.getEntityData().get(seamlesssleep$BED_LOOK_PITCH);
    }

    @Override
    public void seamlesssleep$setBedLookYaw(float yaw) {
        Player self = (Player) (Object) this;
        self.getEntityData().set(seamlesssleep$BED_LOOK_YAW, yaw);
    }

    @Override
    public void seamlesssleep$setBedLookPitch(float pitch) {
        Player self = (Player) (Object) this;
        self.getEntityData().set(seamlesssleep$BED_LOOK_PITCH, pitch);
    }

    @Override
    public float seamlesssleep$getVisualBedLookYaw(float partialTick) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide() || !this.seamlesssleep$hasClientBedLookVisualState) {
            return this.seamlesssleep$getBedLookYaw();
        }

        return Mth.rotLerp(
                this.seamlesssleep$getClientBedLookVisualProgress(self, partialTick),
                this.seamlesssleep$clientPrevBedLookYaw,
                this.seamlesssleep$clientTargetBedLookYaw
        );
    }

    @Override
    public float seamlesssleep$getVisualBedLookPitch(float partialTick) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide() || !this.seamlesssleep$hasClientBedLookVisualState) {
            return this.seamlesssleep$getBedLookPitch();
        }

        return Mth.lerp(
                this.seamlesssleep$getClientBedLookVisualProgress(self, partialTick),
                this.seamlesssleep$clientPrevBedLookPitch,
                this.seamlesssleep$clientTargetBedLookPitch
        );
    }

    @Unique
    private void seamlesssleep$tickClientBedLookVisualState(Player self) {
        float syncedYaw = self.getEntityData().get(seamlesssleep$BED_LOOK_YAW);
        float syncedPitch = self.getEntityData().get(seamlesssleep$BED_LOOK_PITCH);

        if (!this.seamlesssleep$hasClientBedLookVisualState) {
            this.seamlesssleep$hasClientBedLookVisualState = true;
            this.seamlesssleep$clientPrevBedLookYaw = syncedYaw;
            this.seamlesssleep$clientPrevBedLookPitch = syncedPitch;
            this.seamlesssleep$clientTargetBedLookYaw = syncedYaw;
            this.seamlesssleep$clientTargetBedLookPitch = syncedPitch;
            this.seamlesssleep$clientBedLookUpdateTick = self.tickCount;
            return;
        }

        if (!BedRestingHelper.isManagedBedState(self)) {
            this.seamlesssleep$clientPrevBedLookYaw = syncedYaw;
            this.seamlesssleep$clientPrevBedLookPitch = syncedPitch;
            this.seamlesssleep$clientTargetBedLookYaw = syncedYaw;
            this.seamlesssleep$clientTargetBedLookPitch = syncedPitch;
            this.seamlesssleep$clientBedLookUpdateTick = self.tickCount;
            return;
        }

        if (Math.abs(Mth.wrapDegrees(syncedYaw - this.seamlesssleep$clientTargetBedLookYaw)) < 0.01F
                && Math.abs(syncedPitch - this.seamlesssleep$clientTargetBedLookPitch) < 0.01F) {
            return;
        }

        this.seamlesssleep$clientPrevBedLookYaw = this.seamlesssleep$getVisualBedLookYaw(0.0F);
        this.seamlesssleep$clientPrevBedLookPitch = this.seamlesssleep$getVisualBedLookPitch(0.0F);
        this.seamlesssleep$clientTargetBedLookYaw = syncedYaw;
        this.seamlesssleep$clientTargetBedLookPitch = syncedPitch;
        this.seamlesssleep$clientBedLookUpdateTick = self.tickCount;
    }

    @Unique
    private float seamlesssleep$getClientBedLookVisualProgress(Player self, float partialTick) {
        float ticksSinceUpdate = (self.tickCount - this.seamlesssleep$clientBedLookUpdateTick) + partialTick;
        return Mth.clamp(ticksSinceUpdate / seamlesssleep$REMOTE_BED_LOOK_LERP_TICKS, 0.0F, 1.0F);
    }
}
