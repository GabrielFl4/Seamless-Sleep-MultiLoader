package net.aqualoco.sec.mixin.compat.vivecraft;

import net.aqualoco.sec.client.VivecraftSleepingBodyOffsetCompensation;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.vivecraft.client.utils.ModelUtils", remap = false)
public abstract class VivecraftModelUtilsSleepingOffsetMixin {

    @Inject(
            method = "worldToModel",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private static void seamlesssleep$compensateSleepingBodyOffset(HumanoidRenderState renderState,
                                                                   Vector3fc position,
                                                                   @Coerce Object rotInfo,
                                                                   float bodyYaw,
                                                                   boolean useWorldScale,
                                                                   Vector3f out,
                                                                   CallbackInfo ci) {
        VivecraftSleepingBodyOffsetCompensation.compensateWorldToModel(renderState, rotInfo, useWorldScale, out);
    }
}
