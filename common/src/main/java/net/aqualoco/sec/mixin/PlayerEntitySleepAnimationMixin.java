package net.aqualoco.sec.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerEntitySleepAnimationMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;stopSleepInBed(ZZ)V"
            )
    )
    private void seamlesssleep$forwardWakeUp(Player self,
                                             boolean skipSleepTimer,
                                             boolean updateSleepingPlayers) {
        self.stopSleepInBed(skipSleepTimer, updateSleepingPlayers);
    }
}
