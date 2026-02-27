package net.aqualoco.sec.platform;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.network.SeamlessSleepPacket;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

// Forge networking bridge that owns channel setup and payload dispatch.
public class ForgeNetworkHelper implements INetworkHelper {

    // Small client-only handler contract used by channel callbacks.
    interface ClientHandler {
        void handleStart(SleepAnimationStartPayload payload);
        void handleStop(SleepAnimationStopPayload payload);
        void handleServerConfig(ServerConfigSyncPayload payload);
    }

    private static final String PROTOCOL_VERSION = "1";
    private static final ResourceLocation CHANNEL_ID =
            new ResourceLocation(Constants.MOD_ID, "network");
    private static SimpleChannel channel;
    private static boolean registered;
    private static ClientHandler clientHandler;

    @Override
    public void registerPayloads() {
        if (registered) {
            return;
        }
        registered = true;

        channel = NetworkRegistry.newSimpleChannel(
                CHANNEL_ID,
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        int packetId = 0;
        channel.registerMessage(
                packetId++,
                SleepAnimationStartPayload.class,
                SleepAnimationStartPayload::write,
                SleepAnimationStartPayload::read,
                ForgeNetworkHelper::handleStart,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        channel.registerMessage(
                packetId++,
                SleepAnimationStopPayload.class,
                SleepAnimationStopPayload::write,
                SleepAnimationStopPayload::read,
                ForgeNetworkHelper::handleStop,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        channel.registerMessage(
                packetId,
                ServerConfigSyncPayload.class,
                ServerConfigSyncPayload::write,
                ServerConfigSyncPayload::read,
                ForgeNetworkHelper::handleServerConfig,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    @Override
    public void registerClientHandlers() {
        if (clientHandler == null) {
            clientHandler = new ForgeClientNetworkHandler();
        }
    }

    @Override
    public void sendToPlayers(ServerLevel world, SeamlessSleepPacket payload) {
        if (channel == null) {
            return;
        }

        for (ServerPlayer player : world.players()) {
            if (payload instanceof SleepAnimationStartPayload startPayload) {
                channel.send(PacketDistributor.PLAYER.with(() -> player), startPayload);
                continue;
            }
            if (payload instanceof SleepAnimationStopPayload stopPayload) {
                channel.send(PacketDistributor.PLAYER.with(() -> player), stopPayload);
                continue;
            }
            if (payload instanceof ServerConfigSyncPayload configPayload) {
                channel.send(PacketDistributor.PLAYER.with(() -> player), configPayload);
                continue;
            }
            Constants.warn("Ignoring unsupported packet type on Forge network helper: {}", payload.getClass().getName());
        }
    }

    private static void handleStart(SleepAnimationStartPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        handleClientPacket(contextSupplier, () -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleStart(payload);
            }
        });
    }

    private static void handleStop(SleepAnimationStopPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        handleClientPacket(contextSupplier, () -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleStop(payload);
            }
        });
    }

    private static void handleServerConfig(ServerConfigSyncPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        handleClientPacket(contextSupplier, () -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleServerConfig(payload);
            }
        });
    }

    private static void handleClientPacket(Supplier<NetworkEvent.Context> contextSupplier, Runnable work) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(work);
        context.setPacketHandled(true);
    }
}
