package net.aqualoco.sec.handshake;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.RemoteServerConfigClientState;
import net.aqualoco.sec.client.VivecraftClientCompat;
import net.aqualoco.sec.network.ClientHelloC2SPayload;
import net.aqualoco.sec.network.ServerHelloS2CPayload;
import net.aqualoco.sec.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientHandshakeState {
    private static final String LOG_PREFIX = "[Seamless Sleep Handshake]";

    private static ClientState state = ClientState.IDLE;
    private static boolean disconnectRequested;
    private static String serverVersion = "unknown";
    private static int serverProtocol = -1;
    private static int serverFeatureFlags;

    private ClientHandshakeState() {
    }

    public static void tick(Minecraft client) {
        if (client == null || client.player == null || client.getConnection() == null) {
            reset("client_not_connected");
            return;
        }

        if (state == ClientState.CONFIRMED || disconnectRequested) {
            return;
        }

        if (state == ClientState.IDLE) {
            state = ClientState.WAITING_FOR_SERVER_HELLO;
        }

        disconnectIfServerMissingSeamless(client);
    }

    public static void onPlayConnectionReady(Minecraft client) {
        if (client == null || client.player == null || client.getConnection() == null) {
            return;
        }
        if (state == ClientState.IDLE) {
            state = ClientState.WAITING_FOR_SERVER_HELLO;
        }
        disconnectIfServerMissingSeamless(client);
    }

    public static void handleServerHello(ServerHelloS2CPayload payload) {
        if (payload == null || disconnectRequested) {
            return;
        }

        serverProtocol = payload.protocolVersion();
        serverVersion = payload.modVersion();
        serverFeatureFlags = payload.featureFlags();

        if (payload.protocolVersion() != SeamlessProtocol.PROTOCOL_VERSION) {
            disconnect(Minecraft.getInstance(), protocolMismatchMessage(payload.protocolVersion()));
            return;
        }

        if (!SeamlessFeatureFlags.hasRequiredFeatures(payload.featureFlags())) {
            disconnect(Minecraft.getInstance(), featureMismatchMessage(payload.featureFlags()));
            return;
        }

        if (!SeamlessProtocol.isVersionCompatible(payload.modVersion())) {
            disconnect(Minecraft.getInstance(), versionMismatchMessage(payload.modVersion()));
            return;
        }

        try {
            Services.NETWORK.sendToServer(new ClientHelloC2SPayload(
                    SeamlessProtocol.PROTOCOL_VERSION,
                    SeamlessProtocol.modVersion(),
                    SeamlessFeatureFlags.CURRENT
            ));
        } catch (Exception exception) {
            Constants.LOG.warn("{} Failed to send client hello: {}", LOG_PREFIX, exception.getMessage());
            disconnect(Minecraft.getInstance(), whiteLiteral("Failed to complete Seamless Sleep handshake."));
            return;
        }
        state = ClientState.CONFIRMED;
        VivecraftClientCompat.sendVrStateToServer(true);
        Constants.LOG.info("{} Confirmed server support (protocol {}, version {}, features {}).",
                LOG_PREFIX,
                serverProtocol,
                serverVersion,
                serverFeatureFlags);
    }

    public static boolean isConfirmed() {
        return state == ClientState.CONFIRMED;
    }

    public static void reset(String reason) {
        if (state != ClientState.IDLE || disconnectRequested) {
            Constants.LOG.info("{} Reset client handshake state: {}.", LOG_PREFIX, reason);
        }
        state = ClientState.IDLE;
        disconnectRequested = false;
        serverVersion = "unknown";
        serverProtocol = -1;
        serverFeatureFlags = 0;
        RemoteServerConfigClientState.reset();
        VivecraftClientCompat.resetClientSync();
    }

    private static void disconnectIfServerMissingSeamless(Minecraft client) {
        if (client.hasSingleplayerServer()) {
            return;
        }
        if (!Services.NETWORK.canSendToServer(ClientHelloC2SPayload.ID)) {
            disconnect(client, serverMissingMessage());
        }
    }

    private static Component serverMissingMessage() {
        return whiteLiteral("This server does not have Seamless Sleep installed.\n"
                + "Install it on the server as well or remove it from your client!");
    }

    private static Component protocolMismatchMessage(int remoteProtocol) {
        return whiteLiteral("Seamless Sleep protocol mismatch.\n"
                + "Remote protocol "
                + remoteProtocol
                + ", local protocol "
                + SeamlessProtocol.PROTOCOL_VERSION
                + ".");
    }

    private static Component versionMismatchMessage(String remoteVersion) {
        return whiteLiteral("Seamless Sleep version mismatch.\n"
                + "Remote version "
                + sanitizeVersion(remoteVersion)
                + ", local version "
                + SeamlessProtocol.modVersion()
                + ".");
    }

    private static Component featureMismatchMessage(int remoteFlags) {
        return whiteLiteral("Seamless Sleep feature mismatch.\n"
                + "Remote flags "
                + remoteFlags
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

    private static void disconnect(Minecraft client, Component message) {
        if (client == null || client.getConnection() == null || disconnectRequested) {
            return;
        }

        disconnectRequested = true;
        state = ClientState.DISCONNECTING;
        Constants.LOG.warn("{} Disconnecting client: {}", LOG_PREFIX, message.getString());
        client.getConnection().getConnection().disconnect(message);
    }

    private enum ClientState {
        IDLE,
        WAITING_FOR_SERVER_HELLO,
        CONFIRMED,
        DISCONNECTING
    }
}
