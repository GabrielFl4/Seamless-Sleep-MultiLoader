package net.aqualoco.sec.mixin.compat.vivecraft;

import net.aqualoco.sec.client.VivecraftSleepingBodyOffsetCompensation;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.vivecraft.client.render.VRPlayerModel", remap = false)
public abstract class VivecraftVRPlayerModelSleepingOffsetMixin {

    @Inject(
            method = "animateVRModel",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private static void seamlesssleep$beginSleepingOffsetCompensation(PlayerModel model,
                                                                      AvatarRenderState renderState,
                                                                      Vector3f tempV,
                                                                      Vector3f tempV2,
                                                                      Matrix3f tempM,
                                                                      CallbackInfo ci) {
        VivecraftSleepingBodyOffsetCompensation.begin(renderState);
    }

    @Inject(
            method = "animateVRModel",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private static void seamlesssleep$endSleepingOffsetCompensation(PlayerModel model,
                                                                    AvatarRenderState renderState,
                                                                    Vector3f tempV,
                                                                    Vector3f tempV2,
                                                                    Matrix3f tempM,
                                                                    CallbackInfo ci) {
        VivecraftSleepingBodyOffsetCompensation.end();
    }
}
