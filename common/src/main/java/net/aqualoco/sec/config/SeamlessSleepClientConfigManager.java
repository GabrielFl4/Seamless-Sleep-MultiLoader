package net.aqualoco.sec.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.sleepindicator.SleepIndicatorAnchor;
import net.aqualoco.sec.client.sleepindicator.SleepIndicatorMode;
import net.aqualoco.sec.client.sleepindicator.SleepIndicatorVisibility;
import net.aqualoco.sec.client.sleepindicator.TimestampStyle;
import net.aqualoco.sec.client.sleepvisual.SleepZzzConfigBridge;
import net.aqualoco.sec.client.sleepvisual.SleepZzzStyle;
import net.aqualoco.sec.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

// Handles disk persistence for the client config TOML.
public final class SeamlessSleepClientConfigManager {

    private static final String FILE_NAME = "seamless_sleep.toml";
    private static final String LEGACY_JSON_FILE_NAME = "seamless_sleep.json";
    private static final String LEGACY_JSONC_FILE_NAME = "seamless_sleep.jsonc";
    private static final int CONFIG_VERSION = 6;

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

            Integer fileConfigVersion = readOptionalInt(file, "config_version");
            SeamlessSleepClientConfig cfg = readClientConfig(file, fileConfigVersion == null ? 0 : fileConfigVersion);
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

    private static SeamlessSleepClientConfig readClientConfig(CommentedFileConfig file, int fileConfigVersion) {
        SeamlessSleepClientConfig cfg = defaultConfig();

        Boolean legacySleepOverlayEnabled = readOptionalBoolean(file, List.of("overlay", "sleepOverlayEnabled"), "sleepOverlayEnabled");
        cfg.sleepOverlayDarknessMultiplier = readDouble(file, List.of("overlay", "sleepOverlayDarknessMultiplier"), "sleepOverlayDarknessMultiplier", cfg.sleepOverlayDarknessMultiplier);
        cfg.leaveBedHintEnabled = readBoolean(file, List.of("overlay", "leaveBedHintEnabled"), "leaveBedHintEnabled", cfg.leaveBedHintEnabled);
        cfg.sleepContextEnabled = readBoolean(file, List.of("overlay", "sleepContextEnabled"), "sleepContextEnabled", cfg.sleepContextEnabled);
        Object sleepIndicatorModeValue = readRaw(file, List.of("sleep_indicator", "mode"), "sleepIndicatorMode");
        if (sleepIndicatorModeValue != null) {
            cfg.sleepIndicatorMode = parseSleepIndicatorMode(sleepIndicatorModeValue, cfg.sleepIndicatorMode);
        } else if (legacySleepOverlayEnabled != null) {
            cfg.sleepIndicatorMode = legacySleepOverlayEnabled ? SleepIndicatorMode.TEXT : SleepIndicatorMode.OFF;
            if (legacySleepOverlayEnabled) {
                cfg.sleepIndicatorAnchor = SleepIndicatorAnchor.TOP_LEFT;
                cfg.sleepIndicatorVisibility = SleepIndicatorVisibility.SLEEP;
            }
        }
        cfg.sleepIndicatorAnchor = readEnum(file, List.of("sleep_indicator", "anchor"), "sleepIndicatorAnchor", SleepIndicatorAnchor.class, cfg.sleepIndicatorAnchor);
        cfg.sleepIndicatorVisibility = readEnum(file, List.of("sleep_indicator", "visibility"), "sleepIndicatorVisibility", SleepIndicatorVisibility.class, cfg.sleepIndicatorVisibility);
        cfg.sleepIndicatorScale = readDouble(file, List.of("sleep_indicator", "scale"), "sleepIndicatorScale", cfg.sleepIndicatorScale);
        cfg.timestampStyle = readEnum(file, List.of("sleep_indicator", "timestamp", "style"), "timestampStyle", TimestampStyle.class, cfg.timestampStyle);
        cfg.timestampColor = sanitizeRgb(readInt(file, List.of("sleep_indicator", "timestamp", "color"), "timestampColor", cfg.timestampColor));
        cfg.sleepZzzChance = readInt(file, List.of("sleep_zzz", "chance"), "sleepZzzChance", cfg.sleepZzzChance);
        cfg.sleepZzzStyle = readEnum(file, List.of("sleep_zzz", "style"), "sleepZzzStyle", SleepZzzStyle.class, SleepZzzConfigBridge.DEFAULT_STYLE).name();
        cfg.sleepChatTextOpacityMultiplier = readDouble(file, List.of("chat", "sleepChatTextOpacityMultiplier"), "sleepChatTextOpacityMultiplier", cfg.sleepChatTextOpacityMultiplier);
        cfg.sleepChatBackgroundOpacityMultiplier = readDouble(file, List.of("chat", "sleepChatBackgroundOpacityMultiplier"), "sleepChatBackgroundOpacityMultiplier", cfg.sleepChatBackgroundOpacityMultiplier);
        cfg.sleepChatOpacityMultiplier = readDouble(file, List.of("chat", "sleepChatOpacityMultiplier"), "sleepChatOpacityMultiplier", cfg.sleepChatOpacityMultiplier);
        cfg.sleepChatMaxLines = readInt(file, List.of("chat", "sleepChatMaxLines"), "sleepChatMaxLines", cfg.sleepChatMaxLines);
        cfg.sleepCameraTiltDegrees = readDouble(file, List.of("camera", "sleepCameraTiltDegrees"), "sleepCameraTiltDegrees", cfg.sleepCameraTiltDegrees);
        cfg.mouseSmoothnessPercent = readInt(file, List.of("camera", "mouseSmoothnessPercent"), "mouseSmoothnessPercent", cfg.mouseSmoothnessPercent);
        cfg.replayCompatibilityEnabled = readBoolean(file, List.of("advanced", "replayCompatibilityEnabled"), "replayCompatibilityEnabled", cfg.replayCompatibilityEnabled);
        cfg.debugLogsEnabled = readBoolean(file, List.of("advanced", "debugLogsEnabled"), "debugLogsEnabled", cfg.debugLogsEnabled);
        if (fileConfigVersion > 0 && fileConfigVersion < 3) {
            cfg.sleepChatOpacityMultiplier *= 0.5D;
        }
        return cfg;
    }

