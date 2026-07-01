package net.aqualoco.sec.compat;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
import net.aqualoco.sec.network.VivecraftBedOffsetC2SPayload;
import net.aqualoco.sec.network.VivecraftBedOffsetS2CPayload;
import net.aqualoco.sec.network.VivecraftVrStatePayload;
import net.aqualoco.sec.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Shared Vivecraft identifiers and server-side VR state mirrored from Seamless clients.
public final class VivecraftCompat {
    public static final String MOD_ID = "vivecraft";
    public static final String CLIENT_API_RESOURCE = "org/vivecraft/api/client/VRClientAPI.class";
    public static final String PLAYER_API_RESOURCE = "org/vivecraft/api/VRAPI.class";
    public static final String POST_PROCESS_UBO_RESOURCE = "org/vivecraft/client_vr/render/ubos/PostProcessUBO.class";
    public static final String PLAYER_EXTENSION_RESOURCE = "org/vivecraft/client_vr/extensions/PlayerExtension.class";
    public static final String INTERACT_TRACKER_RESOURCE = "org/vivecraft/client_vr/gameplay/trackers/InteractTracker.class";
    public static final String VR_PLAYER_RESOURCE = "org/vivecraft/client_vr/gameplay/VRPlayer.class";
    public static final String VR_PLAYER_MODEL_RESOURCE = "org/vivecraft/client/render/VRPlayerModel.class";
    public static final String MODEL_UTILS_RESOURCE = "org/vivecraft/client/utils/ModelUtils.class";

    private static final Map<UUID, Boolean> SERVER_VR_STATES = new HashMap<>();
    private static final Map<UUID, Double> SERVER_BED_ROOM_Y_OFFSETS = new HashMap<>();
    private static volatile Boolean vivecraftLoaded;
    private static boolean detectedLogged;

    private VivecraftCompat() {
    }

    public static void init() {
        if (isVivecraftLoaded()) {
            logDetected();
        }
    }

    public static boolean isVivecraftLoaded() {
        Boolean cached = vivecraftLoaded;
        if (cached != null) {
            return cached;
        }

        synchronized (VivecraftCompat.class) {
            cached = vivecraftLoaded;
            if (cached == null) {
                cached = detectVivecraft();
                vivecraftLoaded = cached;
            }
        }
        return cached;
    }

    private static boolean detectVivecraft() {
        try {
            if (Services.PLATFORM.isModLoaded(MOD_ID)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return hasClassResource(CLIENT_API_RESOURCE, Thread.currentThread().getContextClassLoader())
                || hasClassResource(CLIENT_API_RESOURCE, VivecraftCompat.class.getClassLoader());
    }

    public static boolean hasClassResource(String classResourcePath) {
        return hasClassResource(classResourcePath, Thread.currentThread().getContextClassLoader())
                || hasClassResource(classResourcePath, VivecraftCompat.class.getClassLoader());
    }

    public static void handleClientVrState(ServerPlayer player, VivecraftVrStatePayload payload) {
        if (!ServerSeamlessClientPresenceManager.requireConfirmed(player, "vivecraft_vr_state")) {
            return;
        }

        setServerVrActive(player, payload.active());
        if (!payload.active()) {
            clearServerBedOffset(player, true);
        }
    }

    public static void handleClientBedOffset(ServerPlayer player, VivecraftBedOffsetC2SPayload payload) {
        if (!ServerSeamlessClientPresenceManager.requireConfirmed(player, "vivecraft_bed_offset")) {
            return;
        }

        boolean active = payload.active() && Double.isFinite(payload.yOffset());
        double yOffset = active ? payload.yOffset() : 0.0D;
        if (active) {
            SERVER_BED_ROOM_Y_OFFSETS.put(player.getUUID(), yOffset);
        } else {
            SERVER_BED_ROOM_Y_OFFSETS.remove(player.getUUID());
        }

        if (player.level() instanceof ServerLevel world) {
            sendBedOffsetToConfirmedPlayers(world, player.getUUID(), active, yOffset);
        }
    }

    public static boolean isServerVrActive(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        return SERVER_VR_STATES.getOrDefault(player.getUUID(), false);
    }

    public static void setServerVrActive(ServerPlayer player, boolean active) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUUID();
        if (active) {
            SERVER_VR_STATES.put(playerId, true);
        } else {
            SERVER_VR_STATES.remove(playerId);
        }
    }

    public static void clearServerVrState(ServerPlayer player) {
        if (player != null) {
            SERVER_VR_STATES.remove(player.getUUID());
            clearServerBedOffset(player, true);
        }
    }

    public static void resetServerVrStates() {
        SERVER_VR_STATES.clear();
        SERVER_BED_ROOM_Y_OFFSETS.clear();
    }

    public static void sendBedOffsetSnapshotsToPlayer(ServerPlayer player) {
        if (player == null || SERVER_BED_ROOM_Y_OFFSETS.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Double> entry : SERVER_BED_ROOM_Y_OFFSETS.entrySet()) {
            Services.NETWORK.sendToPlayerIfSupported(
                    player,
                    new VivecraftBedOffsetS2CPayload(entry.getKey(), true, entry.getValue())
            );
        }
    }

    private static void sendBedOffsetToConfirmedPlayers(ServerLevel world,
                                                        UUID sourcePlayerId,
                                                        boolean active,
                                                        double yOffset) {
        VivecraftBedOffsetS2CPayload payload = new VivecraftBedOffsetS2CPayload(sourcePlayerId, active, yOffset);
        for (ServerPlayer target : world.players()) {
            if (ServerSeamlessClientPresenceManager.isConfirmed(target)) {
                Services.NETWORK.sendToPlayerIfSupported(target, payload);
            }
        }
    }

    private static void clearServerBedOffset(ServerPlayer player, boolean broadcast) {
        UUID playerId = player.getUUID();
        boolean removed = SERVER_BED_ROOM_Y_OFFSETS.remove(playerId) != null;
        if (broadcast && removed && player.level() instanceof ServerLevel world) {
            sendBedOffsetToConfirmedPlayers(world, playerId, false, 0.0D);
        }
    }

    private static void logDetected() {
        if (detectedLogged) {
            return;
        }

        detectedLogged = true;
        Constants.info("Vivecraft detected. VR compatibility hooks are available when local VR is active.");
    }

    private static boolean hasClassResource(String classResourcePath, ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            return classLoader.getResource(classResourcePath) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
