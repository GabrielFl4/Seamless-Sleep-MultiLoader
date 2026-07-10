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
            "addCloudsPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/CloudStatus;Lnet/minecraft/world/phys/Vec3;FIFLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V";

    @Unique
    private static final CloudAccelerationController seamlesssleep$cloudController =
            new CloudAccelerationController("NeoForge Better Clouds");

    @Unique
    private static final Class<?>[] seamlesssleep$ADD_CLOUDS_PASS_TYPES = {
            FrameGraphBuilder.class, CloudStatus.class, Vec3.class, float.class, int.class, float.class
    };

    @Unique
    private static final Class<?>[] seamlesssleep$ADD_CLOUDS_PASS_WITH_MODEL_VIEW_TYPES = {
            FrameGraphBuilder.class, CloudStatus.class, Vec3.class, float.class, int.class, float.class, Matrix4f.class, Matrix4f.class
    };

    @Unique
    private static Method seamlesssleep$addCloudsPass;

    @Unique
    private static Method seamlesssleep$addCloudsPassWithModelView;

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
        seamlesssleep$redirectAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight, null, null);
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
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix
    ) {
        seamlesssleep$redirectAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight, modelViewMatrix, projectionMatrix);
    }

    @Unique
    private void seamlesssleep$redirectAddCloudsPass(LevelRenderer instance,
                                                     FrameGraphBuilder frameGraphBuilder,
                                                     CloudStatus mode,
                                                     Vec3 cameraPos,
                                                     float cloudTime,
                                                     int color,
                                                     float cloudHeight,
                                                     Matrix4f modelViewMatrix,
                                                     Matrix4f projectionMatrix) {
        if (!BetterCloudsCompatBridge.isBridgeActive()) {
            seamlesssleep$callAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight, modelViewMatrix, projectionMatrix);
            return;
        }

        long now = System.currentTimeMillis();
        var sample = seamlesssleep$cloudController.sample(cloudTime, this.level, now);
        seamlesssleep$cloudController.logApplied(now, cloudTime, sample.adjustedValue());

        if (sample.wholeTicks() == this.ticks) {
            seamlesssleep$callAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, sample.adjustedValue(), color, cloudHeight, modelViewMatrix, projectionMatrix);
            return;
        }

        int originalTicks = this.ticks;
        try {
            this.ticks = sample.wholeTicks();
            seamlesssleep$callAddCloudsPass(instance, frameGraphBuilder, mode, cameraPos, sample.adjustedValue(), color, cloudHeight, modelViewMatrix, projectionMatrix);
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
                                                        Matrix4f modelViewMatrix,
                                                        Matrix4f projectionMatrix) {
        try {
            if (modelViewMatrix == null || projectionMatrix == null) {
                Method method = seamlesssleep$resolveAddCloudsPass();
                method.invoke(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight);
                return;
            }

            Method method = seamlesssleep$resolveAddCloudsPassWithModelView();
            method.invoke(instance, frameGraphBuilder, mode, cameraPos, cloudTime, color, cloudHeight, modelViewMatrix, projectionMatrix);
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
