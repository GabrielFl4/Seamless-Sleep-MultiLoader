package net.aqualoco.sec.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class SeamlessSleepLocalClientHints {
    private static final String PREFS_NODE = "net/aqualoco/seamlesssleep";
    private static final String LOCAL_HINTS_RELATIVE_PATH = "data/seamlesssleep/client-hints.properties";
    private static final String GUIDE_CLICKED_KEY = "yaclGuideClicked";
    private static final String GUIDE_CLICK_TOKEN_KEY = "yaclGuideClickToken";

    private SeamlessSleepLocalClientHints() {
    }

    public static boolean shouldShowYaclGuideHighlight() {
        try {
            LocalHints localHints = readLocalHints();
            if (!localHints.clicked() || localHints.token().isBlank()) {
                return true;
            }

            Preferences instancePrefs = instancePreferences();
            if (instancePrefs == null) {
                return true;
            }

            boolean prefClicked = instancePrefs.getBoolean(GUIDE_CLICKED_KEY, false);
            String prefToken = instancePrefs.get(GUIDE_CLICK_TOKEN_KEY, "");
            boolean hideCorners = prefClicked && localHints.token().equals(prefToken);
            return !hideCorners;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    public static boolean markYaclGuideClicked() {
        String token = UUID.randomUUID().toString();
        try {
            Preferences instancePrefs = instancePreferences();
            if (instancePrefs == null) {
                return false;
            }
            instancePrefs.putBoolean(GUIDE_CLICKED_KEY, true);
            instancePrefs.put(GUIDE_CLICK_TOKEN_KEY, token);
            instancePrefs.flush();
            writeLocalHints(token);
            return true;
        } catch (BackingStoreException | IOException | RuntimeException ignored) {
            return false;
        }
    }

    static String instanceHash() {
        try {
            Path gameDirectory = gameDirectoryPath();
            Path normalizedPath;
            try {
                normalizedPath = gameDirectory.toRealPath();
            } catch (IOException ignored) {
                normalizedPath = gameDirectory.toAbsolutePath().normalize();
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedPath.toString().getBytes(StandardCharsets.UTF_8));
            return hex(hash);
        } catch (NoSuchAlgorithmException | RuntimeException ignored) {
            return "";
        }
    }

    static Path localHintsPath() {
        return gameDirectoryPath().resolve(LOCAL_HINTS_RELATIVE_PATH);
    }

    static LocalHints readLocalHints() {
        try {
            Path path = localHintsPath();
            if (!Files.isRegularFile(path)) {
                return LocalHints.EMPTY;
            }

            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            }

            boolean clicked = Boolean.parseBoolean(properties.getProperty(GUIDE_CLICKED_KEY, "false"));
            String token = properties.getProperty(GUIDE_CLICK_TOKEN_KEY, "");
            return new LocalHints(clicked, token == null ? "" : token.trim());
        } catch (IOException | RuntimeException ignored) {
            return LocalHints.EMPTY;
        }
    }

    static void writeLocalHints(String token) throws IOException {
        Path path = localHintsPath();
        Files.createDirectories(path.getParent());

        Properties properties = new Properties();
        properties.setProperty(GUIDE_CLICKED_KEY, "true");
        properties.setProperty(GUIDE_CLICK_TOKEN_KEY, token);
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "Seamless Sleep local client hints");
        }
    }

    private static Preferences instancePreferences() {
        String hash = instanceHash();
        if (hash.isBlank()) {
            return null;
        }
        return Preferences.userRoot().node(PREFS_NODE).node(hash);
    }

    private static Path gameDirectoryPath() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            throw new IllegalStateException("Minecraft game directory is unavailable.");
        }
        return minecraft.gameDirectory.toPath();
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    record LocalHints(boolean clicked, String token) {
        static final LocalHints EMPTY = new LocalHints(false, "");
    }
}
