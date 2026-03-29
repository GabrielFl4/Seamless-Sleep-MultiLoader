package net.aqualoco.sec.platform;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

public class ForgeNetworkHelper implements INetworkHelper {

    interface ClientHandler {
        void handleStart(SleepAnimationStartPayload payload);

        void handleStop(SleepAnimationStopPayload payload);

        void handleServerConfig(ServerConfigSyncPayload payload);
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
                .build();
    }

    @Override
    public void registerClientHandlers() {
        if (clientHandler == null) {
            clientHandler = ClientInit.createHandler();
        }
        ClientInit.registerDisconnectReset();
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

    private static void handleStart(SleepAnimationStartPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        ClientHandler handler = clientHandler;
        if (handler != null) {
            handler.handleStart(payload);
        }
    }

    private static void handleStop(SleepAnimationStopPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        ClientHandler handler = clientHandler;
        if (handler != null) {
            handler.handleStop(payload);
        }
    }

    private static void handleServerConfig(ServerConfigSyncPayload payload, CustomPayloadEvent.Context context) {
        if (!context.isClientSide()) {
            return;
        }

        ClientHandler handler = clientHandler;
        if (handler != null) {
            handler.handleServerConfig(payload);
        }
    }

    private static final class ClientInit {
        private ClientInit() {
        }

        private static ClientHandler createHandler() {
            return new ForgeClientNetworkHandler();
        }

        private static void registerDisconnectReset() {
            net.aqualoco.sec.client.ForgeClientLifecycle.registerDisconnectReset();
        }
    }
}
