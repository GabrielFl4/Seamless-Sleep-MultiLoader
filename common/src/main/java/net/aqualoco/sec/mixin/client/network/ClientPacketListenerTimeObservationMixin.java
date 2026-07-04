package net.aqualoco.sec.mixin.client.network;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Observes authoritative vanilla time without letting it drive the held presentation timeline.
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerTimeObservationMixin {

    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void seamlesssleep$observeAuthoritativeDayTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        SeamlessSleepClientState.SLEEP_ANIMATION.observeAuthoritativeDayTime(packet.dayTime());
    }
}
