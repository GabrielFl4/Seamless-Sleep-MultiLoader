package net.aqualoco.sec.mixin.client;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.aqualoco.sec.client.CloudAccelerationController;
import net.aqualoco.sec.client.compat.BetterCloudsCompatBridge;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// NeoForge uses a LevelRenderer bridge because the direct Better Clouds Renderer mixin is not reliable there.
@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class NeoForgeBetterCloudsLevelRendererMixin {

    @Unique
    private static final String seamlesssleep$ADD_CLOUDS_PASS =
            "addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;FIF)V";

    @Unique
    private static final CloudAccelerationController seamlesssleep$cloudController =
            new CloudAccelerationController("NeoForge Better Clouds");

    @Shadow
    private ClientLevel level;

    @Shadow
    private int ticks;

    @Invoker("addCloudsPass")
    protected abstract void seamlesssleep$invokeAddCloudsPass(FrameGraphBuilder frameGraphBuilder,
                                                               Matrix4f modelViewMatrix,
                                                               Matrix4f projectionMatrix,
                                                               CloudStatus mode,
                                                               Vec3 cameraPos,
                                                               float cloudTime,
                                                               int color,
                                                               float cloudHeight);

    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;" + seamlesssleep$ADD_CLOUDS_PASS
            )
    )
    private void seamlesssleep$redirectAddCloudsPass(LevelRenderer instance,
                                                      FrameGraphBuilder frameGraphBuilder,
                                                      Matrix4f modelViewMatrix,
                                                      Matrix4f projectionMatrix,
                                                      CloudStatus mode,
                                                      Vec3 cameraPos,
                                                      float cloudTime,
                                                      int color,
                                                      float cloudHeight) {
        if (!BetterCloudsCompatBridge.isBridgeActive()) {
            seamlesssleep$invokeAddCloudsPass(
                    frameGraphBuilder,
                    modelViewMatrix,
                    projectionMatrix,
                    mode,
                    cameraPos,
                    cloudTime,
                    color,
                    cloudHeight
            );
            return;
        }

        long now = System.currentTimeMillis();
        var sample = seamlesssleep$cloudController.sample(cloudTime, this.level, now);
        seamlesssleep$cloudController.logApplied(now, cloudTime, sample.adjustedValue());

        if (sample.wholeTicks() == this.ticks) {
            seamlesssleep$invokeAddCloudsPass(
                    frameGraphBuilder,
                    modelViewMatrix,
                    projectionMatrix,
                    mode,
                    cameraPos,
                    sample.adjustedValue(),
                    color,
                    cloudHeight
            );
            return;
        }

        int originalTicks = this.ticks;
        try {
            this.ticks = sample.wholeTicks();
            seamlesssleep$invokeAddCloudsPass(
                    frameGraphBuilder,
                    modelViewMatrix,
                    projectionMatrix,
                    mode,
                    cameraPos,
                    sample.adjustedValue(),
                    color,
                    cloudHeight
            );
        } finally {
            this.ticks = originalTicks;
        }
    }
}
