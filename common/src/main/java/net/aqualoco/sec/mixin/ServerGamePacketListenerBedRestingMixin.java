package net.aqualoco.sec.mixin;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerBedRestingMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockUseItemOnWhileBedBound(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (BedRestingHelper.isManagedBedStateServer(this.player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockUseItemWhileBedBound(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (BedRestingHelper.isManagedBedStateServer(this.player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockEntityInteractWhileBedBound(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (BedRestingHelper.isManagedBedStateServer(this.player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockBreakActionsWhileBedBound(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!BedRestingHelper.isManagedBedStateServer(this.player)) {
            return;
        }

        switch (packet.getAction()) {
            case START_DESTROY_BLOCK:
            case ABORT_DESTROY_BLOCK:
            case STOP_DESTROY_BLOCK:
            case RELEASE_USE_ITEM:
                ci.cancel();
                break;
            default:
                break;
        }
    }
}
