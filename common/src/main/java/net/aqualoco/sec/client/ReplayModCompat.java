package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.OptionalLong;

// ReplayMod reflection bridge used by client animation code without a hard dependency.
public final class ReplayModCompat {

    private static final String REPLAY_MOD_ID = "replaymod";
    private static final String REPLAY_STATE_CLASS = "com.replaymod.replay.ReplayModReplay";
    private static boolean reflectionInitAttempted;
    private static boolean reflectionReady;
    private static boolean loggedReflectionFailure;
    private static boolean loggedInvocationFailure;

    private static Field replayModuleInstanceField;
    private static Method getReplayHandlerMethod;
    private static Method getReplaySenderMethod;
    private static Method currentTimeStampMethod;

    private ReplayModCompat() {
    }

    public static boolean isReplayPlaybackActive() {
        if (!Services.PLATFORM.isModLoaded(REPLAY_MOD_ID)) {
            return false;
        }

        try {
            return findReplayHandler() != null;
        } catch (ReflectiveOperationException e) {
            logInvocationFailure(e);
            return false;
        }
    }

    public static OptionalLong getReplayTimelineMillis() {
        if (!Services.PLATFORM.isModLoaded(REPLAY_MOD_ID)) {
            return OptionalLong.empty();
        }

        try {
            Object replayHandler = findReplayHandler();
            if (replayHandler == null) {
                return OptionalLong.empty();
            }

            Object replaySender = getReplaySenderMethod.invoke(replayHandler);
            if (replaySender == null) {
                return OptionalLong.empty();
            }

            Object timestampValue = currentTimeStampMethod.invoke(replaySender);
            if (timestampValue instanceof Number) {
                return OptionalLong.of(((Number) timestampValue).longValue());
            }

            return OptionalLong.empty();
        } catch (ReflectiveOperationException e) {
            logInvocationFailure(e);
            return OptionalLong.empty();
        }
    }

    private static Object findReplayHandler() throws ReflectiveOperationException {
        if (!ensureReflectionReady()) {
            return null;
        }

        Object replayModuleInstance = replayModuleInstanceField.get(null);
        if (replayModuleInstance == null) {
            return null;
        }

        return getReplayHandlerMethod.invoke(replayModuleInstance);
    }

    private static boolean ensureReflectionReady() {
        if (reflectionInitAttempted) {
            return reflectionReady;
        }

        reflectionInitAttempted = true;
        try {
            Class<?> replayStateClass = Class.forName(REPLAY_STATE_CLASS);
            replayModuleInstanceField = replayStateClass.getDeclaredField("instance");
            getReplayHandlerMethod = replayStateClass.getMethod("getReplayHandler");

            Class<?> replayHandlerClass = getReplayHandlerMethod.getReturnType();
            getReplaySenderMethod = replayHandlerClass.getMethod("getReplaySender");

            Class<?> replaySenderClass = getReplaySenderMethod.getReturnType();
            currentTimeStampMethod = replaySenderClass.getMethod("currentTimeStamp");

            reflectionReady = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            reflectionReady = false;
            if (!loggedReflectionFailure) {
                loggedReflectionFailure = true;
                Constants.debug("ReplayMod reflection bridge initialization failed.", e);
            }
        }

        return reflectionReady;
    }

    private static void logInvocationFailure(ReflectiveOperationException e) {
        if (!loggedInvocationFailure) {
            loggedInvocationFailure = true;
            Constants.debug("ReplayMod reflection bridge invocation failed.", e);
        }
    }
}
