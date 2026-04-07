package net.aqualoco.sec.mixin.client.render.compat;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.CloudAccelerationController;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Reuses the vanilla sleep acceleration curve for Better Clouds by overriding the custom renderer time inputs.
@Pseudo
@Mixin(targets = "com.qendolin.betterclouds.clouds.Renderer", remap = false)
public abstract class BetterCloudsRendererSleepAccelerationMixin {

    @Shadow
    private ClientLevel world;

    @Unique
    private static boolean seamlesssleep$loggedHookOnce;

    @Unique
    private final CloudAccelerationController seamlesssleep$cloudController = new CloudAccelerationController("Better Clouds");

    @Unique
    private boolean seamlesssleep$preparedSampleValid;

    @Unique
    private float seamlesssleep$preparedAdjustedTime;

    @Unique
    private int seamlesssleep$preparedWholeTicks;

    @Unique
    private float seamlesssleep$preparedPartialTick;

    @Inject(
            method = "prepare(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;IFLorg/joml/Vector3d;)Lcom/qendolin/betterclouds/clouds/Renderer$PrepareResult;",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void seamlesssleep$prepareBetterCloudsTime(Matrix4f viewMat, Matrix4f projMat, int ticks, float tickDelta, Vector3d cam, CallbackInfoReturnable<Object> cir) {
        if (!seamlesssleep$loggedHookOnce) {
            seamlesssleep$loggedHookOnce = true;
            Constants.debug("Better Clouds acceleration hook active: Renderer.prepare/render visual time override.");
        }

        long now = System.currentTimeMillis();
        float baseTime = ticks + tickDelta;
        var sample = seamlesssleep$cloudController.sample(baseTime, this.world, now);
        seamlesssleep$cloudController.logApplied(now, baseTime, sample.adjustedValue());

        seamlesssleep$preparedSampleValid = true;
        seamlesssleep$preparedAdjustedTime = sample.adjustedValue();
        seamlesssleep$preparedWholeTicks = sample.wholeTicks();
        seamlesssleep$preparedPartialTick = sample.partialTick();
    }

    @ModifyArgs(
            method = "prepare(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;IFLorg/joml/Vector3d;)Lcom/qendolin/betterclouds/clouds/Renderer$PrepareResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/qendolin/betterclouds/clouds/ChunkedGenerator;update(Lorg/joml/Vector3d;IFLcom/qendolin/betterclouds/config/Config;F)V"
            ),
            remap = false,
            require = 0
    )
    private void seamlesssleep$adjustBetterCloudsGeneratorTime(Args args) {
        if (!seamlesssleep$preparedSampleValid) {
            return;
        }

        args.set(1, seamlesssleep$preparedWholeTicks);
        args.set(2, seamlesssleep$preparedPartialTick);
    }

    @ModifyArg(
            method = "render(IFLorg/joml/Vector3d;Lorg/joml/Vector3d;Lnet/minecraft/client/renderer/culling/Frustum;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/qendolin/betterclouds/clouds/Renderer;drawCoverage(FLorg/joml/Vector3d;Lorg/joml/Vector3d;Lnet/minecraft/client/renderer/culling/Frustum;Lcom/qendolin/betterclouds/clouds/fog/FogProvider$Fog;)V"
            ),
            index = 0,
            remap = false,
            require = 0
    )
    private float seamlesssleep$adjustBetterCloudsCoverageTime(float originalTime) {
        if (!seamlesssleep$preparedSampleValid) {
            return originalTime;
        }

        return seamlesssleep$preparedAdjustedTime;
    }
}
