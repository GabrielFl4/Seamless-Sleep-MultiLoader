package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.lang.reflect.Method;
import java.util.OptionalLong;

// Flashback reflection bridge used by client animation code without a hard dependency.
public final class FlashbackCompat {

    private static final String FLASHBACK_MOD_ID = "flashback";
    private static final String FLASHBACK_CLASS = "com.moulberry.flashback.Flashback";
    private static boolean reflectionInitAttempted;
    private static boolean reflectionReady;
    private static boolean loggedReflectionFailure;
    private static boolean loggedInvocationFailure;

    private static Method isInReplayMethod;
    private static Method getReplayServerMethod;
    private static Method getVisualMillisMethod;
    private static Method getPartialReplayTickMethod;

    private FlashbackCompat() {
    }

    public static boolean isReplayPlaybackActive() {
        if (!Services.PLATFORM.isModLoaded(FLASHBACK_MOD_ID)) {
            return false;
        }

        try {
            if (!ensureReflectionReady()) {
                return false;
            }

            if (isInReplayMethod != null) {
                Object inReplay = isInReplayMethod.invoke(null);
                if (inReplay instanceof Boolean && (Boolean) inReplay) {
                    return true;
                }
            }

            return findReplayServer() != null;
        } catch (ReflectiveOperationException e) {
            logInvocationFailure(e);
            return false;
        }
    }

    public static OptionalLong getReplayTimelineMillis() {
        if (!Services.PLATFORM.isModLoaded(FLASHBACK_MOD_ID)) {
            return OptionalLong.empty();
        }

        try {
            if (!ensureReflectionReady()) {
                return OptionalLong.empty();
            }

            Object replayServer = findReplayServer();

            boolean inReplay = false;
            if (isInReplayMethod != null) {
                Object inReplayValue = isInReplayMethod.invoke(null);
                inReplay = inReplayValue instanceof Boolean && (Boolean) inReplayValue;
            }

            if (!inReplay && replayServer == null) {
                return OptionalLong.empty();
            }

            if (getVisualMillisMethod != null) {
                Object visualMillis = getVisualMillisMethod.invoke(null);
                if (visualMillis instanceof Number) {
                    return OptionalLong.of(((Number) visualMillis).longValue());
                }
            }

            if (replayServer != null && getPartialReplayTickMethod != null) {
                Object partialTickValue = getPartialReplayTickMethod.invoke(replayServer);
                if (partialTickValue instanceof Number) {
                    long timelineMillis = Math.max(0L, (long) (((Number) partialTickValue).doubleValue() * 50.0D));
                    return OptionalLong.of(timelineMillis);
                }
            }

            return OptionalLong.empty();
        } catch (ReflectiveOperationException e) {
            logInvocationFailure(e);
            return OptionalLong.empty();
        }
    }

    private static Object findReplayServer() throws ReflectiveOperationException {
        if (!ensureReflectionReady() || getReplayServerMethod == null) {
            return null;
        }
        return getReplayServerMethod.invoke(null);
    }

    private static boolean ensureReflectionReady() {
        if (reflectionInitAttempted) {
            return reflectionReady;
        }

        reflectionInitAttempted = true;
        try {
            Class<?> flashbackClass = Class.forName(FLASHBACK_CLASS);

            isInReplayMethod = findMethodOrNull(flashbackClass, "isInReplay");
            getReplayServerMethod = findMethodOrNull(flashbackClass, "getReplayServer");
            getVisualMillisMethod = findMethodOrNull(flashbackClass, "getVisualMillis");

            if (getReplayServerMethod != null) {
                Class<?> replayServerClass = getReplayServerMethod.getReturnType();
                getPartialReplayTickMethod = findMethodOrNull(replayServerClass, "getPartialReplayTick");
            }

            if (isInReplayMethod == null && getReplayServerMethod == null) {
                throw new NoSuchMethodException("Flashback replay detection methods were not found.");
            }

            reflectionReady = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            reflectionReady = false;
            if (!loggedReflectionFailure) {
                loggedReflectionFailure = true;
                Constants.debug("Flashback reflection bridge initialization failed.", e);
            }
        }

        return reflectionReady;
    }

    private static Method findMethodOrNull(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static void logInvocationFailure(ReflectiveOperationException e) {
        if (!loggedInvocationFailure) {
            loggedInvocationFailure = true;
            Constants.debug("Flashback reflection bridge invocation failed.", e);
        }
    }
}
