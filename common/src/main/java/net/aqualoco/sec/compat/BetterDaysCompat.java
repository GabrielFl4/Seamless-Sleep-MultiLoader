package net.aqualoco.sec.compat;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.platform.Services;

// Centralizes Better Days detection and the sleep-feature kill switch without linking to Better Days classes.
public final class BetterDaysCompat {
    public static final String MOD_ID = "betterdays";
    public static final String TARGET_CLASS_RESOURCE = "betterdays/config/ConfigHandler$Common.class";
    public static final String SLEEP_STATUS_CLASS_RESOURCE = "betterdays/time/SleepStatus.class";

    private static boolean detectedLogged;
    private static boolean neutralizedLogged;
    private static boolean disabledWarningLogged;

    private BetterDaysCompat() {
    }

    public static void init() {
        if (!isBetterDaysLoaded()) {
            return;
        }

        logDetected();
        if (!isTargetAvailable()) {
            return;
        }
        if (isCompatibilityEnabled()) {
            Constants.info("Better Days compatibility is active. Better Days day/night duration remains active.");
        } else {
            logDisabledWarning();
        }
    }

    public static boolean shouldDisableBetterDaysSleepFeature() {
        if (!isBetterDaysLoaded()) {
            return false;
        }

        logDetected();
        if (!isCompatibilityEnabled()) {
            logDisabledWarning();
            return false;
        }

        logNeutralized();
        return true;
    }

    public static boolean isBetterDaysLoaded() {
        try {
            if (Services.PLATFORM.isModLoaded(MOD_ID)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return isTargetAvailable();
    }

    public static boolean isTargetAvailable() {
        return hasClassResource(TARGET_CLASS_RESOURCE, Thread.currentThread().getContextClassLoader())
                || hasClassResource(TARGET_CLASS_RESOURCE, BetterDaysCompat.class.getClassLoader());
    }

    private static boolean isCompatibilityEnabled() {
        if (SeamlessSleepServerConfigSnapshot.isInitialized()) {
            return SeamlessSleepServerConfigSnapshot.isBetterDaysCompatibilityEnabled();
        }
        return SeamlessSleepServerConfigManager.get().betterDaysCompatibilityEnabled;
    }

    private static void logDetected() {
        if (detectedLogged) {
            return;
        }
        detectedLogged = true;
        Constants.info("Better Days detected.");
    }

    private static void logNeutralized() {
        if (neutralizedLogged) {
            return;
        }
        neutralizedLogged = true;
        Constants.info("Better Days sleep features are disabled through Seamless Sleep compatibility.");
    }

    private static void logDisabledWarning() {
        if (disabledWarningLogged) {
            return;
        }
        disabledWarningLogged = true;
        Constants.warn("Better Days detected, but Seamless Sleep Better Days compatibility is disabled. Sleep behavior may conflict.");
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
