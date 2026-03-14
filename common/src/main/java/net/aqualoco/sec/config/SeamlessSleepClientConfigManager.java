package net.aqualoco.sec.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// Handles disk persistence for the client config TOML.
public final class SeamlessSleepClientConfigManager {

    private static final String FILE_NAME = "seamless_sleep.toml";
    private static final String LEGACY_FILE_JSON = "seamless_sleep.json";
    private static final String LEGACY_FILE_JSONC = "seamless_sleep.jsonc";
    private static final int CONFIG_VERSION = 1;

    private static SeamlessSleepClientConfig config = defaultConfig();
    private static Path configPath;

    private SeamlessSleepClientConfigManager() {
    }

    public static void init() {
        configPath = Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
        deleteLegacyFiles(configPath);
        config = loadOrCreate(configPath);
        Constants.setDebugLogsEnabled(config.debugLogsEnabled);
    }

    public static SeamlessSleepClientConfig get() {
        return config;
    }

    private static SeamlessSleepClientConfig loadOrCreate(Path path) {
        if (Files.notExists(path)) {
            SeamlessSleepClientConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return cfg;
        }

        try (CommentedFileConfig fileConfig = CommentedFileConfig.builder(path, TomlFormat.instance())
                .sync()
                .preserveInsertionOrder()
                .build()) {
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

    private static void save(Path path, SeamlessSleepClientConfig cfg) {
        cfg.clamp();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    toToml(cfg),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            Constants.warn("Failed to save client config {}: {}", path, e.getMessage());
        }
    }

    private static String toToml(SeamlessSleepClientConfig cfg) {
        StringBuilder toml = new StringBuilder();
        toml.append("# Seamless Sleep client config").append('\n');
        toml.append("config_version = ").append(CONFIG_VERSION).append('\n');
        toml.append("mod_version = \"").append(escapeToml(resolveModVersion())).append('"').append('\n');
        toml.append('\n');

        toml.append("[overlay]").append('\n');
        toml.append("enabled = ").append(cfg.sleepOverlayEnabled).append('\n');
        toml.append("darkness_multiplier = ").append(cfg.sleepOverlayDarknessMultiplier).append('\n');
        toml.append('\n');

        toml.append("[chat]").append('\n');
        toml.append("text_opacity_multiplier = ").append(cfg.sleepChatTextOpacityMultiplier).append('\n');
        toml.append("background_opacity_multiplier = ").append(cfg.sleepChatBackgroundOpacityMultiplier).append('\n');
        toml.append("opacity_multiplier = ").append(cfg.sleepChatOpacityMultiplier).append('\n');
        toml.append("max_lines = ").append(cfg.sleepChatMaxLines).append('\n');
        toml.append('\n');

        toml.append("[camera]").append('\n');
        toml.append("tilt_degrees = ").append(cfg.sleepCameraTiltDegrees).append('\n');
        toml.append('\n');

        toml.append("[advanced]").append('\n');
        toml.append("replay_compatibility_enabled = ").append(cfg.replayCompatibilityEnabled).append('\n');
        toml.append("debug_logs_enabled = ").append(cfg.debugLogsEnabled).append('\n');

        return toml.toString();
    }

    private static boolean readBoolean(CommentedFileConfig fileConfig, boolean fallback, String... keys) {
        for (String key : keys) {
            if (!fileConfig.contains(key)) {
                continue;
            }
            Object rawValue = fileConfig.get(key);
            Boolean parsed = parseBoolean(rawValue);
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static double readDouble(CommentedFileConfig fileConfig, double fallback, String... keys) {
        for (String key : keys) {
            if (!fileConfig.contains(key)) {
                continue;
            }
            Object rawValue = fileConfig.get(key);
            Double parsed = parseDouble(rawValue);
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static int readInt(CommentedFileConfig fileConfig, int fallback, String... keys) {
        for (String key : keys) {
            if (!fileConfig.contains(key)) {
                continue;
            }
            Object rawValue = fileConfig.get(key);
            Integer parsed = parseInt(rawValue);
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static Boolean parseBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        return null;
    }

    private static Double parseDouble(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer parseInt(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
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
        deleteLegacyFile(parent.resolve(LEGACY_FILE_JSON));
        deleteLegacyFile(parent.resolve(LEGACY_FILE_JSONC));
    }

    private static void deleteLegacyFile(Path path) {
        try {
            if (Files.deleteIfExists(path)) {
                Constants.info("Deleted legacy client config {}", path);
            }
        } catch (IOException e) {
            Constants.warn("Failed to delete legacy client config {}: {}", path, e.getMessage());
        }
    }

    private static String resolveModVersion() {
        String modVersion = Services.PLATFORM.getModVersion(Constants.MOD_ID);
        if (modVersion == null || modVersion.isBlank()) {
            return "unknown";
        }
        return modVersion;
    }

    private static String escapeToml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static SeamlessSleepClientConfig defaultConfig() {
        return new SeamlessSleepClientConfig();
    }
}
