package net.aqualoco.sec.platform.services;

import java.nio.file.Path;

// Loader-agnostic hooks for platform info and config directory access.
public interface IPlatformHelper {

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Gets the resolved version string for a loaded mod.
     *
     * @param modId The mod id to query.
     * @return The resolved version string, or "unknown" when unavailable.
     */
    String getModVersion(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the config directory for the current platform.
     *
     * @return The config directory path.
     */
    Path getConfigDir();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
