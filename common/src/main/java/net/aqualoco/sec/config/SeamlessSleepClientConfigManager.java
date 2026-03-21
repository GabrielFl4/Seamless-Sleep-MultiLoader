package net.aqualoco.sec.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Handles disk persistence for the client config TOML.
public final class SeamlessSleepClientConfigManager {

    private static final String FILE_NAME = "seamless_sleep.toml";
    private static final String LEGACY_JSON_FILE = "seamless_sleep.json";
    private static final String LEGACY_JSONC_FILE = "seamless_sleep.jsonc";
    private static final int CONFIG_VERSION = 1;

    private static SeamlessSleepClientConfig config = defaultConfig();
    private static Path configPath;

    private SeamlessSleepClientConfigManager() {
    }

    public static void init() {
        configPath = Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
        config = loadOrCreate(configPath);
        Constants.setDebugLogsEnabled(config.debugLogsEnabled);
    }

    public static SeamlessSleepClientConfig get() {
        return config;
    }

    private static SeamlessSleepClientConfig loadOrCreate(Path path) {
        deleteLegacyFiles(path);

        if (Files.notExists(path)) {
            SeamlessSleepClientConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return cfg;
        }

        try (CommentedFileConfig fileConfig = createTomlConfig(path)) {
            fileConfig.load();
            SeamlessSleepClientConfig cfg = readConfig(fileConfig);
            cfg.clamp();
            save(path, cfg);
            return cfg;
        } catch (Exception e) {
            Constants.warn("Failed to read client config {}, using defaults. Error: {}", path, e.getMessage());
            SeamlessSleepClientConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return cfg;
        }
    }

    public static void save() {
        if (configPath == null || config == null) {
            return;
        }
        config.clamp();
        Constants.setDebugLogsEnabled(config.debugLogsEnabled);
        save(configPath, config);
    }

