package net.aqualoco.sec.handshake;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.ServerConfigMutationService;
import net.aqualoco.sec.network.ClientHelloC2SPayload;
import net.aqualoco.sec.network.ServerConfigSync;
import net.aqualoco.sec.network.ServerHelloS2CPayload;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServerSeamlessClientPresenceManager {
    private static final String LOG_PREFIX = "[Seamless Sleep Handshake]";
    private static final int MAX_FAILURE_HISTORY = 32;

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private static final ArrayDeque<HandshakeFailureRecord> FAILURE_HISTORY = new ArrayDeque<>();

    private ServerSeamlessClientPresenceManager() {
    }

    public static void beginHandshake(ServerPlayer player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUUID();
        Entry entry = new Entry(player);
        ENTRIES.put(playerId, entry);

        if (!Services.NETWORK.canSendToPlayer(player, ServerHelloS2CPayload.ID)) {
            failAndKick(entry, ServerSeamlessClientPresenceState.MISSING_CLIENT_MOD, missingClientMessage());
            return;
        }

        ServerHelloS2CPayload payload = new ServerHelloS2CPayload(
                SeamlessProtocol.PROTOCOL_VERSION,
                SeamlessProtocol.modVersion(),
                SeamlessFeatureFlags.CURRENT,
                ServerConfigMutationService.currentRevision()
        );
        try {
            Services.NETWORK.sendToPlayer(player, payload);
        } catch (Exception exception) {
            failAndKick(entry, ServerSeamlessClientPresenceState.INTERNAL_ERROR,
                    whiteLiteral("Failed to send Seamless Sleep handshake payload."));
            return;
        }
        Constants.LOG.info("{} Sent server hello to {} (protocol {}, version {}, features {}).",
                LOG_PREFIX,
                entry.playerName,
                payload.protocolVersion(),
                payload.modVersion(),
                payload.featureFlags());
    }

    public static void handleClientHello(ServerPlayer player, ClientHelloC2SPayload payload) {
        if (player == null || payload == null) {
            return;
        }

        Entry entry = ENTRIES.computeIfAbsent(player.getUUID(), ignored -> new Entry(player));
        entry.player = player;
        entry.protocolVersion = payload.protocolVersion();
        entry.modVersion = sanitizeVersion(payload.modVersion());
        entry.featureFlags = payload.featureFlags();

        if (payload.protocolVersion() != SeamlessProtocol.PROTOCOL_VERSION) {
            failAndKick(entry, ServerSeamlessClientPresenceState.PROTOCOL_MISMATCH,
                    protocolMismatchMessage(payload.protocolVersion(), SeamlessProtocol.PROTOCOL_VERSION));
            return;
        }

        if (!SeamlessFeatureFlags.hasRequiredFeatures(payload.featureFlags())) {
            failAndKick(entry, ServerSeamlessClientPresenceState.PROTOCOL_MISMATCH,
                    missingFeatureMessage(payload.featureFlags()));
            return;
        }

        if (!SeamlessProtocol.isVersionCompatible(payload.modVersion())) {
            failAndKick(entry, ServerSeamlessClientPresenceState.VERSION_MISMATCH,
                    versionMismatchMessage(payload.modVersion(), SeamlessProtocol.modVersion()));
            return;
        }

        entry.state = ServerSeamlessClientPresenceState.CONFIRMED;
        Constants.LOG.info("{} Confirmed {} (protocol {}, version {}, features {}).",
                LOG_PREFIX,
                entry.playerName,
                entry.protocolVersion,
                entry.modVersion,
                entry.featureFlags);
        syncConfirmedClient(player);
    }

    public static void tick(MinecraftServer server) {
        // Intentionally no timeout kick: a lagged client is left pending rather than disconnected.
    }

    public static void handleDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }

        Entry entry = ENTRIES.remove(player.getUUID());
        if (entry != null && entry.state == ServerSeamlessClientPresenceState.PENDING) {
            recordFailure(entry, ServerSeamlessClientPresenceState.DISCONNECTED, "Disconnected before handshake confirmation.");
        }
    }

    public static void reset() {
        ENTRIES.clear();
        FAILURE_HISTORY.clear();
    }

    public static boolean isConfirmed(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        Entry entry = ENTRIES.get(player.getUUID());
        return entry != null && entry.state == ServerSeamlessClientPresenceState.CONFIRMED;
    }

    public static boolean requireConfirmed(ServerPlayer player, String payloadName) {
        if (isConfirmed(player)) {
            return true;
        }

        String playerName = player == null ? "<null>" : player.getGameProfile().name();
        Constants.LOG.warn("{} Rejected {} from {} before confirmed handshake.",
                LOG_PREFIX,
                payloadName,
                playerName);
        return false;
    }

    public static Collection<SeamlessClientPresenceSnapshot> snapshots() {
        long now = System.currentTimeMillis();
        List<SeamlessClientPresenceSnapshot> snapshots = new ArrayList<>(ENTRIES.size());
        for (Entry entry : ENTRIES.values()) {
            snapshots.add(new SeamlessClientPresenceSnapshot(
                    entry.playerId,
                    entry.playerName,
                    entry.state,
                    entry.protocolVersion,
                    entry.modVersion,
                    entry.featureFlags,
                    Math.max(0L, now - entry.createdAtMillis)
            ));
        }
        return snapshots;
    }

    public static List<HandshakeFailureRecord> failureHistory() {
        return List.copyOf(FAILURE_HISTORY);
    }

    public static int confirmedCount() {
        int count = 0;
        for (Entry entry : ENTRIES.values()) {
            if (entry.state == ServerSeamlessClientPresenceState.CONFIRMED) {
                count++;
            }
        }
        return count;
    }

    private static void syncConfirmedClient(ServerPlayer player) {
        ServerConfigSync.sendToPlayer(player, SeamlessSleepServerConfigManager.get());
        ServerConfigMutationService.sendAccessToPlayer(player);
        SleepAnimationNetworking.sendActiveSnapshotToPlayer(player);
    }

    private static void failAndKick(Entry entry,
                                    ServerSeamlessClientPresenceState state,
                                    Component message) {
        entry.state = state;
        recordFailure(entry, state, message.getString());
        Constants.LOG.warn("{} Disconnecting {}: {}", LOG_PREFIX, entry.playerName, message.getString());
        try {
            entry.player.connection.disconnect(message);
        } catch (Exception exception) {
            entry.state = ServerSeamlessClientPresenceState.INTERNAL_ERROR;
            recordFailure(entry, ServerSeamlessClientPresenceState.INTERNAL_ERROR, exception.getMessage());
            Constants.LOG.warn("{} Failed to disconnect {} after handshake failure: {}",
                    LOG_PREFIX,
                    entry.playerName,
                    exception.getMessage());
        }
    }

    private static void recordFailure(Entry entry,
                                      ServerSeamlessClientPresenceState state,
                                      String reason) {
        if (FAILURE_HISTORY.size() >= MAX_FAILURE_HISTORY) {
            FAILURE_HISTORY.removeFirst();
        }
        FAILURE_HISTORY.addLast(new HandshakeFailureRecord(
                entry.playerId,
                entry.playerName,
                state,
                reason == null ? "" : reason,
                System.currentTimeMillis()
        ));
    }

    private static Component missingClientMessage() {
        return whiteLiteral("Seamless Sleep is required on both client and server.\n"
                + "Your client is missing it or using an incompatible version.");
    }

    private static Component protocolMismatchMessage(int clientProtocol, int serverProtocol) {
        return whiteLiteral("Seamless Sleep protocol mismatch.\nClient protocol "
                + clientProtocol
                + ", server protocol "
                + serverProtocol
                + ".");
    }

    private static Component versionMismatchMessage(String clientVersion, String serverVersion) {
        return whiteLiteral("Seamless Sleep version mismatch.\nClient "
                + sanitizeVersion(clientVersion)
                + ", server "
                + sanitizeVersion(serverVersion)
                + ".");
    }

    private static Component missingFeatureMessage(int flags) {
        return whiteLiteral("Seamless Sleep feature mismatch.\nClient flags "
                + flags
                + ", required flags "
                + SeamlessFeatureFlags.REQUIRED_CLIENT_FEATURES
                + ".");
    }

    private static Component whiteLiteral(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    private static String sanitizeVersion(String version) {
        return version == null || version.isBlank() ? "unknown" : version;
    }

    private static final class Entry {
        private final UUID playerId;
        private final String playerName;
        private final long createdAtMillis = System.currentTimeMillis();
        private ServerPlayer player;
        private ServerSeamlessClientPresenceState state = ServerSeamlessClientPresenceState.PENDING;
        private int protocolVersion = -1;
        private String modVersion = "unknown";
        private int featureFlags;

        private Entry(ServerPlayer player) {
            this.player = player;
            this.playerId = player.getUUID();
            this.playerName = player.getGameProfile().name();
        }
    }
}
