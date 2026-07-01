package net.aqualoco.sec.mixin.network;

import com.mojang.authlib.GameProfile;
import net.aqualoco.sec.config.ServerConfigMutationService;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListOperatorAccessMixin {
    @Inject(method = "op(Lcom/mojang/authlib/GameProfile;)V", at = @At("TAIL"))
    private void seamlesssleep$syncAccessAfterOp(GameProfile profile, CallbackInfo ci) {
        ServerConfigMutationService.sendAccessToOnlineProfile((PlayerList) (Object) this, profile);
    }

    @Inject(method = "deop(Lcom/mojang/authlib/GameProfile;)V", at = @At("TAIL"))
    private void seamlesssleep$syncAccessAfterDeop(GameProfile profile, CallbackInfo ci) {
        ServerConfigMutationService.sendAccessToOnlineProfile((PlayerList) (Object) this, profile);
    }
}
