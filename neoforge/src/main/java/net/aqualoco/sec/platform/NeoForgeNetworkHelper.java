package net.aqualoco.sec.platform;

import net.aqualoco.sec.SeamlessSleep;
import net.aqualoco.sec.compat.VivecraftCompat;
import net.aqualoco.sec.network.BedLookNetworking;
import net.aqualoco.sec.network.BedLookSyncPayload;
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
import net.aqualoco.sec.network.VivecraftVrStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

// NeoForge networking bridge that registers payload handlers and dispatches to clients.
public class NeoForgeNetworkHelper implements INetworkHelper {

    // Small client-only handler contract used by payload callbacks.
    interface ClientHandler {
        void handleBedHudSleepProgress(BedHudSleepProgressPayload payload);
        void handleStart(SleepAnimationStartPayload payload);
        void handleStop(SleepAnimationStopPayload payload);
        void handleServerConfig(ServerConfigSyncPayload payload);
        void handleServerConfigAccess(ServerConfigAccessS2CPayload payload);
        void handleServerConfigUpdateResult(ServerConfigUpdateResultS2CPayload payload);
        void handleServerHello(ServerHelloS2CPayload payload);
    }

    private static boolean registered;
    private static ClientHandler clientHandler;

    @Override
    public void registerPayloads() {
        if (registered) {
            return;
        }
        registered = true;

        SeamlessSleep.eventBus.addListener(NeoForgeNetworkHelper::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                BedHudSleepProgressPayload.ID,
                BedHudSleepProgressPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleBedHudSleepProgress
        );
        registrar.playToClient(
                SleepAnimationStartPayload.ID,
                SleepAnimationStartPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleStart
        );
        registrar.playToClient(
                SleepAnimationStopPayload.ID,
                SleepAnimationStopPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleStop
        );
        registrar.playToClient(
                ServerConfigSyncPayload.ID,
                ServerConfigSyncPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleServerConfig
        );
        registrar.playToClient(
                ServerConfigAccessS2CPayload.ID,
                ServerConfigAccessS2CPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleServerConfigAccess
        );
        registrar.playToClient(
                ServerConfigUpdateResultS2CPayload.ID,
                ServerConfigUpdateResultS2CPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleServerConfigUpdateResult
        );
        registrar.playToClient(
                ServerHelloS2CPayload.ID,
                ServerHelloS2CPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleServerHello
        );
        registrar.playToServer(
                ClientHelloC2SPayload.ID,
                ClientHelloC2SPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleClientHello
        );
        registrar.playToServer(
                ServerConfigAccessRequestC2SPayload.ID,
                ServerConfigAccessRequestC2SPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleServerConfigAccessRequest
        );
        registrar.playToServer(
                ServerConfigUpdateC2SPayload.ID,
                ServerConfigUpdateC2SPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleServerConfigUpdate
        );
        registrar.playToServer(
                BedLookSyncPayload.ID,
                BedLookSyncPayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleBedLookSync
        );
        registrar.playToServer(
                VivecraftVrStatePayload.ID,
                VivecraftVrStatePayload.CODEC.cast(),
                NeoForgeNetworkHelper::handleVivecraftVrState
        );
    }

    @Override
    public void registerClientHandlers() {
        if (clientHandler == null) {
            clientHandler = new NeoForgeClientNetworkHandler();
        }
    }

    @Override
    public void sendToPlayers(ServerLevel world, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersInDimension(world, payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player != null
                && player.connection != null
                && type != null
                && NetworkRegistry.hasChannel(player.connection.getConnection(), ConnectionProtocol.PLAY, type.id());
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        Minecraft client = Minecraft.getInstance();
        return client.getConnection() != null
                && type != null
                && NetworkRegistry.hasChannel(client.getConnection().getConnection(), ConnectionProtocol.PLAY, type.id());
    }

    private static void handleBedHudSleepProgress(BedHudSleepProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleBedHudSleepProgress(payload);
            }
        });
    }

    private static void handleStart(SleepAnimationStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleStart(payload);
            }
        });
    }

    private static void handleStop(SleepAnimationStopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleStop(payload);
            }
        });
    }

    private static void handleServerConfig(ServerConfigSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleServerConfig(payload);
            }
        });
    }

    private static void handleServerConfigAccess(ServerConfigAccessS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleServerConfigAccess(payload);
            }
        });
    }

    private static void handleServerConfigUpdateResult(ServerConfigUpdateResultS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleServerConfigUpdateResult(payload);
            }
        });
    }

    private static void handleServerHello(ServerHelloS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.handleServerHello(payload);
            }
        });
    }

    private static void handleClientHello(ClientHelloC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServerSeamlessClientPresenceManager.handleClientHello(serverPlayer, payload);
            }
        });
    }

    private static void handleServerConfigAccessRequest(ServerConfigAccessRequestC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServerConfigMutationService.handleAccessRequest(serverPlayer);
            }
        });
    }

    private static void handleServerConfigUpdate(ServerConfigUpdateC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServerConfigMutationService.handleRemoteUpdate(serverPlayer, payload);
            }
        });
    }

    private static void handleBedLookSync(BedLookSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                BedLookNetworking.handleServer(serverPlayer, payload);
            }
        });
    }

    private static void handleVivecraftVrState(VivecraftVrStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                VivecraftCompat.handleClientVrState(serverPlayer, payload);
            }
        });
    }
}
