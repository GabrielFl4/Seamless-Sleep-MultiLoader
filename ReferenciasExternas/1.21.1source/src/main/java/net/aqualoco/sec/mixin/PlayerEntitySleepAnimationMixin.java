package net.aqualoco.sec.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySleepAnimationMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;wakeUp(ZZ)V"
            )
    )
    private void aquasec$forwardWakeUp(PlayerEntity self,
                                       boolean skipSleepTimer,
                                       boolean updateSleepingPlayers) {
        
        self.wakeUp(skipSleepTimer, updateSleepingPlayers);
    }
}

