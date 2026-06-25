package net.aqualoco.sec.compat;

import net.aqualoco.sec.platform.Services;
import net.minecraft.server.MinecraftServer;

// Server-side Flashback replay detection without a hard dependency on Flashback classes.
public final class FlashbackReplayServerCompat {
    private static final String FLASHBACK_MOD_ID = "flashback";
    private static final String REPLAY_SERVER_CLASS = "com.moulberry.flashback.playback.ReplayServer";

    private FlashbackReplayServerCompat() {
    }

    public static boolean isReplayServer(MinecraftServer server) {
        if (server == null || !Services.PLATFORM.isModLoaded(FLASHBACK_MOD_ID)) {
            return false;
        }

        Class<?> type = server.getClass();
        while (type != null) {
            if (REPLAY_SERVER_CLASS.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
