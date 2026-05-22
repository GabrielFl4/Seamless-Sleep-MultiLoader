package net.aqualoco.sec.platform;

import net.aqualoco.sec.network.BedLookNetworking;
import net.aqualoco.sec.network.BedLookSyncPayload;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.config.ServerConfigMutationService;
import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
import net.aqualoco.sec.network.BedHudSleepProgressPayload;
import net.aqualoco.sec.network.ClientHelloC2SPayload;
import net.aqualoco.sec.network.ServerConfigAccessRequestC2SPayload;
import net.aqualoco.sec.network.ServerConfigAccessS2CPayload;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.ServerConfigUpdateC2SPayload;
import net.aqualoco.sec.network.ServerConfigUpdateResultS2CPayload;
import net.aqualoco.sec.network.ServerHelloS2CPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

// Forge networking bridge that owns channel setup and payload dispatch.
public class ForgeNetworkHelper implements INetworkHelper {

    // Small client-only handler contract used by channel callbacks.
    interface ClientHandler {
        void handleBedHudSleepProgress(BedHudSleepProgressPayload payload);
        void handleStart(SleepAnimationStartPayload payload);
        void handleStop(SleepAnimationStopPayload payload);
        void handleServerConfig(ServerConfigSyncPayload payload);
        void handleServerConfigAccess(ServerConfigAccessS2CPayload payload);
        void handleServerConfigUpdateResult(ServerConfigUpdateResultS2CPayload payload);
        void handleServerHello(ServerHelloS2CPayload payload);
    }

    private static final Identifier CHANNEL_ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "network");
    private static Channel<CustomPacketPayload> channel;
    private static boolean registered;
    private static ClientHandler clientHandler;

    @Override
    public void registerPayloads() {
        if (registered) {
            return;
        }
        registered = true;

        channel = ChannelBuilder.named(CHANNEL_ID)
                .networkProtocolVersion(1)
                .payloadChannel()
                .play()
                .clientbound()
                .addMain(
                        BedHudSleepProgressPayload.ID,
                        BedHudSleepProgressPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleBedHudSleepProgress
                )
                .addMain(
                        SleepAnimationStartPayload.ID,
                        SleepAnimationStartPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleStart
                )
                .addMain(
                        SleepAnimationStopPayload.ID,
                        SleepAnimationStopPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleStop
                )
                .addMain(
                        ServerConfigSyncPayload.ID,
                        ServerConfigSyncPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleServerConfig
                )
                .addMain(
                        ServerConfigAccessS2CPayload.ID,
                        ServerConfigAccessS2CPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleServerConfigAccess
                )
                .addMain(
                        ServerConfigUpdateResultS2CPayload.ID,
                        ServerConfigUpdateResultS2CPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleServerConfigUpdateResult
                )
                .addMain(
                        ServerHelloS2CPayload.ID,
                        ServerHelloS2CPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleServerHello
                )
                .serverbound()
                .addMain(
                        ClientHelloC2SPayload.ID,
                        ClientHelloC2SPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleClientHello
                )
                .addMain(
                        ServerConfigAccessRequestC2SPayload.ID,
                        ServerConfigAccessRequestC2SPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleServerConfigAccessRequest
                )
                .addMain(
                        ServerConfigUpdateC2SPayload.ID,
                        ServerConfigUpdateC2SPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleServerConfigUpdate
                )
                .addMain(
                        BedLookSyncPayload.ID,
                        BedLookSyncPayload.CODEC.cast(),
                        ForgeNetworkHelper::handleBedLookSync
                )
                .build();
    }

    @Override
    public void registerClientHandlers() {
        if (clientHandler == null) {
            clientHandler = new ForgeClientNetworkHandler();
        }
    }

    @Override
    public void sendToPlayers(ServerLevel world, CustomPacketPayload payload) {
        if (channel == null) {
            return;
        }

        for (ServerPlayer player : world.players()) {
            channel.send(payload, PacketDistributor.PLAYER.with(player));
        }
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (channel == null) {
            return;
        }

        channel.send(payload, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (channel == null) {
            return;
        }

        channel.send(payload, PacketDistributor.SERVER.noArg());
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return channel != null
                && player != null
                && player.connection != null
                && channel.isRemotePresent(player.connection.getConnection());
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        Minecraft client = Minecraft.getInstance();
        return channel != null
                && type != null
                && client.getConnection() != null
                && channel.isRemotePresent(client.getConnection().getConnection());
    }

    private static void handleStart(SleepAnimationStartPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleStart(payload);
            }
        });
    }

    private static void handleBedHudSleepProgress(BedHudSleepProgressPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleBedHudSleepProgress(payload);
            }
        });
    }

    private static void handleStop(SleepAnimationStopPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleStop(payload);
            }
        });
    }

    private static void handleServerConfig(ServerConfigSyncPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleServerConfig(payload);
            }
        });
    }

    private static void handleServerConfigAccess(ServerConfigAccessS2CPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleServerConfigAccess(payload);
            }
        });
    }

    private static void handleServerConfigUpdateResult(ServerConfigUpdateResultS2CPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleServerConfigUpdateResult(payload);
            }
        });
    }

    private static void handleServerHello(ServerHelloS2CPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleServerHello(payload);
            }
        });
    }

    private static void handleClientHello(ClientHelloC2SPayload payload, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerSeamlessClientPresenceManager.handleClientHello(player, payload);
            }
        });
    }

    private static void handleServerConfigAccessRequest(ServerConfigAccessRequestC2SPayload payload, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerConfigMutationService.handleAccessRequest(player);
            }
        });
    }

    private static void handleServerConfigUpdate(ServerConfigUpdateC2SPayload payload, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerConfigMutationService.handleRemoteUpdate(player, payload);
            }
        });
    }

    private static void handleBedLookSync(BedLookSyncPayload payload, CustomPayloadEvent.Context context) {
        if (context.isClientSide()) {
            return;
        }

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                BedLookNetworking.handleServer(player, payload);
            }
        });
    }
}
