package net.aqualoco.sec.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Handles disk persistence for the client config TOML.
public final class SeamlessSleepClientConfigManager {

    private static final String FILE_NAME = "seamless_sleep.toml";
    private static final String LEGACY_JSON_FILE_NAME = "seamless_sleep.json";
    private static final String LEGACY_JSONC_FILE_NAME = "seamless_sleep.jsonc";
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
        deleteLegacyJsonFiles(path);

        if (Files.notExists(path)) {
            SeamlessSleepClientConfig cfg = defaultConfig();
            cfg.clamp();
            save(path, cfg);
            return cfg;
        }

        try (CommentedFileConfig file = openConfig(path)) {
            file.load();
            logMetadataInfo(path, file);

            SeamlessSleepClientConfig cfg = readClientConfig(file);
            cfg.clamp();
            save(path, cfg); // Canonicalize order/comments and keep metadata fresh.
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
            Files.writeString(path, toClientToml(cfg, resolveCurrentModVersion()));
        } catch (IOException e) {
            Constants.warn("Failed to save client config {}: {}", path, e.getMessage());
        } catch (Exception e) {
            Constants.warn("Failed to write client config {}: {}", path, e.getMessage());
        }
    }

    private static SeamlessSleepClientConfig defaultConfig() {
        return new SeamlessSleepClientConfig();
    }

    private static CommentedFileConfig openConfig(Path path) {
        return CommentedFileConfig.builder(path, TomlFormat.instance())
                .sync()
                .preserveInsertionOrder()
                .build();
    }

    private static SeamlessSleepClientConfig readClientConfig(CommentedFileConfig file) {
        SeamlessSleepClientConfig cfg = defaultConfig();

        cfg.sleepOverlayEnabled = readBoolean(file, List.of("overlay", "sleepOverlayEnabled"), "sleepOverlayEnabled", cfg.sleepOverlayEnabled);
        cfg.sleepOverlayDarknessMultiplier = readDouble(file, List.of("overlay", "sleepOverlayDarknessMultiplier"), "sleepOverlayDarknessMultiplier", cfg.sleepOverlayDarknessMultiplier);
        cfg.sleepChatTextOpacityMultiplier = readDouble(file, List.of("chat", "sleepChatTextOpacityMultiplier"), "sleepChatTextOpacityMultiplier", cfg.sleepChatTextOpacityMultiplier);
        cfg.sleepChatBackgroundOpacityMultiplier = readDouble(file, List.of("chat", "sleepChatBackgroundOpacityMultiplier"), "sleepChatBackgroundOpacityMultiplier", cfg.sleepChatBackgroundOpacityMultiplier);
        cfg.sleepChatOpacityMultiplier = readDouble(file, List.of("chat", "sleepChatOpacityMultiplier"), "sleepChatOpacityMultiplier", cfg.sleepChatOpacityMultiplier);
        cfg.sleepChatMaxLines = readInt(file, List.of("chat", "sleepChatMaxLines"), "sleepChatMaxLines", cfg.sleepChatMaxLines);
        cfg.sleepCameraTiltDegrees = readDouble(file, List.of("camera", "sleepCameraTiltDegrees"), "sleepCameraTiltDegrees", cfg.sleepCameraTiltDegrees);
        cfg.replayCompatibilityEnabled = readBoolean(file, List.of("advanced", "replayCompatibilityEnabled"), "replayCompatibilityEnabled", cfg.replayCompatibilityEnabled);
        cfg.debugLogsEnabled = readBoolean(file, List.of("advanced", "debugLogsEnabled"), "debugLogsEnabled", cfg.debugLogsEnabled);
        return cfg;
    }

    private static String toClientToml(SeamlessSleepClientConfig cfg, String modVersion) {
        StringBuilder sb = new StringBuilder(1024);

        sb.append("config_version = ").append(CONFIG_VERSION).append('\n');
        sb.append("mod_version = ").append(toTomlString(modVersion)).append('\n');

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "overlay");
        appendEntry(sb,
                "Enable sleep overlay. Range: true | false. Default: true",
                "sleepOverlayEnabled",
                Boolean.toString(cfg.sleepOverlayEnabled));
        appendEntry(sb,
                "Overlay darkness. Range: 0.0 to 1.0. Default: 0.35",
                "sleepOverlayDarknessMultiplier",
                Double.toString(cfg.sleepOverlayDarknessMultiplier));

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "chat");
        appendEntry(sb,
                "Chat text opacity multiplier. Range: 0.0 to 1.0. Default: 0.5",
                "sleepChatTextOpacityMultiplier",
                Double.toString(cfg.sleepChatTextOpacityMultiplier));
        appendEntry(sb,
                "Chat background opacity multiplier. Range: 0.0 to 1.0. Default: 0.4",
                "sleepChatBackgroundOpacityMultiplier",
                Double.toString(cfg.sleepChatBackgroundOpacityMultiplier));
        appendEntry(sb,
                "Global chat opacity multiplier. Range: 0.1 to 2.0. Default: 1.0",
                "sleepChatOpacityMultiplier",
                Double.toString(cfg.sleepChatOpacityMultiplier));
        appendEntry(sb,
                "Max chat lines in bed chat. Range: integer 0 to 12. Default: 4",
                "sleepChatMaxLines",
                Integer.toString(cfg.sleepChatMaxLines));

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "camera");
        appendEntry(sb,
                "Initial bed look angle. Range: -90.0 to 90.0. Default: 10.0",
                "sleepCameraTiltDegrees",
                Double.toString(cfg.sleepCameraTiltDegrees));

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "advanced");
        appendEntry(sb,
                "Replay compatibility mode. Range: true | false. Default: true",
                "replayCompatibilityEnabled",
                Boolean.toString(cfg.replayCompatibilityEnabled));
        appendEntry(sb,
                "Verbose debug logs. Range: true | false. Default: false",
                "debugLogsEnabled",
                Boolean.toString(cfg.debugLogsEnabled));

        trimTrailingBlankLines(sb);
        sb.append('\n');
        return sb.toString();
    }

    private static void appendSectionHeader(StringBuilder sb, String name) {
        sb.append('[').append(name).append("]\n");
    }

    private static void appendEntry(StringBuilder sb, String comment, String key, String valueLiteral) {
        sb.append("    #").append(comment).append('\n');
        sb.append("    ").append(key).append(" = ").append(valueLiteral).append("\n\n");
    }

    private static void appendSectionGap(StringBuilder sb, int blankLines) {
        for (int i = 0; i < blankLines; i++) {
            sb.append('\n');
        }
    }

    private static void trimTrailingBlankLines(StringBuilder sb) {
        while (sb.length() > 0) {
            char c = sb.charAt(sb.length() - 1);
            if (c != '\n' && c != '\r') {
                break;
            }
            sb.setLength(sb.length() - 1);
        }
    }

    private static String toTomlString(String value) {
        String safe = (value == null || value.isBlank()) ? "unknown" : value;
        String escaped = safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static boolean readBoolean(CommentedFileConfig file, List<String> path, String legacyKey, boolean fallback) {
        Object value = readRaw(file, path, legacyKey);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static int readInt(CommentedFileConfig file, List<String> path, String legacyKey, int fallback) {
        Object value = readRaw(file, path, legacyKey);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double readDouble(CommentedFileConfig file, List<String> path, String legacyKey, double fallback) {
        Object value = readRaw(file, path, legacyKey);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static Object readRaw(CommentedFileConfig file, List<String> path, String legacyKey) {
        Object value = file.getRaw(path);
        return value != null ? value : file.getRaw(legacyKey);
    }

    private static Integer readOptionalInt(CommentedFileConfig file, String key) {
        Object value = file.getRaw(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String readOptionalString(CommentedFileConfig file, String key) {
        Object value = file.getRaw(key);
        return value instanceof String string ? string : null;
    }

    private static void logMetadataInfo(Path path, CommentedFileConfig file) {
        Integer fileConfigVersion = readOptionalInt(file, "config_version");
        String fileModVersion = readOptionalString(file, "mod_version");
        String currentModVersion = resolveCurrentModVersion();

        if (fileConfigVersion != null && fileConfigVersion.intValue() != CONFIG_VERSION) {
            Constants.warn(
                    "Client config {} uses config_version {} but current version is {}.",
                    path.getFileName(),
                    fileConfigVersion,
                    CONFIG_VERSION
            );
        }
        if (fileModVersion != null && !fileModVersion.isBlank() && !fileModVersion.equals(currentModVersion)) {
            Constants.info(
                    "Client config {} was generated by mod version {} (current {}).",
                    path.getFileName(),
                    fileModVersion,
                    currentModVersion
            );
        }
    }

    private static String resolveCurrentModVersion() {
        try {
            String version = Services.PLATFORM.getModVersion(Constants.MOD_ID);
            return (version == null || version.isBlank()) ? "unknown" : version;
        } catch (Exception e) {
            Constants.warn("Failed to resolve mod version for config metadata: {}", e.getMessage());
            return "unknown";
        }
    }

    private static void deleteLegacyJsonFiles(Path tomlPath) {
        Path parent = tomlPath.getParent();
        if (parent == null) {
            return;
        }
        deleteIfExists(parent.resolve(LEGACY_JSON_FILE_NAME));
        deleteIfExists(parent.resolve(LEGACY_JSONC_FILE_NAME));
    }

    private static void deleteIfExists(Path file) {
        try {
            if (Files.deleteIfExists(file)) {
                Constants.info("Deleted legacy config file {}", file.getFileName());
            }
        } catch (IOException e) {
            Constants.warn("Failed to delete legacy config {}: {}", file, e.getMessage());
        }
    }
}
