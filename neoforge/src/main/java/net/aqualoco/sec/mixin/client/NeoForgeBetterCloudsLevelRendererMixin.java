package net.aqualoco.sec.mixin.client;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.CloudAccelerationController;
import net.aqualoco.sec.client.compat.BetterCloudsCompatBridge;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.joml.Matrix4f;

import java.lang.reflect.Method;

// NeoForge uses a LevelRenderer bridge because the direct Better Clouds Renderer mixin is not reliable there.
@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class NeoForgeBetterCloudsLevelRendererMixin {

    @Unique
    private static final String seamlesssleep$ADD_CLOUDS_PASS =
            "addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;FIF)V";

    @Unique
    private static final String seamlesssleep$ADD_CLOUDS_PASS_WITH_MODEL_VIEW =
            "addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;FIFLorg/joml/Matrix4f;)V";

    @Unique
    private static final String seamlesssleep$ADD_CLOUDS_PASS_WITH_TICK =
            "addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;JFIF)V";

    @Unique
    private static final String seamlesssleep$ADD_CLOUDS_PASS_WITH_TICK_AND_MODEL_VIEW =
            "addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;JFIFLorg/joml/Matrix4f;)V";

    @Unique
    private static final CloudAccelerationController seamlesssleep$cloudController =
            new CloudAccelerationController("NeoForge Better Clouds");

    @Unique
    private static final Class<?>[] seamlesssleep$ADD_CLOUDS_PASS_TYPES = {
            FrameGraphBuilder.class, CloudStatus.class, Vec3.class, float.class, int.class, float.class
    };

    @Unique
    private static final Class<?>[] seamlesssleep$ADD_CLOUDS_PASS_WITH_MODEL_VIEW_TYPES = {
            FrameGraphBuilder.class, CloudStatus.class, Vec3.class, float.class, int.class, float.class, Matrix4f.class
    };

    @Unique
    private static final Class<?>[] seamlesssleep$ADD_CLOUDS_PASS_WITH_TICK_TYPES = {
            FrameGraphBuilder.class, CloudStatus.class, Vec3.class, long.class, float.class, int.class, float.class
    };

    @Unique
    private static final Class<?>[] seamlesssleep$ADD_CLOUDS_PASS_WITH_TICK_AND_MODEL_VIEW_TYPES = {
            FrameGraphBuilder.class, CloudStatus.class, Vec3.class, long.class, float.class, int.class, float.class, Matrix4f.class
    };

    @Unique
    private static Method seamlesssleep$addCloudsPass;

    @Unique
    private static Method seamlesssleep$addCloudsPassWithModelView;

    @Unique
    private static Method seamlesssleep$addCloudsPassWithTick;

    @Unique
    private static Method seamlesssleep$addCloudsPassWithTickAndModelView;

    @Unique
    private static boolean seamlesssleep$loggedAddCloudsPassReflectionFailure;

    @Shadow
    private ClientLevel level;

    @Shadow
    private int ticks;

    @Group(name = "seamlesssleep$addCloudsPass", min = 1, max = 1)
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;" + seamlesssleep$ADD_CLOUDS_PASS
            ),
            require = 0
    )
    private void seamlesssleep$redirectAddCloudsPass(
            LevelRenderer instance,
            FrameGraphBuilder frameGraphBuilder,
            CloudStatus mode,
            Vec3 cameraPos,
            float cloudTime,
            int color,
            float cloudHeight
    ) {
        seamlesssleep$redirectAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight, null);
    }

    @Group(name = "seamlesssleep$addCloudsPass", min = 1, max = 1)
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;" + seamlesssleep$ADD_CLOUDS_PASS_WITH_MODEL_VIEW
            ),
            require = 0
    )
    private void seamlesssleep$redirectAddCloudsPassWithModelView(
            LevelRenderer instance,
            FrameGraphBuilder frameGraphBuilder,
            CloudStatus mode,
            Vec3 cameraPos,
            float cloudTime,
            int color,
            float cloudHeight,
            Matrix4f modelViewMatrix
    ) {
        seamlesssleep$redirectAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight, modelViewMatrix);
    }

    @Group(name = "seamlesssleep$addCloudsPass", min = 1, max = 1)
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;" + seamlesssleep$ADD_CLOUDS_PASS_WITH_TICK
            ),
            require = 0
    )
    private void seamlesssleep$redirectAddCloudsPassWithTick(
            LevelRenderer instance,
            FrameGraphBuilder frameGraphBuilder,
            CloudStatus mode,
            Vec3 cameraPos,
            long time,
            float tickDelta,
            int color,
            float cloudHeight
    ) {
        seamlesssleep$redirectAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, time, tickDelta, color, cloudHeight, null);
    }

    @Group(name = "seamlesssleep$addCloudsPass", min = 1, max = 1)
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;" + seamlesssleep$ADD_CLOUDS_PASS_WITH_TICK_AND_MODEL_VIEW
            ),
            require = 0
    )
    private void seamlesssleep$redirectAddCloudsPassWithTickAndModelView(
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
        seamlesssleep$redirectAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, time, tickDelta, color, cloudHeight, modelViewMatrix);
    }

    @Unique
    private void seamlesssleep$redirectAddCloudsPass(LevelRenderer instance,
                                                     FrameGraphBuilder frameGraphBuilder,
                                                     CloudStatus mode,
                                                     Vec3 cameraPos,
                                                     float cloudTime,
                                                     int color,
                                                     float cloudHeight,
                                                     Matrix4f modelViewMatrix) {
        if (!BetterCloudsCompatBridge.isBridgeActive()) {
            seamlesssleep$callAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight, modelViewMatrix);
            return;
        }

        long now = System.currentTimeMillis();
        var sample = seamlesssleep$cloudController.sample(cloudTime, this.level, now);
        seamlesssleep$cloudController.logApplied(now, cloudTime, sample.adjustedValue());

        if (sample.wholeTicks() == this.ticks) {
            seamlesssleep$callAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, sample.adjustedValue(), color, cloudHeight, modelViewMatrix);
            return;
        }

        int originalTicks = this.ticks;
        try {
            this.ticks = sample.wholeTicks();
            seamlesssleep$callAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, sample.adjustedValue(), color, cloudHeight, modelViewMatrix);
        } finally {
            this.ticks = originalTicks;
        }
    }

    @Unique
    private void seamlesssleep$redirectAddCloudsPass(LevelRenderer instance,
                                                     FrameGraphBuilder frameGraphBuilder,
                                                     CloudStatus mode,
                                                     Vec3 cameraPos,
                                                     long time,
                                                     float tickDelta,
                                                     int color,
                                                     float cloudHeight,
                                                     Matrix4f modelViewMatrix) {
        if (!BetterCloudsCompatBridge.isBridgeActive()) {
            seamlesssleep$callAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, time, tickDelta, color, cloudHeight, modelViewMatrix);
            return;
        }

        long now = System.currentTimeMillis();
        float baseTime = this.ticks + tickDelta;
        var sample = seamlesssleep$cloudController.sample(baseTime, this.level, now);
        seamlesssleep$cloudController.logApplied(now, baseTime, sample.adjustedValue());

        boolean overrideNeeded = sample.wholeTicks() != this.ticks
                || Math.abs(sample.partialTick() - tickDelta) > 0.0001F;
        if (!overrideNeeded) {
            seamlesssleep$callAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, time, tickDelta, color, cloudHeight, modelViewMatrix);
            return;
        }

        int originalTicks = this.ticks;
        try {
            this.ticks = sample.wholeTicks();
            seamlesssleep$callAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, time, sample.partialTick(), color, cloudHeight, modelViewMatrix);
        } finally {
            this.ticks = originalTicks;
        }
    }

    @Unique
    private static void seamlesssleep$callAddCloudsPass(LevelRenderer instance,
                                                        FrameGraphBuilder frameGraphBuilder,
                                                        CloudStatus mode,
                                                        Vec3 cameraPos,
                                                        float cloudTime,
                                                        int color,
                                                        float cloudHeight,
                                                        Matrix4f modelViewMatrix) {
        try {
            if (modelViewMatrix == null) {
                Method method = seamlesssleep$resolveAddCloudsPass();
                method.invoke(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight);
                return;
            }

            Method method = seamlesssleep$resolveAddCloudsPassWithModelView();
            method.invoke(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight, modelViewMatrix);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            seamlesssleep$logAddCloudsPassReflectionFailure(exception);
        }
    }

    @Unique
    private static void seamlesssleep$callAddCloudsPass(LevelRenderer instance,
                                                        FrameGraphBuilder frameGraphBuilder,
                                                        CloudStatus mode,
                                                        Vec3 cameraPos,
                                                        long cloudTick,
                                                        float cloudTime,
                                                        int color,
                                                        float cloudHeight,
                                                        Matrix4f modelViewMatrix) {
        try {
            if (modelViewMatrix == null) {
                Method method = seamlesssleep$resolveAddCloudsPassWithTick();
                method.invoke(instance, frameGraphBuilder, mode, cameraPos, cloudTick, cloudTime, color, cloudHeight);
                return;
            }

            Method method = seamlesssleep$resolveAddCloudsPassWithTickAndModelView();
            method.invoke(instance, frameGraphBuilder, mode, cameraPos, cloudTick, cloudTime, color, cloudHeight, modelViewMatrix);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            seamlesssleep$logAddCloudsPassReflectionFailure(exception);
        }
    }

    @Unique
    private static Method seamlesssleep$resolveAddCloudsPass() throws NoSuchMethodException {
        if (seamlesssleep$addCloudsPass == null) {
            seamlesssleep$addCloudsPass = seamlesssleep$resolveAddCloudsPass(seamlesssleep$ADD_CLOUDS_PASS_TYPES);
        }
        return seamlesssleep$addCloudsPass;
    }

    @Unique
    private static Method seamlesssleep$resolveAddCloudsPassWithModelView() throws NoSuchMethodException {
        if (seamlesssleep$addCloudsPassWithModelView == null) {
            seamlesssleep$addCloudsPassWithModelView = seamlesssleep$resolveAddCloudsPass(seamlesssleep$ADD_CLOUDS_PASS_WITH_MODEL_VIEW_TYPES);
        }
        return seamlesssleep$addCloudsPassWithModelView;
    }

    @Unique
    private static Method seamlesssleep$resolveAddCloudsPassWithTick() throws NoSuchMethodException {
        if (seamlesssleep$addCloudsPassWithTick == null) {
            seamlesssleep$addCloudsPassWithTick = seamlesssleep$resolveAddCloudsPass(seamlesssleep$ADD_CLOUDS_PASS_WITH_TICK_TYPES);
        }
        return seamlesssleep$addCloudsPassWithTick;
    }

    @Unique
    private static Method seamlesssleep$resolveAddCloudsPassWithTickAndModelView() throws NoSuchMethodException {
        if (seamlesssleep$addCloudsPassWithTickAndModelView == null) {
            seamlesssleep$addCloudsPassWithTickAndModelView = seamlesssleep$resolveAddCloudsPass(seamlesssleep$ADD_CLOUDS_PASS_WITH_TICK_AND_MODEL_VIEW_TYPES);
        }
        return seamlesssleep$addCloudsPassWithTickAndModelView;
    }

    @Unique
    private static Method seamlesssleep$resolveAddCloudsPass(Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = LevelRenderer.class.getDeclaredMethod("addCloudsPass", parameterTypes);
        method.setAccessible(true);
        return method;
    }

    @Unique
    private static void seamlesssleep$logAddCloudsPassReflectionFailure(Throwable exception) {
        if (seamlesssleep$loggedAddCloudsPassReflectionFailure) {
            return;
        }
        seamlesssleep$loggedAddCloudsPassReflectionFailure = true;
        Constants.warn("NeoForge cloud pass bridge could not call LevelRenderer#addCloudsPass: {}", exception.getClass().getSimpleName());
    }
}
