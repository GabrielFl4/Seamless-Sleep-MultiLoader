package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

// Optional ShoulderSurfing bridge used only when the extra third-person perspective is active.
public final class ShoulderSurfingCompat {

    private static final String SHOULDER_SURFING_MOD_ID = "shouldersurfing";
    private static final String SHOULDER_SURFING_API_CLASS = "com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing";
    private static boolean loggedFailure;
    private static boolean reflectionInitAttempted;
    private static boolean reflectionReady;
    private static Method getInstanceMethod;
    private static Method isShoulderSurfingMethod;
    private static Method getCameraMethod;
    private static Method turnMethod;
    private static Method getXRotMethod;
    private static Method getYRotMethod;
    private static Method calcOffsetMethod;
    private static Method getRenderOffsetMethod;

    private ShoulderSurfingCompat() {
    }

    public record CameraRotation(float yaw, float pitch) {
    }

    public static boolean isShoulderSurfingPerspectiveActive() {
        if (!Services.PLATFORM.isModLoaded(SHOULDER_SURFING_MOD_ID)) {
            return false;
        }

        try {
            Object instance = seamlesssleep$getInstance();
            if (instance == null || isShoulderSurfingMethod == null) {
                return false;
            }

            Object active = isShoulderSurfingMethod.invoke(instance);
            return active instanceof Boolean && (Boolean) active;
        } catch (Throwable t) {
            seamlesssleep$logFailure(t);
            return false;
        }
    }

    public static @Nullable Vec3 getCameraOffset(Camera camera, BlockGetter level, float partialTick, Entity cameraEntity) {
        if (!isShoulderSurfingPerspectiveActive()) {
            return null;
        }

        try {
            Object shoulderCamera = seamlesssleep$getCamera();
            if (shoulderCamera == null) {
                return null;
            }

            Vec3 offset = calcOffsetMethod != null
                    ? (Vec3) calcOffsetMethod.invoke(shoulderCamera, camera, level, partialTick, cameraEntity)
                    : null;
            if (offset != null) {
                return offset;
            }

            return getRenderOffsetMethod != null
                    ? (Vec3) getRenderOffsetMethod.invoke(shoulderCamera)
                    : null;
        } catch (Throwable t) {
            seamlesssleep$logFailure(t);
            return null;
        }
    }

    public static void applyCameraTurn(LocalPlayer player, double yawDelta, double pitchDelta) {
        if (!isShoulderSurfingPerspectiveActive()) {
            return;
        }

        try {
            Object shoulderCamera = seamlesssleep$getCamera();
            if (shoulderCamera == null || turnMethod == null) {
                return;
            }

            turnMethod.invoke(shoulderCamera, player, yawDelta, pitchDelta);
        } catch (Throwable t) {
            seamlesssleep$logFailure(t);
        }
    }

    public static @Nullable CameraRotation getCameraRotation() {
        if (!isShoulderSurfingPerspectiveActive()) {
            return null;
        }

        try {
            Object shoulderCamera = seamlesssleep$getCamera();
            if (shoulderCamera == null || getXRotMethod == null || getYRotMethod == null) {
                return null;
            }

            Object xRot = getXRotMethod.invoke(shoulderCamera);
            Object yRot = getYRotMethod.invoke(shoulderCamera);
            if (!(xRot instanceof Number pitch) || !(yRot instanceof Number yaw)) {
                return null;
            }

            return new CameraRotation(yaw.floatValue(), pitch.floatValue());
        } catch (Throwable t) {
            seamlesssleep$logFailure(t);
            return null;
        }
    }

    private static @Nullable Object seamlesssleep$getInstance() {
        if (!seamlesssleep$ensureReflectionReady() || getInstanceMethod == null) {
            return null;
        }

        try {
            return getInstanceMethod.invoke(null);
        } catch (Throwable t) {
            seamlesssleep$logFailure(t);
            return null;
        }
    }

    private static @Nullable Object seamlesssleep$getCamera() {
        Object instance = seamlesssleep$getInstance();
        if (instance == null || getCameraMethod == null) {
            return null;
        }

        try {
            return getCameraMethod.invoke(instance);
        } catch (Throwable t) {
            seamlesssleep$logFailure(t);
            return null;
        }
    }

    private static boolean seamlesssleep$ensureReflectionReady() {
        if (reflectionInitAttempted) {
            return reflectionReady;
        }

        reflectionInitAttempted = true;
        try {
            Class<?> apiClass = Class.forName(SHOULDER_SURFING_API_CLASS);
            getInstanceMethod = apiClass.getMethod("getInstance");

            Object instance = getInstanceMethod.invoke(null);
            if (instance != null) {
                Class<?> instanceClass = instance.getClass();
                isShoulderSurfingMethod = instanceClass.getMethod("isShoulderSurfing");
                getCameraMethod = instanceClass.getMethod("getCamera");

                Object camera = getCameraMethod.invoke(instance);
                if (camera != null) {
                    Class<?> cameraClass = camera.getClass();
                    turnMethod = cameraClass.getMethod("turn", LocalPlayer.class, double.class, double.class);
                    getXRotMethod = cameraClass.getMethod("getXRot");
                    getYRotMethod = cameraClass.getMethod("getYRot");
                    calcOffsetMethod = cameraClass.getMethod("calcOffset", Camera.class, BlockGetter.class, float.class, Entity.class);
                    getRenderOffsetMethod = cameraClass.getMethod("getRenderOffset");
                }
            }

            reflectionReady = true;
        } catch (Throwable t) {
            reflectionReady = false;
            seamlesssleep$logFailure(t);
        }

        return reflectionReady;
    }

    private static void seamlesssleep$logFailure(Throwable t) {
        if (loggedFailure) {
            return;
        }

        loggedFailure = true;
        Constants.debug("ShoulderSurfing compatibility bridge failed.", t);
    }
}