    private static String toClientToml(SeamlessSleepClientConfig cfg, String modVersion) {
        StringBuilder sb = new StringBuilder(1024);

        sb.append("config_version = ").append(CONFIG_VERSION).append('\n');
        sb.append("mod_version = ").append(toTomlString(modVersion)).append('\n');

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "overlay");
        appendEntry(sb,
                "Sleep vignette darkness while resting in bed. Range: 0.0 to 1.0. 0.0=hidden, 1.0=vanilla. Default: 0.35",
                "sleepOverlayDarknessMultiplier",
                Double.toString(cfg.sleepOverlayDarknessMultiplier));
        appendEntry(sb,
                "Show leave bed hint. Range: true | false. Default: true",
                "leaveBedHintEnabled",
                Boolean.toString(cfg.leaveBedHintEnabled));
        appendEntry(sb,
                "Show sleep context messages. Includes bed restrictions and sleeping progress. Range: true | false. Default: true",
                "sleepContextEnabled",
                Boolean.toString(cfg.sleepContextEnabled));

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "sleep_indicator");
        appendEntry(sb,
                "Sleep indicator renderer. Range: OFF | TEXT | BIOME_CLOCK | TIMESTAMP. Default: BIOME_CLOCK",
                "mode",
                toTomlString(cfg.sleepIndicatorMode.name()));
        appendEntry(sb,
                "Sleep indicator screen anchor. Range: TOP_LEFT | TOP_CENTER | TOP_RIGHT | BOTTOM_RIGHT | CENTER. Default: TOP_LEFT",
                "anchor",
                toTomlString(cfg.sleepIndicatorAnchor.name()));
        appendEntry(sb,
                "Sleep indicator visibility. Range: BED | SLEEP | ALWAYS. Default: SLEEP",
                "visibility",
                toTomlString(cfg.sleepIndicatorVisibility.name()));
        appendEntry(sb,
                "Sleep indicator visual scale. Range: 0.25 to 4.0. Default: 1.0",
                "scale",
                Double.toString(cfg.sleepIndicatorScale));

        appendSectionGap(sb, 1);
        appendSectionHeader(sb, "sleep_indicator.timestamp");
        appendEntry(sb,
                "Timestamp layout. Range: DAY_FIRST | TIME_FIRST. Default: DAY_FIRST",
                "style",
                toTomlString(cfg.timestampStyle.name()));
        appendEntry(sb,
                "Timestamp text color as RGB decimal. Default: 16777215 (white)",
                "color",
                Integer.toString(sanitizeRgb(cfg.timestampColor)));

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "sleep_zzz");
        appendEntry(sb,
                "Chance to show world-space Zs for each counted sleep session. Range: 0 to 100. 0=never, 100=always. Default: 70",
                "chance",
                Integer.toString(cfg.sleepZzzChance));
        appendEntry(sb,
                "Visual style for sleeping Zs. Range: SEQUENTIAL_TRAIL | CARTOON_DRIFT. Default: CARTOON_DRIFT",
                "style",
                toTomlString(cfg.sleepZzzStyle));

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "chat");
        appendEntry(sb,
                "Overall chat opacity while resting in bed. Range: 0.0 to 1.0. 0.0=hidden, 0.5=current Seamless preset, 1.0=vanilla. Default: 0.5",
                "sleepChatOpacityMultiplier",
                Double.toString(cfg.sleepChatOpacityMultiplier));
        appendEntry(sb,
                "Visible chat lines while resting in bed. Range: integer 0 to 12. Default: 4",
                "sleepChatMaxLines",
                Integer.toString(cfg.sleepChatMaxLines));
        appendEntry(sb,
                "Legacy text opacity anchor kept for compatibility. It defines the midpoint preset used by the overall chat opacity slider. Range: 0.0 to 1.0. Default: 0.5",
                "sleepChatTextOpacityMultiplier",
                Double.toString(cfg.sleepChatTextOpacityMultiplier));
        appendEntry(sb,
                "Legacy background opacity anchor kept for compatibility. It defines the midpoint preset used by the overall chat opacity slider. Range: 0.0 to 1.0. Default: 0.4",
                "sleepChatBackgroundOpacityMultiplier",
                Double.toString(cfg.sleepChatBackgroundOpacityMultiplier));

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "camera");
        appendEntry(sb,
                "Lay down camera tilt in degrees. Range: 0.0 to 90.0. Value 0.0 is canonicalized to 0.1. Default: 10.0",
                "sleepCameraTiltDegrees",
                Double.toString(cfg.sleepCameraTiltDegrees));
        appendEntry(sb,
                "Mouse damping percent while resting and during the sleep skip. It scales both the smoothing and the custom reduced look response. Range: 0 to 100. 0=vanilla, 100=max. Default: 100",
                "mouseSmoothnessPercent",
                Integer.toString(cfg.mouseSmoothnessPercent));

        appendSectionGap(sb, 2);
        appendSectionHeader(sb, "advanced");
        appendEntry(sb,
                "Replay and Flashback compatibility mode. Range: true | false. Default: true",
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

    private static int sanitizeRgb(int value) {
        return value & 0x00FFFFFF;
    }

    private static double readDouble(CommentedFileConfig file, List<String> path, String legacyKey, double fallback) {
        Object value = readRaw(file, path, legacyKey);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static Object readRaw(CommentedFileConfig file, List<String> path, String legacyKey) {
        Object value = path == null ? null : file.getRaw(path);
        if (value != null || legacyKey == null || legacyKey.isBlank()) {
            return value;
        }
        return file.getRaw(legacyKey);
    }

    private static Boolean readOptionalBoolean(CommentedFileConfig file, List<String> path, String legacyKey) {
        Object value = readRaw(file, path, legacyKey);
        return value instanceof Boolean bool ? bool : null;
    }

    private static <E extends Enum<E>> E readEnum(
            CommentedFileConfig file,
            List<String> path,
            String legacyKey,
            Class<E> enumClass,
            E fallback
    ) {
        return parseEnum(readRaw(file, path, legacyKey), enumClass, fallback);
    }

    private static <E extends Enum<E>> E parseEnum(Object value, Class<E> enumClass, E fallback) {
        if (!(value instanceof String raw)) {
            return fallback;
        }

        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumClass, normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static SleepIndicatorMode parseSleepIndicatorMode(Object value, SleepIndicatorMode fallback) {
        if (!(value instanceof String raw)) {
            return fallback;
        }

        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if ("OVERLAY".equals(normalized)) {
            return SleepIndicatorMode.TEXT;
        }
        return parseEnum(normalized, SleepIndicatorMode.class, fallback);
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
