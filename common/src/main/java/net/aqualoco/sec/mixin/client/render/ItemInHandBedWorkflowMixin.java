package net.aqualoco.sec.mixin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Cancels first-person hand rendering at the ItemInHandRenderer layer so shader-specific callers are covered too.
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandBedWorkflowMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$hideHandsWithItems(float tickDelta,
                                                  PoseStack poseStack,
                                                  SubmitNodeCollector submitNodeCollector,
                                                  LocalPlayer player,
                                                  int packedLight,
                                                  CallbackInfo ci) {
        if (ClientBedWorkflow.shouldHideVanillaHands(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void seamlesssleep$hideArmWithItem(AbstractClientPlayer player,
                                               float tickDelta,
                                               float pitch,
                                               InteractionHand hand,
                                               float swingProgress,
                                               ItemStack item,
                                               float equipProgress,
                                               PoseStack poseStack,
                                               SubmitNodeCollector submitNodeCollector,
                                               int packedLight,
                                               CallbackInfo ci) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && player == localPlayer && ClientBedWorkflow.shouldHideVanillaHands(localPlayer)) {
            ci.cancel();
        }
    }
}
