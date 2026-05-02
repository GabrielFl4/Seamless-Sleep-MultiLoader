package net.aqualoco.sec.platform;

import net.aqualoco.sec.network.BedLookNetworking;
import net.aqualoco.sec.network.BedLookSyncPayload;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.network.BedHudSleepProgressPayload;
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

// Forge networking bridge that owns channel setup and payload dispatch.
public class ForgeNetworkHelper implements INetworkHelper {

    // Small client-only handler contract used by channel callbacks.
    interface ClientHandler {
        void handleBedHudSleepProgress(BedHudSleepProgressPayload payload);
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
                .serverbound()
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
