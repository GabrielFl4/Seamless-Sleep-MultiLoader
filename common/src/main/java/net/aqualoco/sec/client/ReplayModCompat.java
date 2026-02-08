package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Lightweight ReplayMod state probe used to switch client behavior during replay playback.
public final class ReplayModCompat {

    private static final String REPLAY_MOD_ID = "replaymod";
    private static final String REPLAY_STATE_CLASS = "com.replaymod.replay.ReplayModReplay";
    private static boolean loggedStateLookupFailure;

    private ReplayModCompat() {
    }

    public static boolean isReplayPlaybackActive() {
        if (!Services.PLATFORM.isModLoaded(REPLAY_MOD_ID)) {
            return false;
        }

        try {
            Class<?> replayStateClass = Class.forName(REPLAY_STATE_CLASS);
            Field instanceField = replayStateClass.getDeclaredField("instance");
            Object replayModuleInstance = instanceField.get(null);
            if (replayModuleInstance == null) {
                return false;
            }

            Method getReplayHandler = replayStateClass.getMethod("getReplayHandler");
            return getReplayHandler.invoke(replayModuleInstance) != null;
        } catch (ReflectiveOperationException | LinkageError e) {
            if (!loggedStateLookupFailure) {
                loggedStateLookupFailure = true;
                Constants.LOG.debug("ReplayMod is loaded, but replay state lookup failed. Falling back to normal mode.", e);
            }
            return false;
        }
    }
}
