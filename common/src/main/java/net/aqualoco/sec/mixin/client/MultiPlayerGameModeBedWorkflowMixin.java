package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeBedWorkflowMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockDestroyStart(net.minecraft.core.BlockPos loc, net.minecraft.core.Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.player != null && ClientBedWorkflow.shouldBlockGameplayInteractions(this.minecraft.player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockDestroyContinue(net.minecraft.core.BlockPos posBlock, net.minecraft.core.Direction directionFacing, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.player != null && ClientBedWorkflow.shouldBlockGameplayInteractions(this.minecraft.player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockUseOn(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir) {
        if (ClientBedWorkflow.shouldBlockGameplayInteractions(player)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (this.minecraft.player != null && ClientBedWorkflow.shouldBlockGameplayInteractions(this.minecraft.player)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockAttack(Player player, Entity targetEntity, CallbackInfo ci) {
        if (this.minecraft.player != null && ClientBedWorkflow.shouldBlockGameplayInteractions(this.minecraft.player)) {
            ci.cancel();
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockEntityInteract(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (this.minecraft.player != null && ClientBedWorkflow.shouldBlockGameplayInteractions(this.minecraft.player)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$blockEntityInteractAt(Player player, Entity target, EntityHitResult ray, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (this.minecraft.player != null && ClientBedWorkflow.shouldBlockGameplayInteractions(this.minecraft.player)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
