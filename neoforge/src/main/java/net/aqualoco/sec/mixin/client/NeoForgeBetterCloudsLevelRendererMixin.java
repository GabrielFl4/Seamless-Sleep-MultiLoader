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
    private static final String seamlesssleep$ADD_CLOUDS_PASS_WITH_MODEL_VIEW =
            "addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;JFIFLorg/joml/Matrix4f;)V";

    @Unique
    private static final String seamlesssleep$RENDER_LEVEL =
            "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V";

    @Unique
    private static final CloudAccelerationController seamlesssleep$cloudController =
            new CloudAccelerationController("NeoForge Better Clouds");

    @Shadow
    private ClientLevel level;

    @Shadow
    private int ticks;

    @Invoker("addCloudsPass")
    protected abstract void seamlesssleep$invokeAddCloudsPass(FrameGraphBuilder frameGraphBuilder, CloudStatus mode, Vec3 cameraPos, long time, float tickDelta, int color, float cloudHeight, Matrix4f modelViewMatrix);

    @Redirect(
            method = seamlesssleep$RENDER_LEVEL,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;" + seamlesssleep$ADD_CLOUDS_PASS_WITH_MODEL_VIEW
            )
    )
    private void seamlesssleep$redirectAddCloudsPass(
            LevelRenderer instance,
            FrameGraphBuilder frameGraphBuilder,
            CloudStatus mode,
            Vec3 cameraPos,
            long time,
            float tickDelta,
            int color,
            float cloudHeight,
            Matrix4f modelViewMatrix
    ) {
        if (!BetterCloudsCompatBridge.isBridgeActive()) {
            seamlesssleep$invokeAddCloudsPass(frameGraphBuilder, mode, cameraPos, time, tickDelta, color, cloudHeight, modelViewMatrix);
            return;
        }

        long now = System.currentTimeMillis();
        float baseTime = this.ticks + tickDelta;
        var sample = seamlesssleep$cloudController.sample(baseTime, this.level, now);
        seamlesssleep$cloudController.logApplied(now, baseTime, sample.adjustedValue());

        boolean overrideNeeded = sample.wholeTicks() != this.ticks
                || Math.abs(sample.partialTick() - tickDelta) > 0.0001F;
        if (!overrideNeeded) {
            seamlesssleep$invokeAddCloudsPass(frameGraphBuilder, mode, cameraPos, time, tickDelta, color, cloudHeight, modelViewMatrix);
            return;
        }

        int originalTicks = this.ticks;
        this.ticks = sample.wholeTicks();
        try {
            seamlesssleep$invokeAddCloudsPass(frameGraphBuilder, mode, cameraPos, time, sample.partialTick(), color, cloudHeight, modelViewMatrix);
        } finally {
            this.ticks = originalTicks;
        }
    }
}
