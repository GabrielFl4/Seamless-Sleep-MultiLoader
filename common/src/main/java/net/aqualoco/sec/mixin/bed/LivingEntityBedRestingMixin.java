package net.aqualoco.sec.mixin.bed;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.bed.BedRestingPlayer;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Redirects sleeping position/orientation queries so the temporary resting pose behaves like a real bed pose.
@Mixin(LivingEntity.class)
public abstract class LivingEntityBedRestingMixin {

    @Inject(method = "getBedOrientation", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$useRestingBedOrientation(CallbackInfoReturnable<Direction> cir) {
        if (!((Object) this instanceof Player player) || player.isSleeping() || !BedRestingHelper.isResting(player)) {
            return;
        }

        Direction direction = BedRestingHelper.getRestingBedDirection(player);
        if (direction != null) {
            cir.setReturnValue(direction);
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void seamlesssleep$leaveRestingBeforeDamage(ServerLevel serverLevel, DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player
                && player instanceof BedRestingPlayer restingPlayer
                && restingPlayer.seamlesssleep$isResting()) {
            restingPlayer.seamlesssleep$stopResting(true, true);
        }
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setXRot(F)V"
            )
    )
    private void seamlesssleep$keepSleepingPitchWhenLookingAround(LivingEntity livingEntity, float xRot) {
        if (livingEntity instanceof Player player
                && BedRestingHelper.isOverworldWorkflow(player)
                && player.isSleeping()
                && xRot == 0.0F) {
            return;
        }

        livingEntity.setXRot(xRot);
    }
}