    private static void save(Path path, SeamlessSleepClientConfig cfg) {
        cfg.clamp();

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (CommentedFileConfig fileConfig = createTomlConfig(path)) {
                fileConfig.clear();

                fileConfig.setComment("config_version", "Schema version for this config.");
                fileConfig.set("config_version", CONFIG_VERSION);
                fileConfig.setComment("mod_version", "Mod version that last wrote this file.");
                fileConfig.set("mod_version", resolveModVersion());

                fileConfig.setComment("overlay", "Overlay settings while sleeping.");
                fileConfig.set("overlay.enabled", cfg.sleepOverlayEnabled);
                fileConfig.set("overlay.darkness_multiplier", cfg.sleepOverlayDarknessMultiplier);

                fileConfig.setComment("chat", "Chat dimming settings while sleeping.");
                fileConfig.set("chat.text_opacity_multiplier", cfg.sleepChatTextOpacityMultiplier);
                fileConfig.set("chat.background_opacity_multiplier", cfg.sleepChatBackgroundOpacityMultiplier);
                fileConfig.set("chat.opacity_multiplier", cfg.sleepChatOpacityMultiplier);
                fileConfig.set("chat.max_lines", cfg.sleepChatMaxLines);

                fileConfig.setComment("camera", "Camera animation settings.");
                fileConfig.set("camera.tilt_degrees", cfg.sleepCameraTiltDegrees);

                fileConfig.setComment("advanced", "Advanced client options.");
                fileConfig.set("advanced.replay_compatibility_enabled", cfg.replayCompatibilityEnabled);
                fileConfig.set("advanced.debug_logs_enabled", cfg.debugLogsEnabled);

                fileConfig.save();
            }
        } catch (IOException e) {
            Constants.warn("Failed to save client config {}: {}", path, e.getMessage());
        } catch (Exception e) {
            Constants.warn("Failed to write client TOML {}: {}", path, e.getMessage());
        }
    }

    private static SeamlessSleepClientConfig readConfig(CommentedFileConfig fileConfig) {
        SeamlessSleepClientConfig cfg = defaultConfig();

        cfg.sleepOverlayEnabled = readBoolean(
                fileConfig,
                cfg.sleepOverlayEnabled,
                "overlay.enabled",
                "sleepOverlayEnabled"
        );
        cfg.sleepOverlayDarknessMultiplier = readDouble(
                fileConfig,
                cfg.sleepOverlayDarknessMultiplier,
                "overlay.darkness_multiplier",
                "overlay.darknessMultiplier",
                "sleepOverlayDarknessMultiplier"
        );
        cfg.sleepChatTextOpacityMultiplier = readDouble(
                fileConfig,
                cfg.sleepChatTextOpacityMultiplier,
                "chat.text_opacity_multiplier",
                "sleepChatTextOpacityMultiplier"
        );
        cfg.sleepChatBackgroundOpacityMultiplier = readDouble(
                fileConfig,
                cfg.sleepChatBackgroundOpacityMultiplier,
                "chat.background_opacity_multiplier",
                "sleepChatBackgroundOpacityMultiplier"
        );
        cfg.sleepChatOpacityMultiplier = readDouble(
                fileConfig,
                cfg.sleepChatOpacityMultiplier,
                "chat.opacity_multiplier",
                "sleepChatOpacityMultiplier"
        );
        cfg.sleepChatMaxLines = readInt(
                fileConfig,
                cfg.sleepChatMaxLines,
                "chat.max_lines",
                "sleepChatMaxLines"
        );
        cfg.sleepCameraTiltDegrees = readDouble(
                fileConfig,
                cfg.sleepCameraTiltDegrees,
                "camera.tilt_degrees",
                "sleepCameraTiltDegrees"
        );
        cfg.replayCompatibilityEnabled = readBoolean(
                fileConfig,
                cfg.replayCompatibilityEnabled,
                "advanced.replay_compatibility_enabled",
                "replayCompatibilityEnabled"
        );
        cfg.debugLogsEnabled = readBoolean(
                fileConfig,
                cfg.debugLogsEnabled,
                "advanced.debug_logs_enabled",
                "debugLogsEnabled"
        );

        return cfg;
    }

    private static boolean readBoolean(CommentedFileConfig fileConfig, boolean fallback, String... keys) {
        for (String key : keys) {
            Boolean parsed = parseBoolean(fileConfig.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static double readDouble(CommentedFileConfig fileConfig, double fallback, String... keys) {
        for (String key : keys) {
            Double parsed = parseDouble(fileConfig.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static int readInt(CommentedFileConfig fileConfig, int fallback, String... keys) {
        for (String key : keys) {
            Integer parsed = parseInt(fileConfig.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static Boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }
        return null;
    }

    private static Double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer parseInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void deleteLegacyFiles(Path tomlPath) {
        Path parent = tomlPath.getParent();
        if (parent == null) {
            return;
        }
        deleteLegacyFile(parent.resolve(LEGACY_JSON_FILE));
        deleteLegacyFile(parent.resolve(LEGACY_JSONC_FILE));
    }

    private static void deleteLegacyFile(Path legacyPath) {
        if (Files.notExists(legacyPath)) {
            return;
        }
        try {
            Files.delete(legacyPath);
            Constants.info("Deleted legacy config file {}", legacyPath);
        } catch (IOException e) {
            Constants.warn("Failed to delete legacy config file {}: {}", legacyPath, e.getMessage());
        }
    }

    private static String resolveModVersion() {
        String version = Services.PLATFORM.getModVersion(Constants.MOD_ID);
        if (version == null || version.isBlank()) {
            return "unknown";
        }
        return version;
    }

    private static CommentedFileConfig createTomlConfig(Path path) {
        return CommentedFileConfig.builder(path, TomlFormat.instance())
                .sync()
                .preserveInsertionOrder()
                .build();
    }

    private static SeamlessSleepClientConfig defaultConfig() {
        return new SeamlessSleepClientConfig();
    }
}
