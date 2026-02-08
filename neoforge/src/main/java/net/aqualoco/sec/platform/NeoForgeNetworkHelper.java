package net.aqualoco.sec.platform;

import net.aqualoco.sec.SeamlessSleep;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// NeoForge networking bridge that registers payload handlers and dispatches to clients.
public class NeoForgeNetworkHelper implements INetworkHelper {

    // Small client-only handler contract used by payload callbacks.
    interface ClientHandler {
        void handleStart(SleepAnimationStartPayload payload);
        void handleStop(SleepAnimationStopPayload payload);
        void handleServerConfig(ServerConfigSyncPayload payload);
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
}
