package net.aqualoco.sec.mixin.client.compat;

import net.aqualoco.sec.client.BetterCloudsSleepTimeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Keeps Blaze3D cloud wind in sync with the accelerated generator.
@Pseudo
@Mixin(targets = "com.qendolin.betterclouds.rendering.blaze3d.Blaze3DRenderer", remap = false)
public abstract class BetterCloudsBlaze3DSleepAccelerationMixin {

    @ModifyVariable(
            method = "render(JFLorg/joml/Vector3d;Lorg/joml/Vector3d;Lnet/minecraft/client/renderer/culling/Frustum;)V",
            at = @At("STORE"),
            name = "cloudTimeSeconds",
            remap = false,
            require = 0
    )
    private float seamlesssleep$adjustBetterCloudsWindTime(float originalTimeSeconds) {
        if (!((Object) this instanceof BetterCloudsSleepTimeAccess access)) {
            return originalTimeSeconds;
        }

        return originalTimeSeconds + access.seamlesssleep$getCloudPhaseOffset() / 20.0F;
    }
}
