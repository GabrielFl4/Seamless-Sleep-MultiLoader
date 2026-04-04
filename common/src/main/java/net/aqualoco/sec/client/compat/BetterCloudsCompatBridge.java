package net.aqualoco.sec.client.compat;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.lang.reflect.Method;

// Runtime-only reflection bridge so loader-specific code can query Better Clouds without a compile dependency.
public final class BetterCloudsCompatBridge {

    private static final String BETTER_CLOUDS_MOD_ID = "betterclouds";
    private static final String BETTER_CLOUDS_CLASS = "com.qendolin.betterclouds.BetterClouds";
    private static final String BETTER_CLOUDS_CLASS_RESOURCE = "com/qendolin/betterclouds/BetterClouds.class";

    private static boolean initialized;
    private static boolean resolutionFailed;
    private static boolean loggedResolutionFailure;

    private static Method isEnabledMethod;
    private static Method getCloudsRendererMethod;

    private BetterCloudsCompatBridge() {
    }

    public static boolean isBridgeActive() {
        if (!isBetterCloudsAvailable()) {
            return false;
        }

        ensureInitialized();
        if (resolutionFailed || isEnabledMethod == null || getCloudsRendererMethod == null) {
            return false;
        }

        try {
            Object enabled = isEnabledMethod.invoke(null);
            if (!(enabled instanceof Boolean) || !((Boolean) enabled)) {
                return false;
            }

            return getCloudsRendererMethod.invoke(null) != null;
        } catch (Throwable throwable) {
            if (!loggedResolutionFailure) {
                loggedResolutionFailure = true;
                Constants.warn("Better Clouds compat bridge invocation failed: {}", throwable.toString());
            }
            return false;
        }
    }

    private static boolean isBetterCloudsAvailable() {
        try {
            if (Services.PLATFORM.isModLoaded(BETTER_CLOUDS_MOD_ID)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return hasClassResource(BETTER_CLOUDS_CLASS_RESOURCE, Thread.currentThread().getContextClassLoader())
                || hasClassResource(BETTER_CLOUDS_CLASS_RESOURCE, BetterCloudsCompatBridge.class.getClassLoader());
    }

    private static void ensureInitialized() {
        if (initialized || resolutionFailed) {
            return;
        }

        initialized = true;
        try {
            Class<?> betterCloudsClass = resolveClass();
            isEnabledMethod = betterCloudsClass.getMethod("isEnabled");
            getCloudsRendererMethod = betterCloudsClass.getMethod("getCloudsRenderer");
        } catch (Throwable throwable) {
            resolutionFailed = true;
            if (!loggedResolutionFailure) {
                loggedResolutionFailure = true;
                Constants.warn("Better Clouds compat bridge resolution failed: {}", throwable.toString());
            }
        }
    }

    private static Class<?> resolveClass() throws ClassNotFoundException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return Class.forName(BETTER_CLOUDS_CLASS, false, contextClassLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        return Class.forName(BETTER_CLOUDS_CLASS, false, BetterCloudsCompatBridge.class.getClassLoader());
    }

    private static boolean hasClassResource(String classResourcePath, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            return classLoader.getResource(classResourcePath) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
