package net.aqualoco.sec.platform;

import net.aqualoco.sec.network.BedHudSleepProgressPayload;
import net.aqualoco.sec.network.BedLookNetworking;
import net.aqualoco.sec.network.BedLookSyncPayload;
import net.aqualoco.sec.config.ServerConfigMutationService;
import net.aqualoco.sec.handshake.ServerSeamlessClientPresenceManager;
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
import net.aqualoco.sec.compat.VivecraftCompat;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// Fabric networking bridge for payload registration and player sends.
public class FabricNetworkHelper implements INetworkHelper {

    private static Method seamlesssleep$clientSendMethod;
    private static Method seamlesssleep$clientCanSendMethod;
    private static boolean seamlesssleep$clientSendResolved;
    private static boolean seamlesssleep$clientCanSendResolved;

    @Override
    public void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(
                SleepAnimationStartPayload.ID,
                SleepAnimationStartPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                SleepAnimationStopPayload.ID,
                SleepAnimationStopPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                ServerConfigSyncPayload.ID,
                ServerConfigSyncPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                ServerConfigAccessS2CPayload.ID,
                ServerConfigAccessS2CPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                ServerConfigUpdateResultS2CPayload.ID,
                ServerConfigUpdateResultS2CPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                BedHudSleepProgressPayload.ID,
                BedHudSleepProgressPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                ServerHelloS2CPayload.ID,
                ServerHelloS2CPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                ClientHelloC2SPayload.ID,
                ClientHelloC2SPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                ServerConfigAccessRequestC2SPayload.ID,
                ServerConfigAccessRequestC2SPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                ServerConfigUpdateC2SPayload.ID,
                ServerConfigUpdateC2SPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                BedLookSyncPayload.ID,
                BedLookSyncPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                VivecraftVrStatePayload.ID,
                VivecraftVrStatePayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                BedLookSyncPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> BedLookNetworking.handleServer(context.player(), payload)
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(
                VivecraftVrStatePayload.ID,
                (payload, context) -> context.server().execute(
                        () -> VivecraftCompat.handleClientVrState(context.player(), payload)
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ClientHelloC2SPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> ServerSeamlessClientPresenceManager.handleClientHello(context.player(), payload)
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ServerConfigAccessRequestC2SPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> ServerConfigMutationService.handleAccessRequest(context.player())
                )
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ServerConfigUpdateC2SPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> ServerConfigMutationService.handleRemoteUpdate(context.player(), payload)
                )
        );
    }

    @Override
    public void registerClientHandlers() {
        FabricClientNetworkHandler.register();
    }

    @Override
    public void sendToPlayers(ServerLevel world, CustomPacketPayload payload) {
        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        Method sendMethod = seamlesssleep$resolveClientSendMethod();
        if (sendMethod == null) {
            throw new IllegalStateException("Fabric ClientPlayNetworking.send(CustomPacketPayload) is unavailable");
        }

        try {
            sendMethod.invoke(null, payload);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new RuntimeException("Failed to send Fabric client payload", exception);
        }
    }

    @Override
    public boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player != null && type != null && ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        Method canSendMethod = seamlesssleep$resolveClientCanSendMethod();
        if (canSendMethod == null || type == null) {
            return false;
        }

        try {
            Object result = canSendMethod.invoke(null, type);
            return result instanceof Boolean bool && bool;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

    private static Method seamlesssleep$resolveClientSendMethod() {
        if (seamlesssleep$clientSendResolved) {
            return seamlesssleep$clientSendMethod;
        }

        seamlesssleep$clientSendResolved = true;
        try {
            Class<?> clientNetworkingClass = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            seamlesssleep$clientSendMethod = clientNetworkingClass.getMethod("send", CustomPacketPayload.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            seamlesssleep$clientSendMethod = null;
        }

        return seamlesssleep$clientSendMethod;
    }

    private static Method seamlesssleep$resolveClientCanSendMethod() {
        if (seamlesssleep$clientCanSendResolved) {
            return seamlesssleep$clientCanSendMethod;
        }

        seamlesssleep$clientCanSendResolved = true;
        try {
            Class<?> clientNetworkingClass = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            seamlesssleep$clientCanSendMethod = clientNetworkingClass.getMethod("canSend", CustomPacketPayload.Type.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            seamlesssleep$clientCanSendMethod = null;
        }

        return seamlesssleep$clientCanSendMethod;
    }
}
