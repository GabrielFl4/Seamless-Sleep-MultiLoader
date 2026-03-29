package net.aqualoco.sec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Shared identifiers and logger used across every module.
public class Constants {

	public static final String MOD_ID = "seamlesssleep";
	public static final String MOD_NAME = "Seamless Sleep";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
    private static final String LOG_PREFIX = "[Seamless Sleep] ";
    private static volatile boolean debugLogsEnabled;

    private Constants() {
    }

    public static void setDebugLogsEnabled(boolean enabled) {
        debugLogsEnabled = enabled;
    }

    public static boolean isDebugLogsEnabled() {
        return debugLogsEnabled;
    }

    public static void info(String message, Object... args) {
        LOG.info(withPrefix(message), args);
    }

    public static void warn(String message, Object... args) {
        LOG.warn(withPrefix(message), args);
    }

    public static void error(String message, Object... args) {
        LOG.error(withPrefix(message), args);
    }

    public static void debug(String message, Object... args) {
        if (!debugLogsEnabled) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(withPrefix(message), args);
            return;
        }
        LOG.info(withPrefix(message), args);
    }

    private static String withPrefix(String message) {
        return message.startsWith(LOG_PREFIX) ? message : LOG_PREFIX + message;
    }
}
