package net.aqualoco.sec.mixin.bed;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Preserves look across the single vanilla stopSleepInBed wake path used by the managed bed workflow.
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerBedRestingMixin {

    @Unique
    private boolean seamlesssleep$preserveWakeLook;

    @Unique
    private float seamlesssleep$wakeYaw;

    @Unique
    private float seamlesssleep$wakePitch;

    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void seamlesssleep$captureWakeLook(boolean wakeImmediately, boolean updateLevelForSleepingPlayers, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        this.seamlesssleep$preserveWakeLook = BedRestingHelper.isManagedBedStateServer(self);
        if (!this.seamlesssleep$preserveWakeLook) {
            return;
        }

        this.seamlesssleep$wakeYaw = BedRestingHelper.getAuthoritativeBedLookYaw(self);
        this.seamlesssleep$wakePitch = BedRestingHelper.getAuthoritativeBedLookPitch(self);
    }

    @Inject(method = "stopSleepInBed", at = @At("TAIL"))
    private void seamlesssleep$restoreWakeLook(boolean wakeImmediately, boolean updateLevelForSleepingPlayers, CallbackInfo ci) {
        if (!this.seamlesssleep$preserveWakeLook) {
            return;
        }

        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.connection != null) {
            seamlesssleep$applyWakeLook(self, this.seamlesssleep$wakeYaw, this.seamlesssleep$wakePitch);
            self.connection.teleport(self.getX(), self.getY(), self.getZ(), this.seamlesssleep$wakeYaw, this.seamlesssleep$wakePitch);
        }
        seamlesssleep$applyWakeLook(self, this.seamlesssleep$wakeYaw, this.seamlesssleep$wakePitch);

        this.seamlesssleep$preserveWakeLook = false;
    }

    @Unique
    private static void seamlesssleep$applyWakeLook(ServerPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
    }
}
