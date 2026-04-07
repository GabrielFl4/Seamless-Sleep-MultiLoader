package net.aqualoco.sec.mixin;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.bed.BedRestingPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerBedRestingMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void seamlesssleep$leaveBedWithShiftBeforeAnimation(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (BedRestingHelper.isResting(self)
                || !BedRestingHelper.isPreAnimationBedStateServer(self)
                || !self.isSleeping()
                || self.getSleepTimer() <= 5
                || !self.isShiftKeyDown()) {
            return;
        }

        self.stopSleepInBed(false, true);
    }

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void seamlesssleep$clearRestingStateOnDisconnect(CallbackInfo ci) {
        this.seamlesssleep$clearRestingWithoutSnap();
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"))
    private void seamlesssleep$clearRestingBeforeSimpleTeleport(double x, double y, double z, CallbackInfo ci) {
        this.seamlesssleep$clearRestingWithoutSnap();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at = @At("HEAD"))
    private void seamlesssleep$clearRestingBeforeLevelTeleport(ServerLevel level,
                                                               double x,
                                                               double y,
                                                               double z,
                                                               Set<Relative> relatives,
                                                               float yaw,
                                                               float pitch,
                                                               boolean setCamera,
                                                               CallbackInfoReturnable<Boolean> cir) {
        this.seamlesssleep$clearRestingWithoutSnap();
    }

    @Inject(method = "setServerLevel", at = @At("HEAD"))
    private void seamlesssleep$clearRestingBeforeServerLevelSwap(ServerLevel serverLevel, CallbackInfo ci) {
        this.seamlesssleep$clearRestingWithoutSnap();
    }

    private void seamlesssleep$clearRestingWithoutSnap() {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self instanceof BedRestingPlayer restingPlayer && restingPlayer.seamlesssleep$isResting()) {
            restingPlayer.seamlesssleep$stopResting(true, false);
        }
    }
}
