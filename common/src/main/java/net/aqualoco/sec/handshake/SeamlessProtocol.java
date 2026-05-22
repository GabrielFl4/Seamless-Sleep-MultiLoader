package net.aqualoco.sec.handshake;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;

public final class SeamlessProtocol {
    public static final int PROTOCOL_VERSION = 3;

    private SeamlessProtocol() {
    }

    public static String modVersion() {
        try {
            String version = Services.PLATFORM.getModVersion(Constants.MOD_ID);
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (Exception exception) {
            return "unknown";
        }
    }

    public static boolean isVersionCompatible(String remoteVersion) {
        String localVersion = modVersion();
        if (isUnknown(localVersion) || isUnknown(remoteVersion)) {
            return true;
        }
        return localVersion.equals(remoteVersion);
    }

    public static boolean isUnknown(String version) {
        return version == null || version.isBlank() || "unknown".equalsIgnoreCase(version);
    }
}
