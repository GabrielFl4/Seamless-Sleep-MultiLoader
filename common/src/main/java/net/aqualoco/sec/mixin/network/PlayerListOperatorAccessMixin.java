package net.aqualoco.sec.mixin.network;

import net.aqualoco.sec.config.ServerConfigMutationService;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(PlayerList.class)
public abstract class PlayerListOperatorAccessMixin {
    @Inject(
            method = "op(Lnet/minecraft/server/players/NameAndId;Ljava/util/Optional;Ljava/util/Optional;)V",
            at = @At("TAIL")
    )
    private void seamlesssleep$syncAccessAfterDetailedOp(NameAndId profile,
                                                         Optional<?> permissions,
                                                         Optional<Boolean> bypassPlayerLimit,
                                                         CallbackInfo ci) {
        ServerConfigMutationService.sendAccessToOnlineProfile((PlayerList) (Object) this, profile);
    }

    @Inject(method = "deop(Lnet/minecraft/server/players/NameAndId;)V", at = @At("TAIL"))
    private void seamlesssleep$syncAccessAfterDeop(NameAndId profile, CallbackInfo ci) {
        ServerConfigMutationService.sendAccessToOnlineProfile((PlayerList) (Object) this, profile);
    }
}
