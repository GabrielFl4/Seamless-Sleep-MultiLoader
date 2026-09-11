package net.aqualoco.sec.mixin.client.compat;

import net.aqualoco.sec.client.BetterCloudsSleepTimeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Keeps OpenGL cloud coverage in sync with the accelerated generator.
@Pseudo
@Mixin(targets = "com.qendolin.betterclouds.rendering.opengl.OpenGLRenderer", remap = false)
public abstract class BetterCloudsOpenGLSleepAccelerationMixin {

    @ModifyArg(
            method = "render(JFLorg/joml/Vector3d;Lorg/joml/Vector3d;Lnet/minecraft/client/renderer/culling/Frustum;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/qendolin/betterclouds/rendering/opengl/OpenGLRenderer;drawCoverage(FLorg/joml/Vector3d;Lorg/joml/Vector3d;Lnet/minecraft/client/renderer/culling/Frustum;Lcom/qendolin/betterclouds/mixin/provider/FogProvider$Fog;)V"
            ),
            index = 0,
            remap = false,
            require = 0
    )
    private float seamlesssleep$adjustBetterCloudsCoverageTime(float originalTime) {
        if (!((Object) this instanceof BetterCloudsSleepTimeAccess access)) {
            return originalTime;
        }

        return originalTime + access.seamlesssleep$getCloudPhaseOffset();
    }
}
