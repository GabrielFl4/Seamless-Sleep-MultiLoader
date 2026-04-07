package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.client.BedCameraRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelBedBodyMixin {

    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;

    @Unique
    private boolean seamlesssleep$headVisibilityOverridden;

    @Unique
    private boolean seamlesssleep$hatVisibilityOverridden;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"))
    private void seamlesssleep$restoreHeadVisibility(HumanoidRenderState renderState, CallbackInfo ci) {
        if (this.seamlesssleep$headVisibilityOverridden) {
            this.head.visible = true;
            this.seamlesssleep$headVisibilityOverridden = false;
        }
        if (this.seamlesssleep$hatVisibilityOverridden) {
            this.hat.visible = true;
            this.seamlesssleep$hatVisibilityOverridden = false;
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void seamlesssleep$hideCameraHead(HumanoidRenderState renderState, CallbackInfo ci) {
        if (!((BedCameraRenderState) renderState).seamlesssleep$isCameraBody()) {
            return;
        }

        this.head.visible = false;
        this.hat.visible = false;
        this.seamlesssleep$headVisibilityOverridden = true;
        this.seamlesssleep$hatVisibilityOverridden = true;
    }
}
