package net.aqualoco.sec.mixin.bed;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;

// Preserves real pitch while the player is in the managed sleeping workflow.
@Mixin(LivingEntity.class)
public abstract class LivingEntityBedRestingMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setXRot(F)V"
            )
    )
    private void seamlesssleep$keepSleepingPitchWhenLookingAround(LivingEntity livingEntity, float xRot) {
        if (livingEntity instanceof Player player
                && BedRestingHelper.isManagedBedState(player)
                && xRot == 0.0F) {
            return;
        }

        livingEntity.setXRot(xRot);
    }
}
